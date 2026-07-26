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
     * Off by default. In ring mode Android already plays a stock ringtone's own
     * haptics correctly, so firing as well would put two vibrations on top of
     * each other. It is switchable only because Task 2 needs to observe every
     * mode to fill in its results table.
     */
    var fireInRingMode: Boolean
        get() = prefs.getBoolean(KEY_RING_MODE, false)
        set(v) = prefs.edit().putBoolean(KEY_RING_MODE, v).apply()

    /** Loop the score while the phone rings, rather than playing it once. */
    var loopWhileRinging: Boolean
        get() = prefs.getBoolean(KEY_LOOP, true)
        set(v) = prefs.edit().putBoolean(KEY_LOOP, v).apply()

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
    }
}
