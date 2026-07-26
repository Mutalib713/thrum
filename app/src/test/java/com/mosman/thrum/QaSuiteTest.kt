package com.mosman.thrum

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The QA suite named in PROFILE.md §12. Runs on the PC in seconds with no
 * device, which is the only automated verification this project can have —
 * there is no emulator on this machine and emulators cannot do vibration.
 *
 * It only grows from here. Task 4 adds the beat-detection checks once there is
 * an analyser to check.
 */
class QaSuiteTest {

    @Test
    fun `silence is silent`() {
        val score = Score(20, List(50) { 0 })
        assertTrue(score.isSilent())
        assertEquals(0, score.pulseCount())
    }

    @Test
    fun `timings always match amplitudes in length`() {
        // A mismatch here throws inside the vibrator and takes the app down,
        // so it is made impossible by construction rather than checked at runtime.
        for (size in listOf(0, 1, 7, 240)) {
            val score = Score(20, List(size) { 100 })
            assertEquals(score.amplitudes.size, score.timings().size)
        }
    }

    @Test
    fun `every demo score stays inside the motor's range`() {
        for (score in Demo.all()) {
            assertTrue(
                "${score.sourceName} has an out-of-range amplitude",
                score.amplitudes.all { it in 0..Score.MAX_AMPLITUDE },
            )
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `an amplitude above the maximum is rejected`() {
        Score(20, listOf(0, 128, 256))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a zero step is rejected`() {
        Score(0, listOf(100))
    }

    @Test
    fun `a score survives being written and read back`() {
        val original = Score(20, listOf(0, 55, 255, 0, 12), "my ring|tone\\odd\nname")
        val restored = Score.decode(original.encode())
        assertEquals(original, restored)
    }

    @Test
    fun `an empty score survives being written and read back`() {
        val original = Score(20, emptyList(), "")
        assertEquals(original, Score.decode(original.encode()))
    }

    @Test
    fun `corrupt stored data reads as no score rather than crashing`() {
        for (junk in listOf("", "nonsense", "1|20|name", "1|20|name|1,2,oops", "1|0|n|1", "9|20|n|1")) {
            assertNull("decoded junk: $junk", Score.decode(junk))
        }
    }

    @Test
    fun `the rhythm demo has the accents it claims to have`() {
        val rhythm = Demo.rhythm()
        assertEquals(10, rhythm.pulseCount())
        assertTrue(rhythm.durationMs in 4000..5500)
        assertTrue("a rhythm must vary", rhythm.amplitudes.distinct().size > 10)
    }

    @Test
    fun `the system buzz demo is deliberately flat`() {
        val buzz = Demo.systemBuzz()
        assertEquals(3, buzz.pulseCount())
        // The whole point of the comparison: two values, on and off, nothing between.
        assertEquals(setOf(0, 255), buzz.amplitudes.toSet())
    }

    @Test
    fun `the swell demo rises smoothly`() {
        val swell = Demo.swell()
        assertEquals(2, swell.pulseCount())
        assertTrue("a swell must be finely graded", swell.amplitudes.distinct().size > 30)
    }

    // --- Task 2: the event log, which is the only instrument available for
    // --- measuring a real call while there is no USB cable on this machine.

    @Test
    fun `an event survives being written and read back`() {
        val original = Event(
            at = 1_784_000_000_000,
            kind = Event.Kind.FIRED,
            ringer = "vibrate",
            latencyMs = 137,
            note = "looping | with an awkward\nnote",
        )
        assertEquals(original, Event.decode(original.encode()))
    }

    @Test
    fun `every event kind survives the round trip`() {
        for (kind in Event.Kind.entries) {
            val event = Event(1, kind, "silent", 0)
            assertEquals(event, Event.decode(event.encode()))
        }
    }

    @Test
    fun `a corrupt event line is dropped instead of crashing the screen`() {
        for (junk in listOf("", "x", "1|abc|FIRED|vibrate|0|", "1|1|NOPE|vibrate|0|", "9|1|FIRED|v|0|")) {
            assertNull("decoded junk: $junk", Event.decode(junk))
        }
    }

    @Test
    fun `a corrupt line does not take the rest of the log with it`() {
        val good = Event(1, Event.Kind.FIRED, "vibrate", 10)
        val text = listOf(good.encode(), "garbage", good.encode()).joinToString("\n")
        assertEquals(2, Event.decodeAll(text).size)
    }

    @Test
    fun `the log stops growing forever`() {
        val many = (1..Event.MAX_KEPT * 3).map { Event(it.toLong(), Event.Kind.FIRED, "vibrate", 0) }
        val kept = Event.decodeAll(Event.encodeAll(many))
        assertEquals(Event.MAX_KEPT, kept.size)
        // The newest must be the ones that survive, not the oldest.
        assertEquals(many.last(), kept.last())
    }

    @Test
    fun `an empty log reads as no events`() {
        assertEquals(emptyList<Event>(), Event.decodeAll(""))
    }
}
