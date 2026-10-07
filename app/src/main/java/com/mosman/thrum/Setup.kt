package com.mosman.thrum

/**
 * Whether Thrum will actually do anything on the next call.
 *
 * Task 9. `PROFILE.md` §4.6 asks for setup guidance, and §11 R2 says how much of
 * it survives contact with the measurements: **almost none**. `vibrate_when_ringing`
 * stayed on and `ring_vibration_intensity` stayed at 3/3 through all thirteen of
 * Task 2's real calls and made no difference, because the last `RINGTONE`
 * vibration wins — ours supersedes the system's buzz regardless of either. So
 * there is nothing to tell a user to change there, and a screen that told them to
 * change it would be teaching a superstition.
 *
 * What is left is the two ways the app can be silently dead:
 *
 * - **Silent mode is a wall, not a blank canvas.** Android discards a `RINGTONE`
 *   vibration outright when the ringer is silent — `ignored_for_ringer_mode`,
 *   duration `0 ms`, in the system's own record. Mutalib felt it and named it:
 *   *"the silent mode is just silent, nothing else."* No app can get around this,
 *   so the only honest thing is to say so **before** someone relies on a phone
 *   that will never buzz. This is the case that matters, because to a normal
 *   person "silent" and "vibrate" are both just *no sound*, and the wrong one
 *   makes the app look broken.
 *
 * - **Ring mode when the ringtone isn't the Thrum song.** Thrum stays quiet so
 *   the user never hears one song and feels another (Task 31), and one tap of
 *   Set as ringtone undoes it; but from the outside it looks like a broken app.
 *
 * Pure Kotlin, no Android imports, for the same reason [Score] has none
 * (`PROFILE.md` §7): this is a decision table, and a decision table is the part
 * that can be proved on this PC in seconds. [Haptics.ringerMode] turns the
 * system's `AudioManager` constant into the word; everything after that is here.
 */
object Setup {

    /**
     * The phone's ringer, in the words [Haptics.ringerMode] already returns.
     *
     * [UNKNOWN] is a real state and not an error path to hide. `AudioManager`
     * reports an unrecognised mode on some OEM builds, and a verdict screen that
     * guessed "it will work" from a mode it could not read would be exactly the
     * class of lie this project keeps having to fix.
     */
    enum class Ringer {
        VIBRATE,
        RING,
        SILENT,
        UNKNOWN,
        ;

        companion object {
            /**
             * Never throws. This is called from a polled `produceState` and, in
             * spirit, from anywhere a ringer word arrives — an unexpected word
             * must degrade to [UNKNOWN], not take a screen down.
             */
            fun of(word: String): Ringer = when (word) {
                "vibrate" -> VIBRATE
                "ring" -> RING
                "silent" -> SILENT
                else -> UNKNOWN
            }
        }
    }

    /**
     * What will happen on the next call.
     *
     * Five outcomes rather than two, because "will it work" has three different
     * failures and each one needs a different sentence and a different fix.
     */
    enum class Verdict {
        /** Ringer on vibrate. The case the whole app exists for. */
        WILL_FIRE,

        /**
         * Ring mode, and the phone's ringtone is the song Thrum vibrates to:
         * the user hears and feels the same song (Task 31).
         */
        WILL_FIRE_IN_RING,

        /**
         * Ring mode, but the ringtone is something else, so Thrum stays quiet
         * rather than play one song over another. Fixed by Set as ringtone,
         * or by switching to vibrate. Before 2026-10-07 this was "the switch
         * is off".
         */
        WONT_FIRE_RING_OFF,

        /** Silent mode. Android drops the vibration before it reaches the motor. */
        WONT_FIRE_SILENT,

        /** The ringer mode could not be read, so nothing can be promised. */
        UNKNOWN,
        ;

        /** True when the user's next call will vibrate to their track. */
        val fires: Boolean
            get() = this == WILL_FIRE || this == WILL_FIRE_IN_RING

        /**
         * True when the next call definitely will not vibrate.
         *
         * Not simply `!fires`: [UNKNOWN] is not a failure, it is an absence of
         * information, and a screen that shouted "Won't work yet" at a user whose
         * phone might be perfectly fine would be crying wolf. Only the two cases
         * that were actually read off the phone count.
         */
        val blocked: Boolean
            get() = this == WONT_FIRE_SILENT || this == WONT_FIRE_RING_OFF

        /**
         * True when the only fix is a system setting, rather than a button on
         * this screen. Silent mode is the sole case: there is no in-app control
         * that can talk Android out of discarding the vibration, and there never
         * will be.
         */
        val needsSoundSettings: Boolean
            get() = this == WONT_FIRE_SILENT
    }

    /**
     * The whole decision, in one expression.
     *
     * Deliberately not `when (ringer) { SILENT -> false; else -> true }` plus a
     * second check for the ringtone: the two interact (ring mode depends on
     * the ringtone, vibrate mode does not) and splitting them across two call sites is
     * how a screen ends up disagreeing with the listener. [NotifService.onIncoming]
     * makes the same decision at call time; this is the same table, shown to the
     * user in advance.
     */
    fun verdict(ringer: Ringer, ringtoneIsTheSong: Boolean): Verdict = when (ringer) {
        Ringer.VIBRATE -> Verdict.WILL_FIRE
        Ringer.RING -> if (ringtoneIsTheSong) Verdict.WILL_FIRE_IN_RING else Verdict.WONT_FIRE_RING_OFF
        Ringer.SILENT -> Verdict.WONT_FIRE_SILENT
        Ringer.UNKNOWN -> Verdict.UNKNOWN
    }
}
