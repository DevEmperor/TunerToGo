package net.devemperor.tunertogo.tuner

import net.devemperor.tunertogo.audio.PitchEstimate
import net.devemperor.tunertogo.music.Pitch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TunerProcessorTest {
    private val chromatic = TunerConfig(440.0, emptyList(), null, 3.0, -50.0)
    private val guitar = chromatic.copy(strings = listOf(40, 45, 50, 55, 59, 64))

    private val processor = TunerProcessor()
    private var time = 0L

    private fun note(midi: Double, a4: Double = 440.0) = PitchEstimate(Pitch.frequencyOf(midi, a4), 0.95, -30.0)
    private val silence = PitchEstimate.silence(-80.0)

    /** Feeds [frames] estimates 23 ms apart, like the microphone does, and returns the last reading. */
    private fun feed(estimate: PitchEstimate, config: TunerConfig = chromatic, frames: Int = 1): TunerReading {
        var reading = TunerReading.Idle
        repeat(frames) {
            time += 23
            reading = processor.process(estimate, time, config)
        }
        return reading
    }

    @Test
    fun `idle without a signal`() {
        assertEquals(SignalState.IDLE, feed(silence, frames = 10).state)
    }

    @Test
    fun `chromatic mode finds the nearest note and its deviation`() {
        val reading = feed(note(69.2), frames = 20)
        assertEquals(SignalState.ACTIVE, reading.state)
        assertEquals(69, reading.targetMidi)
        assertEquals(20.0, reading.cents, 0.05)
        assertEquals(440.0, reading.targetFrequency, 1e-9)
    }

    @Test
    fun `in tune only after the dwell time, reported once`() {
        var tunedEvents = 0
        var firstInTuneAt = -1L
        repeat(40) {
            val reading = feed(note(69.01))
            if (reading.justTuned) tunedEvents++
            if (reading.inTune && firstInTuneAt < 0) firstInTuneAt = time
        }
        assertEquals(1, tunedEvents)
        assertTrue("in tune too early at $firstInTuneAt ms", firstInTuneAt >= 23 + TunerProcessor.IN_TUNE_DWELL_MILLIS)
    }

    @Test
    fun `leaving the tolerance clears in tune, with a little hysteresis`() {
        assertTrue(feed(note(69.0), frames = 20).inTune)
        assertTrue("4 cents is inside the release margin", feed(note(69.04), frames = 20).inTune)
        assertFalse(feed(note(69.08), frames = 20).inTune)
    }

    @Test
    fun `a re-pluck after a gap does not inherit the old in-tune pitch`() {
        // In tune for ~100 ms (shorter than the dwell), then the string stops ringing...
        feed(note(69.0), frames = 4)
        feed(silence, frames = 12)
        // ...the peg is turned 12 cents and the string plucked again.
        var falseTuned = false
        repeat(3) { if (feed(note(69.12)).run { justTuned || inTune }) falseTuned = true }
        assertFalse(falseTuned)
        assertEquals(12.0, feed(note(69.12)).cents, 0.5)
    }

    @Test
    fun `a few dropped frames inside a ringing note keep the reading`() {
        feed(note(69.0), frames = 20)
        assertTrue(feed(note(69.0)).inTune)
        feed(silence, frames = 3) // 69 ms
        assertTrue(feed(note(69.0)).inTune)
    }

    @Test
    fun `single-frame octave slip is ignored`() {
        feed(note(57.0), frames = 10)
        val reading = feed(note(69.0))
        assertEquals(57, reading.targetMidi)
        assertEquals(57, feed(note(57.0)).targetMidi)
    }

    @Test
    fun `a new note is followed after two consistent frames`() {
        feed(note(69.0), frames = 10)
        assertEquals(69, feed(note(71.0)).targetMidi)
        assertEquals(71, feed(note(71.0)).targetMidi)
    }

    @Test
    fun `last reading is held briefly after the note stops`() {
        feed(note(64.0), frames = 10)
        val held = feed(silence, frames = 20) // 460 ms
        assertEquals(SignalState.HOLD, held.state)
        assertEquals(64, held.targetMidi)
        assertEquals(SignalState.IDLE, feed(silence, frames = 60).state) // > 1.5 s
    }

    @Test
    fun `chromatic note does not flicker at the boundary`() {
        feed(note(69.0), frames = 10)
        // 55 cents sharp is past the midpoint but inside the hysteresis band.
        val reading = feed(note(69.55), frames = 30)
        assertEquals(69, reading.targetMidi)
        assertEquals(55.0, reading.cents, 0.5)
        assertEquals(70, feed(note(69.65), frames = 30).targetMidi)
    }

    @Test
    fun `instrument mode measures against the nearest string`() {
        val lowE = feed(note(39.4), guitar, frames = 20) // E2 tuned 60 cents flat
        assertEquals(0, lowE.stringIndex)
        assertEquals(40, lowE.targetMidi)
        assertEquals(-60.0, lowE.cents, 0.5)

        processor.reset()
        val d = feed(note(50.0), guitar, frames = 20)
        assertEquals(2, d.stringIndex)
    }

    @Test
    fun `string detection needs a clear advantage to switch`() {
        feed(note(47.4), guitar, frames = 30) // 2.4 semitones above A2, 2.6 below D3
        assertEquals(1, feed(note(47.4), guitar).stringIndex)
        // D3 is now 0.2 closer: not enough to switch.
        assertEquals(1, feed(note(47.6), guitar, frames = 30).stringIndex)
        // 0.4 closer: switch.
        assertEquals(2, feed(note(47.7), guitar, frames = 30).stringIndex)
    }

    @Test
    fun `a locked string is always the target`() {
        val locked = guitar.copy(lockedString = 1)
        val reading = feed(note(40.0), locked, frames = 20)
        assertEquals(1, reading.stringIndex)
        assertEquals(45, reading.targetMidi)
        assertEquals(-500.0, reading.cents, 0.5)
    }

    @Test
    fun `quiet and unclear frames are ignored`() {
        assertEquals(SignalState.IDLE, feed(PitchEstimate(440.0, 0.95, -65.0), frames = 10).state)
        assertEquals(SignalState.IDLE, feed(PitchEstimate(440.0, 0.5, -30.0), frames = 10).state)
    }

    @Test
    fun `changing the reference pitch starts over`() {
        feed(note(69.0), frames = 20)
        val baroque = chromatic.copy(referenceA4 = 415.0)
        val reading = feed(note(69.0, a4 = 415.0), baroque, frames = 20)
        assertEquals(69, reading.targetMidi)
        assertEquals(0.0, reading.cents, 0.05)
        assertEquals(415.0, reading.targetFrequency, 1e-9)
    }

    @Test
    fun `chromatic readings carry no string`() {
        assertNull(feed(note(52.0), frames = 10).stringIndex)
    }
}
