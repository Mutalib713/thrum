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
}
