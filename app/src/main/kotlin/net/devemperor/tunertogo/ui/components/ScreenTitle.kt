package net.devemperor.tunertogo.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.MaterialTheme

/**
 * Title at the top of a full-screen picker. At 12 % from the top a round display is only about
 * two thirds as wide as the screen, so the title is limited to that width and shrinks (down to
 * 10 sp, the Wear OS minimum for secondary text) with long translations or a large font setting
 * instead of being cut off by the curved edge.
 */
@Composable
fun ScreenTitle(text: String, screenSize: Dp, modifier: Modifier = Modifier) {
    val style = MaterialTheme.typography.titleMedium
    val color = MaterialTheme.colorScheme.primary
    BasicText(
        text = text,
        style = style.copy(textAlign = TextAlign.Center),
        color = { color },
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = style.fontSize),
        modifier = modifier.padding(top = screenSize * 0.12f).width(screenSize * 0.66f),
    )
}
