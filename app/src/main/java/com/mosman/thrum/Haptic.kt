package com.mosman.thrum

/**
 * The vibration for a whole track, the tuning it was made with, and when it
 * was made. PROFILE.md §8.
 *
 * The score is the **whole track**, not the first 45 seconds — that is the
 * change the full app makes. A call takes [callWindow]; the player plays the
 * whole thing through [pieces]. The two consumers want different slices of the
 * same saved score, and storing the whole thing is what makes a re-slice free
 * (a call window or a coarsening costs nothing; re-analysing costs seconds).
 *
 * The tuning rides on the haptic because the score was **made with it**: a
 * retune (Task 24) rebuilds the score and rewrites this row, so a haptic and
 * its tuning can never disagree — the same one-writer rule [Store.arm] enforces
 * for the call settings.
 *
 * Pure Kotlin: [Score] inside it is pure, and the two derived slices are
 * arithmetic. Persisting is [LibraryDb]'s job.
 */
data class Haptic(
    val trackUri: String,
    val score: Score,
    val punch: Int,
    val distance: Int,
    val bodyMs: Int,
    val madeAtMs: Long,
) {
    init {
        require(trackUri.isNotEmpty()) { "a haptic needs a track" }
        require(score.amplitudes.isNotEmpty()) { "a haptic needs a score with steps in it" }
    }

    /**
     * What an incoming call plays: the first 45 seconds, which the caller
     * repeats until the call is answered or ends. Never over the vibrator's
     * limit — 45 s at 20 ms is 2,250 steps against a cap of 8,000.
     */
    fun callWindow(): Score = score.firstSeconds(ScoreBuilder.RINGTONE_SECONDS)

    /**
     * The whole song in pieces the vibrator accepts. R10: the full score can
     * be far over the cap — the real AIZO track is 8,862 steps against 8,000 —
     * so a whole song always plays as pieces, and this is the only way it is
     * ever allowed to.
     */
    fun pieces(): List<Score> = score.pieces(Haptics.MAX_STEPS)
}
