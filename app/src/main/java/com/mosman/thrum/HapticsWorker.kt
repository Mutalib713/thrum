package com.mosman.thrum

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The background walk: makes a haptic for the song at the head of the queue,
 * then asks to be run again until the queue is empty. Task 22.
 *
 * **Why one track per run, self-rescheduled, rather than one long job.** A
 * library walk is tens of minutes of work (each song is about eight seconds;
 * R12's arithmetic), and WorkManager only lets a background job run past ten
 * minutes as a **foreground service** — which would mean a permanent
 * notification and `FOREGROUND_SERVICE` permissions, for work the design
 * describes as quiet ("Thrum works through them while you use your phone").
 * One song per run is a few seconds, well inside the limit, and the
 * self-reschedule carries the walk forward. Android may still pause the walk
 * to save battery — that is R12, accepted at Mutalib's choice of background
 * mode — and the pause costs nothing: the queue is in storage, so the next
 * run picks up exactly where this one stopped.
 *
 * **The order is the promise.** The head of [Store.hapticQueuePending] is
 * made next, and a song played jumps to that head ([Player.jumpAhead]) — the
 * run that follows makes what the user asked for, not what the scan found
 * first. A file that cannot be decoded is settled off the queue with its row
 * marked unreadable; it is never retried into an infinite loop.
 */
class HapticsWorker(
    ctx: Context,
    params: WorkerParameters,
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val store = Store(ctx)
        val db = LibraryDb.get(ctx)
        return try {
            val head = store.hapticQueuePending.firstOrNull() ?: run {
                // The walk is done; the progress line clears itself.
                store.hapticsDone = 0
                store.hapticsTotal = 0
                return Result.success()
            }
            val queue = HapticQueue(
                store.hapticQueuePending,
                withContext(Dispatchers.IO) { db.dao().madeTrackUris() }.toSet(),
            )
            val track = withContext(Dispatchers.IO) { db.dao().trackFor(head) }?.toTrack()
            if (track == null) {
                // The song left the library (deleted, re-scanned away). Off
                // the queue, and never retried.
                store.hapticQueuePending = queue.completed(head).pending
            } else {
                // Failure is handled inside [HapticMaker] — unreadable marked,
                // sentence recorded — so a poison file skips rather than loops.
                HapticMaker.make(ctx, track)
                store.hapticQueuePending = queue.completed(head).pending
                store.hapticsDone = store.hapticsDone + 1
            }
            if (store.hapticQueuePending.isNotEmpty()) ensureEnqueued(ctx)
            Result.success()
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

        /**
         * Ask for the next run. `KEEP`: if a run is already alive, it will
         * pick up the reordered queue when it finishes — nothing to replace,
         * and replacing it mid-decode would waste the eight seconds in flight.
         */
        fun ensureEnqueued(ctx: Context) {
            if (Store(ctx).hapticsMode != MODE_BACKGROUND) return
            WorkManager.getInstance(ctx).enqueueUniqueWork(
                UNIQUE,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<HapticsWorker>().build(),
            )
        }
    }
}
