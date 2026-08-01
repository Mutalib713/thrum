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
    fun `stereo collapses to the average of both channels`() {
        // Interleaved: L R L R. A hard-panned sound must survive the downmix,
        // at half strength, rather than vanishing with its channel.
        val stereo = shortArrayOf(100, 300, -200, 0, 0, 1000)
        val mono = ShortArray(3)
        assertEquals(3, Pcm.downmixToMono(stereo, stereo.size, 2, mono))
        assertArrayEquals(shortArrayOf(200, -100, 500), mono)
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
        assertEquals(15, mono[0].toInt())
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
