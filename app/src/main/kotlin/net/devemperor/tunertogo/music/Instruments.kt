package net.devemperor.tunertogo.music

import androidx.annotation.StringRes
import net.devemperor.tunertogo.R

/** One open string: its MIDI note and how its name is spelled (F♯ in Open D, E♭ in E♭ Standard). */
data class TuningString(val midi: Int, val accidentals: Accidentals)

/**
 * A tuning lists its strings in the order players count them on the instrument: lowest-pitched
 * course first for guitars and basses, re-entrant strings where they physically are (the high g
 * of ukulele and banjo comes first).
 */
data class Tuning(
    val id: String,
    @StringRes val nameRes: Int,
    val strings: List<TuningString>,
) {
    val isChromatic: Boolean get() = strings.isEmpty()
}

data class Instrument(
    val id: String,
    @StringRes val nameRes: Int,
    val tunings: List<Tuning>,
    /** Shown where space is tight (the tuner's edge button). */
    @StringRes val shortNameRes: Int = nameRes,
) {
    val defaultTuning: Tuning get() = tunings.first()
}

object Instruments {
    const val DEFAULT_TUNING_ID = "guitar.standard"

    // Must be initialised before the tables below, which parse their notes with it.
    private val NOTE_PATTERN = Regex("([A-G])([#b]?)(-?\\d)")

    val chromatic = Instrument(
        id = "chromatic",
        nameRes = R.string.instrument_chromatic,
        tunings = listOf(Tuning("chromatic", R.string.instrument_chromatic, emptyList())),
    )

    val all: List<Instrument> = listOf(
        chromatic,
        instrument("guitar", R.string.instrument_guitar) {
            tuning("standard", R.string.tuning_standard, "E2 A2 D3 G3 B3 E4")
            tuning("eb_standard", R.string.tuning_eb_standard, "Eb2 Ab2 Db3 Gb3 Bb3 Eb4")
            tuning("d_standard", R.string.tuning_d_standard, "D2 G2 C3 F3 A3 D4")
            tuning("drop_d", R.string.tuning_drop_d, "D2 A2 D3 G3 B3 E4")
            tuning("drop_c", R.string.tuning_drop_c, "C2 G2 C3 F3 A3 D4")
            tuning("dadgad", R.string.tuning_dadgad, "D2 A2 D3 G3 A3 D4")
            tuning("open_d", R.string.tuning_open_d, "D2 A2 D3 F#3 A3 D4")
            tuning("open_g", R.string.tuning_open_g, "D2 G2 D3 G3 B3 D4")
            tuning("open_e", R.string.tuning_open_e, "E2 B2 E3 G#3 B3 E4")
            tuning("open_c", R.string.tuning_open_c, "C2 G2 C3 G3 C4 E4")
        },
        instrument("guitar7", R.string.instrument_guitar7, R.string.instrument_guitar7_short) {
            tuning("standard", R.string.tuning_standard, "B1 E2 A2 D3 G3 B3 E4")
            tuning("drop_a", R.string.tuning_drop_a, "A1 E2 A2 D3 G3 B3 E4")
        },
        instrument("bass", R.string.instrument_bass) {
            tuning("standard", R.string.tuning_standard, "E1 A1 D2 G2")
            tuning("eb_standard", R.string.tuning_eb_standard, "Eb1 Ab1 Db2 Gb2")
            tuning("drop_d", R.string.tuning_drop_d, "D1 A1 D2 G2")
            tuning("five_string", R.string.tuning_five_string, "B0 E1 A1 D2 G2")
            tuning("six_string", R.string.tuning_six_string, "B0 E1 A1 D2 G2 C3")
        },
        instrument("ukulele", R.string.instrument_ukulele) {
            tuning("standard", R.string.tuning_standard, "G4 C4 E4 A4")
            tuning("low_g", R.string.tuning_low_g, "G3 C4 E4 A4")
            tuning("d_tuning", R.string.tuning_d_tuning, "A4 D4 F#4 B4")
            tuning("baritone", R.string.tuning_baritone, "D3 G3 B3 E4")
        },
        instrument("violin", R.string.instrument_violin) {
            tuning("standard", R.string.tuning_standard, "G3 D4 A4 E5")
        },
        instrument("viola", R.string.instrument_viola) {
            tuning("standard", R.string.tuning_standard, "C3 G3 D4 A4")
        },
        instrument("cello", R.string.instrument_cello) {
            tuning("standard", R.string.tuning_standard, "C2 G2 D3 A3")
        },
        instrument("double_bass", R.string.instrument_double_bass) {
            tuning("orchestra", R.string.tuning_orchestra, "E1 A1 D2 G2")
            tuning("solo", R.string.tuning_solo, "F#1 B1 E2 A2")
        },
        instrument("mandolin", R.string.instrument_mandolin) {
            tuning("standard", R.string.tuning_standard, "G3 D4 A4 E5")
        },
        instrument("banjo", R.string.instrument_banjo) {
            tuning("open_g", R.string.tuning_open_g, "G4 D3 G3 B3 D4")
            tuning("double_c", R.string.tuning_double_c, "G4 C3 G3 C4 D4")
            tuning("open_d", R.string.tuning_open_d, "F#4 D3 F#3 A3 D4")
        },
    )

    private val tuningsById: Map<String, Pair<Instrument, Tuning>> =
        all.flatMap { instrument -> instrument.tunings.map { it.id to (instrument to it) } }.toMap()

    fun find(tuningId: String): Pair<Instrument, Tuning> =
        tuningsById[tuningId] ?: tuningsById.getValue(DEFAULT_TUNING_ID)

    fun instrument(id: String): Instrument? = all.firstOrNull { it.id == id }

    /**
     * Parses "E2", "F#3" or "Bb1" into a [TuningString]. Only the spellings used by the tables
     * above are accepted, so a typo fails loudly at startup (and in the unit tests).
     */
    fun parseNote(note: String): TuningString {
        val match = requireNotNull(NOTE_PATTERN.matchEntire(note)) { "Invalid note: $note" }
        val (letter, accidental, octave) = match.destructured
        val natural = when (letter) {
            "C" -> 0
            "D" -> 2
            "E" -> 4
            "F" -> 5
            "G" -> 7
            "A" -> 9
            else -> 11 // "B"
        }
        val offset = when (accidental) {
            "#" -> 1
            "b" -> -1
            else -> 0
        }
        val midi = (octave.toInt() + 1) * 12 + natural + offset
        return TuningString(midi, if (accidental == "b") Accidentals.FLATS else Accidentals.SHARPS)
    }

    private class InstrumentBuilder(private val instrumentId: String) {
        val tunings = mutableListOf<Tuning>()

        fun tuning(id: String, @StringRes nameRes: Int, notes: String) {
            tunings += Tuning("$instrumentId.$id", nameRes, notes.split(' ').map(::parseNote))
        }
    }

    private fun instrument(
        id: String,
        @StringRes nameRes: Int,
        @StringRes shortNameRes: Int = nameRes,
        block: InstrumentBuilder.() -> Unit,
    ) = Instrument(id, nameRes, InstrumentBuilder(id).apply(block).tunings, shortNameRes)
}
