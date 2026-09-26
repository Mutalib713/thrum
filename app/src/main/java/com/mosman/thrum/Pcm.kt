package com.mosman.thrum

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/**
 * The parts of audio handling that do not need a phone.
 *
 * Decoding itself needs `MediaCodec`, which only exists on Android — but the
 * arithmetic around it does not, and arithmetic is where the bugs hide. A
 * downmix that overflows, or a duration that drifts, would show up as a
 * vibration that feels wrong on a real track and be very hard to trace back
 * from there. So it lives here, in pure Kotlin, and gets real tests that run on
 * the PC in seconds. Same reason as [Score]. PROFILE.md §12.
 */
object Pcm {

    /**
     * How long [frames] of audio last.
     *
     * A frame is one sample per channel — the unit that actually corresponds to
     * a moment in time. Counting raw samples instead would report stereo files
     * as twice their real length, which is exactly the kind of mistake that
     * looks plausible in a log.
     *
     * Rounds to nearest rather than truncating: a three-minute track at 44.1 kHz
     * truncates about half a millisecond low, and Task 3's proof is that our
     * duration matches what the music player shows.
     */
    fun framesToMs(frames: Long, sampleRate: Int): Long {
        require(sampleRate > 0) { "sampleRate must be positive, was $sampleRate" }
        if (frames <= 0) return 0
        return (frames * 1000L + sampleRate / 2) / sampleRate
    }

    /** How many mono frames [sampleCount] interleaved samples will produce. */
    fun monoFrames(sampleCount: Int, channels: Int): Int {
        require(channels > 0) { "channels must be positive, was $channels" }
        if (sampleCount <= 0) return 0
        return sampleCount / channels
    }

    /**
     * Collapse interleaved multi-channel audio to one channel by taking the
     * channel with the greater magnitude, sign preserved.
     *
     * A vibration motor has no stereo. The obvious downmix is to average the
     * channels, and it is wrong twice over:
     *
     * 1. **It cancels out-of-phase content.** Where left and right carry the
     *    same sound in opposite polarity — a wide synth bass, a stereo-widened
     *    kick, anything the mastering engineer spread — L = +10000 and
     *    R = −10000 average to exactly **zero**. The bass does not get quieter,
     *    it disappears, and the score for that part of the track is silence. No
     *    amount of Punch recovers a hit that was never detected. This is one of
     *    the causes of "it doesn't vibrate to the max, I can't feel it
     *    sometimes."
     * 2. **It halves hard-panned content.** A sound on one side only, averaged
     *    with the empty channel, arrives at half strength — often the hi-hats,
     *    sometimes the whole hook.
     *
     * Taking the greater magnitude fixes both without rectifying the waveform:
     * the sign is kept, so the low-pass and envelope follower downstream still
     * receive a real waveform rather than a full-wave-rectified one. In-phase
     * stereo (where both channels are equal, which is most centred content)
     * comes through exactly as the average would have.
     *
     * Averages through `Int`. Two channels at full negative scale sum to -65536,
     * which overflows a `Short` and wraps to a positive number — a silent bug
     * that would read as a bright transient exactly where the audio is loudest.
     *
     * A trailing partial frame is ignored. Decoders hand back whatever fills the
     * buffer, so the last chunk of a stereo file can end mid-frame, and half a
     * frame has no meaning.
     *
     * @return the number of mono frames written to [out].
     */
    fun downmixToMono(interleaved: ShortArray, sampleCount: Int, channels: Int, out: ShortArray): Int {
        require(channels > 0) { "channels must be positive, was $channels" }
        val frames = monoFrames(sampleCount.coerceAtMost(interleaved.size), channels)
        require(out.size >= frames) { "out holds ${out.size}, needs $frames" }
        if (frames == 0) return 0

        if (channels == 1) {
            interleaved.copyInto(out, 0, 0, frames)
            return frames
        }
        var read = 0
        for (frame in 0 until frames) {
            // Strictly greater keeps the first channel on a tie, so the result
            // is deterministic rather than dependent on iteration order.
            var loudest = 0
            repeat(channels) {
                val v = interleaved[read++].toInt()
                if (abs(v) > abs(loudest)) loudest = v
            }
            out[frame] = loudest.toShort()
        }
        return frames
    }

    /**
     * Loudest sample in the range, as a positive number.
     *
     * Reported by Task 3 so a file that decodes to silence is visible
     * immediately, rather than becoming a score of all zeroes in Task 4 that
     * looks like an analyser bug.
     *
     * `-32768` is negated to `32767`, not to itself: `-Short.MIN_VALUE` does not
     * fit in a `Short`, and letting it through would make the peak of a
     * full-scale track read as a large negative number.
     */
    fun peak(samples: ShortArray, sampleCount: Int): Int {
        var peak = 0
        for (i in 0 until sampleCount.coerceAtMost(samples.size)) {
            val v = samples[i].toInt()
            val magnitude = if (v < 0) -v else v
            if (magnitude > peak) peak = magnitude
        }
        return peak.coerceAtMost(MAX_SAMPLE)
    }

    // --- Reading a decoder's buffer -----------------------------------------
    //
    // Task 11. These two functions used to live in AudioDecoder and knew about
    // exactly two encodings: 16-bit and float. Everything else fell through to
    // `size / 2` and a short read, which does not fail — it produces *numbers*.
    // A 24-bit WAV decoded as 1.5× too many samples and a 8-bit WAV as half as
    // many, both as noise that still "decoded", and both became a vibration score
    // with nothing anywhere saying it was wrong. That is the same failure shape as
    // reading float bytes as shorts, which the old comment here already warned
    // about; it just did not close the door behind the warning.
    //
    // Moved into pure Kotlin for the usual reason: this is arithmetic, and
    // arithmetic is the part that can be proved on the PC in seconds.

