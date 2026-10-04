package com.mosman.thrum

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Task 17: the data layer the full app stands on — [Track], [Haptic],
 * [HapticQueue] and `Score.pieces`. The checks PROFILE.md §12 names for the
 * full app are here, plus the guards the design earns along the way.
 *
 * Every one of these runs on the PC with no phone, because every one of them
 * is arithmetic on strings and ints. That is the point of the pure layer: the
 * Room database itself needs a device to verify, but nothing about it is
 * clever — the clever parts are here, where a wrong answer shows up in
 * seconds instead of on a call.
 */
class LibraryTest {

    // --- The call window. §12: "the call window is exactly the first 45
    // --- seconds of a whole-song haptic".

    @Test
    fun `the call window is exactly the first forty-five seconds`() {
        // 3:58 of steps — the real Keche track's length from Task 3.
        val whole = Score(20, List(11_910) { 200 })
        val haptic = Haptic("song", whole, punch = 208, distance = 61, bodyMs = 400, madeAtMs = 1L)
        val window = haptic.callWindow()
        assertEquals(45_000, window.durationMs)
        assertEquals(2_250, window.amplitudes.size)
        // The window is a prefix, not a sample: step zero of the call is step
        // zero of the song.
        assertEquals(whole.amplitudes.take(2_250), window.amplitudes)
    }

    @Test
    fun `a shorter song is its own call window`() {
        // 30 seconds — under the window. Trimming must not touch it, and it
        // must not come back padded: a 30-second call window that secretly
        // loops a 15-second tail would be heard.
        val haptic = Haptic("song", Score(20, List(1_500) { 180 }), punch = 185, distance = 0, bodyMs = 400, madeAtMs = 1L)
        assertEquals(30_000, haptic.callWindow().durationMs)
    }

    // --- The split. §12: "a whole-song score split into pieces under
    // --- Haptics.MAX_STEPS keeps every step once: none dropped, none doubled".

    @Test
    fun `a whole song splits into pieces the vibrator accepts`() {
        // 8,862 steps: the real AIZO track (R10). Two pieces, each under the
        // 8,000 cap, joined covering the whole song.
        val whole = Score(20, List(8_862) { it % 255 })
        val pieces = Haptic("aizo", whole, punch = 208, distance = 61, bodyMs = 400, madeAtMs = 1L).pieces()
        assertEquals(2, pieces.size)
        assertTrue(pieces.all { it.amplitudes.size <= Haptics.MAX_STEPS })
        assertEquals(whole.amplitudes, pieces.flatMap { it.amplitudes })
        assertEquals(whole.durationMs, pieces.sumOf { it.durationMs })
    }

    @Test
    fun `a score already under the cap is one piece`() {
        val whole = Score(20, List(2_250) { 100 })
        assertEquals(listOf(whole), whole.pieces(Haptics.MAX_STEPS))
    }

    @Test
    fun `splitting keeps every step once whatever the cap`() {
        // Deterministic pseudo-random, so a dropped or doubled step cannot
        // hide behind a pattern. Every cap from 1 to 200 over a 500-step
        // score: joined, the pieces are the original — nothing dropped, none
        // doubled — and no piece ever exceeds the cap it was given.
        val amps = List(500) { (it * 37 + 11) % 256 }
        val whole = Score(20, amps)
        for (cap in listOf(1, 2, 3, 7, 41, 200, 500, 800)) {
            val pieces = whole.pieces(cap)
            assertEquals("cap=$cap lost the original", amps, pieces.flatMap { it.amplitudes })
            assertTrue(
                "cap=$cap produced a piece of ${pieces.maxOf { it.amplitudes.size }} steps",
                pieces.all { it.amplitudes.size <= cap },
            )
            // No piece is empty: an empty piece would be a silent gap the
            // player would have to skip, and the splitter's job is to produce
            // nothing that needs skipping.
            assertTrue("cap=$cap produced an empty piece", pieces.all { it.amplitudes.isNotEmpty() })
        }
    }

