package net.devemperor.tunertogo.ui.tuner

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import net.devemperor.tunertogo.R
import net.devemperor.tunertogo.music.NoteNames
import net.devemperor.tunertogo.music.Tuning
import net.devemperor.tunertogo.settings.TunerSettings
import net.devemperor.tunertogo.ui.theme.TunerColors

/**
 * The open strings of the current tuning in a gentle arc that follows the round bezel. The
 * string being tuned is highlighted, tuned strings get a green tint, and tapping a string pins
 * the tuner to it (useful when a string is so far off that auto detection picks its neighbour).
 */
@Composable
fun StringSelector(
    tuning: Tuning,
    settings: TunerSettings,
    activeIndex: Int?,
    activeInTune: Boolean,
    activeColor: Color,
    lockedIndex: Int?,
    tunedStrings: Set<Int>,
    onToggle: (Int) -> Unit,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val count = tuning.strings.size
    val chip = stringChipSize(size, count)
    val gap = size * if (count >= 7) 0.014f else 0.022f
    val curveDepth = size * 0.05f
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.Top) {
        tuning.strings.forEachIndexed { index, string ->
            val label = NoteNames.label(string.midi, settings.naming, string.accidentals)
            val centered = if (count > 1) (index - (count - 1) / 2f) / ((count - 1) / 2f) else 0f
            StringChip(
                text = label.pitchClass,
                accessibleName = label.text,
                active = index == activeIndex,
                inTune = index == activeIndex && activeInTune,
                activeColor = activeColor,
                tuned = index in tunedStrings,
                locked = index == lockedIndex,
                diameter = chip,
                onClick = { onToggle(index) },
                modifier = Modifier.offset(y = -curveDepth * centered * centered),
            )
        }
    }
}

/** Chip diameter: seven strings need slightly smaller chips to fit the arc. */
internal fun stringChipSize(size: Dp, count: Int): Dp = size * if (count >= 7) 0.098f else 0.112f

@Composable
private fun StringChip(
    text: String,
    accessibleName: String,
    active: Boolean,
    inTune: Boolean,
    activeColor: Color,
    tuned: Boolean,
    locked: Boolean,
    diameter: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val container by animateColorAsState(
        targetValue = when {
            inTune -> TunerColors.InTune
            // The string being tuned takes the colour of its deviation, like the dial.
            active -> activeColor.copy(alpha = 0.28f)
            tuned -> TunerColors.InTune.copy(alpha = 0.18f)
            else -> scheme.surfaceContainerLow
        },
        animationSpec = tween(200),
        label = "chipContainer",
    )
    val content = when {
        inTune -> scheme.onPrimary
        active -> scheme.onSurface
        tuned -> TunerColors.InTune
        else -> scheme.onSurfaceVariant
    }
    val border = when {
        locked -> BorderStroke(diameter * 0.08f, scheme.secondary)
        active -> BorderStroke(diameter * 0.06f, activeColor)
        tuned -> BorderStroke(diameter * 0.04f, TunerColors.InTune.copy(alpha = 0.6f))
        else -> BorderStroke(diameter * 0.04f, scheme.outlineVariant)
    }
    val description = stringResource(
        when {
            locked -> R.string.tuner_string_locked
            tuned -> R.string.tuner_string_tuned
            else -> R.string.tuner_string_auto
        },
        accessibleName,
    )
    Box(
        modifier = modifier
            .size(diameter)
            .clip(CircleShape)
            .background(container)
            .border(border, CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = description
                selected = locked
            },
        contentAlignment = Alignment.Center,
    ) {
        if (tuned && !active) {
            Icon(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(diameter * 0.62f),
            )
        } else {
            Text(
                text = text,
                color = content,
                fontSize = diameter.fixedSp(if (text.length <= 1) 0.5f else if (text.length == 2) 0.4f else 0.32f),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}
