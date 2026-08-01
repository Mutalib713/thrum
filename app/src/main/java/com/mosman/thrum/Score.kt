package com.mosman.thrum

/**
 * A vibration score: one strength value per fixed-length step of time.
 *
 * Deliberately pure Kotlin — no Android imports, no JSON library, no framework
 * types. This machine has no emulator, so the only code that can be tested
 * before it reaches a phone is code that doesn't need one. Everything worth
 * getting right lives here; the vibrator wrapper stays as thin as possible.
 *
 * [amplitudes] is a strength per step, 0 (still) to 255 (hardest the motor
 * goes). A uniform [stepMs] means the timings array handed to the vibrator is
 * derived rather than stored, so the two can never drift out of sync — a
 * mismatch there throws at the vibrator and takes the app down with it.
 */
data class Score(
    val stepMs: Int,
    val amplitudes: List<Int>,
    val sourceName: String = "",
) {
    init {
        require(stepMs > 0) { "stepMs must be positive, was $stepMs" }
        require(amplitudes.all { it in 0..MAX_AMPLITUDE }) {
            "amplitudes must all be 0..$MAX_AMPLITUDE"
        }
    }

    val durationMs: Long get() = stepMs.toLong() * amplitudes.size

    /** Step durations for the vibrator, one per amplitude. Equal length by construction. */
    fun timings(): LongArray = LongArray(amplitudes.size) { stepMs.toLong() }

    fun isSilent(): Boolean = amplitudes.all { it == 0 }

    /**
     * The same score at a coarser resolution: [factor] steps merged into one,
     * keeping the loudest of each group.
     *
     * This is the R8 lever (PROFILE.md §11). `VibrationEffect.createWaveform`
     * may cap how many steps one effect can hold, and a four-minute track at
     * 20 ms is about 12,000 of them. If a device refuses that, halving the count
     * costs timing precision — 40 ms instead of 20 — which is far cheaper than
     * a score that is silently truncated partway through.
     *
     * Taking the loudest rather than the average is deliberate: averaging a hit
     * with the silence beside it flattens exactly the transients that make a
     * rhythm feel like one.
     */
    fun coarsen(factor: Int): Score {
        require(factor > 0) { "factor must be positive, was $factor" }
        if (factor == 1) return this
        val merged = amplitudes.chunked(factor) { group -> group.max() }
        return Score(stepMs * factor, merged, sourceName)
    }

    /**
     * The same score, coarsened just enough to fit within [maxSteps].
     *
     * Widening the step is the honest trade: a score that covers the whole track
     * at 40 ms beats one that covers the first two thirds at 20 ms and stops
     * without saying so. Returns `this` when it already fits, so a normal-length
     * score keeps full resolution.
     */
    fun fitWithin(maxSteps: Int): Score {
        require(maxSteps > 0) { "maxSteps must be positive, was $maxSteps" }
        if (amplitudes.size <= maxSteps) return this
        // Round up, or the result lands one group over the limit.
        val factor = (amplitudes.size + maxSteps - 1) / maxSteps
        return coarsen(factor)
    }

    /**
     * The score from [fromMs] onward, for starting partway through a track.
     *
     * Task 5 needs this because audio does not begin the instant it is asked to:
     * a player buffers, the audio path wakes up, and by the time sound actually
     * leaves the speaker some milliseconds have passed. Starting the vibration
     * at step zero anyway would run it ahead of the music for the whole track.
     */
    fun from(fromMs: Long): Score {
        if (fromMs <= 0) return this
        val skip = (fromMs / stepMs).toInt()
        if (skip >= amplitudes.size) return Score(stepMs, emptyList(), sourceName)
        return Score(stepMs, amplitudes.drop(skip), sourceName)
    }

    /**
     * Every pulse held for at least [minSteps], keeping its strongest value.
     *
     * A vibration motor has mass. Asking for full strength for 40 ms produces
     * almost no movement, because the motor is still spinning up when the step
     * ends — Mutalib's test: on a table, the phone did not move at all, while
     * Android's own buzz (255 held for a full second) shakes the table.
     *
     * Rhythm cannot use second-long pulses, but it can stop asking for
     * forty-millisecond ones. Widening runs forward rather than around the peak
     * keeps the hit's leading edge exactly where the beat is; moving that would
     * put the rhythm ahead of the music.
     */
    fun holdPulsesAtLeast(minSteps: Int): Score {
        require(minSteps > 0) { "minSteps must be positive, was $minSteps" }
        if (minSteps == 1 || amplitudes.isEmpty()) return this

        val out = amplitudes.toMutableList()
        var i = 0
        while (i < out.size) {
            if (out[i] == 0) {
                i++
                continue
            }
            var end = i
            var peak = 0
            while (end < out.size && out[end] > 0) {
                peak = maxOf(peak, out[end])
                end++
            }
            // Hold the peak forward into the silence that follows, never over a
            // later hit: a run that already reaches the next one is long enough.
            var held = end - i
            var at = end
            while (held < minSteps && at < out.size && out[at] == 0) {
                out[at] = peak
                at++
                held++
            }
            i = at
        }
        return Score(stepMs, out, sourceName)
    }

    /** Number of separate pulses — a run of non-zero steps counts once. */
    fun pulseCount(): Int {
        var count = 0
        var inPulse = false
        for (a in amplitudes) {
            if (a > 0 && !inPulse) count++
            inPulse = a > 0
        }
        return count
    }

    /**
     * A compact single-line form for SharedPreferences.
     *
     * Not JSON: a score is a few thousand small integers, and the JSON library
     * available inside Android unit tests is a stub that throws on every call.
     * Hand-rolling the format keeps the whole thing testable on the PC.
     *
     * Layout: `1|stepMs|escapedName|amp,amp,amp`
     */
    fun encode(): String = buildString {
        append(FORMAT_VERSION).append(SEP)
        append(stepMs).append(SEP)
        append(escape(sourceName)).append(SEP)
        amplitudes.joinTo(this, ",")
    }

    companion object {
        const val MAX_AMPLITUDE = 255
        private const val FORMAT_VERSION = 1
        private const val SEP = '|'

        /** Returns null for anything unparseable, so a corrupt store degrades to "no score". */
        fun decode(text: String): Score? {
            val parts = text.split(SEP)
            if (parts.size != 4) return null
            if (parts[0].toIntOrNull() != FORMAT_VERSION) return null
            val step = parts[1].toIntOrNull() ?: return null
            if (step <= 0) return null
            val amps = if (parts[3].isEmpty()) {
                emptyList()
            } else {
                parts[3].split(",").map { it.toIntOrNull() ?: return null }
            }
            if (amps.any { it !in 0..MAX_AMPLITUDE }) return null
            return Score(step, amps, unescape(parts[2]))
        }

        private fun escape(s: String) =
            s.replace("\\", "\\\\").replace("|", "\\p").replace("\n", "\\n")

        private fun unescape(s: String): String {
            val out = StringBuilder(s.length)
            var i = 0
            while (i < s.length) {
                val c = s[i]
                if (c == '\\' && i + 1 < s.length) {
                    when (s[i + 1]) {
                        '\\' -> out.append('\\')
                        'p' -> out.append('|')
                        'n' -> out.append('\n')
                        else -> out.append(s[i + 1])
                    }
                    i += 2
                } else {
                    out.append(c)
                    i++
                }
            }
            return out.toString()
        }
    }
}
