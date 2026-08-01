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

    /**
     * Identical taps, repeated [perSecond] times a second, for [seconds].
     *
     * The instrument test. A motor has mass: it takes time to start moving and
     * time to stop. Ask for taps faster than it can finish one and they run into
     * each other, and what reaches the hand is one continuous buzz rather than a
     * rhythm — no matter what the score says.
     *
     * Thrum's scores currently run at about 3 taps a second, and Mutalib keeps
     * reporting that real changes in the data make almost no difference in his
     * hand. This is how we find out whether that is the analyser's fault or the
     * motor's: the tap is the *same length* at every rate, so the only thing
     * changing is the gap between them.
     *
     * Uses a 10 ms step so the fast rates are not rounded into each other.
     */
    fun pulseTrain(perSecond: Int, seconds: Double = 2.5, pulseMs: Int = 60): Score {
        require(perSecond > 0) { "perSecond must be positive, was $perSecond" }
        val step = 10
        val pulseSteps = (pulseMs / step).coerceAtLeast(1)
        val periodSteps = (1000 / perSecond / step).coerceAtLeast(pulseSteps + 1)
        val gapSteps = periodSteps - pulseSteps
        val repeats = ((seconds * 1000) / (periodSteps * step)).toInt().coerceAtLeast(1)

        val amps = ArrayList<Int>(repeats * periodSteps)
        repeat(repeats) {
            repeat(pulseSteps) { amps.add(Score.MAX_AMPLITUDE) }
            repeat(gapSteps) { amps.add(0) }
        }
        return Score(step, amps, "$perSecond a second")
    }

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
