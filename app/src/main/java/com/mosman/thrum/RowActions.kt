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
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
    /** The ringtone question, while it is showing. */
    var asking by mutableStateOf<RingtoneAsk?>(null)
        internal set

    /**
     * The app, not the screen: a ringtone takes a few seconds to make, and
     * leaving the player or a tab in that time used to stop it halfway with
     * no word said.
     */
    private val app: Context = ctx.applicationContext

    /**
     * Where ringtone work runs: not tied to any screen, so it finishes and
     * says how it went even after the screen that asked has closed. A failure
     * is said in words rather than closing the app.
     */
    private val lasting = CoroutineScope(
        SupervisorJob() + Dispatchers.Main + CoroutineExceptionHandler { _, _ -> say(app, app.getString(R.string.error_title)) },
    )

    /**
     * Sent to Android's settings page to allow it; finished on the way back.
     * Written to the app's storage, because Android may close Thrum while
     * the user is on that page ([RingtoneRules.encodePending]).
     */
    internal fun rememberPending(ask: RingtoneAsk) {
        Store(app).pendingRingtone = RingtoneRules.encodePending(ask, System.currentTimeMillis())
    }

    internal fun forgetPending() {
        Store(app).pendingRingtone = null
    }

    /** The ask waiting on Android's page, taken so only one screen finishes it. */
    internal fun takePending(): RingtoneAsk? {
        val store = Store(app)
        val text = store.pendingRingtone ?: return null
        store.pendingRingtone = null
        return RingtoneRules.decodePending(text, System.currentTimeMillis())
    }

    /**
     * Set as ringtone: the phone rings with the song and Thrum vibrates to
     * it. Since 2026-10-07 the one way to choose what calls play (Task 31),
     * from Home, the player and every ⋮ menu. The ringtone needs Android's
     * permission first, asked for in words (sketch I); "Just the vibration"
     * stays as the way out.
     *
     * @param armed true when calls already vibrate to [track] (Home arms its
     *   own pick as it reads it), so only the ringtone is left to set.
     * @param previousSong the song calls vibrated to before this one. Read
     *   now, before anything is armed; a caller that has already armed
     *   [track] passes the song it replaced.
     */
    fun setAsRingtone(track: Track, armed: Boolean = false, previousSong: String? = Store(ctx).sourceUri) {
        val ask = RingtoneAsk(track, armed, previousSong)
        when {
            // No sound inside: the vibration is all there is to set.
            ThrumFile.isImported(track.sourceUri) -> vibrationOnly(ask)
            Ringtone.canChange(ctx) -> applyRingtone(ask)
            else -> asking = ask
        }
    }

    internal fun applyRingtone(ask: RingtoneAsk) {
        lasting.launch {
            if (!ask.armed) {
                val haptic = withHaptic(ask.track) ?: return@launch
                LibraryActions.useForCalls(app, haptic)
            }
            say(app, app.getString(R.string.ringtone_making, ask.track.name))
            // The vibration is set whatever happens to the sound: it never
            // needed a permission. Without the sound, Thrum plays on vibrate
            // only, so the user never hears one song and feels another.
            when (Ringtone.setFrom(app, ask.track, ask.previousSong)) {
                Ringtone.Outcome.Set -> say(app, app.getString(R.string.ringtone_done, ask.track.name))
                Ringtone.Outcome.NoSound -> say(app, app.getString(R.string.ringtone_no_sound, ask.track.name))
                Ringtone.Outcome.Refused, Ringtone.Outcome.NoPermission ->
                    say(app, app.getString(R.string.ringtone_refused, ask.track.name))
            }
        }
    }

    /** Only the vibration: calls vibrate to it on vibrate, and the ringtone stays as it is. */
    fun vibrationOnly(ask: RingtoneAsk) {
        lasting.launch {
            if (!ask.armed) {
                val haptic = withHaptic(ask.track) ?: return@launch
                LibraryActions.useForCalls(app, haptic)
            }
            say(app, app.getString(R.string.calls_done, ask.track.name))
        }
    }

    /** For an imported haptic's menu item, "Vibrate for calls". */
    fun useForCalls(track: Track) = vibrationOnly(RingtoneAsk(track, armed = false, previousSong = null))

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
        val existing = LibraryDb.get(app).dao().let { dao ->
            kotlinx.coroutines.withContext(Dispatchers.IO) { dao.hapticFor(track.sourceUri) }
        }?.toHaptic()
        if (existing != null) return existing
        if (!track.readable || ThrumFile.isImported(track.sourceUri)) {
            say(app, app.getString(R.string.player_unreadable))
            return null
        }
        say(app, app.getString(R.string.making_first, track.name))
        val made = HapticMaker.make(app, track)
        made.error?.let { say(app, it) }
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
                // Whichever screen comes back first finishes it, even one
                // that didn't ask, or a fresh Thrum after Android closed it.
                actions.takePending()?.let { ask ->
                    if (Ringtone.canChange(ctx)) {
                        actions.applyRingtone(ask)
                    } else {
                        say(ctx, ctx.getString(R.string.ringtone_not_allowed))
                    }
                }
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    actions.asking?.let { ask ->
        RingtoneAskSheet(
            name = ask.track.name,
            onOpenSettings = {
                actions.asking = null
                actions.rememberPending(ask)
                runCatching { ctx.startActivity(Ringtone.permissionIntent(ctx)) }
                    .onFailure { actions.forgetPending() }
            },
            onVibrationOnly = {
                actions.asking = null
                actions.vibrationOnly(ask)
            },
            onDismiss = { actions.asking = null },
        )
    }
}
