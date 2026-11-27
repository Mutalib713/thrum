package com.mosman.thrum

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager

/**
 * Task 32, test builds only (this file lives in `src/debug`, so the real
 * app never has it): Android's "the phone is ringing" signal, which needs
 * only the Phone permission (`READ_PHONE_STATE`) rather than notification
 * access, the permission Android locks for apps sent as a file.
 *
 * It writes down when the signal came and how long Thrum had been running,
 * next to [NotifService]'s line for the same call ([PhoneProbeLog]). With
 * "Probe vibrates" on in Developer tools, it also plays the call song from
 * here, to see whether this signal alone can carry a whole ring.
 *
 * The real app doesn't change: [NotifService] still catches calls.
 */
class PhoneProbe : BroadcastReceiver() {

    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: "unknown"
        val ringer = runCatching { Haptics.ringerMode(ctx) }.getOrDefault("?")
        PhoneProbeLog.note(
            ctx,
            "PHONE $state · ringer $ringer · Thrum running for ${PhoneProbeLog.processAgeMs()} ms",
        )
        if (!PhoneProbeLog.vibrates(ctx)) return

        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                val armed = Store(ctx).armedScore
                if (armed == null) {
                    PhoneProbeLog.note(ctx, "probe: nothing armed, no vibration")
                    return
                }
                val failure = Haptics.play(ctx.applicationContext, armed, loop = true)
                PhoneProbeLog.note(ctx, "probe: ${failure?.let { "vibration failed: $it" } ?: "playing ${armed.sourceName}, looping"}")
            }
            // Answered or ended: stop, or the motor runs through the call.
            else -> {
                Haptics.stop(ctx.applicationContext)
                PhoneProbeLog.note(ctx, "probe: stopped")
            }
        }
    }
}
