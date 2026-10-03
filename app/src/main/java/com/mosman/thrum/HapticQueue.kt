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
     * The user played this song. With a haptic already made, nothing to do.
     * Otherwise it goes to the front — ahead of everything waiting — whether
     * it was queued or not, because pressing play is a stronger signal than
     * anything the scan learned.
     */
    fun played(uri: String): HapticQueue {
        if (uri in made) return this
        val rest = pending.filter { it != uri }
        return copy(pending = listOf(uri) + rest)
    }

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
}
