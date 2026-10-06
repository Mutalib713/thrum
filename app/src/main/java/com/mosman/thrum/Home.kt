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

    /**
     * What Thrum did on one call, in the terms Phone check's "Your last
     * calls" says it. Moved there from the old diagnostics page on
     * 2026-10-06, so anyone can check that Thrum is working, not only a
     * test build.
     */
    enum class CallOutcome {
        /** The song's vibration started. */
        PLAYED,

        /** Thrum started it, but the phone was on Silent, where Android lets nothing vibrate. */
        PLAYED_ON_SILENT,

        /** The ringer was on and "Also when the ringer is on" was off. */
        RINGER_ON,

        /** No song for calls, so the phone's own buzz played. */
        NO_SONG,

        /** This phone's motor has one strength and cannot play a rhythm. */
        PHONE_CANNOT,

        /** Android refused the vibration. */
        FAILED,
    }

    data class CallLine(val atMs: Long, val outcome: CallOutcome, val latencyMs: Long)

    /**
     * The newest [limit] calls, newest first, one line per call.
     *
     * Everything that is not a call is left out: the listener connecting,
     * a call ending, a dialer's duplicate notification for a call already
     * playing, and anything an older test build wrote here. A line this
     * does not recognise is dropped rather than guessed at, because a wrong
     * "couldn't play" would send someone hunting for a fault that isn't there.
     */
    fun recentCalls(events: List<Event>, limit: Int = RECENT_CALLS): List<CallLine> =
        events.mapNotNull { e ->
            val outcome = when (e.kind) {
                Event.Kind.FIRED -> if (e.ringer == "silent") CallOutcome.PLAYED_ON_SILENT else CallOutcome.PLAYED
                Event.Kind.SKIPPED -> when {
                    e.note.startsWith(NOTE_RINGER_ON) -> CallOutcome.RINGER_ON
                    e.note.startsWith(NOTE_NO_SONG) -> CallOutcome.NO_SONG
                    e.note.startsWith(NOTE_PHONE_CANNOT) -> CallOutcome.PHONE_CANNOT
                    e.note.startsWith(NOTE_FAILED) -> CallOutcome.FAILED
                    else -> null
                }
                else -> null
            }
            outcome?.let { CallLine(e.at, it, e.latencyMs) }
        }
            .sortedByDescending { it.atMs }
            .take(limit)

    /**
     * The notes the call listener writes, shared so the listener and
     * [recentCalls] cannot drift apart. The first three are the words it
     * has always written, so calls recorded before this change still read.
     */
    const val NOTE_RINGER_ON = "ring mode"
    const val NOTE_NO_SONG = "no song for calls"
    const val NOTE_PHONE_CANNOT = "motor cannot vary strength"
    const val NOTE_FAILED = "couldn't play: "
    const val NOTE_DUPLICATE = "duplicate notification, already playing"

    /** Enough to see a pattern, short enough to read at a glance. */
    const val RECENT_CALLS = 8

    /** "Sun 20 Sep at 20:27" — the design's shape, in the phone's locale. */
    fun callWords(atMs: Long, locale: Locale = Locale.getDefault()): String =
        SimpleDateFormat("EEE d MMM 'at' HH:mm", locale).format(Date(atMs))
}
