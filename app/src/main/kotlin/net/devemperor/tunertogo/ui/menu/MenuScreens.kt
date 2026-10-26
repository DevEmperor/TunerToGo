package net.devemperor.tunertogo.ui.menu

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import net.devemperor.tunertogo.R
import net.devemperor.tunertogo.audio.ReferenceTonePlayer
import net.devemperor.tunertogo.music.Accidentals
import net.devemperor.tunertogo.music.Instrument
import net.devemperor.tunertogo.music.Instruments
import net.devemperor.tunertogo.music.NoteNames
import net.devemperor.tunertogo.music.NoteNaming
import net.devemperor.tunertogo.music.Transposition
import net.devemperor.tunertogo.music.Tuning
import net.devemperor.tunertogo.settings.DisplayStyle
import net.devemperor.tunertogo.settings.Sensitivity
import net.devemperor.tunertogo.settings.TunerSettings
import net.devemperor.tunertogo.ui.components.Header
import net.devemperor.tunertogo.ui.components.ListScreen
import net.devemperor.tunertogo.ui.components.MenuButton
import net.devemperor.tunertogo.ui.components.RadioItem
import net.devemperor.tunertogo.ui.components.SubHeader
import net.devemperor.tunertogo.ui.components.SwitchItem

/** Settings that are picked from a short list of options on their own screen. */
enum class OptionKey { TRANSPOSITION, NAMING, ACCIDENTALS, TOLERANCE, DISPLAY, SENSITIVITY }

@Composable
fun MenuScreen(
    settings: TunerSettings,
    onUpdate: ((TunerSettings) -> TunerSettings) -> Unit,
    onOpenInstruments: () -> Unit,
    onOpenReferencePitch: () -> Unit,
    onOpenReferenceTone: () -> Unit,
    onOpenOption: (OptionKey) -> Unit,
    onOpenAbout: () -> Unit,
) {
    val context = LocalContext.current
    val canPlayTone = remember { ReferenceTonePlayer.canPlay(context) }
    val (instrument, tuning) = Instruments.find(settings.tuningId)
    val instrumentSummary = instrumentSummary(instrument, tuning)
    val values = optionSummaries(settings)

    ListScreen { spec ->
        item { Header(stringResource(R.string.app_title), spec) }
        item {
            MenuButton(
                label = stringResource(R.string.menu_instrument),
                secondaryLabel = instrumentSummary,
                icon = R.drawable.ic_piano,
                spec = spec,
                onClick = onOpenInstruments,
                highlighted = true,
            )
        }
        if (canPlayTone) {
            item {
                MenuButton(
                    label = stringResource(R.string.menu_reference_tone),
                    secondaryLabel = stringResource(R.string.menu_reference_tone_summary),
                    icon = R.drawable.ic_volume_up,
                    spec = spec,
                    onClick = onOpenReferenceTone,
                )
            }
        }

        item { SubHeader(stringResource(R.string.section_tuning), spec) }
        item {
            MenuButton(
                label = stringResource(R.string.setting_reference_pitch),
                secondaryLabel = stringResource(R.string.reference_value, settings.referenceA4),
                icon = R.drawable.ic_tune,
                spec = spec,
                onClick = onOpenReferencePitch,
            )
        }
        item {
            MenuButton(
                label = stringResource(R.string.setting_transposition),
                secondaryLabel = values.getValue(OptionKey.TRANSPOSITION),
                icon = R.drawable.ic_swap_vert,
                spec = spec,
                onClick = { onOpenOption(OptionKey.TRANSPOSITION) },
            )
        }

        item { SubHeader(stringResource(R.string.section_display), spec) }
        item {
            MenuButton(
                label = stringResource(R.string.setting_display_style),
                secondaryLabel = values.getValue(OptionKey.DISPLAY),
                icon = R.drawable.ic_speed,
                spec = spec,
                onClick = { onOpenOption(OptionKey.DISPLAY) },
            )
        }
        item {
            MenuButton(
                label = stringResource(R.string.setting_note_names),
                secondaryLabel = values.getValue(OptionKey.NAMING),
                icon = R.drawable.ic_abc,
                spec = spec,
                onClick = { onOpenOption(OptionKey.NAMING) },
            )
        }
        item {
            MenuButton(
                label = stringResource(R.string.setting_accidentals),
                secondaryLabel = values.getValue(OptionKey.ACCIDENTALS),
                icon = R.drawable.ic_music_note,
                spec = spec,
                onClick = { onOpenOption(OptionKey.ACCIDENTALS) },
            )
        }
        item {
            MenuButton(
                label = stringResource(R.string.setting_tolerance),
                secondaryLabel = values.getValue(OptionKey.TOLERANCE),
                icon = R.drawable.ic_adjust,
                spec = spec,
                onClick = { onOpenOption(OptionKey.TOLERANCE) },
            )
        }

        item { SubHeader(stringResource(R.string.section_behavior), spec) }
        item {
            SwitchItem(
                label = stringResource(R.string.setting_haptics),
                checked = settings.haptics,
                onCheckedChange = { checked -> onUpdate { it.copy(haptics = checked) } },
                icon = R.drawable.ic_vibration,
                spec = spec,
            )
        }
        item {
            SwitchItem(
                label = stringResource(R.string.setting_keep_screen_on),
                secondaryLabel = stringResource(R.string.setting_keep_screen_on_summary),
                checked = settings.keepScreenOn,
                onCheckedChange = { checked -> onUpdate { it.copy(keepScreenOn = checked) } },
                icon = R.drawable.ic_light_mode,
                spec = spec,
            )
        }
        item {
            MenuButton(
                label = stringResource(R.string.setting_sensitivity),
                secondaryLabel = values.getValue(OptionKey.SENSITIVITY),
                icon = R.drawable.ic_graphic_eq,
                spec = spec,
                onClick = { onOpenOption(OptionKey.SENSITIVITY) },
            )
        }
        item {
            MenuButton(
                label = stringResource(R.string.menu_about),
                icon = R.drawable.ic_info,
                spec = spec,
                onClick = onOpenAbout,
            )
        }
    }
}

