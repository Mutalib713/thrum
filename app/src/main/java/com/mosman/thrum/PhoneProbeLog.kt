package com.mosman.thrum

import android.content.Context
import android.os.SystemClock
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Task 32's notebook: when Android said "ringing" (the Phone permission's
 * signal, heard by the test-build-only `PhoneProbe`), and when the call
 * notification reached [NotifService]. Lined up, they answer whether the
 * Phone permission is earlier or later than notification access, and
 * whether it arrives with Thrum closed.
 *
 * Test builds only: every write checks [BuildConfig.DEBUG], so a release
 * build never keeps this file. Nothing leaves the phone (Sacred Rule 3);
 * the lines are read on the Developer tools screen, or with
 * `adb logcat -s ThrumProbe`.
 */
object PhoneProbeLog {

    const val TAG = "ThrumProbe"
    private const val FILE = "phone-probe.txt"
    private const val KEEP = 200
    private const val PREFS = "thrum_probe"
    private const val KEY_VIBRATES = "vibrates_on_ring"

    /** Write one line: wall clock to the millisecond, then what happened. */
    fun note(ctx: Context, what: String) {
        if (!BuildConfig.DEBUG) return
        val line = "${clock.format(Date())} | $what"
        Log.i(TAG, line)
        runCatching {
            val file = File(ctx.filesDir, FILE)
            val kept = if (file.exists()) file.readLines().takeLast(KEEP - 1) else emptyList()
            file.writeText((kept + line).joinToString("\n", postfix = "\n"))
        }
    }

    /** Newest first. */
    fun read(ctx: Context): List<String> = runCatching {
        File(ctx.filesDir, FILE).takeIf { it.exists() }?.readLines()?.filter { it.isNotBlank() }?.reversed()
    }.getOrNull().orEmpty()

    fun clear(ctx: Context) {
        runCatching { File(ctx.filesDir, FILE).delete() }
    }

    /**
     * Whether the probe also plays the call song when it hears "ringing", to
     * answer question 4: can the Phone permission's signal alone keep the
     * vibration going for the whole ring? Off by default, so it never plays
     * on top of [NotifService] while the timing is measured.
     */
    fun vibrates(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_VIBRATES, false)

    fun setVibrates(ctx: Context, on: Boolean) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_VIBRATES, on).apply()
    }

    /**
     * How long Thrum's process had been alive when a line was written. A few
     * hundred milliseconds means Android started Thrum just to deliver the
     * signal: Thrum was closed, and still heard the call.
     */
    fun processAgeMs(): Long = SystemClock.uptimeMillis() - android.os.Process.getStartUptimeMillis()

    private val clock = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
}