    /**
     * PCM encodings, mirrored from `android.media.AudioFormat` so this file keeps
     * no Android imports.
     *
     * Mirrored rather than referenced is a real risk — a platform value could
     * change and these would silently disagree. `QaSuiteTest` asserts every one of
     * them against the real `AudioFormat` constant, which is enough because those
     * are compile-time inlined `int`s and so are readable in a JVM test.
     */
    const val ENCODING_PCM_16BIT = 2
    const val ENCODING_PCM_8BIT = 3
    const val ENCODING_PCM_FLOAT = 4
    const val ENCODING_PCM_24BIT_PACKED = 21
    const val ENCODING_PCM_32BIT = 22

    /** Returned by [samplesIn] for an encoding this app cannot read. */
    const val UNREADABLE_ENCODING = -1

    /**
     * Bytes per sample, or [UNREADABLE_ENCODING] for anything this app does not
     * read.
     *
     * The single source of truth for which encodings are supported. [samplesIn]
     * and [isReadable] are both derived from it, so they cannot disagree — and
     * `readSamples` has a branch for every value it returns.
     */
    private fun bytesPerSample(encoding: Int): Int = when (encoding) {
        ENCODING_PCM_8BIT -> 1
        ENCODING_PCM_16BIT -> 2
        ENCODING_PCM_24BIT_PACKED -> 3
        ENCODING_PCM_32BIT, ENCODING_PCM_FLOAT -> 4
        else -> UNREADABLE_ENCODING
    }

    /** True when [encoding] is one of the forms [readSamples] reads correctly. */
    fun isReadable(encoding: Int): Boolean = bytesPerSample(encoding) != UNREADABLE_ENCODING

    /**
     * How many samples [byteCount] bytes hold in [encoding], or
     * [UNREADABLE_ENCODING] when the encoding is not one this app reads.
     *
     * Returning a sentinel rather than guessing is the whole point. A wrong count
     * here is not an error that shows up as an error — it is a plausible score
     * built from nonsense.
     */
    fun samplesIn(byteCount: Int, encoding: Int): Int {
        val width = bytesPerSample(encoding)
        return if (width == UNREADABLE_ENCODING) UNREADABLE_ENCODING else byteCount / width
    }

    /**
     * Copy one decoder buffer into [out] as 16-bit samples, honouring [encoding].
     *
     * @return the number of samples written, or [UNREADABLE_ENCODING] for an
     *   encoding this app does not read. Never a wrong number: a caller that sees
     *   the sentinel refuses the file and says so.
     *
     * 24- and 32-bit are read **little-endian**, one byte at a time. Every Android
     * ABI is little-endian, so this is not a portability compromise; it is the
     * platform's own layout, and reading bytes individually avoids depending on a
     * buffer's `ByteOrder` having been set correctly by a caller.
     */
    fun readSamples(
        buffer: ByteBuffer,
        offset: Int,
        size: Int,
        encoding: Int,
        out: ShortArray,
    ): Int {
        buffer.position(offset)
        buffer.limit(offset + size)

        return when (encoding) {
            ENCODING_PCM_FLOAT -> {
                buffer.order(ByteOrder.nativeOrder())
                val floats = buffer.asFloatBuffer()
                val count = minOf(floats.remaining(), out.size)
                for (i in 0 until count) {
                    val v = floats.get(i).coerceIn(-1f, 1f)
                    out[i] = (v * MAX_SAMPLE).toInt().toShort()
                }
                count
            }

            ENCODING_PCM_16BIT -> {
                buffer.order(ByteOrder.nativeOrder())
                val shorts = buffer.asShortBuffer()
                val count = minOf(shorts.remaining(), out.size)
                shorts.get(out, 0, count)
                count
            }

            ENCODING_PCM_8BIT -> {
                // WAV's 8-bit is **unsigned**: 128 is silence, not 0. Reading it
                // as signed would invert the whole waveform about a zero that is
                // not where signed arithmetic thinks it is.
                val count = minOf(size, out.size)
                for (i in 0 until count) {
                    val b = buffer.get(offset + i).toInt() and 0xFF
                    out[i] = ((b - 128) shl 8).toShort()
                }
                count
            }

            ENCODING_PCM_24BIT_PACKED -> {
                val count = minOf(size / 3, out.size)
                for (i in 0 until count) {
                    val at = offset + i * 3
                    // The top byte is read **signed**, so it carries the sign of
                    // the 24-bit value into the 16-bit one. Keeping the top two
                    // bytes is the whole conversion: it is a truncation, and
                    // truncating a signed value toward zero is what dropping the
                    // low byte does.
                    val hi = buffer.get(at + 2).toInt()
                    val mid = buffer.get(at + 1).toInt() and 0xFF
                    out[i] = ((hi shl 8) or mid).toShort()
                }
                count
            }

            ENCODING_PCM_32BIT -> {
                val count = minOf(size / 4, out.size)
                for (i in 0 until count) {
                    val at = offset + i * 4
                    // Four bytes little-endian, so the most significant byte is the
                    // *fourth* one. Read signed, for the same reason as 24-bit.
                    val hi = buffer.get(at + 3).toInt()
                    val mid = buffer.get(at + 2).toInt() and 0xFF
                    out[i] = ((hi shl 8) or mid).toShort()
                }
                count
            }

            else -> UNREADABLE_ENCODING
        }
    }

    /** Full scale for 16-bit audio, which is what every decoder here is asked for. */
    const val MAX_SAMPLE = 32767
}
