package com.mosman.thrum

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.SilenceMediaSource
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.media3.common.Player as Engine

/**
 * The in-app player: a whole song, heard and felt, from the first second to
 * the last. Task 21, drawn in screen 14.
 *
 * **The motor follows the audio.** Media3 (Mutalib's pick, PROFILE §7)
 * plays the sound, and every change to it, whoever made it, lands in
 * [follow]: Thrum's own buttons, the notification and lock screen
 * ([PlaybackService]), headphone buttons, headphones pulled out, a call
 * taking the audio away. Playing starts the haptic drive; anything else stops
 * the motor. The drive plays the score in pieces (R10) and every piece starts
 * from where the audio *actually is*, so drift is absorbed at each join
 * instead of adding up (Task 5).
 *
 * **Feel only is the same song played silently.** One clock for both modes,
 * so switching mid-song changes the volume and nothing else. An imported
 * haptic has no song at all, so it plays against silence of its own length.
 *
 * **The motor is always started and stopped through the app's context**
 * ([motor]): Android cancels a vibration only through the vibrator that
 * started it ([Haptics.vibrator]). The 2026-10-04 pause bug was exactly that.
 *
 * The haptic for a song is **made on first play**, about eight seconds once
 * per song, and kept in the library; a call later takes its first 45 seconds
 * via `Haptic.callWindow`.
 *
 * State is Compose state inside a plain object, so the Music tab, the mini
 * player, the player screen and the notification all show the same truth:
 * there is one player in the app.
 */
@SuppressLint("StaticFieldLeak") // It holds only the application context: see [motor].
object Player {

    data class Now(
        val track: Track,
        val hearAndFeel: Boolean,
        /** True while the haptic is being made. */
        val preparing: Boolean,
        /** What the user asked for: the song is playing, or would be but for a short interruption. */
        val playing: Boolean,
        val positionMs: Long,
        val durationMs: Long,
        /** The decoder's or the vibrator's own sentence, when something refused. */
        val error: String?,
    )

    /** What is playing, or null. Every surface that shows the player reads this. */
    var now by mutableStateOf<Now?>(null)
        private set

    /** The player screen is open; otherwise a mini player rides above the tabs. */
    var open by mutableStateOf(false)

    /** The haptic now playing, for the player screen's stats and "Use for calls". */
    var haptic by mutableStateOf<Haptic?>(null)
        private set

    /**
     * Nothing in the player may take the app down. A refusal anywhere in
     * here becomes a sentence on the player screen and the song stops. The
     * 2026-10-04 crash came through a scope like this one: an exception
     * thrown in a coroutine with nothing to catch it kills the process.
     */
    private val guard = CoroutineExceptionHandler { _, _ ->
        quiet()
        engine?.runCatching { stop(); clearMediaItems() }
        now = now?.copy(preparing = false, playing = false, error = motor?.getString(R.string.error_title))
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main + guard)

    /**
     * The app's context. The motor is started and stopped through it and
     * nothing else. Always the *application* context, which lives exactly as
     * long as the process, so holding it here leaks no screen — lint can't
     * tell which context it is and warns anyway.
     */
    @SuppressLint("StaticFieldLeak")
    private var motor: Context? = null
    private var engine: ExoPlayer? = null
    private var session: MediaSession? = null

    /** Held while a song is loaded: it keeps [PlaybackService], and so the notification, alive. */
    private var controller: ListenableFuture<MediaController>? = null

    private var driveJob: Job? = null
    private var tickerJob: Job? = null

    /**
     * The song being started. A second tap cancels the first start, so two
     * starts can never both reach the engine.
     */
    private var startJob: Job? = null

    /** The queue the player came from, for previous and next. */
    private var queue: List<Track> = emptyList()
    private var index = -1

    /** Hear and feel, or feel only, as last chosen: the next song keeps it. */
    private var hearAndFeelChoice = true

    /**
     * Play a track from the library. [queue] is the list it was tapped in, so
     * previous and next walk the same order the user was looking at.
     */
    fun play(ctx: Context, track: Track, queue: List<Track>, hearAndFeel: Boolean = hearAndFeelChoice) {
        this.queue = queue
        index = queue.indexOfFirst { it.sourceUri == track.sourceUri }
        start(ctx, track, hearAndFeel)
    }

    /**
     * A list's play button: the song plays where the user is, and the mini
     * player shows it. On the song already loaded it pauses or resumes.
     * Mutalib asked for this on 2026-10-04; the button used to open the whole
     * player screen every time.
     */
    fun playHere(ctx: Context, track: Track, queue: List<Track>) {
        if (now?.track?.sourceUri == track.sourceUri) togglePause(ctx) else play(ctx, track, queue)
    }

