package com.mosman.thrum

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Turns a track into its whole-song haptic — the one maker both users of the
 * library share. Task 22.
 *
 * [Player] calls it on first play ("one at a time, as I play them");
 * [HapticsWorker] calls it walking the whole library in the background. Two
 * hand-copied decode-and-analyse paths would drift — the whole point of this
 * object is that they cannot.
 *
 * The score is the **whole track** (§8): no 45-second trim and no coarsening.
 * A call slices its own window via `Haptic.callWindow`, and the player drives
 * the rest in pieces — trimming or fitting here would silently throw away the
 * song the user asked to feel.
 */
object HapticMaker {

    data class Result(val haptic: Haptic?, val error: String?)

    /**
     * About eight seconds on the phone, the first time only. Returns the
     * haptic, or null with the decoder's own sentence in [Result.error] — and
     * marks the track **unreadable** in the library when the phone cannot
     * decode it, which is how screen 11's "This phone can't read this file"
     * row happens for real.
     */
    suspend fun make(ctx: Context, track: Track): Result {
        val store = Store(ctx)
        val punch = store.punch
        val distance = store.distance
        val body = store.body
        var builder: ScoreBuilder? = null
        val decoded = withContext(Dispatchers.IO) {
            AudioDecoder.decode(
                ctx,
                Uri.parse(track.sourceUri),
                onFormat = { rate, _ -> builder = ScoreBuilder(rate, name = track.name) },
                onMono = { samples, count -> builder?.feed(samples, count) },
            )
        }
        return when (decoded) {
            is Decoded.Failed -> {
                markUnreadable(ctx, track)
                Result(haptic = null, error = decoded.message)
            }

            is Decoded.Ok -> {
                val levels = builder?.levels()
                val built = levels?.let {
                    ScoreBuilder.toScore(
                        it,
                        builder?.stepMsUsed ?: Demo.STEP_MS,
                        track.name,
                        minFelt = punch,
                        bodyMs = body,
                        bodyCeiling = ScoreBuilder.ceilingFor(punch, distance),
                    )
                }
                if (built == null || built.isSilent()) {
                    // Silence is not a decode failure — the file is fine, it
                    // just has nothing to feel. It stays readable; the user
                    // gets the sentence and the row keeps its place.
                    Result(haptic = null, error = ctx.getString(R.string.error_silent))
                } else {
                    val made = Haptic(
                        trackUri = track.sourceUri,
                        score = built,
                        punch = punch,
                        distance = distance,
                        bodyMs = body,
                        madeAtMs = System.currentTimeMillis(),
                    )
                    withContext(Dispatchers.IO) {
                        LibraryDb.get(ctx).dao().upsertHaptic(made.toEntity())
                    }
                    Result(haptic = made, error = null)
                }
            }
        }
    }

    private suspend fun markUnreadable(ctx: Context, track: Track) {
        withContext(Dispatchers.IO) {
            LibraryDb.get(ctx).dao().upsertTracks(
                listOf(track.copy(readable = false).toEntity(System.currentTimeMillis())),
            )
        }
    }
}
