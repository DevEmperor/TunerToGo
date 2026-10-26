package net.devemperor.tunertogo.ui.tone

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Picker
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.rememberPickerState
import net.devemperor.tunertogo.R
import net.devemperor.tunertogo.music.Instruments
import net.devemperor.tunertogo.music.NoteLabel
import net.devemperor.tunertogo.music.NoteNames
import net.devemperor.tunertogo.music.Pitch
import net.devemperor.tunertogo.tuner.TunerViewModel
import net.devemperor.tunertogo.settings.TunerSettings
import net.devemperor.tunertogo.ui.KeepScreenOn
import net.devemperor.tunertogo.ui.theme.TunerColors

/** Chromatic range offered in chromatic mode: C2 to B5. */
private val CHROMATIC_RANGE = 36..83

/**
 * Plays a sustained reference pitch. In instrument mode the wheel offers the open strings of the
 * current tuning; in chromatic mode every note from C2 to B5. Turning the wheel while the tone
 * plays glides to the new note.
 */
@Composable
fun ReferenceToneScreen(viewModel: TunerViewModel, settings: TunerSettings) {
    val tuning = remember(settings.tuningId) { Instruments.find(settings.tuningId).second }
    val notes: List<Pair<Int, NoteLabel>> = remember(tuning, settings.naming, settings.accidentals) {
        if (tuning.isChromatic) {
            CHROMATIC_RANGE.map { it to NoteNames.label(it, settings.naming, settings.accidentals) }
        } else {
            tuning.strings.map { it.midi to NoteNames.label(it.midi, settings.naming, it.accidentals) }
        }
    }
    val initial = if (tuning.isChromatic) notes.indexOfFirst { it.first == Pitch.A4_MIDI } else 0
    val state = rememberPickerState(
        initialNumberOfOptions = notes.size,
        initiallySelectedIndex = initial.coerceAtLeast(0),
        shouldRepeatOptions = tuning.isChromatic,
    )
    val playing by viewModel.playingTone.collectAsStateWithLifecycle()
    val selectedIndex by remember(state, notes) { derivedStateOf { state.selectedOptionIndex.coerceIn(notes.indices) } }
    val selectedMidi = notes[selectedIndex].first
    val locale = LocalConfiguration.current.locales[0]

    // Follow the wheel while playing.
    LaunchedEffect(state, notes) {
        snapshotFlow { state.selectedOptionIndex }.collect { index ->
            if (viewModel.playingTone.value != null) viewModel.playTone(notes[index.coerceIn(notes.indices)].first)
        }
    }
    // Never keep sounding in the background or after leaving the screen, and don't let the
    // display time out (which would pause the app) while the tone plays.
    LifecycleResumeEffect(Unit) { onPauseOrDispose { viewModel.stopTone() } }
    KeepScreenOn(playing != null)
    DisposableEffect(Unit) { onDispose { viewModel.stopTone() } }

    val frequency = Pitch.frequencyOf(selectedMidi, settings.referenceA4.toDouble())
    ScreenScaffold(timeText = {}) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val d = minOf(maxWidth, maxHeight)
            Text(
                text = stringResource(R.string.menu_reference_tone),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = d * 0.12f).fillMaxWidth(),
            )
            Picker(
                state = state,
                contentDescription = { notes[state.selectedOptionIndex.coerceIn(notes.indices)].second.text },
                modifier = Modifier.align(Alignment.Center).offset(y = -d * 0.06f).size(width = d * 0.6f, height = d * 0.38f),
            ) { index ->
                val label = notes[index].second
                Text(
                    text = buildAnnotatedString {
                        append(label.name)
                        if (label.accidental.isNotEmpty()) {
                            withStyle(SpanStyle(baselineShift = BaselineShift(0.4f), fontSize = MaterialTheme.typography.displayMedium.fontSize * 0.6f)) {
                                append(label.accidental)
                            }
                        }
                        withStyle(SpanStyle(fontSize = MaterialTheme.typography.displayMedium.fontSize * 0.45f, color = TunerColors.Muted)) {
                            append(label.octave.toString())
                        }
                    },
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = if (index == selectedIndex) FontWeight.Medium else FontWeight.Normal,
                    color = if (playing != null && index == selectedIndex) TunerColors.InTune else MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = stringResource(R.string.tuner_hz, String.format(locale, "%.2f", frequency)),
                style = MaterialTheme.typography.bodySmall,
                color = TunerColors.Muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).offset(y = d * 0.18f).fillMaxWidth(),
            )
            EdgeButton(
                onClick = { if (playing != null) viewModel.stopTone() else viewModel.playTone(selectedMidi) },
                buttonSize = EdgeButtonSize.Small,
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                Icon(
                    painter = painterResource(if (playing != null) R.drawable.ic_stop else R.drawable.ic_play_arrow),
                    contentDescription = stringResource(if (playing != null) R.string.tone_stop else R.string.tone_play),
                )
            }
        }
    }
}
