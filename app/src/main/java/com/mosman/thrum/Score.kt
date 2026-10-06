package com.mosman.thrum

import kotlin.math.roundToInt

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
     * The first [seconds] of the score, at full resolution.
     *
     * Preferred over [fitWithin] for a ringtone. A four-minute track at 20 ms is
     * 11,922 steps — over the vibrator's limit — and coarsening it to fit costs
     * every step half its precision, so the whole rhythm updates 25 times a
     * second instead of 50. Mutalib felt that as chunky rather than smooth.
     *
     * A phone rings for about thirty seconds. Trimming keeps the part anyone
     * will actually feel and keeps it sharp, instead of blurring four minutes
     * nobody hears.
     */
    fun firstSeconds(seconds: Int): Score {
        require(seconds > 0) { "seconds must be positive, was $seconds" }
        val keep = seconds * 1000 / stepMs
        if (amplitudes.size <= keep) return this
        return Score(stepMs, amplitudes.take(keep), sourceName)
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
     * The score split into consecutive pieces, each within [maxSteps] steps.
     *
     * R10: a whole song's score does not fit in one waveform — this phone
     * silently drops anything over ~10,500 steps, and `Haptics.MAX_STEPS` is
     * 8,000. The player plays the pieces back to back, re-syncing each join
     * against the audio's position, which is Task 16's thing to prove. The
     * splitting itself is pure arithmetic and lives here.
     *
     * Two guarantees, in the order a hand would notice them broken:
     *
     * 1. **Every step survives exactly once, in order.** The pieces joined end
     *    to end are the original score — nothing dropped, nothing doubled.
     * 2. **A cut lands on silence whenever the window holds any.** A hit split
     *    across two pieces means one vibration ends and another begins in the
     *    middle of the motor's motion — the join would sit inside the drive
     *    and read as a stutter. Cutting after a silent step means each piece
     *    starts on a hit's onset, where a fresh start is what the motor is
     *    doing anyway. A score with no silence in a window (a solid drive)
     *    cuts hard rather than failing; Task 16 measures what that join feels
     *    like, and it cannot be designed around until measured.
     */
    fun pieces(maxSteps: Int): List<Score> {
        require(maxSteps > 0) { "maxSteps must be positive, was $maxSteps" }
        if (amplitudes.size <= maxSteps) return listOf(this)
        val out = mutableListOf<Score>()
        var start = 0
        while (start < amplitudes.size) {
            var end = minOf(start + maxSteps, amplitudes.size)
            if (end < amplitudes.size) {
                var cut = end
                while (cut > start + 1 && amplitudes[cut - 1] != 0) cut--
                if (amplitudes[cut - 1] == 0) end = cut
            }
            out.add(Score(stepMs, amplitudes.subList(start, end), sourceName))
            start = end
        }
        return out
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
     * The score starting at [startMs] and **wrapping around to the beginning**,
     * the same total length as the original.
     *
     * This is the ring-mode re-assert. The ringtone repeats, and every repeat
     * makes Android re-issue its own vibration, which takes the motor back — so
     * the rhythm has to be re-asserted every couple of seconds, from wherever it
     * *would* be by now, or the user feels the system's flat buzz instead.
     *
     * [from] is the wrong tool for that job, and using it was a real defect:
     * it *truncates*, so replaying it with `loop = true` loops the tail forever
     * and the opening of the rhythm is never heard again, and replaying it
     * without looping plays the remainder once and then goes quiet for the rest
     * of the ring. Either way the rhythm stops being the song. Rotating instead
     * keeps the whole score — every re-assert starts at the right moment in the
     * music and still runs for a full duration, so there is no gap to fall into.
     *
     * `from` stays exactly as it is for the preview, where the vibration is
     * chasing a real audio file that genuinely does end.
     */
    fun rotated(startMs: Long): Score {
        if (startMs <= 0 || amplitudes.isEmpty()) return this
        val size = amplitudes.size
        val start = ((startMs / stepMs).toInt() % size + size) % size
        if (start == 0) return this
        return Score(stepMs, amplitudes.drop(start) + amplitudes.take(start), sourceName)
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
     *
     * [floor] stops the decay from dropping below what the motor can physically
     * produce. The original [decayTo] could fade a 185-peak hit down to 102 over
     * its held steps — below the ~140 threshold where the motor barely moves,
     * so most of the hold spent its time at amplitudes the user never received.
     * The decay now stops at [floor], so every held step is still felt.
     */
    fun holdPulsesAtLeast(minSteps: Int, decayTo: Float = 1f, floor: Int = 0): Score {
        require(minSteps > 0) { "minSteps must be positive, was $minSteps" }
        require(decayTo in 0f..1f) { "decayTo must be 0..1, was $decayTo" }
        require(floor in 0..MAX_AMPLITUDE) { "floor must be 0..$MAX_AMPLITUDE, was $floor" }
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
            val toAdd = minSteps - (end - i)
            var added = 0
            var at = end
            while (added < toAdd && at < out.size && out[at] == 0) {
                // Fade across the held steps rather than repeating the peak.
                // A flat plateau is a square pulse, and a square pulse is what
                // made the rhythm read as mechanical — a real drum decays.
                val through = (added + 1).toFloat() / (toAdd + 1)
                val scale = 1f - (1f - decayTo) * through
                val scaled = (peak * scale).toInt()
                // Never drop below the floor: under ~140 the motor barely
                // moves, so a decay that fades below it spends most of
                // the hold at strengths the user never receives. A pulse
                // weaker than the floor is left alone — the floor is a
                // limit on the decay, not a lift.
                val landed = if (peak >= floor) maxOf(scaled, floor) else scaled
                out[at] = landed.coerceIn(0, MAX_AMPLITUDE)
                at++
                added++
            }
            i = at
        }
        return Score(stepMs, out, sourceName)
    }

    /**
     * Every beat turned down to [scale] of its strength, never below [floor].
     *
     * The floor is a limit on the turning down, not a lift: a step already
     * weaker than [floor] stays exactly as it was, and a still step stays
     * still. Turning a quiet tap *up* to the floor would make "softer" louder
     * in places, and turning a beat below the floor would hand the motor a
     * strength it cannot produce.
     */
    fun softened(scale: Float, floor: Int): Score {
        require(scale in 0f..1f) { "scale must be 0..1, was $scale" }
        require(floor in 0..MAX_AMPLITUDE) { "floor must be 0..$MAX_AMPLITUDE, was $floor" }
        val out = amplitudes.map { a ->
            if (a == 0) 0 else maxOf(minOf(a, floor), (a * scale).roundToInt())
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
