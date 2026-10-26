package net.devemperor.tunertogo.tuner

import net.devemperor.tunertogo.audio.PitchEstimate
import net.devemperor.tunertogo.music.Pitch
import kotlin.math.abs
import kotlin.math.roundToInt

/** Everything the processor needs to interpret a pitch musically. */
data class TunerConfig(
    val referenceA4: Double,
    /** Open-string MIDI notes, or empty for chromatic mode. */
    val strings: List<Int>,
    /** Index into [strings] the user pinned, or null for automatic string detection. */
    val lockedString: Int?,
    val toleranceCents: Double,
    /** Signals quieter than this (dBFS) are ignored. */
    val gateDb: Double,
)

enum class SignalState {
    /** Nothing playing. */
    IDLE,

    /** A note is sounding right now. */
    ACTIVE,

    /** The note just stopped; its last reading stays on screen for a moment. */
    HOLD,
}

data class TunerReading(
    val state: SignalState,
    /** Sounding target note (nearest semitone, or the chosen string). */
    val targetMidi: Int = 0,
    val stringIndex: Int? = null,
    /** Deviation from [targetMidi], positive = sharp. */
    val cents: Double = 0.0,
    val frequency: Double = 0.0,
    val targetFrequency: Double = 0.0,
    val inTune: Boolean = false,
    /** True only for the reading in which [inTune] switched on. */
    val justTuned: Boolean = false,
    val levelDb: Double = -120.0,
) {
    companion object {
        val Idle = TunerReading(SignalState.IDLE)
    }
}

/**
 * Turns the raw, jittery stream of pitch estimates into a stable tuner reading:
 * - rejects quiet and noisy frames (level gate, YIN clarity),
 * - ignores single-frame outliers such as octave slips and pick attacks, but follows a new note
 *   once it has been heard in two consecutive frames,
 * - smooths with a median of the last frames plus an exponential average,
 * - keeps the current note/string unless another one is clearly closer (no flicker at the
 *   boundary between two notes),
 * - reports "in tune" only after the pitch stayed within tolerance for a short dwell time,
 * - holds the last reading for a while after the string stops ringing.
 *
 * Pure Kotlin and deterministic (time is passed in), so it is fully unit-tested.
 */
class TunerProcessor {
    private var config: TunerConfig? = null
    private val history = ArrayDeque<Double>()
    private var smoothedMidi = Double.NaN
    private var pendingMidi = Double.NaN
    private var pendingCount = 0
    private var lastValidAt = Long.MIN_VALUE
    private var targetMidi: Int? = null
    private var stringIndex: Int? = null
    private var inTuneSince: Long? = null
    private var inTune = false

    fun process(estimate: PitchEstimate, timeMillis: Long, config: TunerConfig): TunerReading {
        applyConfig(config)
        val valid = estimate.hasPitch &&
            estimate.clarity >= MIN_CLARITY &&
            estimate.levelDb >= config.gateDb
        val accepted = valid && accept(Pitch.midiOf(estimate.frequency, config.referenceA4), timeMillis)

        if (smoothedMidi.isNaN()) return TunerReading.Idle.copy(levelDb = estimate.levelDb)
        val state = when {
            accepted || (valid && timeMillis - lastValidAt <= HOLD_MILLIS) -> SignalState.ACTIVE
            timeMillis - lastValidAt <= HOLD_MILLIS -> SignalState.HOLD
            else -> {
                reset()
                return TunerReading.Idle.copy(levelDb = estimate.levelDb)
            }
        }

        val target = chooseTarget(config)
        val cents = (smoothedMidi - target) * 100.0
        var justTuned = false
        if (state == SignalState.ACTIVE) {
            justTuned = updateInTune(abs(cents), timeMillis, config.toleranceCents)
        } else if (!inTune) {
            // Silence never counts towards the in-tune dwell time.
            inTuneSince = null
        }

        return TunerReading(
            state = state,
            targetMidi = target,
            stringIndex = stringIndex,
            cents = cents,
            frequency = Pitch.frequencyOf(smoothedMidi, config.referenceA4),
            targetFrequency = Pitch.frequencyOf(target, config.referenceA4),
            inTune = inTune,
            justTuned = justTuned,
            levelDb = estimate.levelDb,
        )
    }

    fun reset() {
        history.clear()
        smoothedMidi = Double.NaN
        pendingMidi = Double.NaN
        pendingCount = 0
        lastValidAt = Long.MIN_VALUE
        clearTarget()
    }

