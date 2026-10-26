package net.devemperor.tunertogo.ui.tuner

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.collectLatest
import net.devemperor.tunertogo.music.NoteNames
import net.devemperor.tunertogo.settings.DisplayStyle
import net.devemperor.tunertogo.ui.theme.TunerColors
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Half the angular width of the scale: ±50 cents map to ±62° around twelve o'clock. */
private const val SPAN_DEGREES = 62f
private const val SCALE_CENTS = 50f

/** Strobe band speed in degrees per second per cent of deviation. */
private const val STROBE_DEGREES_PER_CENT = 1.6f

/**
 * The arc display along the top edge of the round screen. All dynamic values are passed as
 * lambdas and only read while drawing, so a new pitch reading redraws the canvas without
 * recomposing anything.
 *
 * @param cents deviation from the target note, positive = sharp (clamped to the scale for drawing)
 * @param presence 0 when idle, 1 while a note sounds, in between while fading
 * @param glow 0..1, raised while the note is in tune
 */
@Composable
fun TunerDial(
    style: DisplayStyle,
    cents: () -> Float,
    presence: () -> Float,
    glow: () -> Float,
    color: () -> Color,
    toleranceCents: Float,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val strobePhase = remember { mutableFloatStateOf(0f) }

    if (style == DisplayStyle.STROBE) {
        LaunchedEffect(Unit) {
            // Run the frame loop only while a note sounds, so an idle strobe costs no frames.
            snapshotFlow { presence() > 0.01f }.collectLatest { active ->
                if (!active) return@collectLatest
                var last = withFrameNanos { it }
                while (true) {
                    withFrameNanos { now ->
                        val dt = (now - last) / 1_000_000_000f
                        last = now
                        val speed = cents().coerceIn(-SCALE_CENTS, SCALE_CENTS) * STROBE_DEGREES_PER_CENT
                        strobePhase.floatValue = (strobePhase.floatValue + speed * dt) % 3600f
                    }
                }
            }
        }
    }

    Spacer(
        modifier.drawWithCache {
            val d = size.minDimension
            val labelStyle = TextStyle(color = TunerColors.Muted, fontSize = (d * 0.07f / density / fontScale).sp)
            val flat = textMeasurer.measure(NoteNames.FLAT, labelStyle)
            val sharp = textMeasurer.measure(NoteNames.SHARP, labelStyle)
            val geometry = DialGeometry(size, d)
            val notch = Path().apply {
                val tipY = geometry.center.y - geometry.radius - geometry.stroke * 0.9f
                moveTo(geometry.center.x, tipY)
                lineTo(geometry.center.x - d * 0.024f, tipY - d * 0.034f)
                lineTo(geometry.center.x + d * 0.024f, tipY - d * 0.034f)
                close()
            }
            onDrawBehind {
                val p = presence()
                val g = glow()
                val c = color()
                val value = cents()
                when (style) {
                    DisplayStyle.GAUGE -> drawGauge(geometry, value, p, g, c, toleranceCents)
                    DisplayStyle.STROBE -> drawStrobe(geometry, strobePhase.floatValue, p, g, c)
                }
                drawPath(notch, color = lerp(Color.White.copy(alpha = 0.75f), TunerColors.InTune, g))
                drawEndLabels(geometry, flat, sharp)
            }
        },
    )
}

private class DialGeometry(size: Size, val d: Float) {
    val center = Offset(size.width / 2, size.height / 2)
    val radius = d / 2 - d * 0.075f
    val stroke = d * 0.022f
    val arcTopLeft = Offset(center.x - radius, center.y - radius)
    val arcSize = Size(radius * 2, radius * 2)

    fun angleFor(cents: Float): Float = -90f + cents.coerceIn(-SCALE_CENTS, SCALE_CENTS) / SCALE_CENTS * SPAN_DEGREES

    fun pointAt(angleDegrees: Float, r: Float = radius): Offset {
        val a = Math.toRadians(angleDegrees.toDouble())
        return Offset(center.x + r * cos(a).toFloat(), center.y + r * sin(a).toFloat())
    }
}

private fun DrawScope.arc(geometry: DialGeometry, start: Float, sweep: Float, color: Color, width: Float, cap: StrokeCap = StrokeCap.Round) {
    drawArc(
        color = color,
        startAngle = start,
        sweepAngle = sweep,
        useCenter = false,
        topLeft = geometry.arcTopLeft,
        size = geometry.arcSize,
        style = Stroke(width = width, cap = cap),
    )
}

