package net.devemperor.tunertogo.ui.menu

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CompactButton
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Picker
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.rememberPickerState
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import net.devemperor.tunertogo.R
import net.devemperor.tunertogo.settings.TunerSettings

/** A4 calibration from 400 to 480 Hz on a wheel that follows the crown. */
@Composable
fun ReferencePitchScreen(
    settings: TunerSettings,
    onUpdate: ((TunerSettings) -> TunerSettings) -> Unit,
    onDone: () -> Unit,
) {
    val min = TunerSettings.MIN_REFERENCE_A4
    val count = TunerSettings.MAX_REFERENCE_A4 - min + 1
    // Read once: rememberPickerState is keyed on the initial index, and every debounced save
    // below comes back through the settings – that must not replace the state being scrolled.
    val initialIndex = remember { settings.referenceA4 - min }
    val state = rememberPickerState(
        initialNumberOfOptions = count,
        initiallySelectedIndex = initialIndex,
        shouldRepeatOptions = false,
    )
    val scope = rememberCoroutineScope()
    // Persist while scrolling (debounced), so leaving by swipe keeps the value too.
    LaunchedEffect(state) {
        @OptIn(kotlinx.coroutines.FlowPreview::class)
        snapshotFlow { state.selectedOptionIndex }
            .drop(1)
            .debounce(300)
            .collect { index -> onUpdate { it.copy(referenceA4 = min + index) } }
    }
    val selectedIndex by remember(state) { derivedStateOf { state.selectedOptionIndex } }
    val selected = min + selectedIndex
    val hzLabel = stringResource(R.string.tuner_hz, "")

    ScreenScaffold(timeText = {}) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val d = minOf(maxWidth, maxHeight)
            Text(
                text = stringResource(R.string.setting_reference_pitch),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = d * 0.12f).fillMaxWidth(),
            )
            Row(
                modifier = Modifier.align(Alignment.Center).offset(y = -d * 0.06f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "A4",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(d * 0.17f),
                    textAlign = TextAlign.End,
                )
                Picker(
                    state = state,
                    contentDescription = { "$selected Hz" },
                    modifier = Modifier.size(width = d * 0.38f, height = d * 0.36f),
                ) { index ->
                    Text(
                        text = (min + index).toString(),
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = if (index == selectedIndex) FontWeight.Medium else FontWeight.Normal,
                    )
                }
                Text(
                    text = hzLabel.trim(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(d * 0.17f),
                )
            }
            if (selected != TunerSettings.DEFAULT_REFERENCE_A4) {
                CompactButton(
                    onClick = {
                        scope.launch { state.animateScrollToOption(TunerSettings.DEFAULT_REFERENCE_A4 - min) }
                    },
                    colors = ButtonDefaults.outlinedButtonColors(),
                    border = ButtonDefaults.outlinedButtonBorder(enabled = true),
                    modifier = Modifier.align(Alignment.Center).offset(y = d * 0.19f),
                    icon = { Icon(painterResource(R.drawable.ic_restart_alt), contentDescription = null) },
                    label = { Text("${TunerSettings.DEFAULT_REFERENCE_A4}") },
                )
            }
            EdgeButton(
                onClick = {
                    onUpdate { it.copy(referenceA4 = selected) }
                    onDone()
                },
                buttonSize = EdgeButtonSize.ExtraSmall,
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                Icon(painterResource(R.drawable.ic_check), contentDescription = stringResource(R.string.reference_value, selected))
            }
        }
    }
}