    private fun applyConfig(new: TunerConfig) {
        val old = config
        config = new
        if (old == null) return
        if (old.referenceA4 != new.referenceA4 || old.strings != new.strings) {
            reset()
        } else if (old.lockedString != new.lockedString || old.toleranceCents != new.toleranceCents) {
            clearTarget()
        }
    }

    private fun clearTarget() {
        targetMidi = null
        stringIndex = null
        inTuneSince = null
        inTune = false
    }

    /** Returns false if the frame was discarded as an outlier. */
    private fun accept(midi: Double, time: Long): Boolean {
        // After a real gap the string was plucked again (and maybe the peg turned in between):
        // start over instead of mixing the old pitch into the median.
        val fresh = smoothedMidi.isNaN() || time - lastValidAt > REPLUCK_GAP_MILLIS
        if (!fresh && abs(midi - smoothedMidi) > JUMP_SEMITONES) {
            if (!pendingMidi.isNaN() && abs(midi - pendingMidi) < PENDING_MATCH_SEMITONES) {
                pendingCount++
            } else {
                pendingMidi = midi
                pendingCount = 1
            }
            if (pendingCount < CONFIRM_FRAMES) return false
            startNote(pendingMidi)
            history.addLast(midi)
            smoothedMidi = median()
        } else if (fresh) {
            startNote(midi)
        } else {
            pendingMidi = Double.NaN
            pendingCount = 0
            history.addLast(midi)
            if (history.size > HISTORY_SIZE) history.removeFirst()
            smoothedMidi += SMOOTHING * (median() - smoothedMidi)
        }
        lastValidAt = time
        return true
    }

    private fun startNote(midi: Double) {
        history.clear()
        history.addLast(midi)
        smoothedMidi = midi
        pendingMidi = Double.NaN
        pendingCount = 0
        clearTarget()
    }

    private fun median(): Double {
        val sorted = history.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
    }

    private fun chooseTarget(config: TunerConfig): Int {
        val previous = targetMidi
        val strings = config.strings
        val locked = config.lockedString
        val target = when {
            strings.isEmpty() -> {
                stringIndex = null
                if (previous != null && abs(smoothedMidi - previous) < NOTE_HYSTERESIS) previous
                else smoothedMidi.roundToInt()
            }
            locked != null && locked in strings.indices -> {
                stringIndex = locked
                strings[locked]
            }
            else -> {
                val nearest = strings.indices.minBy { abs(smoothedMidi - strings[it]) }
                val current = stringIndex
                val chosen = if (current == null || current !in strings.indices || nearest == current) {
                    nearest
                } else if (abs(smoothedMidi - strings[nearest]) + STRING_HYSTERESIS < abs(smoothedMidi - strings[current])) {
                    nearest
                } else {
                    current
                }
                stringIndex = chosen
                strings[chosen]
            }
        }
        if (target != previous) {
            inTuneSince = null
            inTune = false
        }
        targetMidi = target
        return target
    }

    /** Returns true when the reading just became in tune. */
    private fun updateInTune(absCents: Double, time: Long, tolerance: Double): Boolean {
        when {
            absCents <= tolerance -> {
                val since = inTuneSince ?: time.also { inTuneSince = it }
                if (!inTune && time - since >= IN_TUNE_DWELL_MILLIS) {
                    inTune = true
                    return true
                }
            }
            absCents > tolerance + IN_TUNE_RELEASE_CENTS -> {
                inTuneSince = null
                inTune = false
            }
            !inTune -> inTuneSince = null
        }
        return false
    }

    companion object {
        /** 1 − YIN aperiodicity; strings are well above 0.9, room noise mostly below 0.7. */
        const val MIN_CLARITY = 0.80
        const val HOLD_MILLIS = 1500L

        /** Longer than a few dropped frames inside one ringing note. */
        const val REPLUCK_GAP_MILLIS = 150L
        const val IN_TUNE_DWELL_MILLIS = 250L
        const val IN_TUNE_RELEASE_CENTS = 2.0
        const val HISTORY_SIZE = 5
        const val SMOOTHING = 0.35
        const val JUMP_SEMITONES = 0.7
        const val PENDING_MATCH_SEMITONES = 0.35
        const val CONFIRM_FRAMES = 2

        /** Stay on the current note until the pitch is 60 cents away (10 cents past the midpoint). */
        const val NOTE_HYSTERESIS = 0.6

        /** Another string must be 30 cents closer before the tuner switches to it. */
        const val STRING_HYSTERESIS = 0.3
    }
}
