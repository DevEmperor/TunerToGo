package net.devemperor.tunertogo.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme

/** Semantic colours of the tuner display, independent of the Material colour roles. */
object TunerColors {
    val InTune = Color(0xFF45E6A6)
    val Close = Color(0xFFC6E86A)
    val Off = Color(0xFFFFB84D)
    val Far = Color(0xFFFF6E61)
    val Track = Color(0x26FFFFFF)
    val Tick = Color(0x59FFFFFF)
    val TickMajor = Color(0x99FFFFFF)
    val Muted = Color(0xFF9AA8A2)

    /**
     * Green inside the tolerance, then gradually lime, amber and coral the further off the note
     * is, so the colour alone already tells how much to turn the peg.
     */
    fun forDeviation(absCents: Float, tolerance: Float): Color = when {
        absCents <= tolerance -> InTune
        absCents <= 10f -> lerp(InTune, Close, (absCents - tolerance) / (10f - tolerance).coerceAtLeast(1f))
        absCents <= 25f -> lerp(Close, Off, (absCents - 10f) / 15f)
        absCents <= 45f -> lerp(Off, Far, (absCents - 25f) / 20f)
        else -> Far
    }
}

private val TunerColorScheme = ColorScheme(
    primary = Color(0xFF6FE8BC),
    primaryDim = Color(0xFF52C99E),
    primaryContainer = Color(0xFF0E5440),
    onPrimary = Color(0xFF00382A),
    onPrimaryContainer = Color(0xFFA6F7D8),
    secondary = Color(0xFFFFC772),
    secondaryDim = Color(0xFFE5AC55),
    secondaryContainer = Color(0xFF5A3D00),
    onSecondary = Color(0xFF412A00),
    onSecondaryContainer = Color(0xFFFFDEAD),
    tertiary = Color(0xFFFF9A8C),
    tertiaryDim = Color(0xFFE57D70),
    tertiaryContainer = Color(0xFF6A1F16),
    onTertiary = Color(0xFF52120A),
    onTertiaryContainer = Color(0xFFFFD6CF),
    surfaceContainerLow = Color(0xFF141B19),
    surfaceContainer = Color(0xFF1C2421),
    surfaceContainerHigh = Color(0xFF26302C),
    onSurface = Color(0xFFE6F0EB),
    onSurfaceVariant = Color(0xFFB5C4BD),
    outline = Color(0xFF7F8E87),
    outlineVariant = Color(0xFF3D4844),
    background = Color.Black,
    onBackground = Color.White,
    error = Color(0xFFFFB4AB),
    errorDim = Color(0xFFE5978E),
    errorContainer = Color(0xFF93000A),
    onError = Color(0xFF690005),
    onErrorContainer = Color(0xFFFFDAD6),
)

@Composable
fun TunerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = TunerColorScheme, content = content)
}
