package net.devemperor.tunertogo.tuner

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.devemperor.tunertogo.audio.MicrophonePermissionException
import net.devemperor.tunertogo.audio.MicrophoneUnavailableException
import net.devemperor.tunertogo.audio.PitchSource
import net.devemperor.tunertogo.audio.ReferenceTonePlayer
import net.devemperor.tunertogo.music.Instruments
import net.devemperor.tunertogo.music.Pitch
import net.devemperor.tunertogo.settings.SettingsRepository
import net.devemperor.tunertogo.settings.TunerSettings

enum class TunerError { PERMISSION, MICROPHONE }

data class TunerUiState(
    val reading: TunerReading = TunerReading.Idle,
    val error: TunerError? = null,
    /** Another app currently has priority on the microphone. */
    val silenced: Boolean = false,
)

class TunerViewModel(
    application: Application,
    private val pitchSource: PitchSource,
) : AndroidViewModel(application) {
    private val repository = SettingsRepository(application)
    private val haptics = Haptics(application)

    private val _playingTone = MutableStateFlow<Int?>(null)
    /** MIDI note of the reference tone currently playing, or null. */
    val playingTone: StateFlow<Int?> = _playingTone.asStateFlow()

    // Also resets the state when the system takes the audio focus away or the output fails.
    private val tonePlayer = ReferenceTonePlayer(application) { _playingTone.value = null }

    val needsMicrophonePermission: Boolean get() = pitchSource.needsMicrophonePermission

    /** Null until the stored settings have been read (a few milliseconds after start). */
    val settings: StateFlow<TunerSettings?> =
        repository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _lockedString = MutableStateFlow<Int?>(null)
    val lockedString: StateFlow<Int?> = _lockedString.asStateFlow()

    private val _tunedStrings = MutableStateFlow<Set<Int>>(emptySet())
    val tunedStrings: StateFlow<Set<Int>> = _tunedStrings.asStateFlow()

    private val restart = MutableStateFlow(0)

    /**
     * The microphone runs only while the tuner screen collects this (it stops shortly after the
     * screen is left or the app goes to the background).
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<TunerUiState> = restart
        .flatMapLatest { tunerFlow() }
        // Forget the last reading when the microphone stops, so returning to the tuner never
        // shows an old note for the moment the first new audio window takes.
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS, replayExpirationMillis = 0),
            TunerUiState(),
        )

    init {
        // A tick means "in tune with the current tuning and reference pitch"; both reset them.
        settings.filterNotNull()
            .map { it.tuningId to it.referenceA4 }
            .distinctUntilChanged()
            .onEach {
                _lockedString.value = null
                _tunedStrings.value = emptySet()
            }
            .launchIn(viewModelScope)
        settings.filterNotNull()
            .map { it.referenceA4 }
            .distinctUntilChanged()
            .onEach { a4 -> _playingTone.value?.let { tonePlayer.setFrequency(Pitch.frequencyOf(it, a4.toDouble())) } }
            .launchIn(viewModelScope)
    }

    private fun tunerFlow(): Flow<TunerUiState> = flow {
        val processor = TunerProcessor()
        var configKey: Pair<TunerSettings, Int?>? = null
        var config: TunerConfig? = null
        // Always process the newest frame: if the UI thread is briefly busy, stale frames are
        // dropped instead of queueing up as latency.
        pitchSource.frames().conflate().collect { frame ->
            val current = settings.value ?: return@collect
            val key = current to _lockedString.value
            val active = config?.takeIf { key == configKey } ?: current.toConfig(key.second).also {
                configKey = key
                config = it
            }
            val reading = processor.process(frame.estimate, frame.timeMillis, active)
            if (reading.justTuned) onTuned(reading, current)
            emit(TunerUiState(reading = reading.quantizedWhileIdle(), silenced = frame.silenced))
        }
    }.distinctUntilChanged().catch { e ->
        when (e) {
            is MicrophonePermissionException -> emit(TunerUiState(error = TunerError.PERMISSION))
            is MicrophoneUnavailableException -> emit(TunerUiState(error = TunerError.MICROPHONE))
            else -> throw e
        }
    }

    private fun onTuned(reading: TunerReading, settings: TunerSettings) {
        if (settings.haptics) haptics.inTune()
        reading.stringIndex?.let { index -> _tunedStrings.update { it + index } }
    }

    fun retry() {
        restart.update { it + 1 }
    }

    /** Pins the tuner to one string; tapping the pinned string again returns to auto mode. */
    fun toggleStringLock(index: Int) {
        _lockedString.update { if (it == index) null else index }
    }

    fun updateSettings(transform: (TunerSettings) -> TunerSettings) {
        viewModelScope.launch { repository.update(transform) }
    }

    fun playTone(midi: Int) {
        val a4 = (settings.value ?: TunerSettings()).referenceA4.toDouble()
        tonePlayer.play(Pitch.frequencyOf(midi, a4))
        _playingTone.value = midi
    }

    fun stopTone() {
        tonePlayer.stop()
        _playingTone.value = null
    }

    override fun onCleared() {
        tonePlayer.stop()
    }

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 600L

        fun factory(pitchSource: PitchSource): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                TunerViewModel(checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]), pitchSource)
            }
        }
    }
}

/**
 * While nothing plays, only the input level changes from frame to frame. Rounding it to 3 dB
 * steps lets [distinctUntilChanged] drop most idle frames, so an idle tuner does not redraw
 * 43 times a second.
 */
private fun TunerReading.quantizedWhileIdle(): TunerReading =
    if (state == SignalState.IDLE) copy(levelDb = Math.round(levelDb / 3.0) * 3.0) else this

fun TunerSettings.toConfig(lockedString: Int?): TunerConfig = TunerConfig(
    referenceA4 = referenceA4.toDouble(),
    strings = Instruments.find(tuningId).second.strings.map { it.midi },
    lockedString = lockedString,
    toleranceCents = toleranceCents.toDouble(),
    gateDb = sensitivity.gateDb,
)
