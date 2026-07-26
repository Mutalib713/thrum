package com.mosman.thrum

/**
 * Hardcoded scores for Task 1, whose whole job is to answer one question with
 * Mutalib's hand: does varying the motor's strength actually feel like rhythm,
 * or does it feel like stuttering? (PROFILE.md §11 R7.)
 *
 * [systemBuzz] is here so the answer can be felt as a comparison rather than
 * judged in the abstract — it imitates the flat pattern Android plays today,
 * which is the thing this app exists to replace.
 *
 * Pure Kotlin, same reason as [Score].
 */
object Demo {

    const val STEP_MS = 20

    /** What Android does now: full strength on, nothing, repeat. No relationship to any ringtone. */
    fun systemBuzz(): Score = score("Android's default buzz") {
        repeat(3) {
            hold(1000, 255)
            hold(1000, 0)
        }
    }

    /** Two bars at 100bpm, accents on 1 and 3, quiet off-beats. What a ringtone should feel like. */
    fun rhythm(): Score = score("Rhythm") {
        repeat(2) {
            hit(255, 140); rest(460)   // beat 1, the heavy one
            hit(110, 90); rest(510)    // beat 2
            hit(200, 130); rest(470)   // beat 3
            hit(110, 90)               // beat 4
            rest(180)
            hit(70, 60); rest(180)     // the off-beat before the loop
        }
    }

    /** Soft rise into a sharp cut, twice. Tests whether fine amplitude steps read as smooth. */
    fun swell(): Score = score("Swell") {
        repeat(2) {
            ramp(900, 0, 230)
            hold(80, 255)
            rest(700)
        }
    }

    fun all(): List<Score> = listOf(rhythm(), swell(), systemBuzz())

    private fun score(name: String, build: Builder.() -> Unit): Score {
        val b = Builder()
        b.build()
        return Score(STEP_MS, b.steps, name)
    }

    private class Builder {
        val steps = mutableListOf<Int>()

        fun hold(ms: Int, amplitude: Int) {
            repeat(stepsFor(ms)) { steps.add(amplitude) }
        }

        fun rest(ms: Int) = hold(ms, 0)

        fun ramp(ms: Int, from: Int, to: Int) {
            val n = stepsFor(ms)
            for (i in 0 until n) {
                steps.add(from + ((to - from) * i) / n)
            }
        }

        /** A peak that decays to nothing — the shape of a drum, not a doorbell. */
        fun hit(peak: Int, decayMs: Int) {
            steps.add(peak)
            ramp(decayMs, peak, 0)
        }

        private fun stepsFor(ms: Int) = (ms / STEP_MS).coerceAtLeast(1)
    }
}
