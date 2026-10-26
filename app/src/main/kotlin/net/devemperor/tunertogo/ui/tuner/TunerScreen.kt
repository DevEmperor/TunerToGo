package net.devemperor.tunertogo.ui.tuner

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.input.InputManager
import android.net.Uri
import android.provider.Settings
import android.view.InputDevice
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.requestFocusOnHierarchyActive
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.LocalContentColor
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import kotlinx.coroutines.delay
import net.devemperor.tunertogo.R
import net.devemperor.tunertogo.music.Instrument
import net.devemperor.tunertogo.music.Instruments
import net.devemperor.tunertogo.music.NoteLabel
import net.devemperor.tunertogo.music.NoteNames
import net.devemperor.tunertogo.music.Transposition
import net.devemperor.tunertogo.music.Tuning
import net.devemperor.tunertogo.settings.TunerSettings
import net.devemperor.tunertogo.tuner.SignalState
import net.devemperor.tunertogo.tuner.TunerError
import net.devemperor.tunertogo.tuner.TunerReading
import net.devemperor.tunertogo.tuner.TunerViewModel
import net.devemperor.tunertogo.ui.KeepScreenOn
import net.devemperor.tunertogo.ui.theme.TunerColors
import kotlin.math.abs
import kotlin.math.roundToInt

/** The screen stays on while tuning, but not forever: two idle minutes release it. */
private const val IDLE_SCREEN_TIMEOUT_MILLIS = 120_000L

@Composable
fun TunerScreen(
    viewModel: TunerViewModel,
    settings: TunerSettings,
    onOpenMenu: () -> Unit,
) {
    val context = LocalContext.current
    var permissionGranted by remember { mutableStateOf(context.hasMicrophonePermission()) }
    var denials by rememberSaveable { mutableIntStateOf(0) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionGranted = granted
        if (!granted) denials++
    }
    LifecycleResumeEffect(Unit) {
        permissionGranted = context.hasMicrophonePermission()
        onPauseOrDispose { }
    }
    val needsPermission = viewModel.needsMicrophonePermission && !permissionGranted
    // Ask right away on first start: the tuner is useless without the microphone.
    LaunchedEffect(needsPermission) {
        if (needsPermission && denials == 0) launcher.launch(Manifest.permission.RECORD_AUDIO)
    }

    if (needsPermission) {
        // Dismissing the system dialog also reports "denied" without a rationale, so only a
        // second refusal without rationale means "don't ask again" and needs the settings.
        val permanentlyDenied = denials >= 2 && !context.shouldShowMicRationale()
        MessageScreen(
            iconRes = R.drawable.ic_mic,
            text = stringResource(if (permanentlyDenied) R.string.permission_denied_text else R.string.permission_text),
            actionLabel = stringResource(if (permanentlyDenied) R.string.permission_open_settings else R.string.permission_allow),
            onAction = {
                if (permanentlyDenied) context.openAppSettings()
                else launcher.launch(Manifest.permission.RECORD_AUDIO)
            },
        )
    } else {
        TunerContent(viewModel, settings, onOpenMenu)
    }
}

