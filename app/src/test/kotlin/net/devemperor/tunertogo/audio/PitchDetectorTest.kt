package net.devemperor.tunertogo.audio

import net.devemperor.tunertogo.music.Pitch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

class PitchDetectorTest {
    private val sampleRate = PitchSource.SAMPLE_RATE
    private val size = PitchSource.WINDOW_SIZE
    private val detector = PitchDetector(sampleRate, size)

    /** A tone with the given harmonic amplitudes (index 0 = fundamental) plus optional noise. */
    private fun tone(
        frequency: Double,
        harmonics: List<Double> = listOf(1.0),
        noise: Double = 0.0,
        phase: Double = 0.3,
        amplitude: Double = 0.5,
    ): FloatArray {
        val random = Random(7)
        val norm = harmonics.sum()
        return FloatArray(size) { i ->
            val t = i.toDouble() / sampleRate
            var v = 0.0
            harmonics.forEachIndexed { h, a -> v += a * sin(2 * PI * frequency * (h + 1) * t + phase * (h + 1)) }
            (amplitude * v / norm + noise * random.nextDouble(-1.0, 1.0)).toFloat()
        }
    }

    private fun assertPitch(expected: Double, estimate: PitchEstimate, maxCents: Double) {
        assertTrue("no pitch found for $expected Hz", estimate.hasPitch)
        val error = Pitch.cents(estimate.frequency, expected)
        assertTrue("expected $expected Hz, got ${estimate.frequency} Hz (${"%.3f".format(error)} cents)", abs(error) <= maxCents)
    }

    @Test
    fun `pure sines are measured to a fraction of a cent across the whole range`() {
        // Every note from B0 to C8 in steps of a fifth, slightly detuned so we never hit an
        // exact integer period by accident.
        var midi = 23.0
        while (midi <= 108) {
            val f = Pitch.frequencyOf(midi + 0.13, 440.0)
            val maxCents = if (f <= 2000) 0.5 else 2.0
            assertPitch(f, detector.analyze(tone(f)), maxCents)
            midi += 7
        }
    }

    @Test
    fun `guitar-like tones with a weak fundamental keep the right octave`() {
        // A watch microphone hardly picks up the fundamental of low strings.
        val weakFundamental = listOf(0.15, 1.0, 0.7, 0.45, 0.3, 0.15)
        for (midi in listOf(40, 45, 50, 55, 59, 64)) {
            val f = Pitch.frequencyOf(midi, 440.0)
            assertPitch(f, detector.analyze(tone(f, weakFundamental)), 1.0)
        }
    }

    @Test
    fun `missing fundamental of a bass string is still found`() {
        val f = Pitch.frequencyOf(28, 440.0) // E1, 41.2 Hz
        assertPitch(f, detector.analyze(tone(f, listOf(0.0, 1.0, 0.8, 0.6, 0.4))), 1.0)
    }

    @Test
    fun `dominant second harmonic does not cause an octave error`() {
        for (midi in listOf(33, 40, 52, 64)) {
            val f = Pitch.frequencyOf(midi, 440.0)
            assertPitch(f, detector.analyze(tone(f, listOf(0.12, 1.0, 0.08, 0.25))), 1.0)
        }
    }

    @Test
    fun `noisy tone at 20 dB SNR stays within two cents`() {
        val f = 196.0
        assertPitch(f, detector.analyze(tone(f, listOf(1.0, 0.5, 0.3), noise = 0.05)), 2.0)
    }

    @Test
    fun `plucked string synthesised with Karplus-Strong is measured correctly`() {
        // In this Karplus-Strong variant each delay-line slot is replaced by the average of itself
        // and its successor, so a sample comes back half a step early: the period is N − 0.5.
        // The broadband pluck lands between two samples, the classic trap for octave checks.
        val delay = 535
        val expected = sampleRate / (delay - 0.5)
        val random = Random(3)
        val line = DoubleArray(delay) { random.nextDouble(-1.0, 1.0) }
        val out = FloatArray(size + 2048)
        var index = 0
        for (i in out.indices) {
            val current = line[index]
            val next = line[(index + 1) % delay]
            line[index] = 0.996 * 0.5 * (current + next)
            out[i] = (current * 0.4).toFloat()
            index = (index + 1) % delay
        }
        // Skip the noisy attack, like the tuner does in practice.
        assertPitch(expected, detector.analyze(out.copyOfRange(2048, 2048 + size)), 1.5)
    }

    @Test
    fun `silence has no pitch and a very low level`() {
        val estimate = detector.analyze(FloatArray(size))
        assertFalse(estimate.hasPitch)
        assertTrue(estimate.levelDb < -100)
    }

    @Test
    fun `white noise is not mistaken for a clear note`() {
        val random = Random(11)
        val noise = FloatArray(size) { random.nextDouble(-0.3, 0.3).toFloat() }
        val estimate = detector.analyze(noise)
        assertTrue("clarity ${estimate.clarity}", !estimate.hasPitch || estimate.clarity < 0.8)
    }

    @Test
    fun `level is reported in dBFS`() {
        // A full-scale sine has an RMS of 1/sqrt(2), i.e. -3 dBFS.
        val estimate = detector.analyze(tone(440.0, amplitude = 1.0))
        assertEquals(-3.01, estimate.levelDb, 0.05)
    }

    @Test
    fun `detection is independent of the A4 reference`() {
        val f = 415.0 * 2.0.pow(-9 / 12.0) * 2 // a C5 in baroque pitch
        assertPitch(f, detector.analyze(tone(f, listOf(1.0, 0.4))), 0.5)
    }
}
