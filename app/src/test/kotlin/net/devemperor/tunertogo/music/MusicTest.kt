package net.devemperor.tunertogo.music

import net.devemperor.tunertogo.settings.TunerSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class PitchTest {
    @Test
    fun `equal temperament math`() {
        assertEquals(440.0, Pitch.frequencyOf(69, 440.0), 1e-9)
        assertEquals(261.6256, Pitch.frequencyOf(60, 440.0), 1e-4)
        assertEquals(82.4069, Pitch.frequencyOf(40, 440.0), 1e-4)
        assertEquals(30.8677, Pitch.frequencyOf(23, 440.0), 1e-4)
        assertEquals(60.0, Pitch.midiOf(261.6256, 440.0), 1e-5)
        assertEquals(19.56, Pitch.cents(445.0, 440.0), 0.01)
        assertEquals(-100.0, Pitch.cents(Pitch.frequencyOf(68, 442.0), 442.0), 1e-9)
    }

    @Test
    fun `octaves follow scientific pitch notation`() {
        assertEquals(4, Pitch.octaveOf(60))
        assertEquals(3, Pitch.octaveOf(59))
        assertEquals(0, Pitch.octaveOf(23))
        assertEquals(-1, Pitch.octaveOf(0))
        assertEquals(11, Pitch.pitchClassOf(-1))
    }
}

class NoteNamesTest {
    private fun text(midi: Int, naming: NoteNaming, accidentals: Accidentals) = NoteNames.label(midi, naming, accidentals).text

    @Test
    fun `english names`() {
        assertEquals("A4", text(69, NoteNaming.ENGLISH, Accidentals.SHARPS))
        assertEquals("C♯4", text(61, NoteNaming.ENGLISH, Accidentals.SHARPS))
        assertEquals("D♭4", text(61, NoteNaming.ENGLISH, Accidentals.FLATS))
        assertEquals("B♭3", text(58, NoteNaming.ENGLISH, Accidentals.FLATS))
        assertEquals("B3", text(59, NoteNaming.ENGLISH, Accidentals.FLATS))
        assertEquals("E2", text(40, NoteNaming.ENGLISH, Accidentals.SHARPS))
    }

    @Test
    fun `german names use H for B natural and B for B flat`() {
        assertEquals("H3", text(59, NoteNaming.GERMAN, Accidentals.SHARPS))
        assertEquals("H3", text(59, NoteNaming.GERMAN, Accidentals.FLATS))
        assertEquals("B3", text(58, NoteNaming.GERMAN, Accidentals.FLATS))
        assertEquals("A♯3", text(58, NoteNaming.GERMAN, Accidentals.SHARPS))
        assertEquals("E♭4", text(63, NoteNaming.GERMAN, Accidentals.FLATS))
        assertEquals("F♯3", text(54, NoteNaming.GERMAN, Accidentals.SHARPS))
    }

    @Test
    fun `solfege names`() {
        assertEquals("La4", text(69, NoteNaming.SOLFEGE, Accidentals.SHARPS))
        assertEquals("Do♯4", text(61, NoteNaming.SOLFEGE, Accidentals.SHARPS))
        assertEquals("Si♭3", text(58, NoteNaming.SOLFEGE, Accidentals.FLATS))
        assertEquals("Sol2", text(43, NoteNaming.SOLFEGE, Accidentals.FLATS))
    }

    @Test
    fun `every pitch class is spelled with a matching letter`() {
        for (naming in NoteNaming.entries) for (accidentals in Accidentals.entries) for (midi in 0..127) {
            val label = NoteNames.label(midi, naming, accidentals)
            assertTrue(label.name.isNotEmpty())
            assertEquals(Pitch.octaveOf(midi), label.octave)
        }
    }

    @Test
    fun `transpositions show the written note`() {
        assertEquals(0, Transposition.C.semitones)
        assertEquals(2, Transposition.B_FLAT.semitones)  // concert B♭ reads as C
        assertEquals(9, Transposition.E_FLAT.semitones)  // concert E♭ reads as C (major sixth)
        assertEquals(7, Transposition.F.semitones)       // concert F reads as C (fifth)
        assertEquals("B♭", Transposition.B_FLAT.label(NoteNaming.ENGLISH))
        assertEquals("B", Transposition.B_FLAT.label(NoteNaming.GERMAN))
        assertEquals("Mi♭", Transposition.E_FLAT.label(NoteNaming.SOLFEGE))
    }
}

class InstrumentsTest {
    private fun midis(id: String) = Instruments.find(id).second.strings.map { it.midi }

    @Test
    fun `note parser`() {
        assertEquals(40, Instruments.parseNote("E2").midi)
        assertEquals(54, Instruments.parseNote("F#3").midi)
        assertEquals(34, Instruments.parseNote("Bb1").midi)
        assertEquals(23, Instruments.parseNote("B0").midi)
        assertEquals(Accidentals.FLATS, Instruments.parseNote("Eb4").accidentals)
    }

    @Test
    fun `reference tunings`() {
        assertEquals(listOf(40, 45, 50, 55, 59, 64), midis("guitar.standard"))
        assertEquals(listOf(39, 44, 49, 54, 58, 63), midis("guitar.eb_standard"))
        assertEquals(listOf(38, 45, 50, 55, 59, 64), midis("guitar.drop_d"))
        assertEquals(listOf(38, 45, 50, 55, 57, 62), midis("guitar.dadgad"))
        assertEquals(listOf(38, 43, 50, 55, 59, 62), midis("guitar.open_g"))
        assertEquals(listOf(35, 40, 45, 50, 55, 59, 64), midis("guitar7.standard"))
        assertEquals(listOf(28, 33, 38, 43), midis("bass.standard"))
        assertEquals(listOf(23, 28, 33, 38, 43), midis("bass.five_string"))
        assertEquals(listOf(67, 60, 64, 69), midis("ukulele.standard"))
        assertEquals(listOf(55, 62, 69, 76), midis("violin.standard"))
        assertEquals(listOf(48, 55, 62, 69), midis("viola.standard"))
        assertEquals(listOf(36, 43, 50, 57), midis("cello.standard"))
        assertEquals(listOf(67, 50, 55, 59, 62), midis("banjo.open_g"))
    }

    @Test
    fun `tuning ids are unique and every tuning is playable`() {
        val ids = Instruments.all.flatMap { it.tunings }.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        for (instrument in Instruments.all) {
            assertTrue(instrument.tunings.isNotEmpty())
            for (tuning in instrument.tunings) {
                // Distinct strings are needed for automatic string detection.
                assertEquals(tuning.id, tuning.strings.size, tuning.strings.map { it.midi }.toSet().size)
                for (string in tuning.strings) {
                    val f = Pitch.frequencyOf(string.midi, 440.0)
                    assertTrue("$f Hz out of range in ${tuning.id}", f in Pitch.MIN_FREQUENCY..Pitch.MAX_FREQUENCY)
                }
            }
        }
    }

    @Test
    fun `unknown tuning falls back to standard guitar`() {
        assertEquals(Instruments.DEFAULT_TUNING_ID, Instruments.find("does.not.exist").second.id)
    }

    @Test
    fun `default note naming follows the language`() {
        assertEquals(NoteNaming.GERMAN, TunerSettings.defaultNaming(Locale.GERMANY))
        assertEquals(NoteNaming.GERMAN, TunerSettings.defaultNaming(Locale.forLanguageTag("sv-SE")))
        assertEquals(NoteNaming.SOLFEGE, TunerSettings.defaultNaming(Locale.FRANCE))
        assertEquals(NoteNaming.ENGLISH, TunerSettings.defaultNaming(Locale.US))
    }
}
