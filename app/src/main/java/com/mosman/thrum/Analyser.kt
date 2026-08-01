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
    private val sustainedCoef = coefFor(SUSTAINED_SECONDS)

    private var lp1 = 0f
    private var lp2 = 0f
    private var envelope = 0f
    private var sustained = 0f

    private var stepPeak = 0f
    private var stepBody = 0f
    private var samplesInStep = 0

    /** One entry per completed step. Kept as floats until [build] knows the loudest. */
    private val steps = ArrayList<Float>()

    /** The same steps, measured as level rather than as onset. See [Levels]. */
    private val bodies = ArrayList<Float>()

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

            // Then take only what *rises above* the recent average, rather than
            // the level itself.
            //
            // Following the level directly was the first attempt and it failed
            // on real music. Mastered tracks are loud almost all the time, so a
            // sustained bassline never falls back to the gate: Masha Allah came
            // out 96.7 % non-zero, with one unbroken 2m23s vibration at a mean
            // of 124/255. Felt like a massage, not a rhythm.
            //
            // [sustained] is the same envelope followed slowly, so it settles at
            // whatever the track has been doing lately. A held note pulls it up
            // until the difference is nothing; a drum arrives faster than it can
            // follow and stands clear of it. That difference is the beat.
            sustained += sustainedCoef * (envelope - sustained)
            val onset = envelope - sustained

            if (onset > stepPeak) stepPeak = onset
            // The music's body, kept alongside the beat.
            //
            // Onsets alone gave 76 % silence: isolated thumps with nothing
            // between them. That is rhythm, but it is not music, and Mutalib
            // felt the difference immediately. The envelope itself becomes a
            // quiet second layer under the hits, so a sustained note is
            // *present* rather than absent — the way a bass line is present
            // when you put your hand on a speaker cabinet.
            if (envelope > stepBody) stepBody = envelope
            if (++samplesInStep >= samplesPerStep) {
                steps.add(stepPeak)
                bodies.add(stepBody)
                stepPeak = 0f
                stepBody = 0f
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
    fun build(): Score = toScore(levels(), stepMs, name)

    /**
     * The analysed track as one number per step, 0..1, before any decision about
     * how hard the motor should work.
     *
     * Separated from [build] so the strength settings can be changed and a new
     * score produced instantly, without decoding the file again. Tuning by feel
     * means many small adjustments, and seven seconds of decoding between each
     * one is how tuning stops happening.
     */
    fun levels(): Levels {
        // The last partial step still covers real audio. Dropping it would make
        // every score up to one step shorter than its track. Guarded so calling
        // this twice cannot append it twice.
        if (samplesInStep > 0) {
            steps.add(stepPeak)
            bodies.add(stepBody)
            stepPeak = 0f
            stepBody = 0f
            samplesInStep = 0
        }
        val loudestOnset = steps.maxOrNull() ?: 0f
        val loudestBody = bodies.maxOrNull() ?: 0f
        return Levels(
            onsets = if (loudestOnset <= 0f) List(steps.size) { 0f } else steps.map { it / loudestOnset },
            body = if (loudestBody <= 0f) List(bodies.size) { 0f } else bodies.map { it / loudestBody },
        )
    }

    /** Number of steps produced so far. The count [Score.MAX_AMPLITUDE] cares about is in R8. */
    val stepCount: Int get() = steps.size

    /** The step length this builder was constructed with, for re-scoring its [levels]. */
    val stepMsUsed: Int get() = stepMs

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

    companion object {

        /**
         * Turn analysed levels into a score the motor can play.
         *
         * Separate from the builder so the strength can be changed and a new
         * score produced instantly, without decoding the file again.
         *
         * @param minFelt the weakest amplitude worth asking for — see [MIN_FELT].
         * @param minPulseMs the shortest a hit may last — see [MIN_PULSE_MS].
         */
        fun toScore(
            levels: Levels,
            stepMs: Int,
            name: String = "",
            minFelt: Int = MIN_FELT,
            curve: Double = CURVE,
            gate: Float = GATE,
            minPulseMs: Int = MIN_PULSE_MS,
            bodyCeiling: Int = BODY_CEILING,
        ): Score {
            if (levels.onsets.none { it > 0f }) {
                // Silence in, silence out. Not an error: a silent file is a real
                // thing a user can pick, and it must not produce a buzz.
                return Score(stepMs, List(levels.onsets.size) { 0 }, name)
            }

            val hits = levels.onsets.map { level ->
                if (level < gate) {
                    0
                } else {
                    // Map what survives onto minFelt..255 rather than 0..255.
                    //
                    // The bottom of a motor's range is not quiet, it is nothing:
                    // under about 140 the mass barely moves. Spending half the
                    // scale there produced a score Mutalib could feel in his hand
                    // but which could not move the phone on a table, while
                    // Android's own buzz shakes it. Quiet hits must still be hits.
                    val above = ((level - gate) / (1f - gate)).coerceIn(0f, 1f)
                    val curved = above.toDouble().pow(curve)
                    val range = Score.MAX_AMPLITUDE - minFelt
                    (minFelt + curved * range).roundToInt().coerceIn(0, Score.MAX_AMPLITUDE)
                }
            }

            // The body layer, deliberately capped below [minFelt] so it fills
            // the gaps without ever competing with a beat. Loud enough to feel
            // as texture, quiet enough that the hits still land on top of it.
            val body = levels.body.map { level ->
                if (level < BODY_GATE) {
                    0
                } else {
                    val above = ((level - BODY_GATE) / (1f - BODY_GATE)).coerceIn(0f, 1f)
                    (above.toDouble().pow(BODY_CURVE) * bodyCeiling).roundToInt()
                        .coerceIn(0, Score.MAX_AMPLITUDE)
                }
            }

            // Hold the hits first, then lay them over the body — holding a
            // combined track would stretch quiet texture into fake beats.
            val held = Score(stepMs, hits, name)
                .holdPulsesAtLeast((minPulseMs / stepMs).coerceAtLeast(1), HIT_DECAY)
            val combined = held.amplitudes.mapIndexed { i, hit ->
                maxOf(hit, body.getOrElse(i) { 0 })
            }
            return Score(stepMs, combined, name)
        }

        /**
         * Kick drums live around 50–100 Hz and bass guitar just above. 200 Hz
         * keeps both and drops most of the vocal, which carries the melody but
         * not the pulse.
         */
        const val CUTOFF_HZ = 200.0

        /** 3 ms: fast enough that a kick reaches the motor as a hit, not a swell. */
        const val ATTACK_SECONDS = 0.003

        /**
         * 60 ms: a hit is over well before the next one. At 160 bpm beats are
         * 375 ms apart, so even fast music gets stillness between them.
         */
        const val RELEASE_SECONDS = 0.060

        /**
         * 350 ms for the "what has this track been doing lately" average.
         *
         * Long enough to sit still through a single beat — otherwise it would
         * chase each drum and cancel it — and short enough to follow a song from
         * a quiet verse into a loud chorus without the whole verse reading as
         * silence.
         */
        const val SUSTAINED_SECONDS = 0.350

        /** Below 10 % of the strongest onset, output nothing rather than a faint hum. */
        const val GATE = 0.10f

        /**
         * Below 1.0 lifts quieter hits toward the range a hand notices. Gentler
         * than the 0.6 first tried: with onsets rather than levels, 0.6 lifted
         * every small tick into something felt, which is how the whole track
         * turned into one continuous vibration.
         */
        const val CURVE = 0.55

        /**
         * The weakest amplitude worth asking for. Below roughly this the motor
         * hums without moving anything, so a hit mapped there is a hit the user
         * does not get. Everything above the gate is spread across
         * MIN_FELT..255 instead of 0..255.
         */
        const val MIN_FELT = 185

        /**
         * The shortest a hit may last. A motor has mass and needs time to spin
         * up; a single 40 ms step ends before it has moved. 90 ms is still well
         * inside the gap between beats at any tempo a person dances to.
         */
        const val MIN_PULSE_MS = 120

        /**
         * The loudest the body layer may get. Below [MIN_FELT] on purpose: the
         * texture between beats must never be mistaken for a beat.
         */
        const val BODY_CEILING = 135

        /** Quieter than the hit gate — this layer's job is to be almost always present. */
        const val BODY_GATE = 0.05f

        /** Flatter than [CURVE], so the body reads as a steady presence rather than more accents. */
        const val BODY_CURVE = 0.9

        /**
         * How far a hit fades across the steps it is held for. A drum decays; a
         * square pulse is what made the rhythm read as mechanical rather than
         * musical. Not all the way to nothing, or the hold stops adding the
         * energy it exists to add.
         */
        const val HIT_DECAY = 0.55f
    }
}

/**
 * A track measured two ways over the same steps.
 *
 * [onsets] is what *rises* — the beat. [body] is how loud the music simply is —
 * its presence. A score built from onsets alone is 76 % silence and feels like
 * isolated thumps; one built from body alone is a continuous massage (Task 4's
 * first attempt). Mixed, with the body capped below the hits, it reads as music.
 */
data class Levels(val onsets: List<Float>, val body: List<Float>)
