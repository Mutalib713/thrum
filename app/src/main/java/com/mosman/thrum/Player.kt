package com.mosman.thrum

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The in-app player: a whole song, heard and felt, from the first second to
 * the last. Task 21, drawn in screen 14.
 *
 * **How the haptic stays in step.** A whole song's score does not fit in one
 * waveform (R10), so the drive plays it in pieces — and it re-syncs at every
 * join the way Task 5 taught: the next piece starts from wherever the audio
 * *actually is* (`MediaPlayer.getCurrentPosition`), not from where arithmetic
 * assumes it is. Small timing drift therefore cannot accumulate; each join
 * absorbs it. The pure side of this — `Score.pieces` cutting at silence, the
 * re-sync tiling the remainder exactly once — is tested on the PC; what needs
 * the phone is whether Android keeps the motor going with the screen off
 * (R11), which Task 16's spike measures through this very player.
 *
 * **Feel only** has no audio to chase, so the clock takes over and the same
 * piece loop runs against `SystemClock` instead.
 *
 * The haptic for a song is **made on first play** — the "one at a time, as I
 * play them" mode of §4 item 5, about eight seconds once per song. It is the
 * whole-track score (§8), stored in the library; a call later takes its first
 * 45 seconds via `Haptic.callWindow`.
 *
 * State is Compose state inside a plain object, so the Music tab, the mini
 * player and the player screen all observe the same truth — there is one
 * player in the app, and everything that shows it shows *this* one.
 */
object Player {

    data class Now(
        val track: Track,
        val hearAndFeel: Boolean,
        /** True while the haptic is being made or the audio is still buffering. */
        val preparing: Boolean,
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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var appCtx: Context? = null
    private var mediaPlayer: MediaPlayer? = null
    private var driveJob: Job? = null
    private var tickerJob: Job? = null

    /** The queue the player came from, for previous and next. */
    private var queue: List<Track> = emptyList()
    private var index = -1

    /** Where "feel only" was when it paused, so resuming does not jump. */
    private var feelOnlyElapsedMs = 0L

    /** When the feel-only clock was last started, so elapsed advances while playing. */
    private var feelStartedAtMs = 0L

    /**
     * Play a track from the library. [queue] is the list it was tapped in, so
     * previous and next walk the same order the user was looking at.
     */
    fun play(ctx: Context, track: Track, queue: List<Track>, hearAndFeel: Boolean = true) {
        this.queue = queue
        index = queue.indexOfFirst { it.sourceUri == track.sourceUri }
        start(ctx, track, hearAndFeel)
        jumpAhead(ctx, track.sourceUri)
    }

    /**
     * §4 item 5, in the background mode: a song played jumps the queue — to
     * the **front**, ahead of everything waiting, because pressing play is a
     * stronger signal than anything the scan learned. In "as played" mode
     * there is no queue to jump; the haptic is made right here.
     */
    private fun jumpAhead(ctx: Context, uri: String) {
        if (Store(ctx).hapticsMode != HapticsWorker.MODE_BACKGROUND) return
        scope.launch {
            val store = Store(ctx)
            val made = withContext(Dispatchers.IO) {
                LibraryDb.get(ctx).dao().madeTrackUris()
            }.toSet()
            val moved = HapticQueue(store.hapticQueuePending, made).played(uri)
            if (moved.pending != store.hapticQueuePending) {
                store.hapticQueuePending = moved.pending
                HapticsWorker.ensureEnqueued(ctx)
            }
        }
    }

    /**
     * A haptic now exists (or the file was refused) — the walk must not remake
     * or retry it. Settled after every make attempt, success or not.
     */
    private fun settled(ctx: Context, uri: String) {
        if (Store(ctx).hapticsMode != HapticsWorker.MODE_BACKGROUND) return
        scope.launch {
            val store = Store(ctx)
            if (uri in store.hapticQueuePending) {
                store.hapticQueuePending = store.hapticQueuePending - uri
            }
        }
    }

    fun setHearAndFeel(ctx: Context, hearAndFeel: Boolean) {
        val current = now ?: return
        if (hearAndFeel && ThrumFile.isImported(current.track.sourceUri)) {
            // The song was never in the file — only the vibration is. Say so
            // rather than fail inside a player error.
            now = current.copy(error = ctx.getString(R.string.player_imported_no_audio))
            return
        }
        if (current.hearAndFeel == hearAndFeel) return
        now = current.copy(hearAndFeel = hearAndFeel, error = null)
        val playing = current.playing
        if (playing) {
            // Restart the drive from where things actually are: audio paused
            // for feel-only, clock seeded from the audio position for the
            // way back. The piece loop absorbs the switch at its next join.
            scope.launch {
                stopDrive()
                if (hearAndFeel) {
                    mediaPlayer?.start()
                } else {
                    mediaPlayer?.pause()
                    feelOnlyElapsedMs = audioPositionMs()
                }
                Haptics.stop(ctx)
                currentHaptic()?.let { drive(it.score, hearAndFeel, resume = true) }
            }
        }
    }

    fun togglePause(ctx: Context) {
        val current = now ?: return
        if (current.preparing) return
        if (current.playing) {
            now = current.copy(playing = false)
            scope.launch {
                stopDrive()
                Haptics.stop(ctx)
                mediaPlayer?.pause()
                if (!current.hearAndFeel) feelOnlyElapsedMs = feelClockMs()
            }
        } else {
            now = current.copy(playing = true)
            scope.launch {
                mediaPlayer?.start()
                currentHaptic()?.let { drive(it.score, current.hearAndFeel, resume = true) }
            }
        }
    }

    /** Everything stops: motor, audio, drive. The screen empties. */
    fun stop(ctx: Context) {
        scope.launch {
            stopDrive()
            Haptics.stop(ctx)
            mediaPlayer?.release()
            mediaPlayer = null
            now = null
            haptic = null
            open = false
            index = -1
        }
    }

    /** For the rest of the app: Home's test call must not fight a running player. */
    fun stopAll() {
        appCtx?.let { stop(it) }
    }

    fun next(ctx: Context) = step(ctx, +1)

    fun previous(ctx: Context) = step(ctx, -1)

    /** Whether stepping [delta] songs is possible — the buttons' enabled state. */
    fun canStep(delta: Int): Boolean = (index + delta) in queue.indices

    private fun step(ctx: Context, delta: Int) {
        if (queue.isEmpty()) return
        val target = (index + delta).coerceIn(0, queue.size - 1)
        if (target == index) return
        start(ctx, queue[target], now?.hearAndFeel ?: true)
    }

    private fun start(ctx: Context, track: Track, hearAndFeel: Boolean) {
        appCtx = ctx.applicationContext
        scope.launch {
            stopDrive()
            Haptics.stop(ctx)
            mediaPlayer?.release()
            mediaPlayer = null
            feelOnlyElapsedMs = 0L

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
                LibraryDb.get(ctx).dao().hapticFor(track.sourceUri)?.toHaptic()
            }
            val ready: Haptic
            if (existing != null) {
                ready = existing
            } else {
                val made = HapticMaker.make(ctx, track)
                if (now?.track?.sourceUri != track.sourceUri) return@launch // user moved on
                settled(ctx, track.sourceUri)
                if (made.haptic == null) {
                    now = now?.copy(preparing = false, playing = false, error = made.error)
                    return@launch
                }
                ready = made.haptic
            }

            haptic = ready
            now = now?.copy(
                preparing = false,
                durationMs = ready.score.durationMs,
                playing = true,
            )

            if (hearAndFeel) {
                prepareAudio(ctx, track.sourceUri)
            } else {
                drive(ready.score, hearAndFeel = false, resume = false)
            }
        }
    }

