package com.mosman.thrum

/**
 * How a score reads to a hand — the numbers behind "it feels like a hum".
 *
 * R9, measured 2026-09-28 (`docs/device/2026-09-28-vibration-measurement.md`):
 * the armed score drove the motor 89.6 % of the time at a near-constant 0.84 of
 * full strength, and a hand reads that as a hum, not a beat. A hand feels a
 * haptic through its **onset** — the moment a drive *starts* — so what separates
 * a rhythm from a buzz is not the amplitude (that was already at the ceiling)
 * but how much of the time the motor is moving and how steadily.
 *
 * The same measurement had also read the phone's own incoming-call vibration
 * off `dumpsys vibrator_manager`: one second at full amplitude, then one second
 * of complete silence, for ever. That is the benchmark every score is measured
 * against here — [STOCK_BUZZ] — because "is this a rhythm?" is not a question
 * about our score alone; it is a question about our score beside the buzz it
 * replaces.
 *
 * Pure Kotlin with no Android imports, for the same reason [Score] has none:
 * the hum is decided by arithmetic on the amplitudes, and arithmetic is the one
 * thing this project can prove on the PC. The 2026-09-21 measurement that found
 * the split between the dials was a Python transcription of this file's logic;
 * having it in the QA suite means every future tuning change reports these
 * numbers without a phone.
 */
object Feel {

    /**
     * The stock ringtone buzz, as a [Score] at the app's own 20 ms resolution.
     *
     * The phone's pattern (read off `dumpsys vibrator_manager`, and replayed by
     * [Haptics.playStockRingtoneBuzz]) is 1 s at full amplitude, 1 s of
     * silence, repeating. Built here rather than hardcoded as numbers so the
     * benchmark and the scores it judges go through the *same* arithmetic — a
     * benchmark computed by a different formula than the thing it judges would
     * be no witness at all.
     */
    val STOCK_BUZZ: Score = Score(
        stepMs = 20,
        amplitudes = List(50) { Score.MAX_AMPLITUDE } + List(50) { 0 },
        sourceName = "the phone's own buzz",
    )

    /** The metrics of [STOCK_BUZZ], the line every score is read against. */
    val STOCK_BUZZ_METRICS: Metrics = of(STOCK_BUZZ)

    data class Metrics(
        /** Percentage of steps the motor is asked to move at all. */
        val dutyPct: Int,
        /** The complement — the silence between hits, which is what makes onsets. */
        val stillPct: Int,
        /** Mean strength of the steps that drive, 0..255. */
        val meanOn: Int,
        /**
         * Duty × mean-on / 255. 1.000 would be 255 held without a break. The
         * stock buzz measures 0.500; the armed score on 2026-09-21 measured
         * 0.334 at the crisp end and 0.540 at the strongest — so "sustained
         * drive at or under the buzz's 0.500" is the line between upgrading
         * the buzz and replacing it with something flatter.
         */
        val sustainedDrive: Float,
        /**
         * Longest unbroken stretch at or above [feltThreshold] — what a phone
         * lying on a table responds to. The buzz's is 1000 ms; this is the one
         * axis where a rhythm is allowed to beat it, because a table feels a
         * long push and a hand feels an onset.
         */
        val longestFeltRunMs: Int,
    )

    fun of(score: Score, feltThreshold: Int = ScoreBuilder.MIN_FELT): Metrics {
        val amps = score.amplitudes
        val total = amps.size
        require(total > 0) { "cannot measure an empty score" }
        val on = amps.count { it > 0 }
        val duty = on.toFloat() / total
        val meanOn = if (on == 0) 0 else amps.filter { it > 0 }.sum() / on
        var best = 0
        var run = 0
        for (a in amps) {
            run = if (a >= feltThreshold) run + score.stepMs else 0
            best = maxOf(best, run)
        }
        return Metrics(
            dutyPct = Math.round(duty * 100),
            stillPct = 100 - Math.round(duty * 100),
            meanOn = meanOn,
            sustainedDrive = duty * meanOn / Score.MAX_AMPLITUDE,
            longestFeltRunMs = best,
        )
    }
}
