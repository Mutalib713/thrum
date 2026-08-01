package com.mosman.thrum

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Turns decoded audio into a [Score]. Task 4, and the heart of the app.
 *
 * The chain is the one PROFILE.md §7 describes: low-pass, then an envelope
 * follower, then downsample to one amplitude per step.
 *
 * - **Low-pass** because a hand feels bass and drums, not cymbals. Handing the
 *   full spectrum to a motor produces a constant mush at roughly the average
 *   loudness of the track, which is the opposite of rhythm.
 * - **Envelope follower** with a fast attack and slow release, so a drum reads
 *   as a hit that decays rather than as a burst of noise. This is the same shape
 *   `Demo.hit()` draws by hand, and Task 1 proved that shape feels right.
 * - **Downsample** because the motor is handed one amplitude per 20 ms, not one
 *   per sample.
 *
 * **Streaming and stateful, on purpose.** [AudioDecoder] hands over one chunk at
 * a time precisely so a whole track is never in memory, and an analyser that
 * wanted the entire file as an array would undo that. What this holds is one
 * amplitude per step — about 12,000 numbers for a four-minute track, against the
 * 24 MB of audio they were derived from.
 *
 * Pure Kotlin, no Android imports, so the whole thing runs in the PC test suite.
 * PROFILE.md §12.
 *
 * **The tuning here is taste, not correctness** (PLAN.md Task 4). The constants
 * are chosen to be defensible, not final; expect to move them once a hand has
 * felt a real track in Task 5.
 */
class ScoreBuilder(
    private val sampleRate: Int,
    private val stepMs: Int = Demo.STEP_MS,
    private val name: String = "",
) {
    init {
        require(sampleRate > 0) { "sampleRate must be positive, was $sampleRate" }
        require(stepMs > 0) { "stepMs must be positive, was $stepMs" }
    }

    private val samplesPerStep = (sampleRate.toLong() * stepMs / 1000).toInt().coerceAtLeast(1)

    // Two cascaded one-pole sections. A single pole rolls off at 6 dB per octave,
    // which still lets a lot of vocal through at 200 Hz; two make it 12 dB and
    // the difference between "the beat" and "the song" is audible in the output.
    private val lowPassCoef = coefFor(1.0 / (2 * Math.PI * CUTOFF_HZ))
    private val attackCoef = coefFor(ATTACK_SECONDS)
    private val releaseCoef = coefFor(RELEASE_SECONDS)

    private var lp1 = 0f
    private var lp2 = 0f
    private var envelope = 0f

    private var stepPeak = 0f
    private var samplesInStep = 0

    /** One entry per completed step. Kept as floats until [build] knows the loudest. */
    private val steps = ArrayList<Float>()

    /**
     * Feed one chunk of mono samples. Safe to call with the reused array
     * [AudioDecoder] hands out — nothing here keeps a reference to it.
     */
    fun feed(samples: ShortArray, count: Int) {
        val end = count.coerceAtMost(samples.size)
        for (i in 0 until end) {
            val x = samples[i].toFloat()

            lp1 += lowPassCoef * (x - lp1)
            lp2 += lowPassCoef * (lp1 - lp2)

            // Rectify, then follow. Attacking faster than it releases is what
            // makes a hit feel like a hit: the motor reaches full strength almost
            // at once and eases off, instead of fading up into a beat that has
            // already passed.
            val level = abs(lp2)
            envelope += if (level > envelope) {
                attackCoef * (level - envelope)
            } else {
                releaseCoef * (level - envelope)
            }

            if (envelope > stepPeak) stepPeak = envelope
            if (++samplesInStep >= samplesPerStep) {
                steps.add(stepPeak)
                stepPeak = 0f
                samplesInStep = 0
            }
        }
    }

    /**
     * Finish and produce the score.
     *
     * Normalising by the loudest step is what makes a quiet recording and a
     * loud one both use the motor's full range — the alternative is a score
     * whose strength depends on how the track was mastered rather than on how
     * it sounds.
     */
    fun build(): Score {
        // The last partial step still covers real audio. Dropping it would make
        // every score up to one step shorter than its track.
        if (samplesInStep > 0) {
            steps.add(stepPeak)
            stepPeak = 0f
            samplesInStep = 0
        }

        val loudest = steps.maxOrNull() ?: 0f
        if (loudest <= 0f) {
            // Silence in, silence out. Not an error: a silent file is a real
            // thing a user can pick, and it must not produce a buzz.
            return Score(stepMs, List(steps.size) { 0 }, name)
        }

        val amplitudes = steps.map { step ->
            val level = step / loudest
            if (level < GATE) {
                // Below the gate is room tone, tape hiss, the space between
                // hits. Left as a buzz it would smear the rhythm into one
                // continuous vibration.
                0
            } else {
                // Hearing is roughly logarithmic and so is touch, so a linear
                // map wastes most of the motor on the loudest few percent. The
                // curve lifts ordinary hits into the range a hand notices.
                val curved = level.toDouble().pow(CURVE)
                (curved * Score.MAX_AMPLITUDE).roundToInt().coerceIn(0, Score.MAX_AMPLITUDE)
            }
        }
        return Score(stepMs, amplitudes, name)
    }

    /** Number of steps produced so far. The count [Score.MAX_AMPLITUDE] cares about is in R8. */
    val stepCount: Int get() = steps.size

    /**
     * Exponential smoothing coefficient for a given time constant, at this
     * sample rate. Derived rather than hardcoded because files arrive at
     * 44100 Hz and 48000 Hz alike, and a coefficient tuned for one would give
     * the other a different attack — a subtle difference that would show up as
     * "some tracks feel wrong" long after anyone remembered why.
     */
    private fun coefFor(seconds: Double): Float {
        val samples = seconds * sampleRate
        if (samples <= 0) return 1f
        return (1.0 - exp(-1.0 / samples)).toFloat()
    }

    private companion object {
        /**
         * Kick drums live around 50–100 Hz and bass guitar just above. 200 Hz
         * keeps both and drops most of the vocal, which carries the melody but
         * not the pulse.
         */
        const val CUTOFF_HZ = 200.0

        /** 5 ms: fast enough that a kick reaches the motor as a hit, not a swell. */
        const val ATTACK_SECONDS = 0.005

        /** 120 ms: a hit decays and is gone before the next beat at any normal tempo. */
        const val RELEASE_SECONDS = 0.120

        /** Below 8 % of the loudest step, output nothing rather than a faint hum. */
        const val GATE = 0.08f

        /** Below 1.0 lifts quiet detail; 0.6 is a conventional loudness-ish curve. */
        const val CURVE = 0.6
    }
}
