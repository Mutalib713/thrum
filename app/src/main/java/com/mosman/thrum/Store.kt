package com.mosman.thrum

import android.content.Context

/**
 * Everything the app remembers. SharedPreferences, no database — PROFILE.md §7.
 *
 * Kept deliberately thin: the formats it reads and writes live in [Event] and
 * [Score], which are testable without a phone. This class only moves strings.
 */
class Store(ctx: Context) {

    private val prefs = ctx.applicationContext
        .getSharedPreferences("thrum", Context.MODE_PRIVATE)

    /**
     * Whether to also play a score when the phone is in normal ring mode.
     *
     * **On by default since 2026-08-01, at Mutalib's request.** It was off
     * originally on the assumption that our vibration would fight the ringtone's
     * own — Task 2 disproved that: the last `RINGTONE` vibration wins, so ours
     * supersedes the system's cleanly.
     *
     * The honest caveat, surfaced in the UI rather than buried here: with the
     * ringer on, the user hears their *ringtone* and feels their *chosen track*.
     * Unless those are the same file, sound and vibration are playing different
     * music. That is why it stays a switch.
     */
    var fireInRingMode: Boolean
        get() = prefs.getBoolean(KEY_RING_MODE, true)
        set(v) = prefs.edit().putBoolean(KEY_RING_MODE, v).apply()

    /** Loop the score while the phone rings, rather than playing it once. */
    var loopWhileRinging: Boolean
        get() = prefs.getBoolean(KEY_LOOP, true)
        set(v) = prefs.edit().putBoolean(KEY_LOOP, v).apply()

    /**
     * The armed score — what a real call plays.
     *
     * Null means nothing is armed, and [NotifService] stands down so the call
     * gets Android's own buzz: PROFILE.md §4 item 3, no demo pattern. A
     * corrupt or unreadable value also reads as null: a score that cannot be
     * decoded is not an error worth crashing a notification listener for.
     */
    var armedScore: Score?
        get() = prefs.getString(KEY_SCORE, null)?.let { Score.decode(it) }
        set(value) = prefs.edit().apply {
            if (value == null) remove(KEY_SCORE) else putString(KEY_SCORE, value.encode())
        }.apply()

    /**
     * The file the armed score came from, so it can be analysed again.
     *
     * Without this, a restart left the score loaded but its levels gone, and the
     * tuning dials silently did nothing until the user picked a file again —
     * indistinguishable from dials that do not work.
     */
    var sourceUri: String?
        get() = prefs.getString(KEY_URI, null)
        set(value) = prefs.edit().apply {
            if (value == null) remove(KEY_URI) else putString(KEY_URI, value)
        }.apply()

    /**
     * How hard a beat hits, 0–255. The floor every hit is mapped up to.
     *
     * A setting rather than a constant because the plan said from the start that
     * this tuning is taste, not correctness — and taste belongs to the person
     * holding the phone, not to whoever last edited the analyser.
     *
     * **Read-only, and that is the fix rather than a tidiness.** Every tuning
     * key is written by [arm] alone, in the same `edit()` as the score it was
     * used to build, so the two cannot describe different rhythms. A setter here
     * is precisely how they could: it moved the stored value without rebuilding
     * anything, and the phone went on playing the old score — measured on
     * 2026-09-21, `body=400` stored against a score that reproduces exactly at
     * `body=100`. The compiler now enforces what a comment used to ask for.
     */
    val punch: Int
        get() = prefs.getInt(KEY_PUNCH, ScoreBuilder.MIN_FELT)

    /**
     * How far the vibration sits from the music, 0–100. **0 is closest.**
     *
     * A distance rather than an amount, and counted downward, because Mutalib
     * asked for it that way: *"0 when its close to the music and 255 when its
     * not"*. It replaces a dial that showed raw motor amplitudes — internal
     * numbers that meant nothing to anyone holding the phone, and whose ceiling
     * moved when the other dial moved, which is why "the max is 161" kept
     * needing explaining.
     *
     * At 0 the whole kit comes through. Turn it up and the detail falls away
     * until only the bare beat is left.
     *
     * Read-only for the same reason as [punch]: [arm] is the only writer.
     */
    val distance: Int
        get() = prefs.getInt(KEY_DISTANCE, 0)

    /**
     * How long each beat is driven, in milliseconds — the Body dial.
     *
     * **Why this exists as a setting.** Everything else about a score is about
     * *how hard* the motor is asked to work; this is about *how long*. Those are
     * not interchangeable, and the measurement says so: Android's own
     * incoming-call vibration holds 255 for a full second, where Thrum's longest
     * run at 255 was 100 ms — which is why the Pixel's buzz shakes a table and
     * Thrum's did not, at the same usage and the same 1.00 scale.
     *
     * Stored with Punch and Distance, so the rhythm the phone plays and the
     * settings beside it on screen always describe the same thing.
     *
     * Read-only for the same reason as [punch]: [arm] is the only writer, and
     * this key is the one whose split from its score was actually measured.
     */
    val body: Int
        get() = prefs.getInt(KEY_BODY, ScoreBuilder.BODY_MS)

