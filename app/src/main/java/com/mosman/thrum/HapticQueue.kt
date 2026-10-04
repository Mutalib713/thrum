package com.mosman.thrum

/**
 * The queue of tracks waiting for a haptic, and the two rules the app promises
 * about it (PROFILE.md §4 item 5, §12):
 *
 * 1. **A song played jumps the queue.** Tuning by feel is a now thing; the
 *    background walk through the library is not.
 * 2. **No song is ever made twice.** Not by enqueuing a duplicate, not by
 *    playing one that already has a haptic, not by completing a job whose
 *    result was already written.
 *
 * Pure arithmetic on strings, immutable — every operation returns the next
 * queue and the caller persists it. WorkManager (Task 22) decides *when* the
 * work runs; this class decides *what* the work is and in what order, which is
 * exactly the part that can be proved without a phone.
 *
 * [made] lives here rather than being checked outside, because both promises
 * are about the made set: a song with a haptic must never rejoin, and only the
 * queue knows what the promises mean. The library is the source of truth for
 * *which* tracks are made; the caller seeds [made] from it and reports each
 * completion through [completed].
 */
data class HapticQueue(
    val pending: List<String> = emptyList(),
    val made: Set<String> = emptySet(),
) {
    init {
        require(pending.toSet().size == pending.size) { "a queued song is already queued: $pending" }
        require(pending.none { it in made }) { "a made song is still pending: ${pending.filter { it in made }}" }
    }

    /**
     * The user played this song, so the player is making its haptic **now** —
     * the strongest way to jump the queue: ahead of everything waiting, and
     * not waiting for the walk at all. It leaves the queue so the walk never
     * makes it a second time, in parallel, eight seconds of decoding for
     * nothing.
     *
     * This used to move the song to the *front* for the walk to pick up,
     * while the player made it anyway. The two then decoded the same song at
     * once, and the walk's copy of the queue, read before its own eight
     * seconds, was what wrote a made song back in — the 2026-10-04 crash.
     */
    fun claimed(uri: String): HapticQueue = copy(pending = pending.filter { it != uri })

    /**
     * The scan found these tracks. Already-made songs never enter; duplicates
     * within the batch or against the existing queue collapse into one entry.
     */
    fun enqueued(uris: List<String>): HapticQueue {
        val fresh = uris.filter { it !in made }.distinct()
        return copy(pending = pending + fresh.filter { it !in pending.toSet() })
    }

    /**
     * A haptic was written for [uri]. It leaves the queue and joins the made
     * set, so no later scan or play can put it back.
     */
    fun completed(uri: String): HapticQueue =
        copy(pending = pending.filter { it != uri }, made = made + uri)

    companion object {
        /**
         * A queue rebuilt from storage, which is the one place the invariants
         * above cannot be trusted.
         *
         * Storage is written by more than one hand — the background walk, the
         * player, a rescan — and the library is written separately from it,
         * so the two can disagree: a song made while someone held an older
         * copy of the queue, a duplicate from two writers racing. The
         * constructor refuses both, correctly, and on 2026-10-04 that refusal,
         * reached from a song tap on the main thread, closed the app on every
         * tap until its data was cleared. Restoring drops what the library has
         * already made and keeps the first copy of a duplicate, so stored
         * state can never take the app down. [Store.editQueue] is the only
         * caller, and it writes the cleaned list straight back.
         */
        fun restore(pending: List<String>, made: Set<String>): HapticQueue =
            HapticQueue(pending.filter { it !in made }.distinct(), made)

        /**
         * The pending queue as one stored line — pipe-joined, because a pipe
         * cannot appear inside a `content://` or `asset://` URI, so no
         * escaping is needed and none can drift. Decoding filters blanks, so
         * a corrupted or empty store degrades to an empty queue rather than
         * throwing inside a background worker.
         */
        fun encodePending(pending: List<String>): String = pending.joinToString("|")

        fun decodePending(text: String?): List<String> =
            text?.split('|')?.mapNotNull { it.trim().ifEmpty { null } } ?: emptyList()
    }
}