@Composable
private fun TunerContent(
    viewModel: TunerViewModel,
    settings: TunerSettings,
    onOpenMenu: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lockedString by viewModel.lockedString.collectAsStateWithLifecycle()
    val tunedStrings by viewModel.tunedStrings.collectAsStateWithLifecycle()

    if (uiState.error == TunerError.MICROPHONE) {
        MessageScreen(
            iconRes = R.drawable.ic_mic_off,
            text = stringResource(R.string.error_microphone),
            actionLabel = stringResource(R.string.action_retry),
            onAction = viewModel::retry,
        )
        return
    }

    val reading = uiState.reading
    val idle = reading.state == SignalState.IDLE
    var keepOn by remember { mutableStateOf(true) }
    LaunchedEffect(idle, settings.keepScreenOn) {
        keepOn = settings.keepScreenOn
        if (idle && keepOn) {
            delay(IDLE_SCREEN_TIMEOUT_MILLIS)
            keepOn = false
        }
    }
    KeepScreenOn(keepOn)

    val (instrument, tuning) = remember(settings.tuningId) { Instruments.find(settings.tuningId) }
    val tolerance = settings.toleranceCents.toFloat()

    // Animated values for the dial. Springs give the bead a physical, slightly damped feel.
    val cents = animateFloatAsState(
        targetValue = if (idle) 0f else reading.cents.toFloat().coerceIn(-60f, 60f),
        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
        label = "cents",
    )
    val presence = animateFloatAsState(
        targetValue = when (reading.state) {
            SignalState.ACTIVE -> 1f
            SignalState.HOLD -> 0.45f
            SignalState.IDLE -> 0f
        },
        animationSpec = tween(durationMillis = 250),
        label = "presence",
    )
    val glow = animateFloatAsState(
        targetValue = if (reading.inTune && !idle) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "glow",
    )
    val color = animateColorAsState(
        targetValue = TunerColors.forDeviation(abs(reading.cents.toFloat()), tolerance),
        animationSpec = tween(durationMillis = 150),
        label = "color",
    )

    val edgeText = edgeLabel(instrument, tuning)
    val edgeDescription = stringResource(R.string.tuner_open_menu) + ", " + edgeText

    // Crown (or rotating bezel) and a tap on the dial step the string lock: Auto, 1 … n, Auto.
    val stringCount = tuning.strings.size
    val haptics = LocalHapticFeedback.current
    val rotaryStep = with(LocalDensity.current) { ROTARY_STEP.toPx() }
    var rotaryAccumulated by remember { mutableFloatStateOf(0f) }
    val stepLock = { direction: Int ->
        viewModel.stepStringLock(direction, stringCount)
        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
    }
    val rotaryModifier = if (tuning.isChromatic) Modifier else Modifier
        .onRotaryScrollEvent { event ->
            val delta = event.verticalScrollPixels
            if (delta * rotaryAccumulated < 0) rotaryAccumulated = 0f // direction changed
            rotaryAccumulated += delta
            if (abs(rotaryAccumulated) >= rotaryStep) {
                stepLock(if (rotaryAccumulated > 0) 1 else -1)
                rotaryAccumulated = 0f
            }
            true
        }
        .requestFocusOnHierarchyActive()
        .focusable()

    // Immersive screen: no clock, the dial owns the top edge.
    ScreenScaffold(timeText = {}) {
        BoxWithConstraints(Modifier.fillMaxSize().then(rotaryModifier)) {
            val d = minOf(maxWidth, maxHeight)
            val layout = DialLayout(d, stringCount)
            TunerDial(
                style = settings.displayStyle,
                cents = { cents.value },
                presence = { presence.value },
                glow = { glow.value },
                color = { color.value },
                toleranceCents = tolerance,
                modifier = Modifier.fillMaxSize(),
            )

            if (idle) {
                IdleIndicator(
                    levelDb = reading.levelDb,
                    silenced = uiState.silenced,
                    hint = if (tuning.isChromatic) null else stringResource(stringLockHint()),
                    size = d,
                    modifier = Modifier.align(Alignment.TopCenter).offset(y = layout.idleTop),
                )
            } else {
                Readout(
                    reading = reading,
                    settings = settings,
                    tuning = tuning,
                    layout = layout,
                    textAlpha = if (reading.state == SignalState.HOLD) 0.55f else 1f,
                    accent = color.value,
                )
            }

            if (!tuning.isChromatic) {
                StringSelector(
                    tuning = tuning,
                    settings = settings,
                    activeIndex = if (idle) null else reading.stringIndex,
                    activeInTune = reading.inTune && !idle,
                    activeColor = color.value,
                    lockedIndex = lockedString,
                    tunedStrings = tunedStrings,
                    size = d,
                    modifier = Modifier.align(Alignment.TopCenter).offset(y = layout.chipsTop),
                )
                // The middle of the dial is one large touch target for picking a string.
                val pickLabel = stringResource(R.string.tuner_pick_string)
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = layout.centsTop)
                        .size(width = d * 0.6f, height = layout.chipsTop - layout.centsTop)
                        .clickable(
                            interactionSource = null,
                            indication = null,
                            onClickLabel = pickLabel,
                            role = Role.Button,
                        ) { stepLock(1) },
                )
            } else {
                CalibrationHint(settings, d, Modifier.align(Alignment.TopCenter).offset(y = layout.chipsTop))
            }

            EdgeButton(
                onClick = onOpenMenu,
                buttonSize = EdgeButtonSize.ExtraSmall,
                colors = ButtonDefaults.filledTonalButtonColors(),
                modifier = Modifier.align(Alignment.BottomCenter).semantics { contentDescription = edgeDescription },
            ) {
                // Shrinks a little for long names ("Chromatisch") instead of cutting them off.
                val labelStyle = MaterialTheme.typography.labelMedium
                val contentColor = LocalContentColor.current
                BasicText(
                    text = edgeText,
                    style = labelStyle,
                    color = { contentColor },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = labelStyle.fontSize),
                )
            }
        }
    }
}

/**
 * In chromatic mode, a quiet reminder when the reference pitch or the transposition differ
 * from the defaults, so a forgotten "A4 = 415 Hz" or "in B♭" cannot silently mislead.
 */
