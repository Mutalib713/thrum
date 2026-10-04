package com.mosman.thrum

/**
 * The three presets of screen 15 — and why they are three numbers and not
 * nine.
 *
 * The 2026-09-21 measurement (PLAN.md, Task 8 follow-up part two) walked the
 * Duration dial across the real armed track and measured sustained drive at
 * **0.334** at 100 ms, **0.417** at 240 ms and **0.540** at 400 ms, against
 * the stock buzz's 0.500. Those three points are the presets: **Crisp** is
 * more silence than the buzz, **Full** is nearly parity, **Strong** is past
 * it — the design's own words agree ("Strong: closest to a buzz"). No
 * measurement ever distinguished a Punch or a Distance per preset, so the
 * presets do not touch them: the fine-tune dials below keep whatever the
 * user has, and a preset changes exactly the axis that was measured.
 *
 * The final values are still Mutalib's hand to confirm (Task 15's second
 * half) — but they start from measurement, not from a guess.
 */
object Tuning {

    data class Preset(val bodyMs: Int)

    /** "Short taps with gaps." Still 54 % on the measured track — more than the buzz. */
    val CRISP = Preset(ScoreBuilder.BODY_MIN_MS)

    /** "Longer beats." 0.417 sustained — nearly parity with the buzz. */
    val FULL = Preset(240)

    /** "Closest to a buzz." 0.540 sustained, longest felt run 1,460 ms. */
    val STRONG = Preset(ScoreBuilder.BODY_MS)

    val ALL = listOf(CRISP, FULL, STRONG)

    /** The preset a Duration dial is currently sitting on, or null off-ladder. */
    fun matching(bodyMs: Int): Preset? = ALL.firstOrNull { it.bodyMs == bodyMs }

    /**
     * "Reset to balanced": the app's own defaults — the felt floor for
     * Intensity, the whole kit for Focus, and the only Duration that reached
     * parity with the buzz on the measured track.
     */
    val RESET_PUNCH = ScoreBuilder.MIN_FELT
    val RESET_DISTANCE = 0
    val RESET_BODY = ScoreBuilder.BODY_MS
}
