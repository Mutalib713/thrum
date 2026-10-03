package com.mosman.thrum

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The arithmetic behind Home's "last call" line. Task 19.
 *
 * Pure Kotlin, like [Setup]: the words on the screen are formatting, but
 * *which* event is the last call is a decision, and decisions live where the
 * PC can check them.
 */
object Home {

    /**
     * The newest call Thrum actually played for, or null.
     *
     * Only `FIRED` counts. A `SKIPPED` entry means the app *chose* not to
     * play — silent mode, no song, a duplicate — and reporting a call the app
     * declined would be a surface reporting an intention again. `STOPPED` and
     * `CAPPED` ride behind a `FIRED` for the same call.
     */
    fun lastCall(events: List<Event>): Event? =
        events.filter { it.kind == Event.Kind.FIRED }.maxByOrNull { it.at }

    /**
     * Latency in the words the line uses: one decimal under ten seconds
     * ("0.3 s", the design's own example), whole seconds above — nobody needs
     * "10.0 s" spelled out, and past ten the half-second is the honest digit.
     */
    fun latencyWords(latencyMs: Long): String = when {
        latencyMs < 10_000 -> "%.1f s".format(latencyMs / 1000.0)
        else -> "${latencyMs / 1000} s"
    }

    /** "Sun 20 Sep at 20:27" — the design's shape, in the phone's locale. */
    fun callWords(atMs: Long, locale: Locale = Locale.getDefault()): String =
        SimpleDateFormat("EEE d MMM 'at' HH:mm", locale).format(Date(atMs))
}
