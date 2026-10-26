package net.devemperor.tunertogo.music

import kotlin.math.log2
import kotlin.math.pow

/**
 * Equal-temperament pitch math. Pitches are handled as (fractional) MIDI note numbers, which makes
 * cents a simple difference: one semitone is 1.0, one cent 0.01.
 */
object Pitch {
    const val A4_MIDI = 69

    /** Lowest and highest frequency the tuner reports; covers B0 (5-string bass) up to C8. */
    const val MIN_FREQUENCY = 25.0
    const val MAX_FREQUENCY = 4500.0

    fun frequencyOf(midi: Double, referenceA4: Double): Double =
        referenceA4 * 2.0.pow((midi - A4_MIDI) / 12.0)

    fun frequencyOf(midi: Int, referenceA4: Double): Double = frequencyOf(midi.toDouble(), referenceA4)

    fun midiOf(frequency: Double, referenceA4: Double): Double =
        A4_MIDI + 12.0 * log2(frequency / referenceA4)

    /** Signed distance from [reference] to [frequency] in cents. */
    fun cents(frequency: Double, reference: Double): Double = 1200.0 * log2(frequency / reference)

    /** Scientific pitch notation octave (C4 = middle C = MIDI 60). */
    fun octaveOf(midi: Int): Int = Math.floorDiv(midi, 12) - 1

    fun pitchClassOf(midi: Int): Int = Math.floorMod(midi, 12)
}