    /**
     * The piece drive. Every iteration starts from where the audio truly is,
     * so a late join, a pause, or a slow join between pieces corrects itself
     * instead of compounding.
     */
    private suspend fun drive(score: Score, hearAndFeel: Boolean, resume: Boolean) {
        val ctx = appCtx ?: return
        if (hearAndFeel) {
            // Task 5's rule: sound first, vibration second. A player buffers;
            // currentPosition only advances once audio is genuinely running.
            val giveUp = SystemClock.elapsedRealtime() + START_WAIT_MS
            while (audioPositionMs() == 0L && SystemClock.elapsedRealtime() < giveUp) {
                delay(2)
            }
        } else {
            // Feel only has no audio to chase: the clock is the witness. On a
            // fresh start it begins at zero; on a resume it begins where the
            // pause left it.
            if (!resume) feelOnlyElapsedMs = 0L
            feelStartedAtMs = SystemClock.elapsedRealtime()
        }
        stopDrive()
        driveJob = scope.launch {
            while (true) {
                if (now?.playing != true) break
                val elapsed = if (hearAndFeel) audioPositionMs() else feelClockMs()
                val rest = score.from(elapsed)
                if (rest.amplitudes.isEmpty()) break // the song played out
                val piece = rest.pieces(Haptics.MAX_STEPS).first()
                Haptics.play(ctx, piece)?.let { failure ->
                    now = now?.copy(playing = false, error = failure)
                    break
                }
                // The piece runs its own length; the next iteration then
                // re-reads the real position and corrects any drift.
                delay(piece.durationMs)
            }
        }
        tickerJob = scope.launch {
            while (now != null && now?.playing == true) {
                now = now?.copy(
                    positionMs = if (hearAndFeel) audioPositionMs() else feelClockMs(),
                )
                delay(TICK_MS)
            }
        }
    }

    private suspend fun stopDrive() {
        driveJob?.cancelAndJoin()
        driveJob = null
        tickerJob?.cancel()
        tickerJob = null
    }

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

    private fun prepareAudio(ctx: Context, sourceUri: String) {
        val mp = MediaPlayer()
        mediaPlayer = mp
        mp.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build(),
        )
        mp.setOnCompletionListener {
            now = now?.copy(playing = false, positionMs = now?.durationMs ?: 0)
            Haptics.stop(ctx)
        }
        mp.setOnPreparedListener { ready ->
            if (now?.playing == true) {
                ready.start()
                scope.launch {
                    currentHaptic()?.let { drive(it.score, hearAndFeel = true, resume = false) }
                }
            }
        }
        runCatching {
            mp.setDataSource(ctx, Uri.parse(sourceUri))
            mp.prepareAsync()
        }.onFailure {
            now = now?.copy(preparing = false, playing = false, error = it.message)
        }
    }

    private fun currentHaptic(): Haptic? = haptic

    private fun audioPositionMs(): Long =
        runCatching { mediaPlayer?.currentPosition?.toLong() ?: 0L }.getOrDefault(0L)

    /** Feel-only's clock: the pause position plus the time since the last resume. */
    private fun feelClockMs(): Long =
        feelOnlyElapsedMs + (SystemClock.elapsedRealtime() - feelStartedAtMs)

    private const val START_WAIT_MS = 2_000L
    private const val TICK_MS = 500L
}
