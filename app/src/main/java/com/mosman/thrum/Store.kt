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
     * Null means nothing is armed, and [NotifService] falls back to the demo
     * pattern rather than staying silent. A corrupt or unreadable value also
     * reads as null: a score that cannot be decoded is not an error worth
     * crashing a notification listener for.
     */
    var armedScore: Score?
        get() = prefs.getString(KEY_SCORE, null)?.let { Score.decode(it) }
        set(value) = prefs.edit().apply {
            if (value == null) remove(KEY_SCORE) else putString(KEY_SCORE, value.encode())
        }.apply()

    /**
     * How hard a beat hits, 0–255. The floor every hit is mapped up to.
     *
     * A setting rather than a constant because the plan said from the start that
     * this tuning is taste, not correctness — and taste belongs to the person
     * holding the phone, not to whoever last edited the analyser.
     */
    var punch: Int
        get() = prefs.getInt(KEY_PUNCH, ScoreBuilder.MIN_FELT)
        set(v) = prefs.edit().putInt(KEY_PUNCH, v).apply()

    /** How present the music is between the beats. Kept below [punch] by the UI. */
    var texture: Int
        get() = prefs.getInt(KEY_TEXTURE, ScoreBuilder.BODY_CEILING)
        set(v) = prefs.edit().putInt(KEY_TEXTURE, v).apply()

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
        const val KEY_PUNCH = "punch"
        const val KEY_TEXTURE = "texture"
    }
}