    /** A tap on a song's row: its page, playing. The song already loaded keeps its place. */
    fun openSong(ctx: Context, track: Track, queue: List<Track>) {
        if (now?.track?.sourceUri != track.sourceUri) play(ctx, track, queue)
        open = true
    }

    /**
     * §4 item 5: a song played before its turn jumps the queue. The player
     * makes its haptic right now, so in background mode it first takes the
     * song off the waiting list — the walk must not start the same eight-
     * second decode in parallel. In "as played" mode the list is empty and
     * this changes nothing.
     */
    private suspend fun claim(ctx: Context, uri: String) {
        val store = Store(ctx)
        if (store.hapticsMode != HapticsWorker.MODE_BACKGROUND) return
        val made = withContext(Dispatchers.IO) { LibraryDb.get(ctx).dao().madeTrackUris() }.toSet()
        store.editQueue(made) { it.claimed(uri) }
    }

    fun setHearAndFeel(ctx: Context, hearAndFeel: Boolean) {
        val current = now ?: return
        if (hearAndFeel && ThrumFile.isImported(current.track.sourceUri)) {
            // The song was never in the file, only the vibration was. Say so
            // rather than fail inside a player error.
            now = current.copy(error = ctx.getString(R.string.player_imported_no_audio))
            return
        }
        hearAndFeelChoice = hearAndFeel
        if (current.hearAndFeel == hearAndFeel) return
        now = current.copy(hearAndFeel = hearAndFeel, error = null)
        engine?.volume = if (hearAndFeel) 1f else 0f
    }

    fun togglePause(ctx: Context) {
        val current = now ?: return
        if (current.preparing) return
        if (current.playing) pause() else resume(ctx)
    }

    /** The motor first, at once, then the sound. */
    private fun pause() {
        quiet()
        engine?.pause()
        now = now?.copy(playing = false)
    }

    private fun resume(ctx: Context) {
        val e = engine ?: return
        if (now?.preparing != false) return
        if (e.playbackState == Engine.STATE_ENDED) e.seekTo(0)
        if (e.playbackState == Engine.STATE_IDLE) e.prepare()
        e.play()
        now = now?.copy(playing = true)
        connect(ctx)
    }

    /** Everything stops: motor, sound, notification. The screen empties. */
    fun stop(ctx: Context) {
        motor = motor ?: ctx.applicationContext
        startJob?.cancel()
        quiet()
        engine?.run {
            stop()
            clearMediaItems()
        }
        now = null
        haptic = null
        open = false
        index = -1
        queue = emptyList()
        disconnect()
    }

    /** For the rest of the app: Home's test call must not fight a running player. */
    fun stopAll() {
        motor?.let { stop(it) }
    }

    fun next(ctx: Context) = step(ctx, +1)

    fun previous(ctx: Context) = step(ctx, -1)

    /** Whether stepping [delta] songs is possible — the buttons' enabled state. */
    fun canStep(delta: Int): Boolean = (index + delta) in queue.indices

    private fun step(ctx: Context, delta: Int) {
        if (queue.isEmpty()) return
        val target = (index + delta).coerceIn(0, queue.size - 1)
        if (target == index) return
        // Where the player now is. Without this, next worked once and then
        // kept landing on the same song (2026-10-04).
        index = target
        start(ctx, queue[target], now?.hearAndFeel ?: hearAndFeelChoice)
    }

