package net.devemperor.tunertogo.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import net.devemperor.tunertogo.audio.PitchSource
import net.devemperor.tunertogo.music.Instrument
import net.devemperor.tunertogo.music.Instruments
import net.devemperor.tunertogo.music.Tuning
import net.devemperor.tunertogo.settings.TunerSettings
import net.devemperor.tunertogo.tuner.TunerViewModel
import net.devemperor.tunertogo.ui.about.AboutScreen
import net.devemperor.tunertogo.ui.menu.InstrumentsScreen
import net.devemperor.tunertogo.ui.menu.MenuScreen
import net.devemperor.tunertogo.ui.menu.OptionKey
import net.devemperor.tunertogo.ui.menu.OptionScreen
import net.devemperor.tunertogo.ui.menu.ReferencePitchScreen
import net.devemperor.tunertogo.ui.menu.TuningsScreen
import net.devemperor.tunertogo.ui.theme.TunerTheme
import net.devemperor.tunertogo.ui.tone.ReferenceToneScreen
import net.devemperor.tunertogo.ui.tuner.TunerScreen

object Routes {
    const val TUNER = "tuner"
    const val MENU = "menu"
    const val INSTRUMENTS = "instruments"
    const val TUNINGS = "tunings/{instrument}"
    const val REFERENCE_PITCH = "reference_pitch"
    const val OPTION = "option/{key}"
    const val REFERENCE_TONE = "reference_tone"
    const val ABOUT = "about"

    fun tunings(instrumentId: String) = "tunings/$instrumentId"
    fun option(key: OptionKey) = "option/${key.name}"
}

/**
 * @param initialRoutes debug-only deep link (a chain of routes pushed on start), used to take
 *   store screenshots of inner screens on the emulator.
 */
@Composable
fun TunerApp(pitchSource: PitchSource, initialRoutes: List<String> = emptyList()) {
    val viewModel: TunerViewModel = viewModel(factory = TunerViewModel.factory(pitchSource))
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val navController = rememberSwipeDismissableNavController()

    TunerTheme {
        AppScaffold {
            // Show nothing until the stored settings are loaded (a few milliseconds).
            if (settings == null) return@AppScaffold
            val update: ((TunerSettings) -> TunerSettings) -> Unit = viewModel::updateSettings
            val selectTuning: (Tuning) -> Unit = { tuning ->
                viewModel.updateSettings { it.copy(tuningId = tuning.id) }
                navController.popBackStack(Routes.TUNER, inclusive = false)
            }

            // Every destination reads the settings itself (WithSettings): the content lambdas of
            // the navigation graph are kept from when the graph was built, so settings captured
            // out here would go stale on screens that stay open, such as the menu's switches.
            // Every navigation callback goes through whenResumed(): a screen that is already
            // leaving (a quick double tap during the transition) must not navigate a second time.
            SwipeDismissableNavHost(navController = navController, startDestination = Routes.TUNER) {
                composable(Routes.TUNER) {
                    WithSettings(viewModel) { current ->
                        TunerScreen(viewModel, current, onOpenMenu = whenResumed { navController.navigate(Routes.MENU) })
                    }
                }
                composable(Routes.MENU) {
                    WithSettings(viewModel) { current ->
                        MenuScreen(
                            settings = current,
                            onUpdate = update,
                            onOpenInstruments = whenResumed { navController.navigate(Routes.INSTRUMENTS) },
                            onOpenReferencePitch = whenResumed { navController.navigate(Routes.REFERENCE_PITCH) },
                            onOpenReferenceTone = whenResumed { navController.navigate(Routes.REFERENCE_TONE) },
                            onOpenOption = whenResumed { key: OptionKey -> navController.navigate(Routes.option(key)) },
                            onOpenAbout = whenResumed { navController.navigate(Routes.ABOUT) },
                        )
                    }
                }
                composable(Routes.INSTRUMENTS) {
                    WithSettings(viewModel) { current ->
                        InstrumentsScreen(
                            settings = current,
                            onSelectTuning = whenResumed(selectTuning),
                            onOpenTunings = whenResumed { instrument: Instrument ->
                                navController.navigate(Routes.tunings(instrument.id))
                            },
                        )
                    }
                }
                composable(Routes.TUNINGS) { entry ->
                    val instrument = entry.arguments?.getString("instrument")?.let(Instruments::instrument)
                    if (instrument != null) {
                        WithSettings(viewModel) { current -> TuningsScreen(instrument, current, whenResumed(selectTuning)) }
                    }
                }
                composable(Routes.REFERENCE_PITCH) {
                    WithSettings(viewModel) { current ->
                        ReferencePitchScreen(current, update, onDone = whenResumed { navController.popBackStack() })
                    }
                }
                composable(Routes.OPTION) { entry ->
                    val key = entry.arguments?.getString("key")?.let { name -> OptionKey.entries.firstOrNull { it.name == name } }
                    if (key != null) {
                        WithSettings(viewModel) { current ->
                            OptionScreen(key, current, update, onDone = whenResumed { navController.popBackStack() })
                        }
                    }
                }
                composable(Routes.REFERENCE_TONE) {
                    WithSettings(viewModel) { current -> ReferenceToneScreen(viewModel, current) }
                }
                composable(Routes.ABOUT) {
                    AboutScreen()
                }
            }

            LaunchedEffect(Unit) { initialRoutes.forEach(navController::navigateSafely) }
        }
    }
}

/** Hands the live settings to [content] (see the comment in [TunerApp]). */
@Composable
private fun WithSettings(viewModel: TunerViewModel, content: @Composable (TunerSettings) -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    settings?.let { content(it) }
}

/** Runs [block] only while the current destination is resumed (see the comment in [TunerApp]). */
@Composable
private fun whenResumed(block: () -> Unit): () -> Unit {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    return { if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) block() }
}

@Composable
private fun <T> whenResumed(block: (T) -> Unit): (T) -> Unit {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    return { value -> if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) block(value) }
}

private fun NavHostController.navigateSafely(route: String) {
    runCatching { navigate(route) }
}