    @Test
    fun `a split cuts at silence rather than through a hit`() {
        // Nine steps of kick, two of silence, then a driving pattern with a
        // rest every five steps — so every cap-12 window holds a silence to
        // cut at, and a naive cut would land mid-drive. The cut must instead
        // sit after a silence, so each piece starts on a hit's onset rather
        // than restarting one mid-motion.
        val amps = List(9) { 255 } + listOf(0, 0) +
            List(6) { listOf(255, 255, 255, 255, 0) }.flatten()
        val whole = Score(20, amps)
        val pieces = whole.pieces(12)
        assertTrue("expected a split, got ${pieces.size} piece(s)", pieces.size > 1)
        // Every non-final piece ends on silence — that is what "cutting at
        // silence" means at the boundary.
        for (piece in pieces.dropLast(1)) {
            assertEquals(
                "a piece was cut through sound: last step ${piece.amplitudes.last()}",
                0,
                piece.amplitudes.last(),
            )
        }
        assertEquals(amps, pieces.flatMap { it.amplitudes })
    }

    @Test
    fun `a score with no silence still splits rather than failing`() {
        // A solid drive has no cut point. The splitter must not hunt backwards
        // through the whole piece making it ever smaller — it cuts hard, and
        // Task 16 measures what that join feels like on the phone.
        val whole = Score(20, List(101) { 255 })
        val pieces = whole.pieces(3)
        assertEquals(34, pieces.size)
        assertEquals(listOf(255, 255, 255), pieces[0].amplitudes)
        assertEquals(List(2) { 255 }, pieces.last().amplitudes)
    }

    // --- The queue. §12: "the haptic queue lets a song that is played jump
    // --- ahead, and never makes the same song twice".

    @Test
    fun `a played song jumps the queue`() {
        val queue = HapticQueue(pending = listOf("b", "c", "d"))
        assertEquals(listOf("c", "b", "d"), queue.played("c").pending)
    }

    @Test
    fun `a played song that was never queued goes to the front`() {
        // Pressing play is a stronger signal than anything the scan learned —
        // the user wants this one now, and "one at a time as played" (§4 item
        // 5's other mode) is this rule with an empty queue beside it.
        val queue = HapticQueue(pending = listOf("b"))
        assertEquals(listOf("x", "b"), queue.played("x").pending)
    }

    @Test
    fun `a made song never rejoins the queue however it is played`() {
        val queue = HapticQueue(pending = listOf("b"), made = setOf("a"))
        assertEquals(listOf("b"), queue.played("a").pending)
        assertEquals(listOf("b"), queue.enqueued(listOf("a")).pending)
    }

    @Test
    fun `no song is ever queued twice`() {
        val queue = HapticQueue(pending = listOf("b"))
        val twice = queue.enqueued(listOf("b", "c")).played("c").enqueued(listOf("b"))
        assertEquals(listOf("c", "b"), twice.pending)
        assertTrue(twice.pending.toSet().size == twice.pending.size)
    }

