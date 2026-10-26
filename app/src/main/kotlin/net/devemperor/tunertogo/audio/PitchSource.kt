package net.devemperor.tunertogo.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/** One analysed audio window. [silenced] is set when the system currently mutes our capture. */
data class PitchFrame(val estimate: PitchEstimate, val timeMillis: Long, val silenced: Boolean = false)

class MicrophonePermissionException : Exception("RECORD_AUDIO not granted")

class MicrophoneUnavailableException(message: String) : Exception(message)

/** A cold stream of pitch estimates: capture starts on collection and stops on cancellation. */
interface PitchSource {
    val needsMicrophonePermission: Boolean
    fun frames(): Flow<PitchFrame>

    companion object {
        const val SAMPLE_RATE = 44_100

        /** ~93 ms window: two periods of B0 (31 Hz) fit, which YIN needs for the lowest strings. */
        const val WINDOW_SIZE = 4096

        /** A new estimate every ~23 ms. */
        const val HOP_SIZE = 1024
    }
}

class MicrophonePitchSource(private val context: Context) : PitchSource {
    override val needsMicrophonePermission = true

    override fun frames(): Flow<PitchFrame> = flow {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            throw MicrophonePermissionException()
        }
        val record = openRecord() ?: throw MicrophoneUnavailableException("No usable audio source")
        try {
            record.startRecording()
            if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                throw MicrophoneUnavailableException("Recording did not start")
            }
            val detector = PitchDetector(PitchSource.SAMPLE_RATE, PitchSource.WINDOW_SIZE)
            val window = FloatArray(PitchSource.WINDOW_SIZE)
            val chunk = ShortArray(PitchSource.HOP_SIZE)
            val keep = PitchSource.WINDOW_SIZE - PitchSource.HOP_SIZE
            var filled = 0
            var frameCount = 0
            var silenced = false
            while (currentCoroutineContext().isActive) {
                var read = 0
                while (read < chunk.size) {
                    val n = record.read(chunk, read, chunk.size - read)
                    if (n < 0) throw MicrophoneUnavailableException("AudioRecord.read failed: $n")
                    read += n
                }
                System.arraycopy(window, PitchSource.HOP_SIZE, window, 0, keep)
                for (i in chunk.indices) window[keep + i] = chunk[i] / 32768f
                filled = minOf(PitchSource.WINDOW_SIZE, filled + chunk.size)
                if (filled < PitchSource.WINDOW_SIZE) continue

                // Another app with higher capture priority (e.g. the assistant) can silence us;
                // checking roughly once a second is plenty.
                if (frameCount++ % 40 == 0) {
                    silenced = record.activeRecordingConfiguration?.isClientSilenced == true
                }
                emit(PitchFrame(detector.analyze(window), SystemClock.elapsedRealtime(), silenced))
            }
        } finally {
            runCatching { record.stop() }
            record.release()
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Prefers the UNPROCESSED source (no noise suppression or AGC that would smear the pitch),
     * then VOICE_RECOGNITION (AGC off by CDD requirement), then the plain microphone.
     */
    @SuppressLint("MissingPermission") // Checked in frames() before this is called.
    private fun openRecord(): AudioRecord? {
        val minBuffer = AudioRecord.getMinBufferSize(
            PitchSource.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) return null
        val bufferBytes = max(minBuffer, PitchSource.HOP_SIZE * 2 * 4)
        val format = AudioFormat.Builder()
            .setSampleRate(PitchSource.SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .build()
        val audioManager = context.getSystemService(AudioManager::class.java)
        val sources = buildList {
            if (audioManager?.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true") {
                add(MediaRecorder.AudioSource.UNPROCESSED)
            }
            add(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            add(MediaRecorder.AudioSource.MIC)
        }
        for (source in sources) {
            val record = runCatching {
                AudioRecord.Builder()
                    .setAudioSource(source)
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(bufferBytes)
                    .build()
            }.getOrNull() ?: continue
            if (record.state == AudioRecord.STATE_INITIALIZED) return record
            record.release()
        }
        return null
    }
}

/**
 * Debug-only stand-in for the microphone: synthesises a string-like tone (fundamental plus
 * decaying harmonics and a little noise) and runs it through the real [PitchDetector], so the
 * whole pipeline can be exercised on an emulator. An optional slow drift of [driftCents] makes
 * the needle move.
 */
class SyntheticPitchSource(
    private val frequency: Double,
    private val driftCents: Double = 0.0,
) : PitchSource {
    override val needsMicrophonePermission = false

    override fun frames(): Flow<PitchFrame> = flow {
        val detector = PitchDetector(PitchSource.SAMPLE_RATE, PitchSource.WINDOW_SIZE)
        val window = FloatArray(PitchSource.WINDOW_SIZE)
        val keep = PitchSource.WINDOW_SIZE - PitchSource.HOP_SIZE
        val random = Random(42)
        var phase = 0.0
        var sample = 0L
        val hopMillis = PitchSource.HOP_SIZE * 1000L / PitchSource.SAMPLE_RATE
        while (currentCoroutineContext().isActive) {
            System.arraycopy(window, PitchSource.HOP_SIZE, window, 0, keep)
            for (i in 0 until PitchSource.HOP_SIZE) {
                val t = sample++ / PitchSource.SAMPLE_RATE.toDouble()
                val cents = driftCents * sin(2 * PI * t / 6.0)
                val f = frequency * 2.0.pow(cents / 1200.0)
                phase += 2 * PI * f / PitchSource.SAMPLE_RATE
                if (phase > 2 * PI) phase -= 2 * PI
                var v = 0.0
                for (h in 1..6) v += sin(h * phase) / (h * h * 0.6 + 0.4)
                window[keep + i] = (0.18 * v + 0.004 * random.nextDouble(-1.0, 1.0)).toFloat()
            }
            emit(PitchFrame(detector.analyze(window), SystemClock.elapsedRealtime()))
            delay(hopMillis)
        }
    }.flowOn(Dispatchers.Default)
}
