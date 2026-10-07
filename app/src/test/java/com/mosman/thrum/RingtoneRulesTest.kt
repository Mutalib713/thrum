package com.mosman.thrum

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Task 31 (Mutalib, 2026-10-07): "Use for calls" and "Set as ringtone" are
 * one action, so what the phone rings with and what Thrum vibrates are the
 * same song. These are the decisions behind it that don't need a phone.
 */
class RingtoneRulesTest {

    private val clip = "content://media/external/audio/media/1000000412"
    private val song = "content://media/external/audio/media/1000000213"

    @Test
    fun `the same ringtone is recognised however Android writes it`() {
        assertTrue(RingtoneRules.sameAddress(clip, clip))
        // The phone's user in the address, as Android sometimes stores it.
        assertTrue(RingtoneRules.sameAddress("content://0@media/external/audio/media/1000000412", clip))
        // A title added on the end, as a stored ringtone can carry.
        assertTrue(RingtoneRules.sameAddress("$clip?title=AIZO%20(Thrum)&canonical=1", clip))
        assertFalse(RingtoneRules.sameAddress(clip, song))
        assertFalse(RingtoneRules.sameAddress(null, clip))
        assertFalse(RingtoneRules.sameAddress(clip, null))
    }

    @Test
    fun `ring mode plays only while the ringtone is the song calls vibrate to`() {
        assertTrue(RingtoneRules.ringtoneIsTheSong(current = clip, ours = clip, oursFor = song, armed = song))
        // The user changed the ringtone in Android's settings.
        assertFalse(RingtoneRules.ringtoneIsTheSong(current = "content://media/internal/audio/media/27", ours = clip, oursFor = song, armed = song))
        // Calls now vibrate to a different song ("Just the vibration"), so the
        // ringtone is the old song's: hearing one and feeling another.
        assertFalse(RingtoneRules.ringtoneIsTheSong(current = clip, ours = clip, oursFor = song, armed = "content://other"))
        // Nothing for calls at all.
        assertFalse(RingtoneRules.ringtoneIsTheSong(current = clip, ours = clip, oursFor = song, armed = null))
        // Thrum never set a ringtone.
        assertFalse(RingtoneRules.ringtoneIsTheSong(current = clip, ours = null, oursFor = null, armed = song))
    }

    @Test
    fun `the user's own ringtone is noted once and never one of Thrum's`() {
        assertTrue(RingtoneRules.shouldRemember(saved = null, currentIsThrums = false))
        // Second time round: the first note is the one "Go back" must reach.
        assertFalse(RingtoneRules.shouldRemember(saved = "content://media/internal/audio/media/27", currentIsThrums = false))
        // The ringtone now is one Thrum made: going back to it would be no way back.
        assertFalse(RingtoneRules.shouldRemember(saved = null, currentIsThrums = true))
    }

    @Test
    fun `only songs longer than the clip are cut`() {
        assertEquals(45_000L, RingtoneRules.CLIP_MS)
        assertFalse(RingtoneRules.needsCut(30_000))
        assertFalse("a breath over 45 s is used as it is", RingtoneRules.needsCut(45_400))
        assertTrue(RingtoneRules.needsCut(45_600))
        assertTrue(RingtoneRules.needsCut(238_000))
        assertTrue("an unknown length is read and cut if it needs it", RingtoneRules.needsCut(0))
    }

    @Test
    fun `the clip is exactly the call window long`() {
        // The vibration repeats every 45 s; the ringtone has to repeat with it.
        assertEquals(44_100 * 45, RingtoneRules.clipFrames(44_100))
        assertEquals(48_000 * 45, RingtoneRules.clipFrames(48_000))
    }

    @Test
    fun `a cut clip fades out over its last 150 ms, and only then`() {
        val rate = 48_000
        val total = RingtoneRules.clipFrames(rate)
        val fade = (RingtoneRules.FADE_OUT_MS * rate / 1000).toInt()
        assertEquals(1f, RingtoneRules.fadeGain(0, total, rate, cut = true))
        assertEquals(1f, RingtoneRules.fadeGain(total - fade - 1, total, rate, cut = true))
        assertTrue(RingtoneRules.fadeGain(total - fade / 2, total, rate, cut = true) in 0.4f..0.6f)
        assertEquals(0f, RingtoneRules.fadeGain(total - 1, total, rate, cut = true))
        // A song that ended by itself keeps its own ending.
        assertEquals(1f, RingtoneRules.fadeGain(total - 1, total, rate, cut = false))
        // Falling all the way, never back up.
        var last = 1f
        for (frame in total - fade until total) {
            val gain = RingtoneRules.fadeGain(frame, total, rate, cut = true)
            assertTrue("the fade rose at frame $frame", gain <= last)
            last = gain
        }
    }

    @Test
    fun `a late start skips ahead by how late it is, within limits`() {
        assertEquals(0L, RingtoneRules.catchUpMs(0, 45_000))
        assertEquals(420L, RingtoneRules.catchUpMs(420, 45_000))
        // A clock gone wrong is capped, not chased.
        assertEquals(RingtoneRules.MAX_CATCH_UP_MS, RingtoneRules.catchUpMs(60_000, 45_000))
        assertEquals(0L, RingtoneRules.catchUpMs(-200, 45_000))
        // A ringtone shorter than the delay wraps round, as the loop does.
        assertEquals(200L, RingtoneRules.catchUpMs(1_200, 1_000))
        assertEquals(0L, RingtoneRules.catchUpMs(500, 0))
    }
}