    @Test
    fun `completing a song removes it and remembers it as made`() {
        val queue = HapticQueue(pending = listOf("b", "c"))
        val done = queue.completed("b")
        assertEquals(listOf("c"), done.pending)
        assertEquals(setOf("b"), done.made)
        // And the made set holds against every later way back in.
        assertEquals(listOf("c"), done.played("b").pending)
        assertEquals(listOf("c"), done.enqueued(listOf("b")).pending)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a queue with a duplicate entry is rejected rather than carried`() {
        // The invariant is the guarantee; a queue that could hold duplicates
        // would be trusting every caller to enforce it by hand.
        HapticQueue(pending = listOf("b", "b"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a queue with a made song still pending is rejected`() {
        HapticQueue(pending = listOf("b"), made = setOf("b"))
    }

    // --- The rows. A haptic survives being written and read back; a corrupt
    // --- row degrades to absent rather than taking the list down.

    @Test
    fun `a haptic round-trips through the row that stores it`() {
        val haptic = Haptic(
            trackUri = "content://media/external/audio/1234",
            score = Score(20, List(100) { (it * 13) % 256 }, "Keche - No Dulling"),
            punch = 208,
            distance = 61,
            bodyMs = 400,
            madeAtMs = 1_785_000_000_000,
        )
        assertEquals(haptic, haptic.toEntity().toHaptic())
    }

    @Test
    fun `a corrupt score in a row reads as no haptic`() {
        // One bad row must not take the My Haptics list down — the same
        // degrade-to-absent rule Score.decode sets for the call settings.
        val row = HapticEntity(
            trackUri = "x", scoreText = "nonsense", punch = 208, distance = 0, bodyMs = 400, madeAtMs = 1L,
        )
        assertNull(row.toHaptic())
    }

    @Test
    fun `a track round-trips through the row that stores it`() {
        val track = Track(
            sourceUri = "content://media/external/audio/999",
            name = "No Dulling",
            artist = "Keche",
            durationMs = 238_000,
            kind = TrackKind.MUSIC,
            readable = true,
        )
        assertEquals(track, track.toEntity(lastSeenAtMs = 7L).toTrack())
    }

    @Test
    fun `a kind the code no longer knows degrades to a file rather than crashing`() {
        // A track written by a future version with a kind this one cannot
        // name. Losing the kind is cosmetic; losing the list is not.
        val row = TrackEntity(
            sourceUri = "x", name = "n", artist = "", durationMs = 1_000,
            kind = "SOMETHING_NEW", readable = true, lastSeenAtMs = 0L,
        )
        assertEquals(TrackKind.FILE, row.toTrack().kind)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a track without a source is rejected`() {
        Track("", "n", durationMs = 1_000, kind = TrackKind.FILE)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a track without a name is rejected`() {
        Track("uri", "", durationMs = 1_000, kind = TrackKind.FILE)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a negative duration is rejected`() {
        Track("uri", "n", durationMs = -1, kind = TrackKind.FILE)
    }

    // --- Home's last-call line (Task 19).

    @Test
    fun `the last call is the newest one the app actually played`() {
        // A skipped call is the app declining — silent mode, no song, a
        // duplicate — and the line must not report a call the app declined.
        val first = Event(1_000, Event.Kind.FIRED, "vibrate", 243)
        val declined = Event(2_000, Event.Kind.SKIPPED, "silent", 0)
        val last = Event(3_000, Event.Kind.FIRED, "vibrate", 250)
        assertEquals(last, Home.lastCall(listOf(first, declined, last)))
        assertNull(Home.lastCall(listOf(declined)))
        assertNull(Home.lastCall(emptyList()))
    }

    @Test
    fun `latency reads the way the design wrote it`() {
        // 273 ms was the Task 2 median; the design's own example is "0.3 s".
        assertEquals("0.3 s", Home.latencyWords(273))
        assertEquals("0.0 s", Home.latencyWords(0))
        assertEquals("9.9 s", Home.latencyWords(9_940))
        assertEquals("10 s", Home.latencyWords(10_000))
    }

    // --- The Music tab's search (Task 20).

    @Test
    fun `search keeps only tracks whose every word lands in the title or artist`() {
        val asake = Track("1", "Active", artist = "Asake, Travis Scott", durationMs = 1, kind = TrackKind.MUSIC)
        val keche = Track("2", "No Dulling", artist = "Keche", durationMs = 1, kind = TrackKind.MUSIC)
        val lofi = Track("3", "AIZO, but it's lofi hiphop", artist = "", durationMs = 1, kind = TrackKind.MUSIC)
        val library = listOf(asake, keche, lofi)
        assertEquals(listOf(asake), Track.search(library, "asake active"))
        assertEquals(listOf(keche), Track.search(library, "dulling"))
        assertEquals(listOf(lofi), Track.search(library, "LOFI"))
        // Two artists named together find nothing, which is the point of an
        // AND: an OR would flood the list with everything by either.
        assertTrue(Track.search(library, "asake keche").isEmpty())
    }

    @Test
    fun `an empty or messy query is the whole library in the scan's order`() {
        val library = listOf(
            Track("1", "No Dulling", artist = "Keche", durationMs = 1, kind = TrackKind.MUSIC),
            Track("2", "Active", artist = "Asake", durationMs = 1, kind = TrackKind.MUSIC),
        )
        assertEquals(library, Track.search(library, ""))
        assertEquals(library, Track.search(library, "   "))
        // Doubled spaces and padding must not manufacture words.
        assertEquals(listOf(library[0]), Track.search(library, "  no   dulling  "))
    }
}
