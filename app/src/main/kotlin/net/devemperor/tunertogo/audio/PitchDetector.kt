package net.devemperor.tunertogo.audio

import net.devemperor.tunertogo.music.Pitch
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Result of analysing one audio window. [frequency] is NaN when no periodic signal was found;
 * [clarity] is 1 for a perfectly periodic signal and falls towards 0 for noise; [levelDb] is the
 * RMS level in dBFS.
 */
data class PitchEstimate(val frequency: Double, val clarity: Double, val levelDb: Double) {
    val hasPitch: Boolean get() = !frequency.isNaN()

    companion object {
        fun silence(levelDb: Double) = PitchEstimate(Double.NaN, 0.0, levelDb)
    }
}

/**
 * Monophonic pitch detection with the YIN algorithm (de Cheveigné & Kawahara, 2002).
 *
 * The squared difference function is computed through an FFT cross-correlation, so one analysis
 * costs two FFTs of [bufferSize] points. YIN measures periodicity rather than looking for a
 * spectral peak, which keeps it reliable for low strings whose fundamental a watch microphone
 * barely picks up. The minimum is refined by parabolic interpolation for sub-cent accuracy.
 *
 * Instances keep their scratch buffers and are not thread-safe.
 */
class PitchDetector(
    val sampleRate: Int,
    val bufferSize: Int,
    minFrequency: Double = Pitch.MIN_FREQUENCY,
    maxFrequency: Double = Pitch.MAX_FREQUENCY,
    private val threshold: Double = 0.15,
) {
    private val maxTau = min(bufferSize / 2, ceil(sampleRate / minFrequency).toInt())
    private val minTau = max(2, floor(sampleRate / maxFrequency).toInt())
    private val windowLength = bufferSize - maxTau

    private val fft = Fft(bufferSize)
    private val signal = DoubleArray(bufferSize)
    private val re = DoubleArray(bufferSize)
    private val im = DoubleArray(bufferSize)
    private val corrRe = DoubleArray(bufferSize)
    private val corrIm = DoubleArray(bufferSize)
    private val prefixEnergy = DoubleArray(bufferSize + 1)
    private val difference = DoubleArray(maxTau + 1)
    private val normalized = DoubleArray(maxTau + 1)

    /** Analyses [samples] (at least [bufferSize] values in -1..1, oldest first). */
    fun analyze(samples: FloatArray): PitchEstimate {
        require(samples.size >= bufferSize) { "Need $bufferSize samples, got ${samples.size}" }

        var mean = 0.0
        for (i in 0 until bufferSize) mean += samples[i]
        mean /= bufferSize
        var energy = 0.0
        for (i in 0 until bufferSize) {
            val v = samples[i] - mean
            signal[i] = v
            energy += v * v
        }
        val rms = sqrt(energy / bufferSize)
        val levelDb = if (rms > 1e-9) 20 * log10(rms) else -180.0
        if (rms < 1e-6) return PitchEstimate.silence(levelDb)

        computeDifference()
        val tau = findPeriod() ?: return PitchEstimate.silence(levelDb)
        val refined = refine(tau)
        val frequency = sampleRate / refined
        if (frequency !in Pitch.MIN_FREQUENCY..Pitch.MAX_FREQUENCY) return PitchEstimate.silence(levelDb)
        val clarity = (1.0 - normalized[tau]).coerceIn(0.0, 1.0)
        return PitchEstimate(frequency, clarity, levelDb)
    }

    /**
     * d(τ) = Σ (x[j] − x[j+τ])² over the first [windowLength] samples, expanded into
     * energy(window) + energy(window shifted by τ) − 2·crossCorrelation(τ).
     */
    private fun computeDifference() {
        // Pack both real inputs into one complex FFT: the window in the real part, the full
        // buffer in the imaginary part, then split the spectra using conjugate symmetry.
        for (i in 0 until bufferSize) {
            re[i] = if (i < windowLength) signal[i] else 0.0
            im[i] = signal[i]
        }
        fft.transform(re, im)
        for (k in 0 until bufferSize) {
            val nk = (bufferSize - k) and (bufferSize - 1)
            val aRe = (re[k] + re[nk]) * 0.5
            val aIm = (im[k] - im[nk]) * 0.5
            val bRe = (im[k] + im[nk]) * 0.5
            val bIm = -(re[k] - re[nk]) * 0.5
            // conj(A) * B
            corrRe[k] = aRe * bRe + aIm * bIm
            corrIm[k] = aRe * bIm - aIm * bRe
        }
        fft.transform(corrRe, corrIm, inverse = true)

        prefixEnergy[0] = 0.0
        for (i in 0 until bufferSize) prefixEnergy[i + 1] = prefixEnergy[i] + signal[i] * signal[i]
        val windowEnergy = prefixEnergy[windowLength]

        difference[0] = 0.0
        normalized[0] = 1.0
        var runningSum = 0.0
        for (tau in 1..maxTau) {
            val shiftedEnergy = prefixEnergy[tau + windowLength] - prefixEnergy[tau]
            val d = max(0.0, windowEnergy + shiftedEnergy - 2 * corrRe[tau])
            difference[tau] = d
            runningSum += d
            normalized[tau] = if (runningSum > 0) d * tau / runningSum else 1.0
        }
    }

    private fun findPeriod(): Int? {
        var tau = minTau
        var found: Int? = null
        while (tau < maxTau) {
            if (normalized[tau] < threshold) {
                while (tau + 1 < maxTau && normalized[tau + 1] < normalized[tau]) tau++
                found = tau
                break
            }
            tau++
        }
        if (found == null) {
            // No clear dip: fall back to the global minimum, reported with its (low) clarity so
            // the caller can decide whether to trust it. Minima at the range edge are artefacts.
            var best = minTau
            for (t in minTau until maxTau) if (normalized[t] < normalized[best]) best = t
            if (best <= minTau || best >= maxTau - 1) return null
            found = best
        }
        return correctOctave(found)
    }

    /**
     * YIN takes the first dip below the threshold, which lands on half the period when the odd
     * harmonics are almost missing. The true period then shows an almost perfect dip at twice
     * the lag. The test is deliberately strict: bright plucked strings whose period falls
     * between two samples also have a deeper dip at twice the lag (there it hits a whole
     * sample), only less extreme. Measured on synthetic plucks with inharmonicity, high-pass
     * and noise, "below a tenth" separates the two cases without a single false correction.
     */
    private fun correctOctave(tau: Int): Int {
        val doubled = tau * 2
        if (doubled + 2 >= maxTau) return tau
        var best = doubled
        for (t in doubled - 2..doubled + 2) if (normalized[t] < normalized[best]) best = t
        return if (normalized[tau] > 0.02 && normalized[best] < normalized[tau] * 0.1) best else tau
    }

    /** Parabolic interpolation of the raw difference function around [tau]. */
    private fun refine(tau: Int): Double {
        if (tau < 1 || tau >= maxTau) return tau.toDouble()
        val left = difference[tau - 1]
        val center = difference[tau]
        val right = difference[tau + 1]
        val denominator = left + right - 2 * center
        if (denominator <= 0) return tau.toDouble()
        val shift = ((left - right) / (2 * denominator)).coerceIn(-1.0, 1.0)
        return tau + shift
    }
}
