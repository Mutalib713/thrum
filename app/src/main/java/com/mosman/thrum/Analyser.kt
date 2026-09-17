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
 *
 * **The motor's measured ceiling: 8 taps a second, blurring at 12.** Mutalib ran
 * `Demo.pulseTrain` up a ladder of rates on the Pixel 6 Pro, 2026-08-01, and
 * that is where separate taps stopped feeling separate. It matters because the
 * scores before that test ran at **3.1 taps a second** — the analyser was being
 * throttled by a hardware limit that does not exist, and several rounds of
 * tuning changed the numbers while barely changing what reached his hand. Every
 * density and duration constant below is now set against a measured ceiling
 * rather than a guessed one.
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
    private val highAttackCoef = coefFor(HIGH_ATTACK_SECONDS)
    private val highReleaseCoef = coefFor(HIGH_RELEASE_SECONDS)
    private val highSustainedCoef = coefFor(HIGH_SUSTAINED_SECONDS)

    private var lp1 = 0f
    private var lp2 = 0f
    private var envelope = 0f
    private var sustained = 0f

    private var stepPeak = 0f
    private var stepDetail = 0f
    private var highEnv = 0f
    private var highSustained = 0f
    private var samplesInStep = 0
    private var primed = false

    /** One entry per completed step. Kept as floats until [build] knows the loudest. */
    private val steps = ArrayList<Float>()

    /** The same steps, measured as level rather than as onset. See [Levels]. */
    private val details = ArrayList<Float>()

    /**
     * Feed one chunk of mono samples. Safe to call with the reused array
     * [AudioDecoder] hands out — nothing here keeps a reference to it.
     */
    fun feed(samples: ShortArray, count: Int) {
        val end = count.coerceAtMost(samples.size)
        for (i in 0 until end) {
            val x = samples[i].toFloat()

            // Start the followers where the music is, not at zero.
            //
            // Both "recent average" followers begin empty, so for their first
            // half-second everything towers over them and every track opens with
            // a burst of hits that are not in the music. On the cymbal-wash test
            // that burst welded the first two beats into one, turning eight
            // pulses into seven. Priming on the first sample costs nothing and
            // removes an artifact from the opening of every score.
            if (!primed) {
                lp1 = x
                lp2 = x
                envelope = abs(x)
                sustained = envelope
                highEnv = 0f
                highSustained = 0f
                primed = true
            }

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

            // The rest of the kit.
            //
            // The low-pass keeps the kick and drops everything else, so a score
            // was only ever the bass drum — one voice of a pattern that has
            // three or four. That is why it read as thumping rather than as
            // music. What the low-pass threw away is exactly the snare, the rim,
            // the hats: the detail that makes a rhythm sound played rather than
            // pulsed.
            //
            // Subtracting the filtered signal from the original leaves the upper
            // band, and the same onset trick applies to it. These become lighter
            // hits, mapped below the kick's range so the beat still leads.
            val high = x - lp2
            val highLevel = abs(high)
            highEnv += if (highLevel > highEnv) {
                highAttackCoef * (highLevel - highEnv)
            } else {
                highReleaseCoef * (highLevel - highEnv)
            }
            highSustained += highSustainedCoef * (highEnv - highSustained)
            val highOnset = highEnv - highSustained
            if (highOnset > stepDetail) stepDetail = highOnset
            if (++samplesInStep >= samplesPerStep) {
                steps.add(stepPeak)
                details.add(stepDetail)
                stepPeak = 0f
                stepDetail = 0f
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
            details.add(stepDetail)
            stepPeak = 0f
            stepDetail = 0f
            samplesInStep = 0
        }
        val loudestOnset = steps.maxOrNull() ?: 0f
        val loudestDetail = details.maxOrNull() ?: 0f
        return Levels(
            onsets = if (loudestOnset <= 0f) List(steps.size) { 0f } else steps.map { it / loudestOnset },
            detail = if (loudestDetail <= 0f) List(details.size) { 0f } else details.map { it / loudestDetail },
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

            // Headroom the floor may never eat into.
            //
            // A kick is mapped as floor + curve × (255 − floor), so a floor of
            // 255 leaves a range of zero and every hit in the track comes out at
            // exactly 255 — loud ones, quiet ones, all identical. Mutalib had
            // Punch at its maximum and reported that every other setting felt
            // the same, which is precisely what a score with no dynamic range
            // feels like. A dial that can flatten the whole output must not be
            // able to reach that point.
            val floor = minFelt.coerceIn(0, Score.MAX_AMPLITUDE - MIN_HEADROOM)

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
                    val range = Score.MAX_AMPLITUDE - floor
                    (floor + curved * range).roundToInt().coerceIn(0, Score.MAX_AMPLITUDE)
                }
            }

            // The upper band's own hits — snare, rim, hats. Capped below
            // [minFelt] so the kick always leads: a pattern where every voice
            // is equally loud is not a pattern, it is noise.
            //
            // Replaced the earlier "body" layer, which took the *level* rather
            // than the onsets and was built on the fast envelope, so it
            // collapsed to nothing between beats and did almost no work: with it
            // at maximum a real track still measured 75.2 % silent. Mutalib
            // reached the same verdict by hand — the setting he preferred was
            // zero.
            // Mapped into DETAIL_MIN..ceiling, never 0..ceiling.
            //
            // The bug this fixes, which is the same bug the kick layer already
            // had: mapping from zero puts most hits at amplitudes of 1, 3, 7, 20
            // — below the point where the motor moves at all. The whole layer
            // was inaudible, and scaling inaudible numbers by a ceiling produces
            // different inaudible numbers, which is precisely why Mutalib
            // reported that every setting felt the same.
            //
            // A detail hit is either worth feeling or it is zero. There is
            // nothing in between on this hardware.
            // Detail tops out where the kick floor begins, so the beat leads.
            val ceiling = bodyCeiling.coerceAtMost(floor)
            val detail = if (ceiling <= DETAIL_MIN) {
                List(levels.detail.size) { 0 }
            } else {
                levels.detail.map { level ->
                    if (level < DETAIL_GATE) {
                        0
                    } else {
                        val above = ((level - DETAIL_GATE) / (1f - DETAIL_GATE)).coerceIn(0f, 1f)
                        val curved = above.toDouble().pow(DETAIL_CURVE)
                        (DETAIL_MIN + curved * (ceiling - DETAIL_MIN)).roundToInt()
                            .coerceIn(0, Score.MAX_AMPLITUDE)
                    }
                }
            }

            // Hold each layer for its own length, then lay detail over kick.
            //
            // Detail used to get no hold at all, and measured 20–40 ms per hit
            // against the kick's 160 ms. A motor has mass: 20 ms ends while it
            // is still spinning up, so those hits never arrived, and Mutalib
            // reported every setting feeling almost the same. The same law as
            // the felt floor, on the other axis — a hit must be **loud enough
            // and long enough**, or it is not a hit.
            //
            // Shorter than a kick's hold on purpose. A hat that lasts as long as
            // a kick stops being a hat.
            //
            // Ceiling division, not truncation: 45 ms at 20 ms steps truncated
            // to 40 ms, which is *below* the minimum the constant names, and at
            // 40 ms steps it truncated to a single step and the hold did nothing
            // at all. Rounding up is the difference between a rule and a wish.
            val minSteps = stepsFor(minPulseMs, stepMs)
            val detailSteps = stepsFor(DETAIL_PULSE_MS, stepMs)
            // Each layer decays only as far as its own floor. The kick may fade
            // toward [floor] (its weakest felt beat) and the detail toward
            // [DETAIL_MIN], because a hold that fades below those spends most of
            // its time at strengths the motor cannot produce — which is exactly
            // the "I can't feel it sometimes" report this fixes.
            val held = Score(stepMs, hits, name)
                .holdPulsesAtLeast(minSteps.coerceAtLeast(1), HIT_DECAY, floor)
            val heldDetail = Score(stepMs, detail, name)
                .holdPulsesAtLeast(detailSteps.coerceAtLeast(1), HIT_DECAY, DETAIL_MIN)
            val combined = held.amplitudes.mapIndexed { i, hit ->
                maxOf(hit, heldDetail.amplitudes.getOrElse(i) { 0 })
            }
            return Score(stepMs, combined, name)
        }

        /**
         * How many [stepMs] steps are needed to cover [ms], rounded **up**.
         *
         * A hold is a promise that a hit lasts at least [ms]. Truncating the
         * division breaks that promise in the quiet, worst way: 45 ms at 20 ms
         * became 40 ms — under the named minimum — and at 40 ms steps it became
         * one step, so the hold did nothing at all and a hat vanished. Rounding
         * up can only ever overshoot by less than one step, which is the safe
         * direction.
         */
        internal fun stepsFor(ms: Int, stepMs: Int): Int {
            require(stepMs > 0) { "stepMs must be positive, was $stepMs" }
            if (ms <= 0) return 1
            return ((ms + stepMs - 1) / stepMs).coerceAtLeast(1)
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
        const val MIN_PULSE_MS = 100

        /**
         * The loudest a detail hit may get. Below [MIN_FELT] on purpose: the
         * kick has to lead, or a pattern where every voice is equally loud stops
         * being a pattern.
         */
        const val BODY_CEILING = 150

        /**
         * How much of the upper band counts as a hit.
         *
         * Was 0.16, which let only the sharpest transients through — twelve
         * detail hits in forty-five seconds of a track full of hats. Enough of
         * the kit has to arrive for the layer to be worth having.
         */
        const val DETAIL_GATE = 0.08f

        /**
         * The shortest a detail hit may last. Long enough for the motor to move,
         * short enough that a hat is still lighter than a kick's 120 ms.
         */
        const val DETAIL_PULSE_MS = 45

        /**
         * The weakest a detail hit may be. Same law as [MIN_FELT], applied to
         * the second layer after it was forgotten there: under roughly this the
         * motor hums without moving, so a hit mapped below it is a hit the user
         * never receives. Lower than [MIN_FELT] because a hat should be lighter
         * than a kick — but not lower than the hardware's floor.
         */
        const val DETAIL_MIN = 130

        /**
         * How much of the motor's range is reserved for dynamics, whatever the
         * Punch dial says. Without it a floor of 255 leaves nothing between the
         * quietest hit and the loudest, and a track's rhythm flattens into one
         * repeated value.
         */
        const val MIN_HEADROOM = 45

        /** Same shape as the kick's curve, so the two bands feel like one kit. */
        const val DETAIL_CURVE = 0.6

        /** A hat is over almost before it starts, so this band follows much faster. */
        const val HIGH_ATTACK_SECONDS = 0.002
        const val HIGH_RELEASE_SECONDS = 0.040
        /**
         * A quarter of a second, and it has to stay there.
         *
         * This is the "what has the upper band been doing lately" average that
         * onsets are measured against. Lengthening it to 0.35 s and 0.5 s was
         * tried, to let a steady hat pattern register more strongly — and both
         * broke the cymbal-wash check, because a slower average lets a *constant*
         * wash keep reading as fresh onsets, which bridged two beats into one.
         * Bisected, not guessed: 0.25 passes, 0.35 and 0.5 both fail.
         */
        const val HIGH_SUSTAINED_SECONDS = 0.250

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
 * A track measured in two frequency bands over the same steps.
 *
 * [onsets] is the low band rising — the kick, the beat to lead with. [detail] is
 * the same measurement applied to everything the low-pass threw away: snare,
 * rim, hats.
 *
 * A score from the low band alone is only ever the bass drum, which is one voice
 * of a pattern that has three or four — that is why it thumped rather than
 * played. Mixed, with detail capped below the kick, it reads as a kit.
 *
 * The layer this replaced measured *level* rather than onsets, on the fast
 * envelope, so it collapsed between beats and did almost nothing: at maximum a
 * real track still came out 75.2 % silent. Mutalib reached the same verdict by
 * hand — the setting he preferred was zero.
 */
data class Levels(val onsets: List<Float>, val detail: List<Float>)
