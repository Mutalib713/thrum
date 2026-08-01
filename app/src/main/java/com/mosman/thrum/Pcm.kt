package com.mosman.thrum

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
     * Collapse interleaved multi-channel audio to one channel by averaging.
     *
     * A vibration motor has no stereo. Averaging rather than taking the left
     * channel matters for real music: anything panned hard right — often the
     * hi-hats, sometimes the whole hook — would simply vanish from the rhythm.
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
            var sum = 0
            repeat(channels) { sum += interleaved[read++] }
            out[frame] = (sum / channels).toShort()
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

    /** Full scale for 16-bit audio, which is what every decoder here is asked for. */
    const val MAX_SAMPLE = 32767
}
