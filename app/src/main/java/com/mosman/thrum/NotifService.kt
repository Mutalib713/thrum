package com.mosman.thrum

import android.app.Notification
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Watches notifications and plays a vibration score when a call comes in.
 *
 * **Why notifications rather than call state:** reading call state needs
 * `READ_PHONE_STATE`, or the app has to replace the user's dialer. Both draw
 * heavy Play Store scrutiny for an app whose only job is to vibrate. An
 * incoming call posts a notification, so watching notifications gets the same
 * signal for a permission users can grant and revoke themselves. PROFILE.md §7.
 *
 * This is Task 2, whose whole purpose is to answer R1, R2 and R6 with evidence.
 * Every decision it makes is written to [Store] so it can be read off the screen
 * — there is no cable on this machine, so `adb logcat` is not available.
 */
class NotifService : NotificationListenerService() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var store: Store

    override fun onCreate() {
        super.onCreate()
        store = Store(this)
    }

    override fun onListenerConnected() {
        record(Event.Kind.LISTENER, note = "connected")
    }

    override fun onListenerDisconnected() {
        // Evidence for R5: if this shows up in the log without the app being
        // reinstalled, Android is killing the listener and the feature will
        // silently stop working.
        record(Event.Kind.LISTENER, note = "disconnected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val n = sbn.notification ?: return
        if (n.category != Notification.CATEGORY_CALL) return

        when (callType(n)) {
            TYPE_INCOMING -> onIncoming(sbn)
            // The call was answered, so this notification became the in-call
            // one. Stop, or the phone keeps buzzing through the conversation.
            TYPE_ONGOING -> if (sbn.key == activeKey) stopVibration("answered")
            else -> Unit
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.key == activeKey) stopVibration("call notification removed")
    }

    private fun onIncoming(sbn: StatusBarNotification) {
        val ringer = Haptics.ringerMode(this)
        val latency = (System.currentTimeMillis() - sbn.postTime).coerceAtLeast(0)

        // A dialer updates its call notification moments after posting it —
        // caller ID resolves, a photo loads, an action changes. Every update
        // arrives here as another "incoming", and playing again restarts the
        // waveform a fraction of a second into the rhythm. Task 2 caught this on
        // 5 of 13 real calls, the two fires 21–678 ms apart.
        //
        // Same key is the same call. The time window is for a dialer that posts
        // a second, differently-keyed notification for one ring: swallowing a
        // genuine second caller is harmless, since the phone is already playing.
        if (activeKey != null &&
            (sbn.key == activeKey || SystemClock.uptimeMillis() - lastFireAt < DEDUPE_WINDOW_MS)
        ) {
            record(Event.Kind.SKIPPED, ringer, latency, "duplicate notification, already playing")
            return
        }

        if (ringer == "ring" && !store.fireInRingMode) {
            record(
                Event.Kind.SKIPPED,
                ringer,
                latency,
                "ring mode — the system already handles stock ringtones",
            )
            return
        }
        if (!Haptics.capability(this).usable) {
            record(Event.Kind.SKIPPED, ringer, latency, "motor cannot vary strength")
            return
        }

        // Task 19: nothing chosen means Android's own buzz.
        //
        // The demo pattern used to stand in here, on the reasoning that "a
        // call that does not vibrate at all is indistinguishable from the app
        // being broken". The final design decides the opposite, and it is the
        // better argument: with no song chosen, the phone rings exactly as
        // its maker built it to ring — which is what the user has asked for
        // by not choosing. A made-up rhythm stepping in as if it were theirs
        // is the louder lie, and it is the one thing on this screen the user
        // never asked to feel.
        val armed = store.armedScore ?: run {
            record(
                Event.Kind.SKIPPED,
                ringer,
                latency,
                "no song for calls — the phone's own buzz plays",
            )
            return
        }

        val loop = store.loopWhileRinging
        activeKey = sbn.key
        lastFireAt = SystemClock.uptimeMillis()
        val failure = Haptics.play(this, armed, loop = loop)
        if (failure != null) {
            record(Event.Kind.SKIPPED, ringer, latency, failure)
            activeKey = null
            return
        }
        record(
            Event.Kind.FIRED,
            ringer,
            latency,
            "armed: ${armed.sourceName}" + if (loop) " · looping" else " · once",
        )

        // In ring mode, keep asking.
        //
        // The ringtone repeats, and every repeat makes Android re-issue its own
        // vibration. Since the most recent RINGTONE vibration wins, the system
        // takes the motor back part-way through the ring. Measured on a real
        // call, 2026-08-02: ours ran 6,721 ms and was then superseded by
        // `android` for 6,949 ms, then again for 1,570 ms. Mutalib felt exactly
        // that — his song, then the default buzz for the rest of the ring.
        //
        // Re-asserting from the *elapsed position* rather than from the start
        // keeps the rhythm where the music would be; replaying from zero every
        // couple of seconds would turn a song into a stutter.
        //
        // Only in ring mode. Vibrate mode has no ringtone to loop, nothing
        // takes the motor back, and a timer that re-parcels a 2,000-step
        // waveform every two seconds for nothing is not free.
        if (ringer == "ring") {
            handler.removeCallbacksAndMessages(REASSERT_TOKEN)
            scheduleReassert(armed, loop, SystemClock.uptimeMillis())
        }

        // Safety cap. A looping waveform runs until something cancels it, and if
        // the removal callback never arrives — killed listener, missed update —
        // this phone would buzz until it was rebooted. Belt and braces.
        //
        // Armed for a single-shot score too, even though that one stops itself.
        // The cap is what clears [activeKey], and a stuck key would now make the
        // duplicate guard above swallow every later call in silence — a worse
        // failure than the one it prevents, because nothing would look wrong.
        handler.removeCallbacksAndMessages(SAFETY_TOKEN)
        handler.postAtTime(
            { if (activeKey != null) stopVibrationAs(Event.Kind.CAPPED, "safety cap hit") },
            SAFETY_TOKEN,
            SystemClock.uptimeMillis() + SAFETY_CAP_MS,
        )
    }

    /**
     * Replay the score from where it should be by now, over and over, until the
     * call ends. See [onIncoming] for why ring mode needs this.
     */
    private fun scheduleReassert(score: Score, loop: Boolean, startedAt: Long) {
        handler.postAtTime(
            {
                if (activeKey != null) {
                    val elapsed = SystemClock.uptimeMillis() - startedAt
                    val into = if (score.durationMs > 0) elapsed % score.durationMs else 0
                    // Rotate, do not truncate. `from(into)` gave a suffix, so with
                    // looping on it looped the tail and never played the opening
                    // again, and with looping off it played out and left the rest
                    // of the ring silent. `rotated` keeps the whole score and
                    // still starts at the right moment in the music.
                    Haptics.play(this, score.rotated(into), loop = loop)
                    scheduleReassert(score, loop, startedAt)
                }
            },
            REASSERT_TOKEN,
            SystemClock.uptimeMillis() + REASSERT_MS,
        )
    }

    private fun stopVibration(why: String) = stopVibrationAs(Event.Kind.STOPPED, why)

    private fun stopVibrationAs(kind: Event.Kind, why: String) {
        handler.removeCallbacksAndMessages(SAFETY_TOKEN)
        Haptics.stop(this)
        activeKey = null
        record(kind, note = why)
    }

    private fun record(
        kind: Event.Kind,
        ringer: String = Haptics.ringerMode(this),
        latencyMs: Long = 0,
        note: String = "",
    ) {
        if (!::store.isInitialized) store = Store(this)
        store.addEvent(Event(System.currentTimeMillis(), kind, ringer, latencyMs, note))
    }

    /**
     * `1` incoming, `2` ongoing, `3` screening — the values behind
     * `Notification.CallStyle`. Read by key rather than by constant so a change
     * in the platform's visibility for that field cannot break the build.
     *
     * Some dialers post a call notification without the field. An incoming call
     * is the only kind that carries an answer action, so that stands in for it.
     */
    private fun callType(n: Notification): Int {
        val declared = n.extras.getInt(EXTRA_CALL_TYPE, 0)
        if (declared != 0) return declared
        return if (n.extras.containsKey(EXTRA_ANSWER_INTENT)) TYPE_INCOMING else 0
    }

    private companion object {
        const val EXTRA_CALL_TYPE = "android.callType"
        const val EXTRA_ANSWER_INTENT = "android.answerIntent"
        const val TYPE_INCOMING = 1
        const val TYPE_ONGOING = 2

        /** Longer than any phone rings, short enough that a stuck loop is a nuisance not a disaster. */
        const val SAFETY_CAP_MS = 60_000L
        val SAFETY_TOKEN = Any()

        /**
         * How often to take the motor back in ring mode. The system reclaimed it
         * after 6.7 s on the call that exposed this; 2 s is comfortably inside
         * that and still cheap.
         */
        const val REASSERT_MS = 2_000L
        val REASSERT_TOKEN = Any()

        /**
         * How long after firing a second "incoming" is treated as the same call.
         * The updates seen in Task 2 arrived within 678 ms; 2 s covers that with
         * room to spare and is far shorter than any real gap between two calls.
         */
        const val DEDUPE_WINDOW_MS = 2_000L

        /** Uptime, not wall clock — the phone's clock jumps, and Task 2 caught it doing so. */
        @Volatile
        var lastFireAt = 0L

        /**
         * Which notification we started for. Static because Android may recreate
         * the service, and a recreated instance still needs to know it should
         * stop something. Fine for a test build; if it survives into the product
         * it belongs in [Store].
         */
        @Volatile
        var activeKey: String? = null
    }
}