    /**
     * Arm [score] and record everything that belongs with it, in one write.
     *
     * **Why this is one method and not a row of setters.** Task 8's
     * whole point is that what survives a restart is what the phone will play.
     * When the score, the file it came from, and the tuning were written
     * separately, a restart could land between them and restore a score with a
     * *different* file's name and URI — the screen claiming one rhythm while the
     * motor played another. A single `edit()` is atomic: either the whole bundle
     * is stored or none of it is, so that mismatch cannot exist.
     *
     * `uri` null means the score has no rebuildable source, which is the honest
     * state for a demo pattern — better than a stale URI pointing at some other
     * track. `putString(key, null)` removes the key.
     */
    fun arm(score: Score, uri: String?, punch: Int, distance: Int, body: Int) {
        prefs.edit()
            .putString(KEY_SCORE, score.encode())
            .putString(KEY_URI, uri)
            .putInt(KEY_PUNCH, punch)
            .putInt(KEY_DISTANCE, distance.coerceIn(0, 100))
            .putInt(KEY_BODY, body.coerceIn(ScoreBuilder.BODY_MIN_MS, ScoreBuilder.BODY_MAX_MS))
            .apply()
    }

    /**
     * Whether first launch has finished — the seven screens of Task 18.
     *
     * Defaults to false, which means the one install that already exists
     * (Mutalib's phone) sees the flow once more on update. That is not an
     * accident: walking those screens on a fresh install is exactly the proof
     * the task asks for, and the flow is short.
     */
    var onboarded: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDED, false)
        set(v) = prefs.edit().putBoolean(KEY_ONBOARDED, v).apply()

    /** When the music scan last ran — the "Last scanned" line Settings shows (Task 26). */
    var lastScanAtMs: Long
        get() = prefs.getLong(KEY_LAST_SCAN, 0L)
        set(v) = prefs.edit().putLong(KEY_LAST_SCAN, v).apply()

    /**
     * How haptics get made — Task 22's question, answered once:
     * `"background"` walks the whole library, `"as_played"` makes each song's
     * the first time it is played. Null means the question has not been asked,
     * which is what keeps it a first-scan-only screen.
     */
    var hapticsMode: String?
        get() = prefs.getString(KEY_HAPTICS_MODE, null)
        set(v) = prefs.edit().apply {
            if (v == null) remove(KEY_HAPTICS_MODE) else putString(KEY_HAPTICS_MODE, v)
        }.apply()

    /**
     * The songs waiting for a haptic, in the order the background walk will
     * make them — [HapticQueue.encodePending]'s line. Read-only: every change
     * goes through [editQueue].
     */
    val hapticQueuePending: List<String>
        get() = HapticQueue.decodePending(prefs.getString(KEY_HAPTIC_QUEUE, null))

    /**
     * Change the waiting list in one locked step: read it, change it, write it.
     *
     * **This is the 2026-10-04 crash fix.** The player (main thread) and the
     * background walk (a worker thread) both change the list. The walk used
     * to read it, spend eight seconds making a haptic, then write back the
     * copy it had read — erasing everything the player did in between,
     * including taking a finished song off. A finished song back on the list
     * broke the queue's own rule, and the next song tap closed the app.
     *
     * Now nobody holds a copy across a decode: [change] gets the list as it
     * is at this moment, rebuilt with [HapticQueue.restore] against [made]
     * (the library's made set, read just before), and the result is written
     * under the same lock. The lock is process-wide because [Store] objects
     * are created freely, and a lock per object would guard nothing.
     */
    fun editQueue(made: Set<String>, change: (HapticQueue) -> HapticQueue): HapticQueue =
        synchronized(QUEUE_LOCK) {
            val next = change(HapticQueue.restore(hapticQueuePending, made))
            prefs.edit().putString(KEY_HAPTIC_QUEUE, HapticQueue.encodePending(next.pending)).apply()
            next
        }

    /**
     * Songs the background walk has made since the queue was last empty —
     * the "2" in "2 of 5 done". The "5" is this plus what is still waiting,
     * worked out where it is shown, so the two numbers can never drift apart
     * the way a separately stored total did.
     */
    var hapticsDone: Int
        get() = prefs.getInt(KEY_HAPTICS_DONE, 0)
        set(v) = prefs.edit().putInt(KEY_HAPTICS_DONE, v).apply()

    fun events(): List<Event> = Event.decodeAll(prefs.getString(KEY_EVENTS, "") ?: "")

    fun addEvent(event: Event) {
        val updated = events() + event
        prefs.edit().putString(KEY_EVENTS, Event.encodeAll(updated)).apply()
    }

    fun clearEvents() = prefs.edit().remove(KEY_EVENTS).apply()

    private companion object {
        const val KEY_RING_MODE = "fire_in_ring_mode"
        const val KEY_LOOP = "loop_while_ringing"
        const val KEY_EVENTS = "events"
        const val KEY_SCORE = "armed_score"
        const val KEY_URI = "source_uri"
        const val KEY_PUNCH = "punch"
        const val KEY_DISTANCE = "distance"
        const val KEY_BODY = "body"
        const val KEY_ONBOARDED = "onboarded"
        const val KEY_LAST_SCAN = "last_scan_at"
        const val KEY_HAPTICS_MODE = "haptics_mode"
        const val KEY_HAPTIC_QUEUE = "haptic_queue_pending"
        const val KEY_HAPTICS_DONE = "haptics_done"

        /** See [editQueue]: one lock for the whole process, not one per [Store]. */
        val QUEUE_LOCK = Any()
    }
}
