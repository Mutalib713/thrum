package com.mosman.thrum

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * What a row's ⋮ menu, or a selection, does — shared by My Haptics and
 * Music so the two tabs can't drift apart. Draw [RowActionsHost] once per
 * tab for the ringtone question.
 */
class RowActions(
    private val ctx: Context,
    private val scope: CoroutineScope,
    private val onTune: (TuneTarget) -> Unit,
) {
    /** The song the ringtone question is about, while it is showing. */
    var asking by mutableStateOf<Track?>(null)
        internal set

    /** Sent to Android's settings page to allow it; finished on the way back. */
    internal var waiting: Track? = null

    /**
     * Set as ringtone: the phone's ringtone and the call vibration, one song.
     * A haptic with no sound behind it can only be used for calls; one with
     * sound needs Android's permission first, asked for in words (sketch I).
     */
    fun setAsRingtone(track: Track) {
        when {
            ThrumFile.isImported(track.sourceUri) -> useForCalls(track)
            Ringtone.canChange(ctx) -> applyRingtone(track)
            else -> asking = track
        }
    }

    internal fun applyRingtone(track: Track) {
        scope.launch {
            val haptic = withHaptic(track) ?: return@launch
            val outcome = Ringtone.setFrom(ctx, track)
            // The vibration is set whatever happened to the sound: it never
            // needed a permission, and it is what Thrum is for.
            LibraryActions.useForCalls(ctx, haptic)
            when (outcome) {
                Ringtone.Outcome.Set -> {
                    // Hearing and feeling the same song happens with the
                    // ringer on, so Thrum vibrates then too.
                    Store(ctx).fireInRingMode = true
                    say(ctx, ctx.getString(R.string.ringtone_done, track.name))
                }
                Ringtone.Outcome.NoSound -> say(ctx, ctx.getString(R.string.ringtone_no_sound, track.name))
                Ringtone.Outcome.Refused, Ringtone.Outcome.NoPermission ->
                    say(ctx, ctx.getString(R.string.ringtone_refused, track.name))
            }
        }
    }

    /** Only the vibration: what a call plays, with the ringtone left alone. */
    fun useForCalls(track: Track) {
        scope.launch {
            val haptic = withHaptic(track) ?: return@launch
            LibraryActions.useForCalls(ctx, haptic)
            say(ctx, ctx.getString(R.string.calls_done, track.name))
        }
    }

    fun tune(track: Track) {
        scope.launch {
            val haptic = withHaptic(track) ?: return@launch
            onTune(TuneTarget.Song(track, haptic))
        }
    }

    fun share(tracks: List<Track>) {
        scope.launch {
            val haptics = tracks.mapNotNull { withHaptic(it) }
            if (haptics.isEmpty()) return@launch
            LibraryActions.share(
                ctx,
                haptics,
                ctx.resources.getQuantityString(R.plurals.share_title, haptics.size, haptics.size),
            )
        }
    }

    /**
     * The track's haptic, made first if a song has none yet — and said, since
     * making one takes a few seconds and a menu that seems to do nothing for
     * eight seconds reads as broken.
     */
    private suspend fun withHaptic(track: Track): Haptic? {
        val existing = LibraryDb.get(ctx).dao().let { dao ->
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { dao.hapticFor(track.sourceUri) }
        }?.toHaptic()
        if (existing != null) return existing
        if (!track.readable || ThrumFile.isImported(track.sourceUri)) {
            say(ctx, ctx.getString(R.string.player_unreadable))
            return null
        }
        say(ctx, ctx.getString(R.string.making_first, track.name))
        val made = HapticMaker.make(ctx, track)
        made.error?.let { say(ctx, it) }
        return made.haptic
    }
}

@Composable
fun rememberRowActions(onTune: (TuneTarget) -> Unit): RowActions {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    return remember { RowActions(ctx, scope, onTune) }
}

/**
 * The ringtone question, and what happens on the way back from Android's
 * page: allowed, the ringtone is set without asking again; not allowed,
 * Thrum says nothing changed, rather than setting it some later day the
 * permission happens to appear.
 */
@Composable
fun RowActionsHost(actions: RowActions) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                actions.waiting?.let { track ->
                    actions.waiting = null
                    if (Ringtone.canChange(ctx)) {
                        actions.applyRingtone(track)
                    } else {
                        say(ctx, ctx.getString(R.string.ringtone_not_allowed))
                    }
                }
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    actions.asking?.let { track ->
        RingtoneAskSheet(
            name = track.name,
            onOpenSettings = {
                actions.asking = null
                actions.waiting = track
                runCatching { ctx.startActivity(Ringtone.permissionIntent(ctx)) }
                    .onFailure { actions.waiting = null }
            },
            onVibrationOnly = {
                actions.asking = null
                actions.useForCalls(track)
            },
            onDismiss = { actions.asking = null },
        )
    }
}
