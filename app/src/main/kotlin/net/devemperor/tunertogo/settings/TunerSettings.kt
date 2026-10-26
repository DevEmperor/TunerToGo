package net.devemperor.tunertogo.settings

import android.content.Context
import android.util.Log
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.wear.tiles.TileService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import net.devemperor.tunertogo.music.Accidentals
import net.devemperor.tunertogo.music.Instruments
import net.devemperor.tunertogo.music.NoteNaming
import net.devemperor.tunertogo.music.Transposition
import net.devemperor.tunertogo.tile.TunerTileService
import java.io.IOException
import java.util.Locale

enum class DisplayStyle { GAUGE, STROBE }

/** Noise gate: how quiet a note may be and still be analysed. */
enum class Sensitivity(val gateDb: Double) {
    LOW(-40.0),
    NORMAL(-50.0),
    HIGH(-60.0),
}

data class TunerSettings(
    val tuningId: String = Instruments.DEFAULT_TUNING_ID,
    val referenceA4: Int = DEFAULT_REFERENCE_A4,
    val naming: NoteNaming = NoteNaming.ENGLISH,
    val accidentals: Accidentals = Accidentals.SHARPS,
    val transposition: Transposition = Transposition.C,
    val toleranceCents: Int = DEFAULT_TOLERANCE,
    val displayStyle: DisplayStyle = DisplayStyle.GAUGE,
    val haptics: Boolean = true,
    val keepScreenOn: Boolean = true,
    val sensitivity: Sensitivity = Sensitivity.NORMAL,
) {
    companion object {
        const val DEFAULT_REFERENCE_A4 = 440
        const val MIN_REFERENCE_A4 = 400
        const val MAX_REFERENCE_A4 = 480
        const val DEFAULT_TOLERANCE = 3
        val TOLERANCE_OPTIONS = listOf(1, 2, 3, 5)

        /**
         * Musicians in German-speaking, Central European and Nordic countries call B natural
         * "H"; Romance languages use fixed-do solfège. Everyone else gets letter names.
         */
        fun defaultNaming(locale: Locale = Locale.getDefault()): NoteNaming = when (locale.language) {
            "de", "cs", "sk", "pl", "hu", "sl", "hr", "da", "nb", "nn", "no", "sv", "fi", "et" -> NoteNaming.GERMAN
            "fr", "it", "es", "pt", "ca", "ro" -> NoteNaming.SOLFEGE
            else -> NoteNaming.ENGLISH
        }
    }
}

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

class SettingsRepository(context: Context) {
    private val appContext = context.applicationContext
    private val store = appContext.settingsStore

    val settings: Flow<TunerSettings> = store.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map(::read)

    suspend fun update(transform: (TunerSettings) -> TunerSettings) {
        try {
            store.edit { prefs -> write(prefs, transform(read(prefs))) }
        } catch (e: IOException) {
            // Storage full or unwritable: keep running with the settings in effect.
            Log.w(TAG, "Could not save settings", e)
            return
        }
        // The tile shows the current tuning and reference pitch.
        TileService.getUpdater(appContext).requestUpdate(TunerTileService::class.java)
    }

    private fun read(prefs: Preferences): TunerSettings {
        val defaults = TunerSettings(naming = TunerSettings.defaultNaming())
        return TunerSettings(
            tuningId = prefs[TUNING]?.takeIf { Instruments.find(it).second.id == it } ?: defaults.tuningId,
            referenceA4 = (prefs[REFERENCE_A4] ?: defaults.referenceA4)
                .coerceIn(TunerSettings.MIN_REFERENCE_A4, TunerSettings.MAX_REFERENCE_A4),
            naming = prefs[NAMING].toEnum(defaults.naming),
            accidentals = prefs[ACCIDENTALS].toEnum(defaults.accidentals),
            transposition = prefs[TRANSPOSITION].toEnum(defaults.transposition),
            toleranceCents = prefs[TOLERANCE]?.takeIf { it in TunerSettings.TOLERANCE_OPTIONS } ?: defaults.toleranceCents,
            displayStyle = prefs[DISPLAY_STYLE].toEnum(defaults.displayStyle),
            haptics = prefs[HAPTICS] ?: defaults.haptics,
            keepScreenOn = prefs[KEEP_SCREEN_ON] ?: defaults.keepScreenOn,
            sensitivity = prefs[SENSITIVITY].toEnum(defaults.sensitivity),
        )
    }

    private fun write(prefs: androidx.datastore.preferences.core.MutablePreferences, s: TunerSettings) {
        prefs[TUNING] = s.tuningId
        prefs[REFERENCE_A4] = s.referenceA4
        prefs[NAMING] = s.naming.name
        prefs[ACCIDENTALS] = s.accidentals.name
        prefs[TRANSPOSITION] = s.transposition.name
        prefs[TOLERANCE] = s.toleranceCents
        prefs[DISPLAY_STYLE] = s.displayStyle.name
        prefs[HAPTICS] = s.haptics
        prefs[KEEP_SCREEN_ON] = s.keepScreenOn
        prefs[SENSITIVITY] = s.sensitivity.name
    }

    private inline fun <reified E : Enum<E>> String?.toEnum(default: E): E =
        this?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default

    private companion object {
        const val TAG = "SettingsRepository"
        val TUNING = stringPreferencesKey("tuning")
        val REFERENCE_A4 = intPreferencesKey("reference_a4")
        val NAMING = stringPreferencesKey("naming")
        val ACCIDENTALS = stringPreferencesKey("accidentals")
        val TRANSPOSITION = stringPreferencesKey("transposition")
        val TOLERANCE = intPreferencesKey("tolerance")
        val DISPLAY_STYLE = stringPreferencesKey("display_style")
        val HAPTICS = booleanPreferencesKey("haptics")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val SENSITIVITY = stringPreferencesKey("sensitivity")
    }
}
