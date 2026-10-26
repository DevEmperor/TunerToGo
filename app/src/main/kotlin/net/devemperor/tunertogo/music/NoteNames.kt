package net.devemperor.tunertogo.music

/** How note letters are spelled on screen. */
enum class NoteNaming {
    /** C D E F G A B */
    ENGLISH,

    /** C D E F G A H, where B means B♭ (German, Central European and Nordic usage). */
    GERMAN,

    /** Fixed-do solfège: Do Re Mi Fa Sol La Si. */
    SOLFEGE,
}

/** Whether black keys are spelled as raised (C♯) or lowered (D♭) notes. */
enum class Accidentals { SHARPS, FLATS }

/**
 * A spelled note: [name] is the letter or syllable, [accidental] is "♯", "♭" or empty, [octave] is
 * in scientific pitch notation.
 */
data class NoteLabel(val name: String, val accidental: String, val octave: Int) {
    /** Compact single-string form such as "F♯3" or "B♭2". */
    val text: String get() = "$name$accidental$octave"

    /** Pitch class without octave, e.g. "F♯". */
    val pitchClass: String get() = "$name$accidental"
}

object NoteNames {
    const val SHARP = "♯"
    const val FLAT = "♭"

    private val ENGLISH_NATURALS = arrayOf("C", "D", "E", "F", "G", "A", "B")
    private val GERMAN_NATURALS = arrayOf("C", "D", "E", "F", "G", "A", "H")
    private val SOLFEGE_NATURALS = arrayOf("Do", "Re", "Mi", "Fa", "Sol", "La", "Si")

    // For every pitch class: index of the natural it is spelled from and the accidental offset
    // (+1 sharp, -1 flat, 0 natural), once for each spelling preference.
    private val SHARP_SPELLING = arrayOf(0 to 0, 0 to 1, 1 to 0, 1 to 1, 2 to 0, 3 to 0, 3 to 1, 4 to 0, 4 to 1, 5 to 0, 5 to 1, 6 to 0)
    private val FLAT_SPELLING = arrayOf(0 to 0, 1 to -1, 1 to 0, 2 to -1, 2 to 0, 3 to 0, 4 to -1, 4 to 0, 5 to -1, 5 to 0, 6 to -1, 6 to 0)

    fun label(midi: Int, naming: NoteNaming, accidentals: Accidentals): NoteLabel {
        val pitchClass = Pitch.pitchClassOf(midi)
        val octave = Pitch.octaveOf(midi)
        // German calls B♭ simply "B" (and B natural "H"), so a flat spelling of pitch class 10
        // needs no accidental sign at all.
        if (naming == NoteNaming.GERMAN && pitchClass == 10 && accidentals == Accidentals.FLATS) {
            return NoteLabel("B", "", octave)
        }
        val (natural, offset) = when (accidentals) {
            Accidentals.SHARPS -> SHARP_SPELLING[pitchClass]
            Accidentals.FLATS -> FLAT_SPELLING[pitchClass]
        }
        val names = when (naming) {
            NoteNaming.ENGLISH -> ENGLISH_NATURALS
            NoteNaming.GERMAN -> GERMAN_NATURALS
            NoteNaming.SOLFEGE -> SOLFEGE_NATURALS
        }
        val accidental = when (offset) {
            1 -> SHARP
            -1 -> FLAT
            else -> ""
        }
        return NoteLabel(names[natural], accidental, octave)
    }
}

/**
 * Transposing instruments read a different note than they sound. An instrument "in B♭" sounds B♭
 * when it plays a written C, so the tuner shows written = sounding + [semitones], where the
 * instrument sounds a little lower than written (B♭ trumpet: major second, E♭ alto sax: major
 * sixth, horn in F: perfect fifth).
 */
enum class Transposition(val keyPitchClass: Int) {
    C(0),
    B_FLAT(10),
    E_FLAT(3),
    F(5),
    ;

    val semitones: Int get() = (12 - keyPitchClass) % 12

    /** Name of the instrument key in the user's note naming, e.g. "B♭" (or "B" in German). */
    fun label(naming: NoteNaming): String =
        NoteNames.label(60 + keyPitchClass, naming, Accidentals.FLATS).pitchClass
}