@Composable
fun InstrumentsScreen(
    settings: TunerSettings,
    onSelectTuning: (Tuning) -> Unit,
    onOpenTunings: (Instrument) -> Unit,
) {
    val current = Instruments.find(settings.tuningId).first
    ListScreen { spec ->
        item { Header(stringResource(R.string.menu_instrument), spec) }
        Instruments.all.forEach { instrument ->
            item(key = instrument.id) {
                MenuButton(
                    label = stringResource(instrument.nameRes),
                    secondaryLabel = if (instrument.defaultTuning.isChromatic) {
                        stringResource(R.string.instrument_chromatic_summary)
                    } else {
                        stringNotes(instrument.defaultTuning, settings.naming)
                    },
                    spec = spec,
                    highlighted = instrument == current,
                    onClick = {
                        if (instrument.tunings.size == 1) onSelectTuning(instrument.defaultTuning)
                        else onOpenTunings(instrument)
                    },
                )
            }
        }
    }
}

@Composable
fun TuningsScreen(
    instrument: Instrument,
    settings: TunerSettings,
    onSelectTuning: (Tuning) -> Unit,
) {
    ListScreen { spec ->
        item { Header(stringResource(instrument.nameRes), spec) }
        instrument.tunings.forEach { tuning ->
            item(key = tuning.id) {
                RadioItem(
                    label = stringResource(tuning.nameRes),
                    secondaryLabel = stringNotes(tuning, settings.naming),
                    selected = tuning.id == settings.tuningId,
                    onSelect = { onSelectTuning(tuning) },
                    spec = spec,
                )
            }
        }
    }
}

private data class Option(val label: String, val secondary: String?, val apply: (TunerSettings) -> TunerSettings, val selected: Boolean)

@Composable
fun OptionScreen(
    key: OptionKey,
    settings: TunerSettings,
    onUpdate: ((TunerSettings) -> TunerSettings) -> Unit,
    onDone: () -> Unit,
) {
    val title = stringResource(
        when (key) {
            OptionKey.TRANSPOSITION -> R.string.setting_transposition
            OptionKey.NAMING -> R.string.setting_note_names
            OptionKey.ACCIDENTALS -> R.string.setting_accidentals
            OptionKey.TOLERANCE -> R.string.setting_tolerance
            OptionKey.DISPLAY -> R.string.setting_display_style
            OptionKey.SENSITIVITY -> R.string.setting_sensitivity
        },
    )
    val options = options(key, settings)
    val hint = if (key == OptionKey.TRANSPOSITION) stringResource(R.string.transposition_hint) else null
    ListScreen { spec ->
        item { Header(title, spec) }
        options.forEach { option ->
            item {
                RadioItem(
                    label = option.label,
                    secondaryLabel = option.secondary,
                    selected = option.selected,
                    onSelect = {
                        onUpdate(option.apply)
                        onDone()
                    },
                    spec = spec,
                )
            }
        }
        if (hint != null) item { SubHeader(hint, spec) }
    }
}