private fun DrawScope.drawGauge(
    geometry: DialGeometry,
    cents: Float,
    presence: Float,
    glow: Float,
    color: Color,
    tolerance: Float,
) {
    val d = geometry.d
    val start = -90f - SPAN_DEGREES
    val span = SPAN_DEGREES * 2

    // Soft halo along the whole scale while in tune.
    if (glow > 0f) arc(geometry, start, span, TunerColors.InTune.copy(alpha = 0.16f * glow), geometry.stroke * 3.2f)
    arc(geometry, start, span, TunerColors.Track, geometry.stroke)

    // Tick marks every 5 cents, longer every 10 and at zero.
    for (i in -10..10) {
        val angle = geometry.angleFor(i * 5f)
        val major = i % 2 == 0
        val length = when {
            i == 0 -> d * 0.06f
            major -> d * 0.034f
            else -> d * 0.02f
        }
        val outer = geometry.radius - geometry.stroke * 1.2f
        drawLine(
            color = if (major) TunerColors.TickMajor else TunerColors.Tick,
            start = geometry.pointAt(angle, outer),
            end = geometry.pointAt(angle, outer - length),
            strokeWidth = if (major) d * 0.009f else d * 0.006f,
            cap = StrokeCap.Round,
        )
    }

    // The in-tune zone.
    val zoneStart = geometry.angleFor(-tolerance)
    arc(geometry, zoneStart, geometry.angleFor(tolerance) - zoneStart, TunerColors.InTune.copy(alpha = 0.32f + 0.68f * glow), geometry.stroke)

    if (presence <= 0.001f) return

    // Deviation fill from the centre to the current position.
    val zero = geometry.angleFor(0f)
    val needle = geometry.angleFor(cents)
    arc(
        geometry,
        start = minOf(zero, needle),
        sweep = abs(needle - zero),
        color = color.copy(alpha = 0.9f * presence),
        width = geometry.stroke,
        cap = StrokeCap.Butt,
    )

    // The bead: glow, body and a bright core.
    val position = geometry.pointAt(needle)
    val glowRadius = d * (0.1f + 0.03f * glow)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = (0.5f + 0.25f * glow) * presence), Color.Transparent),
            center = position,
            radius = glowRadius,
        ),
        radius = glowRadius,
        center = position,
    )
    drawCircle(color.copy(alpha = presence), radius = d * 0.034f * (1f + 0.22f * glow), center = position)
    drawCircle(Color.White.copy(alpha = 0.9f * presence), radius = d * 0.012f, center = position)
}

private fun DrawScope.drawStrobe(
    geometry: DialGeometry,
    phase: Float,
    presence: Float,
    glow: Float,
    color: Color,
) {
    val d = geometry.d
    val start = -90f - SPAN_DEGREES
    val end = -90f + SPAN_DEGREES
    if (glow > 0f) arc(geometry, start, SPAN_DEGREES * 2, TunerColors.InTune.copy(alpha = 0.16f * glow), geometry.stroke * 3.2f)
    arc(geometry, start, SPAN_DEGREES * 2, TunerColors.Track, geometry.stroke * 0.5f)

    // Two bands like a mechanical strobe disc: the inner band moves twice as fast, so even a
    // tiny deviation is visible as motion. Standing still means in tune.
    val bands = listOf(
        Triple(geometry.radius, 5f, geometry.stroke * 1.5f),
        Triple(geometry.radius - d * 0.058f, 3.5f, geometry.stroke),
    )
    bands.forEachIndexed { index, (radius, segment, width) ->
        val period = segment * 2
        val offset = ((phase * (index + 1)) % period + period) % period
        val bandColor = lerp(TunerColors.Track, color, presence)
        var a = start - period + offset
        while (a < end) {
            val s = maxOf(a, start)
            val e = minOf(a + segment, end)
            if (e > s) {
                // Fade towards both ends of the scale.
                val mid = (s + e) / 2
                val edge = abs(mid + 90f) / SPAN_DEGREES
                val alpha = (1f - edge * edge * edge).coerceIn(0.15f, 1f)
                drawArc(
                    color = bandColor.copy(alpha = bandColor.alpha * alpha),
                    startAngle = s,
                    sweepAngle = e - s,
                    useCenter = false,
                    topLeft = Offset(geometry.center.x - radius, geometry.center.y - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(width = width, cap = StrokeCap.Butt),
                )
            }
            a += period
        }
    }
}

private fun DrawScope.drawEndLabels(geometry: DialGeometry, flat: TextLayoutResult, sharp: TextLayoutResult) {
    val r = geometry.radius
    val flatAt = geometry.pointAt(-90f - SPAN_DEGREES - 9f, r)
    val sharpAt = geometry.pointAt(-90f + SPAN_DEGREES + 9f, r)
    drawText(flat, topLeft = Offset(flatAt.x - flat.size.width / 2f, flatAt.y - flat.size.height / 2f))
    drawText(sharp, topLeft = Offset(sharpAt.x - sharp.size.width / 2f, sharpAt.y - sharp.size.height / 2f))
}
