package net.devemperor.tunertogo.audio

import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.util.Log
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin

/**
 * Plays a sustained reference pitch through the watch speaker.
 *
 * Watch speakers reproduce almost nothing below ~300 Hz, so a pure sine for a low E would be
 * inaudible. The tone therefore carries a stack of harmonics: the ear reconstructs the missing
 * fundamental from them and hears the correct pitch. Frequency changes glide smoothly and the
 * tone fades in and out to avoid clicks.
 */
class ReferenceTonePlayer(
    context: Context,
    /** Called from the audio thread once the tone has faded out, however it was stopped. */
    private val onStopped: () -> Unit = {},
) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(attributes)
        .setOnAudioFocusChangeListener { change ->
            if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) stop()
        }
        .build()

    /** One play-to-stop cycle; a fading-out tone never affects the one started after it. */
    private class Session {
        @Volatile var stopRequested = false
    }

    @Volatile private var targetFrequency = 440.0
    @Volatile private var session: Session? = null
    private var thread: Thread? = null

    val isPlaying: Boolean get() = session?.stopRequested == false

    @Synchronized
    fun play(frequency: Double) {
        targetFrequency = frequency
        if (isPlaying) return
        thread?.join(200)
        audioManager?.requestAudioFocus(focusRequest)
        val current = Session()
        session = current
        thread = Thread({ render(current) }, "ReferenceTone").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    fun setFrequency(frequency: Double) {
        targetFrequency = frequency
    }

    @Synchronized
    fun stop() {
        session?.stopRequested = true
        audioManager?.abandonAudioFocusRequest(focusRequest)
    }

    private fun render(session: Session) {
        // A watch without a usable output (or one taken by a call) must not crash the app.
        try {
            renderTone(session)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Reference tone failed", e)
        } finally {
            session.stopRequested = true
            if (this.session === session) onStopped()
        }
    }

    private fun renderTone(session: Session) {
        val bufferFrames = SAMPLE_RATE / 50
        val track = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(maxOf(
                AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT),
                bufferFrames * 4 * 2,
            ))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        val buffer = FloatArray(bufferFrames)
        // Per-sample smoothing factors: ~60 ms pitch glide, 25 ms fade in, 60 ms fade out.
        val glide = 1 - exp(-1.0 / (0.060 * SAMPLE_RATE))
        val fadeInStep = 1.0 / (0.025 * SAMPLE_RATE)
        val fadeOutStep = 1.0 / (0.060 * SAMPLE_RATE)
        var logFrequency = ln(targetFrequency)
        var phase = 0.0
        var gain = 0.0
        try {
            track.play()
            while (true) {
                val stopping = session.stopRequested
                val targetLog = ln(targetFrequency)
                for (i in buffer.indices) {
                    logFrequency += (targetLog - logFrequency) * glide
                    phase += 2 * PI * exp(logFrequency) / SAMPLE_RATE
                    if (phase > 2 * PI) phase -= 2 * PI
                    gain = if (stopping) (gain - fadeOutStep).coerceAtLeast(0.0) else (gain + fadeInStep).coerceAtMost(1.0)
                    var v = 0.0
                    for (h in HARMONICS.indices) v += HARMONICS[h] * sin((h + 1) * phase)
                    buffer[i] = (v * gain * LEVEL).toFloat()
                }
                // A negative result means the track is gone (e.g. the audio server restarted).
                if (track.write(buffer, 0, buffer.size, AudioTrack.WRITE_BLOCKING) < 0) break
                if (stopping && gain <= 0.0) break
            }
        } finally {
            runCatching { track.stop() }
            track.release()
        }
    }

    companion object {
        private const val TAG = "ReferenceTonePlayer"
        private const val SAMPLE_RATE = 48_000
        private val HARMONICS = doubleArrayOf(1.0, 0.65, 0.45, 0.3, 0.2, 0.12, 0.08)
        private val LEVEL = 0.85 / HARMONICS.sum()

        /** True if the watch has a speaker (or a connected Bluetooth audio device) to play through. */
        fun canPlay(context: Context): Boolean {
            if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUDIO_OUTPUT)) return false
            val audioManager = context.getSystemService(AudioManager::class.java) ?: return false
            val outputs = buildSet {
                add(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)
                add(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    add(AudioDeviceInfo.TYPE_BLE_HEADSET)
                    add(AudioDeviceInfo.TYPE_BLE_SPEAKER)
                }
            }
            return audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.type in outputs }
        }
    }
}
