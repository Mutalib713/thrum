package com.mosman.thrum

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * The only part of the app that talks to the vibration motor. Kept thin on
 * purpose: none of this can be tested without a physical phone, so as little
 * logic as possible lives here. Everything decidable belongs in [Score].
 */
object Haptics {

    /**
     * What this phone's motor can actually do.
     *
     * [amplitudeControl] is the one that matters. Without it the motor has a
     * single speed and can only buzz, so a score's shape is thrown away and
     * Thrum has nothing to offer. PROFILE.md Sacred Rule 2: say so honestly.
     */
    data class Capability(
        val hasVibrator: Boolean,
        val amplitudeControl: Boolean,
        val richPrimitives: Boolean,
    ) {
        val usable: Boolean get() = hasVibrator && amplitudeControl
    }

    fun vibrator(ctx: Context): Vibrator =
        (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator

    fun capability(ctx: Context): Capability {
        val v = vibrator(ctx)
        // Support for the sharper canned effects is a reasonable proxy for a
        // good actuator. Not required — it's reported for information only,
        // since v1 drives amplitudes directly rather than using primitives.
        val rich = runCatching {
            v.areAllPrimitivesSupported(
                VibrationEffect.Composition.PRIMITIVE_CLICK,
                VibrationEffect.Composition.PRIMITIVE_TICK,
            )
        }.getOrDefault(false)
        return Capability(
            hasVibrator = v.hasVibrator(),
            amplitudeControl = v.hasAmplitudeControl(),
            richPrimitives = rich,
        )
    }

    /**
     * Play a score, once or on a loop.
     *
     * Usage is declared as a ringtone rather than plain feedback, because that
     * is what it is — and because Task 2 needs the same call path the real
     * feature will use, not a friendlier one that behaves differently during
     * an incoming call.
     *
     * A looping score runs until [stop] is called. Every caller that loops must
     * also arm a safety cap: this runs on Mutalib's daily phone, and a loop that
     * outlives its call would leave the phone buzzing indefinitely.
     */
    /**
     * @return null when the vibration was accepted, or a plain-language reason
     *   when it was not.
     *
     * Returning the failure rather than throwing exists for two reasons. It is
     * how **R8** gets answered — a four-minute track is about 12,000 steps, and
     * whether `createWaveform` accepts that is a question only a device can
     * settle. And [NotifService] plays scores from a notification callback,
     * where an uncaught throw would take down the listener and quietly end the
     * app's whole reason for existing.
     */
    fun play(ctx: Context, score: Score, loop: Boolean = false): String? {
        if (score.amplitudes.isEmpty()) return "That score has no steps in it."
        return try {
            val effect = VibrationEffect.createWaveform(
                score.timings(),
                score.amplitudes.toIntArray(),
                if (loop) REPEAT_FROM_START else NO_REPEAT,
            )
            val v = vibrator(ctx)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_RINGTONE))
            } else {
                v.vibrate(
                    effect,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
            }
            null
        } catch (e: Exception) {
            "The vibrator refused ${score.amplitudes.size} steps: " +
                "${e.javaClass.simpleName}${e.message?.let { " — $it" } ?: ""}"
        }
    }

    fun stop(ctx: Context) = vibrator(ctx).cancel()

    /** Shown on screen during Milestone 0 testing, so the phone's state is never a guess. */
    fun ringerMode(ctx: Context): String {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return when (am.ringerMode) {
            AudioManager.RINGER_MODE_SILENT -> "silent"
            AudioManager.RINGER_MODE_VIBRATE -> "vibrate"
            AudioManager.RINGER_MODE_NORMAL -> "ring"
            else -> "unknown"
        }
    }

    private const val NO_REPEAT = -1
    private const val REPEAT_FROM_START = 0
}
