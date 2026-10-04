package com.mosman.thrum

import android.content.Context
import android.os.SystemClock
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The background walk: makes a haptic for each song waiting in the queue,
 * head first, until the queue is empty. Task 22.
 *
 * **One run makes songs for up to [RUN_BUDGET_MS], then hands over.** A
 * library walk is tens of minutes of work (about eight seconds a song, R12's
 * arithmetic), and WorkManager only lets a background job run past ten
 * minutes as a **foreground service**, which would mean a permanent
 * notification for work the design describes as quiet ("Thrum works through
 * them while you use your phone"). Four minutes is well inside the limit;
 * when the budget runs out with songs still waiting, the run appends its own
 * successor ([continueWalk]) and finishes. Android may still pause the walk
 * to save battery — R12, accepted at Mutalib's choice of background mode —
 * and the pause costs nothing: the queue is in storage, so the next run
 * picks up exactly where this one stopped.
 *
 * **Two bugs this replaced, both seen on the emulator on 2026-10-04.**
 * The walk used to re-schedule itself after every song with `KEEP`, which
 * means "do nothing if this work is already running" — and it asked while
 * it was still running, so the request was always ignored and the walk
 * stopped after one song. And it read the queue before each eight-second
 * decode and wrote that copy back after, erasing anything the player had
 * done meanwhile; that is what put a finished song back on the list and
 * crashed the app. Now every change to the queue is one locked step on the
 * list as it is at that moment ([Store.editQueue]).
 *
 * A file that cannot be decoded is taken off the queue with its row marked
 * unreadable by [HapticMaker]; it is never retried into an endless loop.
 */
class HapticsWorker(
    ctx: Context,
    params: WorkerParameters,
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val store = Store(ctx)
        val dao = LibraryDb.get(ctx).dao()
        val handOverAt = SystemClock.elapsedRealtime() + RUN_BUDGET_MS
        return try {
            // Checked every song, so switching to "as I play them" in
            // Settings stops the walk at the next song rather than at the end.
            while (store.hapticsMode == MODE_BACKGROUND) {
                val made = withContext(Dispatchers.IO) { dao.madeTrackUris() }.toSet()
                val head = store.editQueue(made) { it }.pending.firstOrNull()
                if (head == null) {
                    // The walk is done; the progress line clears itself.
                    store.hapticsDone = 0
                    return Result.success()
                }
                val track = withContext(Dispatchers.IO) { dao.trackFor(head) }?.toTrack()
                if (track != null) {
                    // Failure is handled inside the maker: unreadable marked,
                    // so a file that can't be decoded is skipped, not looped.
                    HapticMaker.make(ctx, track)
                    store.hapticsDone = store.hapticsDone + 1
                }
                // The queue as it is *now*, not as it was eight seconds ago.
                // A song that left the library is dropped here too.
                val madeNow = withContext(Dispatchers.IO) { dao.madeTrackUris() }.toSet()
                val left = store.editQueue(madeNow) { it.completed(head) }
                if (left.pending.isEmpty()) {
                    store.hapticsDone = 0
                    return Result.success()
                }
                if (SystemClock.elapsedRealtime() >= handOverAt) {
                    continueWalk(ctx)
                    return Result.success()
                }
            }
            Result.success()
        } catch (stopped: CancellationException) {
            // Android stopped the run (battery, constraints, the time limit).
            // Not a failure: the queue is in storage and WorkManager runs the
            // work again later.
            throw stopped
        } catch (t: Throwable) {
            // Unexpected, not a decode refusal. WorkManager retries with its
            // own backoff, and caps the retries itself if this never recovers.
            Result.retry()
        }
    }

    companion object {
        const val MODE_BACKGROUND = "background"
        const val MODE_AS_PLAYED = "as_played"
        private const val UNIQUE = "haptics-walk"

        /** About thirty songs at eight seconds each, well inside the ten-minute limit. */
        private const val RUN_BUDGET_MS = 4 * 60 * 1000L

        /**
         * Add [uris] to the background walk and make sure it is running.
         * Does nothing in "as I play them" mode: there the player makes each
         * song's haptic on first play, and a queue nobody walks only fills up.
         * (A rescan in that mode used to fill it anyway, and the stale list
         * was a second way to the 2026-10-04 crash.)
         */
        suspend fun enqueue(ctx: Context, uris: List<String>) {
            val store = Store(ctx)
            if (store.hapticsMode != MODE_BACKGROUND) return
            val made = withContext(Dispatchers.IO) { LibraryDb.get(ctx).dao().madeTrackUris() }.toSet()
            store.editQueue(made) { it.enqueued(uris) }
            ensureEnqueued(ctx)
        }

        /**
         * Make sure a run is coming. `KEEP`, because this is called from the
         * app: if a run is already queued or working, it reads the queue
         * fresh before every song, so it will find whatever was just added.
         */
        fun ensureEnqueued(ctx: Context) {
            if (Store(ctx).hapticsMode != MODE_BACKGROUND) return
            WorkManager.getInstance(ctx).enqueueUniqueWork(
                UNIQUE,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<HapticsWorker>().build(),
            )
        }

        /**
         * The walk's own hand-over, called from *inside* a running run.
         * `APPEND_OR_REPLACE` queues the next run behind this one; `KEEP`
         * would see this very run still working and do nothing, which is
         * exactly how the walk used to stop after one song.
         */
        private fun continueWalk(ctx: Context) {
            WorkManager.getInstance(ctx).enqueueUniqueWork(
                UNIQUE,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                OneTimeWorkRequestBuilder<HapticsWorker>().build(),
            )
        }
    }
}
