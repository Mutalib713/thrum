package com.mosman.thrum

import org.junit.Assert.assertArrayEquals
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

    // --- Task 3: the arithmetic around decoding. MediaCodec needs a phone;
    // --- none of this does, and this is where a wrong answer would be subtle.

    @Test
    fun `a second of audio is reported as a second`() {
        assertEquals(1000L, Pcm.framesToMs(44_100, 44_100))
        assertEquals(1000L, Pcm.framesToMs(48_000, 48_000))
        assertEquals(0L, Pcm.framesToMs(0, 44_100))
    }

    @Test
    fun `duration rounds to nearest so a long track does not drift`() {
        // Three minutes at 44.1 kHz. Truncating loses half a millisecond here,
        // and Task 3's proof is agreement with what the music player shows.
        assertEquals(180_000L, Pcm.framesToMs(7_938_000, 44_100))
        assertEquals(1L, Pcm.framesToMs(23, 44_100))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a zero sample rate is rejected rather than dividing by zero`() {
        Pcm.framesToMs(1000, 0)
    }

    @Test
    fun `stereo keeps the louder channel rather than averaging the two`() {
        // Interleaved: L R L R. Averaging was the original rule and it is wrong:
        // it halves anything panned to one side, and — worse — cancels anything
        // the two channels carry in opposite polarity. The louder channel wins,
        // sign preserved, so neither can happen.
        val stereo = shortArrayOf(100, 300, -200, 0, 0, 1000)
        val mono = ShortArray(3)
        assertEquals(3, Pcm.downmixToMono(stereo, stereo.size, 2, mono))
        assertArrayEquals(shortArrayOf(300, -200, 1000), mono)
    }

    @Test
    fun `stereo that is out of phase does not cancel to silence`() {
        // The bug behind "it doesnt vibrate to the max i cant feel it
        // sometimes". A stereo-widened bass or kick carries the same sound in
        // opposite polarity on each channel. Averaging gave L + R = 0 — the bass
        // did not get quieter, it vanished, and that part of the track scored as
        // silence. No Punch setting can recover a hit that was never detected.
        val outOfPhase = shortArrayOf(10_000, -10_000, -8_000, 8_000)
        val mono = ShortArray(2)
        assertEquals(2, Pcm.downmixToMono(outOfPhase, outOfPhase.size, 2, mono))
        assertEquals(10_000, mono[0].toInt())
        assertEquals(-8_000, mono[1].toInt())
    }

    @Test
    fun `an in-phase stereo pair comes through at full strength not half`() {
        // Both channels equal is the most common case (anything centred). The
        // average and the louder-channel rule agree here, which is what makes
        // the change safe for ordinary music.
        val inPhase = shortArrayOf(12_000, 12_000, -9_000, -9_000)
        val mono = ShortArray(2)
        Pcm.downmixToMono(inPhase, inPhase.size, 2, mono)
        assertArrayEquals(shortArrayOf(12_000, -9_000), mono)
    }

    @Test
    fun `mono audio passes through untouched`() {
        val samples = shortArrayOf(1, -2, 3, -4)
        val mono = ShortArray(4)
        assertEquals(4, Pcm.downmixToMono(samples, samples.size, 1, mono))
        assertArrayEquals(samples, mono)
    }

    @Test
    fun `the downmix does not overflow at full scale`() {
        // Two channels at full negative scale sum to -65536. Done in Short
        // arithmetic that wraps positive, and the loudest moment of a track
        // would read as a bright transient in the wrong direction.
        val loud = shortArrayOf(Short.MIN_VALUE, Short.MIN_VALUE, Short.MAX_VALUE, Short.MAX_VALUE)
        val mono = ShortArray(2)
        Pcm.downmixToMono(loud, loud.size, 2, mono)
        assertArrayEquals(shortArrayOf(Short.MIN_VALUE, Short.MAX_VALUE), mono)
    }

    @Test
    fun `a trailing half frame is ignored rather than misread`() {
        // Decoders fill buffers, not frames, so the last chunk of a stereo file
        // can end mid-frame. Half a frame has no meaning.
        val stereo = shortArrayOf(10, 20, 30)
        val mono = ShortArray(2)
        assertEquals(1, Pcm.downmixToMono(stereo, stereo.size, 2, mono))
        assertEquals(20, mono[0].toInt())
    }

    @Test
    fun `an empty buffer downmixes to nothing`() {
        assertEquals(0, Pcm.downmixToMono(ShortArray(0), 0, 2, ShortArray(0)))
        assertEquals(0, Pcm.monoFrames(0, 2))
    }

    @Test
    fun `peak reports loudness as a magnitude`() {
        assertEquals(300, Pcm.peak(shortArrayOf(100, -300, 200), 3))
        assertEquals(0, Pcm.peak(shortArrayOf(0, 0, 0), 3))
        // Negating Short.MIN_VALUE does not fit in a Short. A full-scale track
        // must not report a negative peak.
        assertEquals(Pcm.MAX_SAMPLE, Pcm.peak(shortArrayOf(Short.MIN_VALUE), 1))
    }

    @Test
    fun `peak only looks at the samples it was told are valid`() {
        // The chunk array is reused between callbacks, so stale loud samples sit
        // past the end of every short chunk.
        val buffer = shortArrayOf(50, 60, 32000, 32000)
        assertEquals(60, Pcm.peak(buffer, 2))
    }

    @Test
    fun `a decode that matches the container is accepted, one that truncates is not`() {
        assertTrue(decoded(containerMs = 180_000, decodedMs = 180_000).durationAgrees)
        // MP3 encoder padding: inaudible, and must not read as a failure.
        assertTrue(decoded(containerMs = 180_000, decodedMs = 179_970).durationAgrees)
        // A decode that stopped early would silently truncate every score.
        assertTrue(!decoded(containerMs = 180_000, decodedMs = 179_000).durationAgrees)
        // Some containers do not state a duration. Absence is not disagreement.
        assertTrue(decoded(containerMs = 0, decodedMs = 180_000).durationAgrees)
    }

    @Test
    fun `a file that decodes to silence is visible as silence`() {
        assertTrue(decoded(peak = 0).isSilent)
        assertEquals(0, decoded(peak = 0).peakPercent)
        assertEquals(100, decoded(peak = Pcm.MAX_SAMPLE).peakPercent)
    }

    // --- Task 4: the analyser. PROFILE.md §12 names five of these checks by
    // --- hand; they are the reason the whole chain is pure Kotlin.

    @Test
    fun `a silent recording produces a silent score`() {
        val score = analyse { silence(seconds = 2.0) }
        assertTrue("silence must not buzz", score.isSilent())
        assertEquals(0, score.pulseCount())
    }

    @Test
    fun `a four on the floor bar produces exactly four pulses`() {
        // 120 bpm, so a kick every 500 ms and four to the bar. This is the check
        // that says the analyser found the beat rather than the loudness.
        val score = analyse { fourOnTheFloor(bars = 1, bpm = 120) }
        assertEquals(4, score.pulseCount())
    }

    @Test
    fun `the beat is still found across several bars and at another tempo`() {
        assertEquals(12, analyse { fourOnTheFloor(bars = 3, bpm = 120) }.pulseCount())
        assertEquals(8, analyse { fourOnTheFloor(bars = 2, bpm = 90) }.pulseCount())
    }

    @Test
    fun `a score from real-shaped audio stays inside the motor's range`() {
        val score = analyse { fourOnTheFloor(bars = 2, bpm = 128) }
        assertTrue(score.amplitudes.all { it in 0..Score.MAX_AMPLITUDE })
        assertEquals(score.amplitudes.size, score.timings().size)
    }

    @Test
    fun `an analysed score survives being written and read back`() {
        val score = analyse { fourOnTheFloor(bars = 1, bpm = 120) }
        assertEquals(score, Score.decode(score.encode()))
    }

    @Test
    fun `the score lasts as long as the audio it came from`() {
        val score = analyse { fourOnTheFloor(bars = 2, bpm = 120) }
        // Two bars at 120 bpm is 4 seconds. One step of slack, since the final
        // partial step is included rather than dropped.
        assertTrue(
            "score was ${score.durationMs}ms",
            score.durationMs in 4000..(4000 + Demo.STEP_MS),
        )
    }

    @Test
    fun `the beat is found underneath a wash of cymbals`() {
        // The low-pass is the whole reason this feels like rhythm rather than
        // mush. With a continuous 8 kHz wash over the kicks, an unfiltered
        // envelope never falls back to the gate and the whole bar reads as one
        // long vibration. Filtered, the kick still comes through as four hits.
        //
        // Note this cannot be tested by comparing a kick-only score against a
        // cymbal-only score: each is normalised to its own loudest step, so a
        // cymbals-only track deliberately still uses the motor's full range.
        // A user who picks a flute solo should feel the flute, not nothing.
        val score = analyse { fourOnTheFloor(bars = 2, bpm = 120, hats = 6000) }
        assertEquals(8, score.pulseCount())
    }

    @Test
    fun `a sustained bass note is texture, never a run of beats`() {
        // Rewritten 2026-08-01 when Mutalib asked for something smoother and
        // more musical. It used to assert a held note produced almost no
        // movement at all, which was right when a score was hits-only — the
        // original bug was a level-following analyser turning Masha Allah into
        // one unbroken 2m23s vibration.
        //
        // A held note may now be *present*, quietly, as body. What it must never
        // do is read as a sequence of beats, so the check moved from "does it
        // move" to "does it hit".
        //
        // **Known artifact, not fixed here.** A pure 60 Hz tone still produces
        // about 47 onsets over four seconds. Rectifying a sine gives a 120 Hz
        // pulse train, and some of that ripple survives into the onset
        // difference as false beats. It is pre-existing — the hits-only version
        // scored identically — and it does not show on real music, which is
        // never a bare sine. Worth revisiting if a track with an exposed
        // synth bass ever feels wrong; not worth disturbing a feel Mutalib has
        // just approved.
        val score = analyse { tone(hz = 60.0, seconds = 4.0, amplitude = 18000) }
        val hits = score.amplitudes.count { it >= ScoreBuilder.MIN_FELT }
        assertTrue(
            "a 4s held note produced $hits hits of ${score.amplitudes.size} steps",
            hits < score.amplitudes.size / 4,
        )
    }

    @Test
    fun `most of a bar is stillness`() {
        // Rhythm is as much the gaps as the hits. Without this, "4 pulses per
        // bar" can still pass while the motor never actually stops.
        val score = analyse { fourOnTheFloor(bars = 4, bpm = 120) }
        val still = score.amplitudes.count { it == 0 }
        assertTrue(
            "only ${100 * still / score.amplitudes.size}% of the bar was still",
            still > score.amplitudes.size / 2,
        )
    }

    @Test
    fun `a hit is over before the next one arrives`() {
        val score = analyse { fourOnTheFloor(bars = 4, bpm = 120) }
        val longestRun = score.amplitudes
            .fold(0 to 0) { (longest, current), a ->
                val run = if (a > 0) current + 1 else 0
                maxOf(longest, run) to run
            }.first
        // A beat at 120 bpm is 500 ms. A hit that outlasts that has merged with
        // the following one, and the rhythm is gone.
        assertTrue(
            "longest hit ran ${longestRun * Demo.STEP_MS}ms",
            longestRun * Demo.STEP_MS < 500,
        )
    }

    @Test
    fun `nothing in a score is too weak to be felt`() {
        // Restored after being deleted. The original version of this guard
        // covered the kick layer, and adding a second layer replaced it with a
        // narrower check — so the detail layer shipped mapping from zero, put
        // most of its hits at amplitudes of 1, 3, 7, 20, and was entirely
        // inaudible. Mutalib found it by reporting that every setting felt the
        // same, which is exactly what scaling imperceptible numbers feels like.
        //
        // Every layer, present and future, obeys the same law: an amplitude is
        // either worth feeling or it is zero.
        for (detail in listOf(0, 60, 150, 200, 255)) {
            val score = analyse(detailCeiling = detail) { fourOnTheFloor(bars = 2, bpm = 120) }
            val tooWeak = score.amplitudes.filter { it in 1 until ScoreBuilder.DETAIL_MIN }
            assertTrue("detail=$detail produced unfeelable steps $tooWeak", tooWeak.isEmpty())
        }
    }

    @Test
    fun `a track keeps its loud and quiet beats however hard Punch is pushed`() {
        // Mutalib pushed Punch to its maximum and then reported that no other
        // setting made any difference. It didn't: a kick is mapped as
        // floor + curve × (255 − floor), so a floor of 255 leaves a range of
        // zero and every hit in the track comes out at exactly 255 — the loud
        // ones and the quiet ones alike. A dial that can flatten the whole
        // output must not be able to reach that point.
        for (requested in listOf(180, 220, 255, 300)) {
            val score = analyse(punch = requested) { fourOnTheFloor(bars = 3, bpm = 120) }
            val hits = score.amplitudes.filter { it > 0 }.distinct()
            assertTrue(
                "punch=$requested produced ${hits.size} distinct amplitude(s)",
                hits.size > 1,
            )
        }
    }

    @Test
    fun `nothing in a score is too brief to be felt`() {
        // The companion to the felt-floor rule, and the one that was missing.
        // Detail hits shipped with no hold at all: 20-40ms each against a kick's
        // 160ms. A motor has mass, so 20ms ends while it is still spinning up
        // and the hit never arrives. Measuring the score on the phone showed
        // twelve such hits in forty-five seconds, all of them inaudible.
        //
        // Loud enough AND long enough, or it is not a hit.
        for (distanceCeiling in listOf(60, 130, 190)) {
            val score = analyse(detailCeiling = distanceCeiling) {
                fourOnTheFloor(bars = 3, bpm = 120, hats = 9000)
            }
            var run = 0
            val runs = mutableListOf<Int>()
            for (a in score.amplitudes) {
                if (a > 0) run++ else if (run > 0) { runs.add(run); run = 0 }
            }
            if (run > 0) runs.add(run)
            val shortest = (runs.minOrNull() ?: 0) * score.stepMs
            assertTrue(
                "ceiling=$distanceCeiling produced a ${shortest}ms hit",
                shortest >= ScoreBuilder.DETAIL_PULSE_MS,
            )
        }
    }

    @Test
    fun `moving the detail dial actually changes the score`() {
        // The regression that started this: the dial moved and nothing changed.
        val quiet = analyse(detailCeiling = 0) { fourOnTheFloor(bars = 2, bpm = 120, hats = 7000) }
        val loud = analyse(detailCeiling = 220) { fourOnTheFloor(bars = 2, bpm = 120, hats = 7000) }
        assertTrue(
            "detail 0 and detail 220 produced the same score",
            quiet.amplitudes != loud.amplitudes,
        )
        assertTrue(
            "turning detail up did not add anything",
            loud.amplitudes.count { it > 0 } > quiet.amplitudes.count { it > 0 },
        )
    }

    @Test
    fun `there is nothing in the dead zone between texture and a hit`() {
        // A phone on a table did not move at all when hits landed at 45-150:
        // the bottom of the range is not quiet, it is nothing. That produced a
        // rule of "no amplitude below MIN_FELT", which held while a score was
        // hits-only.
        //
        // A score now has two layers by design — body up to BODY_CEILING for
        // texture, hits from MIN_FELT up. The invariant that replaces the old
        // one is the gap between them: an amplitude in between is too weak to
        // register as a beat and too strong to sit under one.
        val score = analyse { fourOnTheFloor(bars = 2, bpm = 120) }
        val stranded = score.amplitudes
            .filter { it > ScoreBuilder.BODY_CEILING && it < ScoreBuilder.MIN_FELT }
        assertTrue("$stranded landed in the dead zone", stranded.isEmpty())
    }

    @Test
    fun `hits still stand clear of the texture underneath them`() {
        val score = analyse { fourOnTheFloor(bars = 2, bpm = 120) }
        val hits = score.amplitudes.filter { it >= ScoreBuilder.MIN_FELT }
        assertTrue("no hits at all", hits.isNotEmpty())
        // The body layer must never be mistaken for a beat.
        assertTrue(hits.min() > ScoreBuilder.BODY_CEILING)
    }

    @Test
    fun `every hit lasts long enough to move the motor`() {
        val score = analyse { fourOnTheFloor(bars = 2, bpm = 120) }
        var run = 0
        val runs = mutableListOf<Int>()
        for (a in score.amplitudes) {
            if (a > 0) run++ else if (run > 0) { runs.add(run); run = 0 }
        }
        if (run > 0) runs.add(run)
        val shortest = (runs.minOrNull() ?: 0) * score.stepMs
        assertTrue("shortest hit was ${shortest}ms", shortest >= ScoreBuilder.MIN_PULSE_MS)
    }

    @Test
    fun `holding a pulse keeps its leading edge where the beat is`() {
        // Widening around the peak instead of forward would move the hit earlier
        // and put the whole rhythm ahead of the music.
        val score = Score(20, listOf(0, 0, 200, 0, 0, 0, 0, 0))
        val held = score.holdPulsesAtLeast(3)
        assertEquals(listOf(0, 0, 200, 200, 200, 0, 0, 0), held.amplitudes)
    }

    @Test
    fun `holding never runs a pulse over the next one`() {
        val score = Score(20, listOf(100, 0, 255, 0, 0, 0))
        val held = score.holdPulsesAtLeast(4)
        // The first hit may only take the one silent step before the next hit.
        assertEquals(listOf(100, 100, 255, 255, 255, 255), held.amplitudes)
    }

    @Test
    fun `a minimum duration rounds up never down`() {
        // The hold is a promise that a hit lasts at least the named number of
        // milliseconds. Truncating the division broke it in the worst way:
        // 45 ms at 20 ms steps gave 2 steps (40 ms), *under* the 45 the constant
        // promises, and at 40 ms steps it gave 1 step, so the hold did nothing.
        assertEquals(3, ScoreBuilder.stepsFor(45, 20))
        assertEquals(2, ScoreBuilder.stepsFor(45, 40))
        assertEquals(1, ScoreBuilder.stepsFor(20, 20))
        assertEquals(5, ScoreBuilder.stepsFor(100, 20))
        assertEquals(3, ScoreBuilder.stepsFor(41, 20))
        assertEquals(1, ScoreBuilder.stepsFor(1, 20))
    }

    @Test
    fun `a held pulse never fades below the floor it must stay above`() {
        // Mutalib: "it doesnt vibrate to the max i cant feel it sometimes."
        // A 185-peak kick held for five steps with a 0.55 decay used to walk
        // down to ~102 — under the ~140 where the motor barely moves — so most
        // of the hold was time his hand never received. The floor stops it.
        val score = Score(20, listOf(ScoreBuilder.MIN_FELT, 0, 0, 0, 0, 0))
        val held = score.holdPulsesAtLeast(6, decayTo = 0.55f, floor = ScoreBuilder.MIN_FELT)
        val tail = held.amplitudes.drop(1)
        assertEquals("held steps: $tail", 5, tail.size)
        assertTrue(
            "held steps faded below the floor: $tail",
            tail.all { it >= ScoreBuilder.MIN_FELT },
        )
    }

    @Test
    fun `a pulse weaker than the floor is not inflated to it`() {
        // The floor is a limit on the decay, not a lift. A genuine quiet detail
        // hit must be allowed to stay quiet, or the floor becomes a second
        // Punch dial that flattens everything to one value.
        val score = Score(20, listOf(90, 0, 0, 0))
        val held = score.holdPulsesAtLeast(4, decayTo = 0.55f, floor = ScoreBuilder.MIN_FELT)
        assertTrue("a weak pulse was inflated: ${held.amplitudes}", held.amplitudes.all { it <= 90 })
    }

    @Test
    fun `no held step lands in the unfeelable dead zone whatever Punch says`() {
        // The decay fix, checked end to end through the real analyser at every
        // Punch setting including the maximum. Nothing may land between 1 and
        // the motor's floor: an amplitude there is too weak to feel and too
        // strong to be zero.
        for (punch in listOf(120, 185, 210, 255)) {
            val score = analyse(punch = punch) { fourOnTheFloor(bars = 3, bpm = 120, hats = 8000) }
            val dead = score.amplitudes.filter { it in 1 until ScoreBuilder.DETAIL_MIN }
            assertTrue("punch=$punch produced unfeelable steps $dead", dead.isEmpty())
        }
    }

    @Test
    fun `a quiet recording is not left quiet`() {
        // Normalising by the loudest step is what stops a score's strength
        // depending on how the track was mastered.
        val loud = analyse { fourOnTheFloor(bars = 1, bpm = 120, amplitude = 30000) }
        val quiet = analyse { fourOnTheFloor(bars = 1, bpm = 120, amplitude = 900) }
        assertEquals(loud.pulseCount(), quiet.pulseCount())
        assertEquals(loud.amplitudes.max(), quiet.amplitudes.max())
    }

    @Test
    fun `the sample rate does not change the rhythm`() {
        // Files arrive at 44100 and 48000 alike. A coefficient tuned for one
        // would give the other a different attack, which would surface much
        // later as "some tracks feel wrong".
        val at44 = analyse(44_100) { fourOnTheFloor(bars = 2, bpm = 120) }
        val at48 = analyse(48_000) { fourOnTheFloor(bars = 2, bpm = 120) }
        assertEquals(at44.pulseCount(), at48.pulseCount())
        assertTrue(kotlin.math.abs(at44.durationMs - at48.durationMs) <= Demo.STEP_MS)
    }

    @Test
    fun `feeding audio in ragged chunks gives the same score`() {
        // The decoder's chunk size is whatever the codec felt like emitting, so
        // the analyser must not depend on it. If it did, the same file would
        // produce different scores on different phones.
        val audio = Fixture(44_100).apply { fourOnTheFloor(bars = 1, bpm = 120) }.samples()
        val whole = ScoreBuilder(44_100).apply { feed(audio, audio.size) }.build()

        val ragged = ScoreBuilder(44_100)
        var at = 0
        var size = 1
        while (at < audio.size) {
            val take = minOf(size, audio.size - at)
            ragged.feed(audio.copyOfRange(at, at + take), take)
            at += take
            size = (size * 3 + 7) % 5000 + 1
        }
        assertEquals(whole, ragged.build())
    }

    // --- R8: the waveform length lever.

    @Test
    fun `coarsening halves the step count and keeps the hits`() {
        val score = Score(20, listOf(0, 200, 0, 0, 255, 0, 30, 0))
        val coarse = score.coarsen(2)
        assertEquals(40, coarse.stepMs)
        assertEquals(listOf(200, 0, 255, 30), coarse.amplitudes)
        // The point of coarsening is to survive a device cap without losing the
        // beat, so total length must not move.
        assertEquals(score.durationMs, coarse.durationMs)
    }

    @Test
    fun `coarsening keeps the loudest of each group rather than the average`() {
        // Averaging a hit with the silence beside it flattens the transient that
        // makes a rhythm feel like one.
        val score = Score(20, listOf(255, 0, 0, 0))
        assertEquals(listOf(255, 0), score.coarsen(2).amplitudes)
        assertEquals(listOf(255), score.coarsen(4).amplitudes)
    }

    @Test
    fun `a ragged tail still coarsens`() {
        val score = Score(20, listOf(10, 20, 30, 40, 50))
        assertEquals(listOf(20, 40, 50), score.coarsen(2).amplitudes)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `coarsening by zero is rejected`() {
        Score(20, listOf(1, 2)).coarsen(0)
    }

    @Test
    fun `a score too long for the vibrator is coarsened until it fits`() {
        // The measured wall on the Pixel 6 Pro is between 10,500 and 11,000
        // steps; over it, the vibration silently never reaches the motor.
        val fourMinutes = Score(20, List(11_922) { 100 })
        val fitted = fourMinutes.fitWithin(Haptics.MAX_STEPS)
        assertTrue("was ${fitted.amplitudes.size}", fitted.amplitudes.size <= Haptics.MAX_STEPS)
        // Coverage is what must survive: the whole track, at a coarser step.
        assertTrue(fitted.durationMs >= fourMinutes.durationMs)
        assertEquals(40, fitted.stepMs)
    }

    @Test
    fun `starting partway through drops exactly the steps already played`() {
        // Task 5: audio does not start the instant it is asked to, so the
        // vibration has to begin from wherever the speaker actually is.
        val score = Score(20, List(100) { it % Score.MAX_AMPLITUDE })
        assertEquals(95, score.from(100).amplitudes.size)
        assertEquals(score.amplitudes[5], score.from(100).amplitudes[0])
        assertEquals(score, score.from(0))
        assertEquals(score, score.from(-50))
    }

    @Test
    fun `starting past the end gives an empty score rather than throwing`() {
        val score = Score(20, List(10) { 100 })
        assertTrue(score.from(10_000).amplitudes.isEmpty())
    }

    @Test
    fun `a score that already fits is left alone`() {
        val short = Score(20, List(1500) { 100 })
        assertEquals(short, short.fitWithin(Haptics.MAX_STEPS))
    }

    @Test
    fun `fitting never lands one group over the limit`() {
        // Rounding down here would produce a score that still fails, and fails
        // invisibly, which is the whole bug being defended against.
        for (size in listOf(8001, 12_000, 16_001, 40_000, 99_999)) {
            val fitted = Score(20, List(size) { 50 }).fitWithin(Haptics.MAX_STEPS)
            assertTrue("$size -> ${fitted.amplitudes.size}", fitted.amplitudes.size <= Haptics.MAX_STEPS)
        }
    }

    // --- The three "I can't feel it" causes found after Task 8: the dead detail
    // --- dial, the quiet opening gated away, and stereo that cancels itself.

    @Test
    fun `the detail dial changes something at every position`() {
        // The old mapping was punch * (100 - distance) / 100, and the layer
        // switched itself off once the ceiling fell to DETAIL_MIN. At the
        // default Punch of 185 that happened at distance 30 — so seven-tenths of
        // the dial did nothing at all, and a dial dead across most of its travel
        // is indistinguishable from a broken one.
        val ceilings = (0..99).map { ScoreBuilder.ceilingFor(ScoreBuilder.MIN_FELT, it) }
        assertTrue(
            "a dial position is dead: $ceilings",
            ceilings.all { it > ScoreBuilder.DETAIL_MIN },
        )
        assertTrue("the dial does not actually vary: $ceilings", ceilings.distinct().size > 50)
    }

    @Test
    fun `only the very top of the detail dial switches the layer off`() {
        assertEquals(0, ScoreBuilder.ceilingFor(ScoreBuilder.MIN_FELT, 100))
        assertTrue(
            "99 should still work",
            ScoreBuilder.ceilingFor(ScoreBuilder.MIN_FELT, 99) > ScoreBuilder.DETAIL_MIN,
        )
    }

    @Test
    fun `closer to the music is never quieter than further away`() {
        // Monotonic, so turning the dial up can never add detail — which would
        // read as the dial fighting the user.
        val ceilings = (0..100).map { ScoreBuilder.ceilingFor(200, it) }
        assertTrue("not monotonic: $ceilings", ceilings.zipWithNext().all { (a, b) -> a >= b })
    }

    @Test
    fun `the detail dial is alive across the whole Punch range that has room for it`() {
        // The kick's floor is Punch, and detail is capped below it so the beat
        // leads. Below DETAIL_MIN + a little there is genuinely no room and the
        // layer is off — a real constraint, not a bug. Everywhere above it, the
        // dial must work at every position.
        for (punch in listOf(150, 185, 210, 255)) {
            val ceilings = (0..99).map { ScoreBuilder.ceilingFor(punch, it) }
            assertTrue(
                "punch=$punch has a dead dial position: $ceilings",
                ceilings.all { it > ScoreBuilder.DETAIL_MIN },
            )
        }
        // And where there is no room, it says so rather than pretending.
        assertEquals(0, ScoreBuilder.ceilingFor(ScoreBuilder.DETAIL_MIN, 0))
    }

    @Test
    fun `a quiet opening survives a much louder section later in the track`() {
        // The ringtone window is the first 45 s, but the scale used to be set by
        // the loudest moment *anywhere*. A track that opens quietly and peaks
        // minutes later had its opening scaled down until the beats fell under
        // the gate and came out as literal zeros — and Punch cannot bring back a
        // step that was gated away.
        val rate = 44_100
        val stepMs = Demo.STEP_MS
        val windowSteps = 100 // one bar at 120 bpm = 2 s = 100 steps at 20 ms
        val audio = Fixture(rate).apply {
            fourOnTheFloor(bars = 1, bpm = 120, amplitude = 2_000)   // the quiet opening
            fourOnTheFloor(bars = 1, bpm = 120, amplitude = 30_000)  // the chorus, 15x louder
        }.samples()

        fun openingHits(window: Int): Int {
            val builder = ScoreBuilder(rate, stepMs, windowSteps = window)
                .apply { feed(audio, audio.size) }
            val score = ScoreBuilder.toScore(builder.levels(), stepMs)
            return score.amplitudes.take(windowSteps).count { it >= ScoreBuilder.DETAIL_MIN }
        }

        val globalNorm = openingHits(Int.MAX_VALUE)
        val windowNorm = openingHits(windowSteps)
        assertTrue(
            "the windowed opening ($windowNorm hits) is no stronger than the " +
                "whole-track one ($globalNorm)",
            windowNorm > globalNorm,
        )
        assertTrue("the opening is still inaudible: $windowNorm hits", windowNorm >= 3)
    }

    @Test
    fun `a near silent window does not amplify its noise floor`() {
        // The guard on the window. At -44 dB the opening is dither, not music,
        // and normalising *to* it would turn the noise floor into a drum kit.
        // Below WINDOW_NORM_GUARD the track's own peak is used instead.
        val rate = 44_100
        val audio = Fixture(rate).apply {
            fourOnTheFloor(bars = 1, bpm = 120, amplitude = 200)
            fourOnTheFloor(bars = 1, bpm = 120, amplitude = 30_000)
        }.samples()
        val builder = ScoreBuilder(rate, Demo.STEP_MS, windowSteps = 100)
            .apply { feed(audio, audio.size) }
        val opening = builder.levels().onsets.take(100)
        assertTrue(
            "a near-silent window was amplified to ${opening.max()}",
            opening.max() < 0.5f,
        )
    }

    private fun analyse(
        sampleRate: Int = 44_100,
        detailCeiling: Int = ScoreBuilder.BODY_CEILING,
        punch: Int = ScoreBuilder.MIN_FELT,
        build: Fixture.() -> Unit,
    ): Score {
        val fixture = Fixture(sampleRate).apply(build)
        val samples = fixture.samples()
        val builder = ScoreBuilder(sampleRate).apply { feed(samples, samples.size) }
        return ScoreBuilder.toScore(
            builder.levels(),
            builder.stepMsUsed,
            minFelt = punch,
            bodyCeiling = detailCeiling,
        )
    }

    /** Builds synthetic mono audio, so the analyser can be tested without a phone or a file. */
    private class Fixture(private val sampleRate: Int) {
        private val out = ArrayList<Short>()

        fun samples(): ShortArray = ShortArray(out.size) { out[it] }

        fun silence(seconds: Double) {
            repeat((seconds * sampleRate).toInt()) { out.add(0) }
        }

        fun tone(hz: Double, seconds: Double, amplitude: Int) {
            val count = (seconds * sampleRate).toInt()
            for (n in 0 until count) {
                val v = amplitude * kotlin.math.sin(2 * Math.PI * hz * n / sampleRate)
                out.add(v.toInt().toShort())
            }
        }

        /**
         * A kick on every beat: a short 60 Hz burst that decays, then silence
         * until the next one. Crude, but it is unambiguously four hits per bar,
         * which is what the check needs to mean something.
         */
        fun fourOnTheFloor(bars: Int, bpm: Int, amplitude: Int = 20000, hats: Int = 0) {
            val beatSamples = (60.0 / bpm * sampleRate).toInt()
            val hitSamples = (0.06 * sampleRate).toInt()
            var t = 0
            repeat(bars * 4) {
                for (n in 0 until beatSamples) {
                    val kick = if (n < hitSamples) {
                        val decay = 1.0 - n.toDouble() / hitSamples
                        amplitude * decay * kotlin.math.sin(2 * Math.PI * 60.0 * n / sampleRate)
                    } else {
                        0.0
                    }
                    // A continuous high-frequency wash, the thing the low-pass
                    // exists to ignore.
                    val wash = hats * kotlin.math.sin(2 * Math.PI * 8000.0 * t / sampleRate)
                    out.add((kick + wash).toInt().coerceIn(-32768, 32767).toShort())
                    t++
                }
            }
        }
    }

    private fun decoded(
        containerMs: Long = 0,
        decodedMs: Long = 0,
        peak: Int = Pcm.MAX_SAMPLE,
    ) = Decoded.Ok(
        name = "test.mp3",
        mime = "audio/mpeg",
        sampleRate = 44_100,
        channels = 2,
        frames = 0,
        decodedMs = decodedMs,
        containerMs = containerMs,
        peak = peak,
        elapsedMs = 0,
    )
}
