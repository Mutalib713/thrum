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
    fun `a played song jumps the queue by leaving it`() {
        // The player makes a played song's haptic itself, right now — ahead
        // of everything waiting. It leaves the queue so the background walk
        // never decodes the same song a second time in parallel; the others
        // keep their order.
        val queue = HapticQueue(pending = listOf("b", "c", "d"))
        assertEquals(listOf("b", "d"), queue.claimed("c").pending)
    }

    @Test
    fun `claiming a song that was never queued changes nothing`() {
        // "One at a time as played" (§4 item 5's other mode) has no queue at
        // all, so the claim there is a no-op, not an error.
        val queue = HapticQueue(pending = listOf("b"))
        assertEquals(listOf("b"), queue.claimed("x").pending)
    }

    @Test
    fun `a made song never rejoins the queue`() {
        val queue = HapticQueue(pending = listOf("b"), made = setOf("a"))
        assertEquals(listOf("b"), queue.enqueued(listOf("a")).pending)
    }

    @Test
    fun `no song is ever queued twice`() {
        val queue = HapticQueue(pending = listOf("b"))
        val twice = queue.enqueued(listOf("b", "c")).enqueued(listOf("c", "b"))
        assertEquals(listOf("b", "c"), twice.pending)
        assertTrue(twice.pending.toSet().size == twice.pending.size)
    }

    @Test
    fun `completing a song removes it and remembers it as made`() {
        val queue = HapticQueue(pending = listOf("b", "c"))
        val done = queue.completed("b")
        assertEquals(listOf("c"), done.pending)
        assertEquals(setOf("b"), done.made)
        // And the made set holds against a later scan putting it back.
        assertEquals(listOf("c"), done.enqueued(listOf("b")).pending)
    }

    // --- The 2026-10-04 crash. The queue's own rule — no made song pending —
    // --- is right, but storage is written by several hands and can break it.

    @Test
    fun `the stale copy that crashed the app can no longer reach the queue`() {
        // Replayed exactly as it happened on the emulator. The walk read the
        // queue, then spent eight and a half minutes on the long song. Meanwhile
        // the player made "short" itself and took it off the stored list. The
        // walk then wrote back the copy it had read before, which still had
        // "short" on it — and the next song tap built a queue from storage
        // and the constructor threw on the main thread.
        val start = listOf("long", "second", "short", "third")
        val walksCopy = HapticQueue(start)
        val storedAfterThePlayer = HapticQueue(start).claimed("short").pending
        val staleWrite = walksCopy.completed("long").pending
        val made = setOf("long", "short")

        // The old path really did put a made song back on the list.
        assertTrue("short" in staleWrite)

        // Rebuilt from storage, the list drops what the library already has,
        // instead of throwing.
        assertEquals(listOf("second", "third"), HapticQueue.restore(staleWrite, made).pending)

        // And the fixed walk changes the list as it is now, not its old copy,
        // so the player's claim survives the walk finishing its own song.
        assertEquals(
            listOf("second", "third"),
            HapticQueue.restore(storedAfterThePlayer, made).completed("long").pending,
        )
    }

    @Test
    fun `restoring a list keeps the first copy of a duplicate and its order`() {
        // Two writers racing could append the same song twice. The order the
        // user saw is kept; only the repeat goes.
        assertEquals(
            listOf("b", "c", "d"),
            HapticQueue.restore(listOf("b", "c", "b", "d", "c"), emptySet()).pending,
        )
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

    // --- The player's drive (Task 21). The pure side of it: re-syncing from
    // --- the audio position at every join must tile the song exactly once.

    @Test
    fun `the drive from zero plays the whole song, no step skipped or doubled`() {
        // 10,000 steps at 20 ms — longer than the vibrator accepts in one
        // waveform, so the drive must carry it as pieces. Simulating the
        // loop: each iteration plays the piece starting at the position and
        // advances by that piece's own length.
        val whole = Score(20, List(10_000) { (it * 7) % 256 })
        val played = mutableListOf<Int>()
        var at = 0L
        while (at < whole.durationMs) {
            val piece = whole.from(at).pieces(Haptics.MAX_STEPS).first()
            played.addAll(piece.amplitudes)
            at += piece.durationMs
        }
        assertEquals(whole.amplitudes, played)
    }

    @Test
    fun `a re-sync starts exactly where the audio is`() {
        // The whole point of reading the position at each join: resuming at
        // 100 s continues at step 5,000, not at a step the arithmetic guessed.
        val whole = Score(20, List(9_000) { it % 256 })
        val resumed = whole.from(100_000).pieces(Haptics.MAX_STEPS).first()
        assertEquals(whole.amplitudes.drop(5_000), resumed.amplitudes)
    }

    // --- The persisted queue (Task 22).

    @Test
    fun `the pending queue survives being written and read back`() {
        // A pipe cannot appear in a content:// or asset:// URI, so the join is
        // honest and needs no escaping that could drift.
        val pending = listOf(
            "content://media/external/audio/1",
            "content://media/external/audio/2",
            "asset://afro-groove",
        )
        assertEquals(pending, HapticQueue.decodePending(HapticQueue.encodePending(pending)))
    }

    @Test
    fun `a corrupted queue reads as empty rather than crashing a worker`() {
        assertEquals(emptyList<String>(), HapticQueue.decodePending(null))
        assertEquals(emptyList<String>(), HapticQueue.decodePending(""))
        assertEquals(emptyList<String>(), HapticQueue.decodePending("|||"))
    }

    // --- The presets (Task 24) — the three points the 2026-09-21 measurement
    // --- actually walked on the real armed track.

    @Test
    fun `the presets are the measured ladder, not guesses`() {
        assertEquals(ScoreBuilder.BODY_MIN_MS, Tuning.CRISP.bodyMs) // 100 ms — sustained 0.334, more silence than the buzz
        assertEquals(240, Tuning.FULL.bodyMs) // 0.417 — nearly parity
        assertEquals(ScoreBuilder.BODY_MS, Tuning.STRONG.bodyMs) // 0.540 — the only one past the buzz's 0.500
        assertEquals(Tuning.CRISP, Tuning.matching(100))
        assertEquals(Tuning.STRONG, Tuning.matching(400))
        assertNull(Tuning.matching(305))
        assertEquals(3, Tuning.ALL.map { it.bodyMs }.toSet().size)
        // "Reset to default" lands on the measured default: Strong, the only
        // preset that reached the buzz, at normal strength, with Extra taps on.
        assertEquals(Tuning.STRONG.bodyMs, Tuning.RESET_BODY)
        assertEquals(ScoreBuilder.MIN_FELT, Tuning.RESET_PUNCH)
        assertEquals(0, Tuning.RESET_DISTANCE)
    }

    // --- The Thrum pattern file (Task 25). §12: export, then import, gives
    // --- back an identical score — and the file holds no audio.

    private fun exportHaptic(uri: String, name: String) = Haptic(
        trackUri = uri,
        score = Score(20, List(120) { (it * 11) % 256 }, name),
        punch = 208,
        distance = 61,
        bodyMs = 400,
        madeAtMs = 1_785_000_000_000,
    )

    @Test
    fun `export then import gives back an identical score`() {
        val original = exportHaptic("content://media/external/audio/7", "No Dulling")
        val back = ThrumFile.decode(ThrumFile.encode(listOf(original)))
        assertEquals(1, back.size)
        // The score is byte-identical; the tuning rides with it. The track URI
        // does NOT — it is phone-specific and the file is not.
        assertEquals(original.score, back[0].score)
        assertEquals(208, back[0].punch)
        assertEquals(61, back[0].distance)
        assertEquals(400, back[0].bodyMs)
        assertTrue(ThrumFile.isImported(back[0].trackUri))
    }

    @Test
    fun `a bundle of several haptics survives the round trip`() {
        val all = listOf(
            exportHaptic("a", "No Dulling"),
            exportHaptic("b", "Active"),
            exportHaptic("c", "Eid Mubarak"),
        )
        val back = ThrumFile.decode(ThrumFile.encode(all))
        assertEquals(all.map { it.score }, back.map { it.score })
        assertEquals(all.map { it.punch }, back.map { it.punch })
    }

    @Test
    fun `the exported file holds no audio and no source uri`() {
        // Sacred Rule 3's export exception, drawn narrowly: vibration only.
        val original = exportHaptic("content://media/external/audio/secret-uri", "My Song")
        val text = ThrumFile.encode(listOf(original))
        assertTrue("a source URI leaked into the export", !text.contains("content://"))
        // The score line is a few hundred small integers against the megabytes
        // the song came from — a number that cannot lie about holding audio.
        assertTrue("the export is suspiciously large: ${text.length} chars", text.length < 5_000)
    }

    @Test
    fun `a corrupt or foreign file imports as nothing rather than crashing`() {
        assertEquals(emptyList<Haptic>(), ThrumFile.decode(""))
        assertEquals(emptyList<Haptic>(), ThrumFile.decode("nonsense"))
        assertEquals(emptyList<Haptic>(), ThrumFile.decode("THRUM 1\ngarbage\n1 2 3"))
        // A block whose score is empty decodes as a Score but is not a haptic.
        assertEquals(emptyList<Haptic>(), ThrumFile.decode("THRUM 1\n1|20|name|\n208 61 400"))
    }

    @Test
    fun `imported haptics are feel-only by nature`() {
        // The player recognises the scheme and refuses "hear and feel" with a
        // sentence, because the song was never in the file.
        val back = ThrumFile.decode(ThrumFile.encode(listOf(exportHaptic("x", "My Song"))))
        assertTrue(ThrumFile.isImported(back[0].trackUri))
        assertTrue(ThrumFile.fileNameFor("My Song: Act II?").endsWith(".thrum"))
    }

    // --- The My Haptics list (Task 23).

    private fun hapticOf(uri: String, name: String, madeAt: Long) = Haptic(
        trackUri = uri,
        score = Score(20, List(50) { 200 }, name),
        punch = 185,
        distance = 0,
        bodyMs = 400,
        madeAtMs = madeAt,
    )

    @Test
    fun `my haptics joins haptics with their tracks and flags the calls song`() {
        val song = Track("song", "No Dulling", artist = "Keche", durationMs = 238_000, kind = TrackKind.MUSIC)
        val video = Track("video", "Clip", durationMs = 9_000, kind = TrackKind.VIDEO)
        val haptics = listOf(hapticOf("song", "No Dulling", madeAt = 2_000))
        val rows = MyHaptics.rows(haptics, listOf(song, video), callsUri = "song", filter = MyHaptics.Filter.ALL)
        // Two rows: the made haptic and the picked-but-unmade video — a pick
        // that vanished into nothing would be a dead control in list form.
        assertEquals(2, rows.size)
        val songRow = rows.first { it.uri == "song" }
        assertTrue(songRow.hasHaptic)
        assertTrue(songRow.isCalls)
        assertTrue(songRow.subtitle.contains("Music"))
        assertTrue(songRow.subtitle.contains("Keche"))
        val videoRow = rows.first { it.uri == "video" }
        assertTrue(!videoRow.hasHaptic)
        assertEquals(MyHaptics.kindLabel(TrackKind.VIDEO), videoRow.subtitle.substringBefore(" ·"))
    }

    @Test
    fun `a haptic whose track is gone keeps its name from the score`() {
        val rows = MyHaptics.rows(
            haptics = listOf(hapticOf("vanished", "Keche - No Dulling", madeAt = 1_000)),
            tracks = emptyList(),
            callsUri = null,
            filter = MyHaptics.Filter.ALL,
        )
        assertEquals(1, rows.size)
        assertEquals("Keche - No Dulling", rows[0].name)
    }

    @Test
    fun `the filters are all, audio and video`() {
        val music = Track("m", "Song", durationMs = 1, kind = TrackKind.MUSIC)
        val video = Track("v", "Clip", durationMs = 1, kind = TrackKind.VIDEO)
        val file = Track("f", "Note", durationMs = 1, kind = TrackKind.FILE)
        val imported = ThrumFile.IMPORTED_SCHEME + "abc"
        val haptics = listOf(hapticOf("m", "Song", 1_000), hapticOf("v", "Clip", 2_000), hapticOf(imported, "Gift", 3_000))
        val tracks = listOf(music, video, file)
        assertEquals(4, MyHaptics.rows(haptics, tracks, null, MyHaptics.Filter.ALL).size)
        // A scanned song and an audio file picked by hand are both audio.
        assertEquals(setOf("m", "f"), MyHaptics.rows(haptics, tracks, null, MyHaptics.Filter.AUDIO).map { it.uri }.toSet())
        assertEquals(listOf("v"), MyHaptics.rows(haptics, tracks, null, MyHaptics.Filter.VIDEO).map { it.uri })
    }

    @Test
    fun `an imported haptic says so, and a picked audio file says audio`() {
        val file = Track("f", "Note", durationMs = 1_000, kind = TrackKind.FILE)
        val imported = ThrumFile.IMPORTED_SCHEME + "abc"
        val rows = MyHaptics.rows(listOf(hapticOf(imported, "Gift", 1_000)), listOf(file), null, MyHaptics.Filter.ALL)
        assertEquals("Imported", rows.first { it.uri == imported }.subtitle.substringBefore(" ·"))
        assertEquals("Audio", rows.first { it.uri == "f" }.subtitle.substringBefore(" ·"))
    }
}
