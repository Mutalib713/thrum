package com.mosman.thrum

/**
 * The decisions behind Set as ringtone, kept apart from Android so they can
 * be tested on the PC. Task 31 (Mutalib, 2026-10-07): "Use for calls" and
 * "Set as ringtone" are one action, so what the phone rings with and what
 * Thrum vibrates are the same song.
 *
 * Addresses are compared as strings here; [Ringtone] turns them into the
 * phone's own `Uri`s.
 */
object RingtoneRules {

    /** How long the ringtone Thrum saves is: the window a call's vibration repeats. */
    const val CLIP_MS = ScoreBuilder.RINGTONE_SECONDS * 1000L

    /**
     * A song this much longer than [CLIP_MS] still counts as fitting, so a
     * 45.3-second ringtone is used as it is rather than cut by a breath.
     */
    const val FITS_SLACK_MS = 500L

    /** The fade at the end of a cut clip, so the jump back to its start doesn't click. */
    const val FADE_OUT_MS = 150L

    /**
     * The most Thrum will skip ahead to catch up with a ringtone that started
     * before it heard about the call. Measured so far: 0.3–0.7 s. Anything far
     * past that is a clock gone wrong, not a late start.
     */
    const val MAX_CATCH_UP_MS = 3_000L

    /** Stored as the old ringtone when the user's ringtone was "None". */
    const val NO_RINGTONE = "none"

    /**
     * The same address, written two ways. Android may keep the phone's user in
     * it (`content://0@media/...`) and hand it back without, and may add a
     * query (`?title=...`) to a stored ringtone. Both are taken out first.
     */
    fun sameAddress(a: String?, b: String?): Boolean {
        if (a == null || b == null) return false
        return bare(a) == bare(b)
    }

    private fun bare(address: String): String =
        address.substringBefore('?').replace(Regex("^content://\\d+@"), "content://")

    /**
     * Whether the phone's ringtone right now is the song Thrum vibrates to.
     * Only then does Thrum play in Ring mode: otherwise the user would hear
     * one song and feel another, which Mutalib ruled out.
     *
     * @param current the phone's ringtone setting now.
     * @param ours the ringtone Thrum last set.
     * @param oursFor the song that ringtone was made from.
     * @param armed the song calls vibrate to.
     */
    fun ringtoneIsTheSong(current: String?, ours: String?, oursFor: String?, armed: String?): Boolean =
        armed != null && oursFor == armed && sameAddress(current, ours)

    /**
     * Whether to note [current] as the user's own ringtone before Thrum
     * replaces it. Only once, and never one of Thrum's: "Go back" has to
     * reach the ringtone the user chose, not the one Thrum set last week.
     *
     * @param saved the old ringtone already noted, if any.
     * @param currentIsThrums [current] is a file Thrum made, or the song calls
     *   already vibrate to (an earlier build set songs as they were).
     */
    fun shouldRemember(saved: String?, currentIsThrums: Boolean): Boolean =
        saved == null && !currentIsThrums

    /** Whether a track of this length needs cutting to [CLIP_MS]; unknown (0) means read it and see. */
    fun needsCut(durationMs: Long): Boolean = durationMs <= 0 || durationMs > CLIP_MS + FITS_SLACK_MS

    /** Frames in the clip at this sample rate. */
    fun clipFrames(sampleRate: Int): Int = (CLIP_MS * sampleRate / 1000).toInt()

    /**
     * The gain for a frame near the end of a clip that was cut: 1 until the
     * last [FADE_OUT_MS], then down in a straight line to 0 at the last frame.
     * A clip that ended on its own (the song was shorter) gets no fade.
     */
    fun fadeGain(frame: Int, totalFrames: Int, sampleRate: Int, cut: Boolean): Float {
        if (!cut || totalFrames <= 0) return 1f
        val fadeFrames = (FADE_OUT_MS * sampleRate / 1000).toInt().coerceIn(1, totalFrames)
        val fromEnd = totalFrames - 1 - frame
        return if (fromEnd >= fadeFrames) 1f else (fromEnd.toFloat() / fadeFrames).coerceIn(0f, 1f)
    }

    /**
     * How far into the vibration a ringing call should start. The ringtone
     * began before Thrum heard about the call, so starting the vibration from
     * zero would leave it behind the sound for the whole ring. Within one
     * loop of the score, and never more than [MAX_CATCH_UP_MS].
     */
    fun catchUpMs(lateMs: Long, scoreMs: Long): Long {
        if (scoreMs <= 0) return 0
        return lateMs.coerceIn(0, MAX_CATCH_UP_MS) % scoreMs
    }
}
