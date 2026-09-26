package com.mosman.thrum

import android.media.AudioFormat
import java.nio.ByteBuffer
import java.nio.ByteOrder
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
        //
        // **The threshold moved when the default did, and the reason is the
        // measurement rather than the failure.** At the 240 ms default this
        // asserted "more than half a bar is still", which a 120 bpm fixture
        // cleared with 52 % to spare. The default is now 400 ms — the only
        // setting that reaches parity with the buzz it replaces, see
        // [ScoreBuilder.BODY_MS] — and a 400 ms hold in a 500 ms bar leaves a
        // 100 ms gap, so the identical assertion reads 16 % and fails.
        //
        // Rather than drop the guarantee, it is now asserted at both ends of the
        // dial: the loud default must still leave a real gap, and the crisp end
        // must still be mostly still. What is *not* relaxed is the hard
        // guarantee that a hit never spans a beat — that is
        // `a hit is over before the next one arrives`, and it is what "the motor
        // stops" actually rests on. This test is the margin around it.
        val bar = analyse { fourOnTheFloor(bars = 4, bpm = 120) }
        val barStill = bar.amplitudes.count { it == 0 }
        assertTrue(
            "only ${100 * barStill / bar.amplitudes.size}% of the bar was still at the default",
            barStill > bar.amplitudes.size / 8,
        )

        val crisp = analyse(bodyMs = ScoreBuilder.BODY_MIN_MS) {
            fourOnTheFloor(bars = 4, bpm = 120)
        }
        val crispStill = crisp.amplitudes.count { it == 0 }
        assertTrue(
            "only ${100 * crispStill / crisp.amplitudes.size}% of the bar was still at the crisp end",
            crispStill > crisp.amplitudes.size / 2,
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
        val shortest = runLengthsMs(score).minOrNull() ?: 0
        // Not merely "long enough to spin up" — the old 100 ms bar was cleared
        // while the phone still could not move a table. This fixture leaves
        // 500 ms between beats, so the whole default Body fits and every hit
        // must actually get it.
        assertTrue("shortest hit was ${shortest}ms", shortest >= ScoreBuilder.BODY_MS)
    }

    @Test
    fun `the default Body drives the motor long enough to be felt on a table`() {
        // Mutalib, 2026-09-20: "a normal vibration should be higher ... lets say
        // the phone is on a table it should vibrate the table". Measured from
        // the system's own record: Android's incoming-call vibration holds 255
        // for 1000 ms, where Thrum's longest full-strength run was 100 ms.
        val score = analyse { fourOnTheFloor(bars = 2, bpm = 120) }
        val held = longestFeltRunMs(score)
        assertTrue(
            "longest felt drive was ${held}ms, under the ${ScoreBuilder.BODY_MS}ms default",
            held >= ScoreBuilder.BODY_MS,
        )
    }

    @Test
    fun `the old fixed pulse length would fail the table test`() {
        // Guards the diagnosis itself. If the crisp end ever stops being
        // genuinely shorter than the default, the measurement behind BODY_MS has
        // gone stale and the default needs re-deriving rather than keeping.
        //
        // Not an exact-equality check: the felt run is a step or two longer than
        // the dial asks for, because the detail layer's own shorter hold lands
        // against the kick's and [ScoreBuilder.toScore] takes the louder of the
        // two. That overlap is real, so the assertion is a bound, not a number.
        val score = analyse(bodyMs = ScoreBuilder.BODY_MIN_MS) { fourOnTheFloor(bars = 2, bpm = 120) }
        val held = longestFeltRunMs(score)
        assertTrue("crisp end drove only ${held}ms", held >= ScoreBuilder.BODY_MIN_MS)
        assertTrue(
            "the old fixed length drove ${held}ms, no shorter than the " +
                "${ScoreBuilder.BODY_MS}ms default — the diagnosis has gone stale",
            held < ScoreBuilder.BODY_MS,
        )
    }

    @Test
    fun `a longer Body drives longer without changing which steps are hits`() {
        val short = analyse(bodyMs = ScoreBuilder.BODY_MIN_MS) { fourOnTheFloor(bars = 2, bpm = 120) }
        val long = analyse(bodyMs = ScoreBuilder.BODY_MAX_MS) { fourOnTheFloor(bars = 2, bpm = 120) }
        // Same number of hits either way: Body changes how long each one is
        // driven, never how many the analyser found.
        assertEquals(short.pulseCount(), long.pulseCount())
        assertTrue(
            "short=${runLengthsMs(short).max()} long=${runLengthsMs(long).max()}",
            runLengthsMs(long).max() > runLengthsMs(short).max(),
        )
    }

    @Test
    fun `a long Body still stops at the next beat instead of merging them`() {
        // The dial's maximum is longer than the gap between beats at a fast
        // tempo, so the hold has to be capped by the next hit rather than by the
        // dial — otherwise "more presence" would quietly become "one long buzz"
        // and the rhythm would be gone at exactly the setting meant to help.
        val score = analyse(bodyMs = ScoreBuilder.BODY_MAX_MS) {
            fourOnTheFloor(bars = 4, bpm = 128)
        }
        // 128 bpm is a beat every 469 ms; a 400 ms hold leaves a real gap.
        val gap = 60_000 / 128 - ScoreBuilder.BODY_MAX_MS
        assertTrue("fixture assumption broken, gap ${gap}ms", gap > 0)
        // Four bars of four-on-the-floor is sixteen beats, and every one of them
        // must still be its own run at the dial's longest setting.
        assertEquals(
            "beats were merged into one run",
            score.pulseCount(),
            runLengthsMs(score).count { it >= ScoreBuilder.BODY_MAX_MS },
        )
    }

    @Test
    fun `raising the Body raises the total drive`() {
        // The point of the whole change. A longer hold adds its steps at the
        // kick's floor rather than at its peak, so the *mean* amplitude can fall
        // while the total still rises — and the total is what a table responds
        // to. This is also the assertion that would have caught the original
        // bug: at a fixed 100 ms the drive could not be raised at all, which is
        // why turning Punch up never helped.
        val short = analyse(bodyMs = ScoreBuilder.BODY_MIN_MS) { fourOnTheFloor(bars = 2, bpm = 120) }
        val long = analyse(bodyMs = ScoreBuilder.BODY_MAX_MS) { fourOnTheFloor(bars = 2, bpm = 120) }
        val shortEnergy = short.amplitudes.sum()
        val longEnergy = long.amplitudes.sum()
        assertTrue(
            "total drive did not rise: $shortEnergy -> $longEnergy",
            longEnergy > shortEnergy,
        )
    }

    @Test
    fun `a long Body joins hits into one run but never erases one`() {
        // The honest cost of the dial, pinned so it stays a known trade rather
        // than a surprise. A hold runs forward into the silence and stops at the
        // next hit, so the two end up sharing one unbroken run and the motor
        // feels one longer push instead of two separate taps. That is what the
        // dial's own help text means by "down to keep the beats crisp and
        // separate".
        //
        // What must NOT happen is the later hit being written over. That would
        // be the dial eating the pattern rather than joining it, and it is the
        // difference between a stronger alert and a broken one.
        val score = Score(20, listOf(255, 0, 0, 0, 0, 0, 0, 0, 0, 0, 130))
        val held = score.holdPulsesAtLeast(12, decayTo = 0.55f, floor = 208)
        assertEquals("the later hit was written over", 130, held.amplitudes[10])
        assertEquals("the hold ran past the end of the score", 11, held.amplitudes.size)
        assertEquals("the two hits should now read as one run", 1, held.pulseCount())
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
    fun `rotating starts where it should and keeps the full length`() {
        // The ring-mode re-assert. `from` truncated, so looping the result looped
        // only the tail and the opening of the rhythm was never heard again.
        val score = Score(20, listOf(10, 20, 30, 40, 50))
        val rotated = score.rotated(60) // three steps in
        assertEquals(listOf(40, 50, 10, 20, 30), rotated.amplitudes)
        assertEquals("rotation must not shorten the score", score.durationMs, rotated.durationMs)
        assertEquals(score.amplitudes.size, rotated.amplitudes.size)
    }

    @Test
    fun `rotating past the end wraps instead of going silent`() {
        // `from` returned an empty score here, and an empty score is not a quiet
        // ring — Haptics refuses it, so the rest of the call had no vibration at
        // all whenever looping was off.
        val score = Score(20, listOf(10, 20, 30, 40, 50))
        val wrapped = score.rotated(120) // six steps in, one past the end
        assertEquals(listOf(20, 30, 40, 50, 10), wrapped.amplitudes)
        assertTrue("a wrapped rotation must never be empty", wrapped.amplitudes.isNotEmpty())
    }

    @Test
    fun `a rotation is the same steps in a different order, nothing lost`() {
        // The invariant that makes rotation safe where truncation was not: the
        // score a user hears is always the whole song, whatever the offset.
        val original = Score(20, List(40) { it * 6 % Score.MAX_AMPLITUDE })
        for (startMs in listOf(0L, 20L, 300L, 780L, 800L, 1234L)) {
            val rotated = original.rotated(startMs)
            assertEquals("$startMs changed the length", original.amplitudes.size, rotated.amplitudes.size)
            assertEquals(
                "$startMs changed the content",
                original.amplitudes.sorted(),
                rotated.amplitudes.sorted(),
            )
        }
    }

    @Test
    fun `rotating by nothing at all is a no-op`() {
        val score = Score(20, listOf(10, 0, 30))
        assertEquals(score, score.rotated(0))
        assertEquals(score, score.rotated(-500))
        assertTrue(Score(20, emptyList()).rotated(100).amplitudes.isEmpty())
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
        bodyMs: Int = ScoreBuilder.BODY_MS,
        build: Fixture.() -> Unit,
    ): Score {
        val fixture = Fixture(sampleRate).apply(build)
        val samples = fixture.samples()
        val builder = ScoreBuilder(sampleRate).apply { feed(samples, samples.size) }
        return ScoreBuilder.toScore(
            builder.levels(),
            builder.stepMsUsed,
            minFelt = punch,
            bodyMs = bodyMs,
            bodyCeiling = detailCeiling,
        )
    }

    /** Every unbroken non-zero run, in milliseconds. */
    private fun runLengthsMs(score: Score): List<Int> {
        val runs = mutableListOf<Int>()
        var run = 0
        for (a in score.amplitudes) {
            if (a > 0) run++ else if (run > 0) { runs.add(run * score.stepMs); run = 0 }
        }
        if (run > 0) runs.add(run * score.stepMs)
        return runs
    }

    /** The longest unbroken stretch driven hard enough for the motor to act on
     *  it — the number that decides whether a phone lying on a table moves.
     *
     *  Measured at [ScoreBuilder.MIN_FELT] rather than at 255 on purpose. Only
     *  a hit's leading step carries the full peak; the steps it is held for
     *  decay toward the floor, which is exactly the behaviour that keeps a hold
     *  from sounding mechanical. Counting only 255 would therefore measure one
     *  step per hit and say nothing about how long the beat is driven — the
     *  thing this dial actually changes. */
    private fun longestFeltRunMs(score: Score, threshold: Int = ScoreBuilder.MIN_FELT): Int {
        var best = 0
        var run = 0
        for (a in score.amplitudes) {
            run = if (a >= threshold) run + score.stepMs else 0
            best = maxOf(best, run)
        }
        return best
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

    // --- Task 9: the setup verdict ------------------------------------------
    //
    // The reason this decision lives in pure Kotlin is so it can be proved here,
    // on the PC, with no phone in the room. Every combination of ringer mode and
    // switch is covered rather than the two happy ones, because the failures are
    // the entire reason the screen exists.
    //
    // What cannot be tested here is whether `AudioManager` really reports
    // "silent" — that needs the phone. What can be, and is: that once it does,
    // the app says the right thing about it.

    @Test
    fun `vibrate fires whatever the ring-mode switch says`() {
        // The switch is about ring mode. It must not be able to reach into the
        // one case this whole app is for.
        for (switch in listOf(true, false)) {
            assertEquals(Setup.Verdict.WILL_FIRE, Setup.verdict(Setup.Ringer.VIBRATE, switch))
        }
    }

    @Test
    fun `silent never fires, whatever the ring-mode switch says`() {
        // The measured one, and the reason the screen is worth building. Android
        // discards a RINGTONE vibration outright when the ringer is silent, so no
        // switch in this app can rescue it — and a verdict that let the switch
        // appear to would be a promise the motor cannot keep.
        for (switch in listOf(true, false)) {
            assertEquals(Setup.Verdict.WONT_FIRE_SILENT, Setup.verdict(Setup.Ringer.SILENT, switch))
        }
    }

    @Test
    fun `ring mode follows the user's switch, both ways`() {
        // The same rule NotifService.onIncoming applies at call time. If these two
        // ever disagree, the screen is describing a different app than the one
        // that answers the phone.
        assertEquals(
            Setup.Verdict.WILL_FIRE_IN_RING,
            Setup.verdict(Setup.Ringer.RING, fireInRingMode = true),
        )
        assertEquals(
            Setup.Verdict.WONT_FIRE_RING_OFF,
            Setup.verdict(Setup.Ringer.RING, fireInRingMode = false),
        )
    }

    @Test
    fun `an unreadable ringer is an admission, not a verdict`() {
        // AudioManager reports a mode this app does not know on some OEM builds.
        // Claiming it will work would be inventing a fact; claiming it will not
        // would be crying wolf. It has to be neither.
        val verdict = Setup.verdict(Setup.Ringer.UNKNOWN, fireInRingMode = true)
        assertEquals(Setup.Verdict.UNKNOWN, verdict)
        assertTrue("an unknown ringer must not read as working", !verdict.fires)
        assertTrue("an unknown ringer must not read as broken", !verdict.blocked)
    }

    @Test
    fun `only the two read failures count as blocked`() {
        // `blocked` picks the headline and the colour. Anything that made it true
        // without a fact behind it would turn a working phone red.
        val expected = mapOf(
            Setup.Verdict.WILL_FIRE to false,
            Setup.Verdict.WILL_FIRE_IN_RING to false,
            Setup.Verdict.WONT_FIRE_SILENT to true,
            Setup.Verdict.WONT_FIRE_RING_OFF to true,
            Setup.Verdict.UNKNOWN to false,
        )
        assertEquals("a verdict was added without deciding this", Setup.Verdict.values().size, expected.size)
        for ((verdict, blocked) in expected) {
            assertTrue("$verdict blocked should be $blocked", blocked == verdict.blocked)
        }
    }

    @Test
    fun `only silent sends the user out to system settings`() {
        // Ring mode has a switch on the screen the verdict sits on, so it needs a
        // sentence rather than a button. Silent mode has nothing, and never will:
        // PROFILE.md §9 — the app explains and deep-links, and does not edit a
        // setting on anyone's behalf.
        for (verdict in Setup.Verdict.values()) {
            val silent = verdict == Setup.Verdict.WONT_FIRE_SILENT
            assertTrue("$verdict needsSoundSettings should be $silent", silent == verdict.needsSoundSettings)
        }
    }

    @Test
    fun `every combination of ringer and switch lands on exactly one verdict`() {
        // The table is total. A ringer mode with no verdict would be a screen with
        // nothing to say at the moment it matters most.
        val reached = mutableSetOf<Setup.Verdict>()
        for (ringer in Setup.Ringer.values()) {
            for (switch in listOf(true, false)) reached += Setup.verdict(ringer, switch)
        }
        assertEquals(Setup.Verdict.values().toSet(), reached)
    }

    @Test
    fun `ringer words map exactly, and anything else is unknown`() {
        assertEquals(Setup.Ringer.VIBRATE, Setup.Ringer.of("vibrate"))
        assertEquals(Setup.Ringer.RING, Setup.Ringer.of("ring"))
        assertEquals(Setup.Ringer.SILENT, Setup.Ringer.of("silent"))
        // Case matters deliberately. The only producer is Haptics.ringerMode, and
        // accepting anything looser would quietly hide a change to that contract —
        // the same shape of mistake as trusting our own FIRED event.
        assertEquals(Setup.Ringer.UNKNOWN, Setup.Ringer.of("VIBRATE"))
        assertEquals(Setup.Ringer.UNKNOWN, Setup.Ringer.of(""))
        assertEquals(Setup.Ringer.UNKNOWN, Setup.Ringer.of("normal"))
    }

    // --- Task 11: reading the sample encodings a phone can hand back ---------
    //
    // These live in Pcm so they can be proved here. Every one of them is a
    // conversion where a mistake does not throw — it produces a number, and that
    // number becomes a vibration score that looks exactly like a working one.
    // Before Task 11 only 16-bit and float were handled; everything else fell
    // through to a short read and became noise.

    @Test
    fun `the mirrored encodings still match the platform's`() {
        // Pcm keeps no Android imports, so it mirrors these by hand. A silent drift
        // would misread every WAV, and it would present as "the rhythm feels wrong"
        // rather than as a failure. AudioFormat's constants are compile-time ints,
        // so they are readable here with no device involved.
        assertEquals(AudioFormat.ENCODING_PCM_8BIT, Pcm.ENCODING_PCM_8BIT)
        assertEquals(AudioFormat.ENCODING_PCM_16BIT, Pcm.ENCODING_PCM_16BIT)
        assertEquals(AudioFormat.ENCODING_PCM_FLOAT, Pcm.ENCODING_PCM_FLOAT)
        assertEquals(AudioFormat.ENCODING_PCM_24BIT_PACKED, Pcm.ENCODING_PCM_24BIT_PACKED)
        assertEquals(AudioFormat.ENCODING_PCM_32BIT, Pcm.ENCODING_PCM_32BIT)
    }

    @Test
    fun `every readable encoding has a branch, and the rest are refused`() {
        val readable = listOf(
            Pcm.ENCODING_PCM_8BIT,
            Pcm.ENCODING_PCM_16BIT,
            Pcm.ENCODING_PCM_24BIT_PACKED,
            Pcm.ENCODING_PCM_32BIT,
            Pcm.ENCODING_PCM_FLOAT,
        )
        for (encoding in readable) {
            assertTrue("$encoding should be readable", Pcm.isReadable(encoding))
            assertTrue(
                "$encoding should report samples, not the sentinel",
                Pcm.samplesIn(480, encoding) > 0,
            )
        }
        // 0 is ENCODING_INVALID and the platform has plenty of other members. An
        // unrecognised encoding must be refused, never guessed at.
        for (encoding in listOf(0, 1, 5, 9, 23, -1)) {
            assertTrue("$encoding should be refused", !Pcm.isReadable(encoding))
            assertEquals(Pcm.UNREADABLE_ENCODING, Pcm.samplesIn(480, encoding))
        }
    }

    @Test
    fun `eight bit audio is read as unsigned, because that is what it is`() {
        // WAV's 8-bit is unsigned, with 128 at silence. Reading it as signed would
        // put silence at 0 and invert the waveform around it.
        val bytes = byteArrayOf(128.toByte(), 255.toByte(), 0.toByte())
        val out = ShortArray(3)
        val count = Pcm.readSamples(
            ByteBuffer.wrap(bytes), 0, bytes.size, Pcm.ENCODING_PCM_8BIT, out,
        )
        assertEquals(3, count)
        assertEquals(0, out[0].toInt())
        assertEquals(32512, out[1].toInt())
        assertEquals(-32768, out[2].toInt())
    }

    @Test
    fun `twenty four bit audio keeps its sign and its scale`() {
        // Little-endian, three bytes each. The top byte is read signed, and that is
        // the entire conversion: a 24-bit value becomes 16-bit by dropping the low
        // byte. Getting this wrong is invisible in a log and obvious in a score.
        val bytes = byteArrayOf(
            0xFF.toByte(), 0xFF.toByte(), 0x7F.toByte(), // +8388607, full scale
            0x00, 0x00, 0x80.toByte(),                   // -8388608, full negative
            0x00, 0x00, 0x00,                            // zero
            0x00, 0x00, 0x40,                            // +4194304, half scale
        )
        val out = ShortArray(4)
        val count = Pcm.readSamples(
            ByteBuffer.wrap(bytes), 0, bytes.size, Pcm.ENCODING_PCM_24BIT_PACKED, out,
        )
        assertEquals(4, count)
        assertEquals(32767, out[0].toInt())
        assertEquals(-32768, out[1].toInt())
        assertEquals(0, out[2].toInt())
        assertEquals(16384, out[3].toInt())
    }

    @Test
    fun `thirty two bit audio reads the sign from the fourth byte`() {
        // Four bytes little-endian, so the byte carrying the sign is the *fourth*.
        // Reading the second instead — which is what the first draft of this code
        // did, and what writing this test caught — reads the wrong end of every
        // sample and produces a plausible waveform that is simply wrong.
        val bytes = byteArrayOf(
            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x7F.toByte(), // +2147483647
            0x00, 0x00, 0x00, 0x80.toByte(),                            // -2147483648
        )
        val out = ShortArray(2)
        val count = Pcm.readSamples(
            ByteBuffer.wrap(bytes), 0, bytes.size, Pcm.ENCODING_PCM_32BIT, out,
        )
        assertEquals(2, count)
        assertEquals(32767, out[0].toInt())
        assertEquals(-32768, out[1].toInt())
    }

    @Test
    fun `sixteen bit audio is copied through untouched`() {
        val bytes = ByteBuffer.allocate(6).order(ByteOrder.nativeOrder())
            .putShort(0).putShort(32767).putShort(-32768).array()
        val out = ShortArray(3)
        val count = Pcm.readSamples(
            ByteBuffer.wrap(bytes), 0, bytes.size, Pcm.ENCODING_PCM_16BIT, out,
        )
        assertEquals(3, count)
        assertArrayEquals(shortArrayOf(0, 32767, -32768), out)
    }

    @Test
    fun `float audio is scaled across the full 16-bit range`() {
        val bytes = ByteBuffer.allocate(12).order(ByteOrder.nativeOrder())
            .putFloat(0f).putFloat(1f).putFloat(-1f).array()
        val out = ShortArray(3)
        val count = Pcm.readSamples(
            ByteBuffer.wrap(bytes), 0, bytes.size, Pcm.ENCODING_PCM_FLOAT, out,
        )
        assertEquals(3, count)
        assertEquals(0, out[0].toInt())
        assertEquals(32767, out[1].toInt())
        assertEquals(-32767, out[2].toInt())
    }

    @Test
    fun `an unreadable encoding writes nothing rather than something plausible`() {
        // The half that matters. Returning a number here would be the whole bug:
        // the caller has no way to tell a decoded sample from an invented one.
        val out = ShortArray(4) { 999 }
        val count = Pcm.readSamples(ByteBuffer.wrap(ByteArray(16)), 0, 16, 5, out)
        assertEquals(Pcm.UNREADABLE_ENCODING, count)
        assertArrayEquals(shortArrayOf(999, 999, 999, 999), out)
    }

    @Test
    fun `no encoding can write past the end of the output array`() {
        // The decoder chooses the chunk size, not us, and an overrun here would be
        // an IndexOutOfBounds inside a notification callback.
        for (encoding in listOf(
            Pcm.ENCODING_PCM_8BIT,
            Pcm.ENCODING_PCM_16BIT,
            Pcm.ENCODING_PCM_24BIT_PACKED,
            Pcm.ENCODING_PCM_32BIT,
            Pcm.ENCODING_PCM_FLOAT,
        )) {
            val out = ShortArray(2)
            val count = Pcm.readSamples(ByteBuffer.wrap(ByteArray(64)), 0, 64, encoding, out)
            assertTrue("$encoding reported $count for a 2-element array", count <= 2)
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
