package com.mosman.thrum

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The numbers behind the bars (2026-11-28): they must be the vibration, nothing invented. */
class BarMathTest {

    // 20 ms steps: still, a full hit for 60 ms, still, a half hit for 40 ms, still.
    private val m = BarMath(listOf(0, 255, 255, 255, 0, 0, 128, 128, 0, 0), 20)

    @Test
    fun `strength follows the score and is zero outside it`() {
        assertEquals(0f, m.amp(0), 0f)
        assertEquals(1f, m.amp(20), 0f)
        assertEquals(1f, m.amp(79), 0f)
        assertEquals(0f, m.amp(80), 0f)
        assertEquals(128 / 255f, m.amp(130), 0.001f)
        assertEquals("before the start", 0f, m.amp(-5), 0f)
        assertEquals("after the end", 0f, m.amp(200), 0f)
        assertEquals(0f, BarMath(emptyList(), 20).amp(0), 0f)
    }

    @Test
    fun `a wide bar keeps the strongest moment inside it`() {
        assertEquals(1f, m.peak(0, 100), 0f)
        assertEquals(128 / 255f, m.peak(100, 60), 0.001f)
        assertEquals(0f, m.peak(160, 40), 0f)
    }

    @Test
    fun `a beat flashes then fades to nothing`() {
        assertEquals(1f, m.env(20), 0f)
        val later = m.env(80 + 130)            // 130 ms after the hit ended
        assertTrue("still fading: $later", later > 0f && later < 1f)
        assertEquals("gone after the pulse", 0f, BarMath(listOf(255, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0), 20).env(300), 0f)
    }

    @Test
    fun `beat starts are where the motor starts, not every running step`() {
        assertTrue(m.isOnset(20))
        assertFalse(m.isOnset(40))
        assertTrue(m.isOnset(120))
        assertEquals(listOf(20L, 120L), m.onsets(0, 200))
        assertEquals(listOf(120L), m.onsets(50, 200))
    }

    @Test
    fun `notes get one of three lanes, the same every time`() {
        val lanes = (0L until 600L step 20).map { BarMath.lane(it, 20) }.toSet()
        assertEquals(setOf(0, 1, 2), lanes)
        assertEquals(BarMath.lane(440, 20), BarMath.lane(440, 20))
    }

    @Test
    fun `the picker saves by key, falls back to Pills, and has his 14`() {
        assertEquals(BarStyle.PILLS, BarStyle.DEFAULT)
        assertEquals(BarStyle.MOON, BarStyle.fromKey("moon"))
        assertEquals(BarStyle.PILLS, BarStyle.fromKey(null))
        assertEquals(BarStyle.PILLS, BarStyle.fromKey("ring"))   // E wasn't picked
        assertEquals("ABCDFGNOPQRSTW", BarStyle.entries.joinToString("") { it.letter.toString() })
        assertEquals(BarStyle.entries.size, BarStyle.entries.map { it.key }.toSet().size)
    }
}