    // setMediaSource is Media3 "unstable API": it may change between
    // versions. Fine here because the version is pinned (1.11.1); re-check
    // this call when Media3 is upgraded.
    @OptIn(UnstableApi::class)
    private fun start(ctx: Context, track: Track, requestedHearAndFeel: Boolean) {
        val app = ctx.applicationContext
        motor = app
        // An imported haptic has no song behind it: the file never held one.
        val imported = ThrumFile.isImported(track.sourceUri)
        val hearAndFeel = requestedHearAndFeel && !imported
        startJob?.cancel()
        startJob = scope.launch {
            quiet()
            // The old song goes quiet at once; its notification stays until
            // the new song replaces it, rather than blinking out.
            engine?.pause()

            now = Now(
                track = track,
                hearAndFeel = hearAndFeel,
                preparing = true,
                playing = false,
                positionMs = 0,
                durationMs = track.durationMs,
                error = null,
            )
            haptic = null

            // The haptic, made once per song. About eight seconds, the first
            // time only — screen 13 says so in exactly those words. The maker
            // is shared with the background walk, so the two can never drift.
            val existing = withContext(Dispatchers.IO) {
                LibraryDb.get(app).dao().hapticFor(track.sourceUri)?.toHaptic()
            }
            val ready: Haptic
            if (existing != null) {
                ready = existing
            } else {
                claim(app, track.sourceUri)
                // Finished and saved even if the user taps another song
                // halfway: the eight seconds are already spent, and the next
                // play of this song should not spend them again.
                val made = withContext(NonCancellable) { HapticMaker.make(app, track) }
                ensureActive() // the user moved on: keep the haptic, drop the playback
                if (made.haptic == null) {
                    engine?.run {
                        stop()
                        clearMediaItems()
                    }
                    now = now?.copy(preparing = false, playing = false, error = made.error)
                    return@launch
                }
                ready = made.haptic
            }

            haptic = ready
            now = now?.copy(preparing = false, durationMs = ready.score.durationMs, playing = true)
            Store(app).addRecent(track.sourceUri)

            val e = engineFor(app)
            if (imported) {
                e.setMediaSource(silence(ready.score.durationMs))
            } else {
                e.setMediaItem(
                    MediaItem.Builder()
                        .setMediaId(track.sourceUri)
                        .setUri(track.sourceUri)
                        .setMediaMetadata(metadata())
                        .build(),
                )
            }
            e.volume = if (hearAndFeel) 1f else 0f
            e.prepare()
            e.play()
            connect(app)
        }
    }

    @OptIn(UnstableApi::class)
    private fun silence(durationMs: Long) =
        SilenceMediaSource.Factory().setDurationUs(durationMs * 1_000).createMediaSource()