@Composable
private fun CalibrationHint(settings: TunerSettings, size: Dp, modifier: Modifier = Modifier) {
    val parts = buildList {
        if (settings.referenceA4 != TunerSettings.DEFAULT_REFERENCE_A4) {
            add(stringResource(R.string.reference_value, settings.referenceA4))
        }
        if (settings.transposition != Transposition.C) {
            add(stringResource(R.string.transposition_key, settings.transposition.label(settings.naming)))
        }
    }
    if (parts.isEmpty()) return
    Text(
        text = parts.joinToString(" · "),
        color = MaterialTheme.colorScheme.secondary,
        fontSize = size.fixedSp(0.052f, minSp = 10f),
        maxLines = 1,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * Vertical layout of the tuner face for a screen of diameter [d]. The dial and the cents
 * readout scale with the screen; everything below is stacked upwards from the edge button,
 * whose height is fixed, so small watches do not get their string row squeezed into it.
 */
internal class DialLayout(val d: Dp, stringCount: Int) {
    val chip = stringChipSize(d, stringCount)
    val chipsTop = d - EDGE_BUTTON_HEIGHT - chip - 4.dp
    val centsTop = d * 0.175f
    val centsHeight = d * 0.1f
    val hzHeight = d * 0.075f
    val hzTop = chipsTop - hzHeight - 1.dp
    val noteHeight = d * 0.27f
    val noteTop = hzTop - noteHeight
    val idleTop = centsTop + d * 0.03f

    private companion object {
        /** [EdgeButtonSize.ExtraSmall]. */
        val EDGE_BUTTON_HEIGHT = 46.dp
    }
}

@Composable
private fun edgeLabel(instrument: Instrument, tuning: Tuning): String =
    if (tuning == instrument.defaultTuning) stringResource(instrument.shortNameRes) else stringResource(tuning.nameRes)

@Composable
private fun Readout(
    reading: TunerReading,
    settings: TunerSettings,
    tuning: Tuning,
    layout: DialLayout,
    textAlpha: Float,
    accent: Color,
) {
    val size = layout.d
    val label = noteLabelFor(reading, settings, tuning)
    val roundedCents = reading.cents.roundToInt()
    val centsText = when {
        roundedCents > 0 -> "+$roundedCents"
        roundedCents < 0 -> "−${-roundedCents}"
        else -> "0"
    }
    val description = stringResource(R.string.tuner_note_description, label.text, centsText)

    Box(Modifier.fillMaxSize().alpha(textAlpha).semantics(mergeDescendants = true) { contentDescription = description }) {
        Box(
            Modifier.align(Alignment.TopCenter).offset(y = layout.centsTop).height(layout.centsHeight).fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.tuner_cents, centsText),
                color = accent,
                fontSize = size.fixedSp(0.078f),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
        Box(
            Modifier.align(Alignment.TopCenter).offset(y = layout.noteTop).height(layout.noteHeight).fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            NoteText(label, size.fixedSp(0.235f), if (reading.inTune) TunerColors.InTune else MaterialTheme.colorScheme.onSurface)
        }
        // Bottom-aligned: with a large font setting the line grows upwards into the empty space
        // below the note letter instead of into the string row.
        Box(
            Modifier.align(Alignment.TopCenter).offset(y = layout.hzTop).height(layout.hzHeight).fillMaxWidth(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Text(
                text = stringResource(R.string.tuner_hz, String.format(LocalConfiguration.current.locales[0], "%.1f", reading.frequency)),
                color = TunerColors.Muted,
                fontSize = size.fixedSp(0.058f, minSp = 12f),
                maxLines = 1,
            )
        }
    }
}

/** The target note: the chosen string's spelling in instrument mode, the written note in chromatic mode. */
private fun noteLabelFor(reading: TunerReading, settings: TunerSettings, tuning: Tuning): NoteLabel {
    val string = reading.stringIndex?.let { tuning.strings.getOrNull(it) }
    return if (string != null) {
        NoteNames.label(string.midi, settings.naming, string.accidentals)
    } else {
        NoteNames.label(reading.targetMidi + settings.transposition.semitones, settings.naming, settings.accidentals)
    }
}

/**
 * The note letter centred, with the accidental raised and the octave lowered beside it. Both
 * hang off to the right in a column as wide as on the left, so the letter itself stays centred
 * whatever is next to it.
 */
@Composable
private fun NoteText(label: NoteLabel, fontSize: TextUnit, color: Color) {
    // Long solfège names ("Sol") get a smaller letter size so they still fit the centre.
    val scale = when (label.name.length) {
        1 -> 1f
        2 -> 0.78f
        else -> 0.64f
    }
    val letterSize = fontSize * scale
    Layout(
        contents = listOf(
            { Text(label.name, color = color, fontSize = letterSize, fontWeight = FontWeight.Medium, maxLines = 1) },
            { Text(label.accidental, color = color, fontSize = letterSize * 0.4f, fontWeight = FontWeight.Medium, maxLines = 1) },
            { Text(label.octave.toString(), color = TunerColors.Muted, fontSize = letterSize * 0.33f, maxLines = 1) },
        ),
    ) { (letterMeasurables, accidentalMeasurables, octaveMeasurables), constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val letter = letterMeasurables.first().measure(loose)
        val accidental = accidentalMeasurables.first().measure(loose)
        val octave = octaveMeasurables.first().measure(loose)
        val gap = (letterSize.toPx() * 0.04f).toInt()
        val side = maxOf(accidental.width, octave.width) + gap
        val baseline = letter[FirstBaseline]
        val capHeight = letterSize.toPx() * 0.72f
        layout(letter.width + side * 2, letter.height) {
            letter.placeRelative(side, 0)
            val x = side + letter.width + gap
            // Accidental: its top a little above the top of the letter, clear of the octave.
            val accidentalTop = baseline - capHeight * 1.06f
            accidental.placeRelative(x, (accidentalTop - (accidental[FirstBaseline] - accidental.height * 0.62f)).toInt())
            // Octave: sitting on the letter's baseline.
            octave.placeRelative(x, baseline - octave[FirstBaseline])
        }
    }
}

@Composable
private fun IdleIndicator(levelDb: Double, silenced: Boolean, hint: String?, size: Dp, modifier: Modifier = Modifier) {
    // Input level from -70 dBFS (silence) to -20 dBFS (loud) drives a soft ring around the mic,
    // so the user sees that the watch is listening even before a clear note arrives.
    val level by animateFloatAsState(
        targetValue = ((levelDb + 70.0) / 50.0).toFloat().coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 120),
        label = "level",
    )
    val ringColor = MaterialTheme.colorScheme.primary
    Column(modifier.width(size * 0.8f), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(size * 0.21f), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val base = this.size.minDimension * 0.30f
                drawCircle(ringColor.copy(alpha = 0.10f + 0.25f * level), radius = base + this.size.minDimension * 0.2f * level)
                drawCircle(ringColor.copy(alpha = 0.22f), radius = base)
            }
            Icon(
                painter = painterResource(if (silenced) R.drawable.ic_mic_off else R.drawable.ic_mic),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(size * 0.11f),
            )
        }
        Spacer(Modifier.height(size * 0.02f))
        Text(
            text = stringResource(if (silenced) R.string.tuner_silenced else R.string.tuner_play_a_note),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (hint != null && !silenced) {
            Text(
                text = hint,
                fontSize = size.fixedSp(0.05f, minSp = 10f),
                color = TunerColors.Muted,
                textAlign = TextAlign.Center,
                maxLines = 2,
                lineHeight = size.fixedSp(0.055f, minSp = 11f),
            )
        }
    }
}

/** A short explanation with one action, scrollable (crown too) on small screens. */
@Composable
private fun MessageScreen(iconRes: Int, text: String, actionLabel: String, onAction: () -> Unit) {
    val listState = rememberTransformingLazyColumnState()
    ScreenScaffold(
        scrollState = listState,
        edgeButton = {
            EdgeButton(onClick = onAction, buttonSize = EdgeButtonSize.Medium) {
                Text(actionLabel, maxLines = 2, textAlign = TextAlign.Center)
            }
        },
    ) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp),
                )
            }
            item {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
            }
        }
    }
}

