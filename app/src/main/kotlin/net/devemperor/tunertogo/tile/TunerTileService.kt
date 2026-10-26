package net.devemperor.tunertogo.tile

import android.content.ComponentName
import androidx.wear.protolayout.ActionBuilders.launchAction
import androidx.wear.protolayout.material3.CardDefaults.filledTonalCardColors
import androidx.wear.protolayout.material3.ColorScheme
import androidx.wear.protolayout.material3.MaterialScope
import androidx.wear.protolayout.material3.Typography
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.material3.textEdgeButton
import androidx.wear.protolayout.material3.titleCard
import androidx.wear.protolayout.modifiers.clickable
import androidx.wear.protolayout.types.argb
import androidx.wear.protolayout.types.layoutString
import androidx.wear.tiles.Material3TileService
import androidx.wear.tiles.RequestBuilders.TileRequest
import androidx.wear.tiles.TileBuilders.Tile
import androidx.wear.tiles.tile
import androidx.wear.tiles.timeline
import androidx.wear.tiles.timelineEntry
import kotlinx.coroutines.flow.first
import net.devemperor.tunertogo.MainActivity
import net.devemperor.tunertogo.R
import net.devemperor.tunertogo.music.Instruments
import net.devemperor.tunertogo.settings.SettingsRepository
import net.devemperor.tunertogo.settings.TunerSettings
import net.devemperor.tunertogo.ui.menu.stringNotes

/**
 * Tile for the watch carousel: shows the current instrument and tuning and opens the tuner with
 * one tap. It is refreshed whenever the settings change (see [SettingsRepository.update]).
 */
class TunerTileService : Material3TileService(allowDynamicTheme = false, defaultColorScheme = TILE_COLORS) {

    override suspend fun MaterialScope.tileResponse(requestParams: TileRequest): Tile {
        val settings = SettingsRepository(this@TunerTileService).settings.first()
        val (instrument, tuning) = Instruments.find(settings.tuningId)
        val open = clickable(
            action = launchAction(ComponentName(this@TunerTileService, MainActivity::class.java)),
            id = "open_tuner",
        )
        val title = if (tuning.isChromatic || instrument.tunings.size == 1) {
            getString(instrument.nameRes)
        } else {
            getString(instrument.nameRes) + " · " + getString(tuning.nameRes)
        }
        val reference = getString(R.string.reference_value, settings.referenceA4)
        val detail = when {
            tuning.isChromatic -> reference
            settings.referenceA4 != TunerSettings.DEFAULT_REFERENCE_A4 -> stringNotes(tuning, settings.naming) + "\n" + reference
            else -> stringNotes(tuning, settings.naming)
        }

        val layout = primaryLayout(
            titleSlot = { text(getString(R.string.app_title).layoutString) },
            mainSlot = {
                titleCard(
                    onClick = open,
                    colors = filledTonalCardColors(),
                    title = { text(title.layoutString, maxLines = 2) },
                    content = { text(detail.layoutString, typography = Typography.BODY_MEDIUM, maxLines = 2) },
                )
            },
            bottomSlot = {
                textEdgeButton(onClick = open, labelContent = { text(getString(R.string.tile_tune).layoutString) })
            },
        )
        return tile(timeline = timeline(timelineEntry(layout)))
    }

    private companion object {
        val TILE_COLORS = ColorScheme(
            primary = 0xFF6FE8BC.toInt().argb,
            onPrimary = 0xFF00382A.toInt().argb,
            primaryContainer = 0xFF0E5440.toInt().argb,
            onPrimaryContainer = 0xFFA6F7D8.toInt().argb,
            surfaceContainer = 0xFF1C2421.toInt().argb,
            onSurface = 0xFFE6F0EB.toInt().argb,
            onSurfaceVariant = 0xFFB5C4BD.toInt().argb,
        )
    }
}
