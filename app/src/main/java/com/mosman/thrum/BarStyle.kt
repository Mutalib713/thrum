package com.mosman.thrum

/**
 * How the rhythm is drawn on the player and the mini player: the styles
 * Mutalib picked on 2026-11-28 from the A–Y sketch (scratch/bars-options.html),
 * with Pills (B) the default. The letters are the sketch's, so a style here can
 * be found there.
 *
 * Saved by [key], never by the enum's name: R8 renames enum constants in a
 * release build, and a saved name would then stop matching after an update.
 */
enum class BarStyle(val key: String, val letter: Char) {
    TAPE("tape", 'A'),
    PILLS("pills", 'B'),
    DOTS("dots", 'C'),
    BLOCKS("blocks", 'D'),
    RISE("rise", 'F'),
    PULSE("pulse", 'G'),
    MIRROR("mirror", 'N'),
    METER("meter", 'O'),
    MOON("moon", 'P'),
    LISTEN("listen", 'Q'),
    CUPS("cups", 'R'),
    HEART("heart", 'S'),
    GLOW("glow", 'T'),
    ALBUM("album", 'W');

    companion object {
        val DEFAULT = PILLS

        /** The style saved as [key], or [DEFAULT] for nothing saved or a style that has since gone. */
        fun fromKey(key: String?): BarStyle = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}

/**
 * What the bars read from a haptic: the motor's strength at a moment, the
 * strongest moment in a stretch, a beat's flash as it fades, and where beats
 * start. Plain Kotlin, so it's tested on the PC; the drawing in [Bars] only
 * turns these numbers into shapes.
 *
 * Everything is the vibration, not the sound: a value is high exactly when the
 * motor is running.
 */
class BarMath(private val amps: List<Int>, private val stepMs: Int) {

    val durationMs: Long = amps.size.toLong() * stepMs

    /** The motor's strength at [ms], 0 to 1; 0 before the start, after the end, or with no score. */
    fun amp(ms: Long): Float {
        if (amps.isEmpty() || stepMs <= 0 || ms < 0) return 0f
        val i = ms / stepMs
        return if (i >= amps.size) 0f else amps[i.toInt()] / 255f
    }

    /** The strongest moment between [from] and [from] + [width], so a short tap inside a wide bar is never lost. */
    fun peak(from: Long, width: Long): Float {
        var best = 0f
        var s = from
        while (s < from + width) {
            best = maxOf(best, amp(s))
            s += stepMs.coerceAtLeast(1)
        }
        return best
    }

    /**
     * A beat that flashes and fades over [pulseMs], so a 20 ms step stays
     * visible to the eye. At a beat's start it is the beat's strength; [pulseMs]
     * later it is gone.
     */
    fun env(ms: Long, pulseMs: Long = PULSE_MS): Float {
        var best = 0f
        var k = 0L
        while (k <= pulseMs) {
            best = maxOf(best, amp(ms - k) * (1f - k.toFloat() / pulseMs))
            k += stepMs.coerceAtLeast(1)
        }
        return best
    }

    /**
     * The moment to show when the song isn't playing (Mutalib, 2026-11-28: rows
     * show the style "still, at its strongest beat"): just inside the first
     * step of the strongest hit, where a beat's flash is at its fullest. 0 for
     * an empty or silent score.
     */
    fun strongestMs(): Long {
        if (amps.isEmpty() || stepMs <= 0) return 0
        var best = 0
        for (i in amps.indices) if (amps[i] > amps[best]) best = i
        return if (amps[best] == 0) 0 else best.toLong() * stepMs + stepMs / 2
    }

    /** Whether a beat starts at [ms]: the motor runs here and was still the step before. */
    fun isOnset(ms: Long): Boolean = amp(ms) > 0f && amp(ms - stepMs) == 0f

    /** The start of every beat between [from] and [to], oldest first, on step boundaries. */
    fun onsets(from: Long, to: Long): List<Long> {
        if (stepMs <= 0) return emptyList()
        val out = ArrayList<Long>()
        var s = (from.coerceAtLeast(0) / stepMs) * stepMs
        while (s <= to) {
            if (isOnset(s)) out += s
            s += stepMs
        }
        return out
    }

    companion object {
        const val PULSE_MS = 260L

        /** One of three lanes for a beat, the same every time, so notes released close together don't stack. */
        fun lane(onsetMs: Long, stepMs: Int): Int = (((onsetMs / stepMs.coerceAtLeast(1)) * 7919) % 3).toInt()
    }
}