/**
 * Text sizes on the dial scale with the screen rather than the font setting, so the round
 * layout holds together; [minSp] keeps small text readable (and growing with the font setting)
 * on small watches.
 */
@Composable
internal fun Dp.fixedSp(fraction: Float, minSp: Float = 0f): TextUnit {
    // Dp.toSp() goes through the (non-linear since Android 14) font scale converter, so the
    // text really ends up at this many dp whatever the font setting.
    val fixed = with(LocalDensity.current) { (this@fixedSp * fraction).toSp() }
    return if (fixed.value >= minSp) fixed else minSp.sp
}

/** Turn the crown where there is one, otherwise tap. */
@Composable
private fun stringLockHint(): Int {
    val context = LocalContext.current
    val hasRotary = remember {
        val input = context.getSystemService(InputManager::class.java)
        input?.inputDeviceIds?.any { id ->
            InputDevice.getDevice(id)?.supportsSource(InputDevice.SOURCE_ROTARY_ENCODER) == true
        } == true
    }
    return if (hasRotary) R.string.tuner_hint_crown else R.string.tuner_hint_tap
}

/** Crown rotation per string step. */
private val ROTARY_STEP = 36.dp

private fun Context.hasMicrophonePermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

private fun Context.shouldShowMicRationale(): Boolean =
    (this as? Activity)?.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) ?: false

private fun Context.openAppSettings() {
    runCatching {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
    }
}