    private val events = object : Engine.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Engine.STATE_ENDED) ended()
        }

        override fun onPlayerError(error: PlaybackException) {
            // The engine's own message ("Source error") means nothing to the
            // person holding the phone.
            quiet()
            now = now?.copy(preparing = false, playing = false, error = motor?.getString(R.string.player_unreadable))
        }

        override fun onEvents(player: Engine, events: Engine.Events) {
            if (events.containsAny(
                    Engine.EVENT_IS_PLAYING_CHANGED,
                    Engine.EVENT_PLAYBACK_STATE_CHANGED,
                    Engine.EVENT_PLAY_WHEN_READY_CHANGED,
                    Engine.EVENT_PLAYBACK_SUPPRESSION_REASON_CHANGED,
                )
            ) {
                follow()
            }
        }
    }

    /**
     * The motor follows the sound, whatever moved it. Only real playing
     * drives it: while the audio is starting, paused, or held for a call, the
     * motor is quiet. Sound first, vibration second (Task 5).
     */
    private fun follow() {
        val e = engine ?: return
        val current = now ?: return
        if (current.preparing) return
        val state = e.playbackState
        now = current.copy(
            playing = e.playWhenReady && state != Engine.STATE_ENDED && state != Engine.STATE_IDLE,
            positionMs = e.currentPosition.coerceAtLeast(0L),
        )
        if (e.isPlaying) drive() else quiet()
    }

    /**
     * The piece drive. Every piece starts from where the audio truly is, so a
     * late join or a slow piece corrects itself instead of compounding.
     */
    private fun drive() {
        if (driveJob?.isActive == true) return
        val ctx = motor ?: return
        val score = haptic?.score ?: return
        driveJob = scope.launch {
            while (isActive) {
                val e = engine ?: break
                if (!e.isPlaying) break
                val rest = score.from(e.currentPosition.coerceAtLeast(0L))
                if (rest.amplitudes.isEmpty()) break // the song played out
                val piece = rest.pieces(Haptics.MAX_STEPS).first()
                val failure = Haptics.play(ctx, piece)
                if (failure != null) {
                    // The sound plays on; the screen says why it can't be felt.
                    now = now?.copy(error = failure)
                    break
                }
                // The piece runs its own length; the next one re-reads the
                // real position and corrects any drift.
                delay(piece.durationMs)
            }
        }
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive) {
                engine?.let { e -> now = now?.copy(positionMs = e.currentPosition.coerceAtLeast(0L)) }
                delay(TICK_MS)
            }
        }
    }

    /** The motor stops now, through the context that started it. */
    private fun quiet() {
        driveJob?.cancel()
        driveJob = null
        tickerJob?.cancel()
        tickerJob = null
        motor?.let { Haptics.stop(it) }
    }

    /** The song finished: like any music player, the list carries on. */
    private fun ended() {
        quiet()
        now = now?.copy(playing = false, positionMs = now?.durationMs ?: 0)
        val ctx = motor ?: return
        if (canStep(+1)) step(ctx, +1)
    }

    /**
     * The Tune screen rebuilt this song's haptic. If it is the song playing,
     * the new rhythm takes over from where the song is.
     */
    fun retuned(ctx: Context, updated: Haptic) {
        val current = now ?: return
        if (current.track.sourceUri != updated.trackUri) return
        haptic = updated
        now = current.copy(durationMs = updated.score.durationMs)
        if (engine?.isPlaying == true) {
            quiet()
            drive()
        }
    }

    /** Whether this song is what calls play, for the "Calls" mark on its page. */
    fun isCallSong(ctx: Context): Boolean =
        now?.track?.sourceUri?.let { it == Store(ctx).sourceUri } == true

    /** "Use for calls": this song's first 45 seconds become what a call plays. */
    fun useForCalls(ctx: Context) {
        val ready = haptic ?: return
        Store(ctx).arm(
            ready.callWindow(),
            ready.trackUri,
            ready.punch,
            ready.distance,
            ready.bodyMs,
        )
    }

    // --- The engine, and what the rest of the phone sees of it. -------------

    private fun engineFor(ctx: Context): ExoPlayer = engine ?: ExoPlayer.Builder(ctx.applicationContext)
        // Music: Android pauses it for a call or another app's sound, and
        // the motor stops with it (see [follow]).
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            true,
        )
        .setHandleAudioBecomingNoisy(true)
        // Keeps the phone awake while a song plays with the screen off, so the
        // drive's next piece is not late (R11).
        .setWakeMode(C.WAKE_MODE_LOCAL)
        .build()
        .also {
            // A video's picture has nowhere to go; decoding it would only spend battery.
            it.trackSelectionParameters = it.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true)
                .build()
            it.addListener(events)
            engine = it
        }

    /** For [PlaybackService]: what the notification and lock screen control. */
    fun session(ctx: Context): MediaSession = session ?: MediaSession.Builder(ctx, Remote(engineFor(ctx)))
        .setSessionActivity(openApp(ctx))
        .build()
        .also { session = it }

    fun releaseSession() {
        session?.release()
        session = null
        disconnect()
    }

    /** Whether a song is playing, for [PlaybackService] when Thrum is swiped away. */
    fun isPlaying(): Boolean = now?.playing == true

    /** Tapping the notification opens Thrum on the player. */
    const val EXTRA_OPEN_PLAYER = "com.mosman.thrum.OPEN_PLAYER"

    private fun openApp(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx,
        0,
        Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_OPEN_PLAYER, true),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun connect(ctx: Context) {
        if (controller != null) return
        controller = MediaController.Builder(ctx, SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java)))
            .buildAsync()
    }

    private fun disconnect() {
        controller?.let { MediaController.releaseFuture(it) }
        controller = null
    }

    private fun metadata(): MediaMetadata {
        val track = now?.track ?: return MediaMetadata.EMPTY
        return MediaMetadata.Builder()
            .setTitle(track.name)
            .setArtist(track.artist.ifEmpty { MyHaptics.kindLabel(track.kind) })
            .build()
    }

    /**
     * What the notification, the lock screen and headphone buttons talk to.
     * Their play, pause, next and previous take the same paths as Thrum's own
     * buttons, so the motor is never left behind.
     */
    // ForwardingPlayer is Media3 "unstable API" too; same reasoning as start().
    @OptIn(UnstableApi::class)
    private class Remote(engine: ExoPlayer) : ForwardingPlayer(engine) {
        override fun getAvailableCommands(): Engine.Commands = super.getAvailableCommands().buildUpon()
            .addAll(
                Engine.COMMAND_SEEK_TO_NEXT,
                Engine.COMMAND_SEEK_TO_PREVIOUS,
                Engine.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                Engine.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            )
            // No dragging through the song from the notification: the motor
            // plays in long pieces and would lag until the next join.
            .remove(Engine.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
            .build()

        override fun isCommandAvailable(command: Int): Boolean = availableCommands.contains(command)

        override fun play() {
            motor?.let { resume(it) }
        }

        override fun pause() = Player.pause()

        override fun seekToNext() = skip(+1)

        override fun seekToNextMediaItem() = skip(+1)

        override fun seekToPrevious() = skip(-1)

        override fun seekToPreviousMediaItem() = skip(-1)

        override fun hasNextMediaItem(): Boolean = canStep(+1)

        override fun hasPreviousMediaItem(): Boolean = canStep(-1)

        override fun getMediaMetadata(): MediaMetadata = metadata()

        private fun skip(delta: Int) {
            motor?.let { step(it, delta) }
        }
    }

    private const val TICK_MS = 500L
}