@Composable
private fun options(key: OptionKey, s: TunerSettings): List<Option> = when (key) {
    OptionKey.TRANSPOSITION -> Transposition.entries.map { t ->
        Option(
            label = transpositionLabel(t, s.naming),
            secondary = when (t) {
                Transposition.C -> null
                Transposition.B_FLAT -> stringResource(R.string.transposition_b_flat_examples)
                Transposition.E_FLAT -> stringResource(R.string.transposition_e_flat_examples)
                Transposition.F -> stringResource(R.string.transposition_f_examples)
            },
            apply = { it.copy(transposition = t) },
            selected = s.transposition == t,
        )
    }
    OptionKey.NAMING -> NoteNaming.entries.map { n ->
        Option(namingLabel(n), namingExample(n), { it.copy(naming = n) }, s.naming == n)
    }
    OptionKey.ACCIDENTALS -> Accidentals.entries.map { a ->
        Option(accidentalsLabel(a), accidentalsExample(a, s.naming), { it.copy(accidentals = a) }, s.accidentals == a)
    }
    OptionKey.TOLERANCE -> TunerSettings.TOLERANCE_OPTIONS.map { cents ->
        Option(pluralStringResource(R.plurals.tolerance_value, cents, cents), null, { it.copy(toleranceCents = cents) }, s.toleranceCents == cents)
    }
    OptionKey.DISPLAY -> DisplayStyle.entries.map { d ->
        Option(displayLabel(d), null, { it.copy(displayStyle = d) }, s.displayStyle == d)
    }
    OptionKey.SENSITIVITY -> Sensitivity.entries.map { level ->
        Option(
            label = sensitivityLabel(level),
            secondary = when (level) {
                Sensitivity.LOW -> stringResource(R.string.sensitivity_low_summary)
                Sensitivity.NORMAL -> null
                Sensitivity.HIGH -> stringResource(R.string.sensitivity_high_summary)
            },
            apply = { it.copy(sensitivity = level) },
            selected = s.sensitivity == level,
        )
    }
}

@Composable
private fun optionSummaries(s: TunerSettings): Map<OptionKey, String> = mapOf(
    OptionKey.TRANSPOSITION to transpositionLabel(s.transposition, s.naming),
    OptionKey.NAMING to namingLabel(s.naming),
    OptionKey.ACCIDENTALS to accidentalsLabel(s.accidentals) + " · " + accidentalsExample(s.accidentals, s.naming),
    OptionKey.TOLERANCE to pluralStringResource(R.plurals.tolerance_value, s.toleranceCents, s.toleranceCents),
    OptionKey.DISPLAY to displayLabel(s.displayStyle),
    OptionKey.SENSITIVITY to sensitivityLabel(s.sensitivity),
)

@Composable
private fun instrumentSummary(instrument: Instrument, tuning: Tuning): String {
    val name = stringResource(instrument.nameRes)
    return if (tuning.isChromatic || instrument.tunings.size == 1) name else "$name · ${stringResource(tuning.nameRes)}"
}

/** "E A D G B E" in the user's note naming, keeping each tuning's own sharp/flat spelling. */
fun stringNotes(tuning: Tuning, naming: NoteNaming): String =
    tuning.strings.joinToString(" ") { NoteNames.label(it.midi, naming, it.accidentals).pitchClass }

@Composable
private fun transpositionLabel(t: Transposition, naming: NoteNaming): String =
    if (t == Transposition.C) stringResource(R.string.transposition_concert)
    else stringResource(R.string.transposition_key, t.label(naming))

@Composable
private fun namingLabel(n: NoteNaming): String = stringResource(
    when (n) {
        NoteNaming.ENGLISH -> R.string.naming_english
        NoteNaming.GERMAN -> R.string.naming_german
        NoteNaming.SOLFEGE -> R.string.naming_solfege
    },
)

@Composable
private fun namingExample(n: NoteNaming): String = stringResource(
    when (n) {
        NoteNaming.ENGLISH -> R.string.naming_english_example
        NoteNaming.GERMAN -> R.string.naming_german_example
        NoteNaming.SOLFEGE -> R.string.naming_solfege_example
    },
)

@Composable
private fun accidentalsLabel(a: Accidentals): String = stringResource(
    if (a == Accidentals.SHARPS) R.string.accidentals_sharps else R.string.accidentals_flats,
)

/** The black keys C♯/D♭, D♯/E♭ and F♯/G♭ spelled the chosen way. */
private fun accidentalsExample(a: Accidentals, naming: NoteNaming): String =
    listOf(61, 63, 66).joinToString(" ") { NoteNames.label(it, naming, a).pitchClass }

@Composable
private fun displayLabel(d: DisplayStyle): String = stringResource(
    if (d == DisplayStyle.GAUGE) R.string.display_gauge else R.string.display_strobe,
)

@Composable
private fun sensitivityLabel(level: Sensitivity): String = stringResource(
    when (level) {
        Sensitivity.LOW -> R.string.sensitivity_low
        Sensitivity.NORMAL -> R.string.sensitivity_normal
        Sensitivity.HIGH -> R.string.sensitivity_high
    },
)
