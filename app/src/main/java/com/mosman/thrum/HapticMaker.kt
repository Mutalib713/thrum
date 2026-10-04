package com.mosman.thrum

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Turns a track into its whole-song haptic — the one maker every part of
 * the app shares. Task 22.
 *
 * [Player] calls it on first play ("one at a time, as I play them");
 * [HapticsWorker] calls it walking the whole library in the background; the
 * Tune screen calls it to rebuild one song with new dials. Hand-copied
 * decode-and-analyse paths would drift — the whole point of this object is
 * that they cannot.
 *
 * The score is the **whole track** (§8): no 45-second trim and no
 * coarsening. A call slices its own window via `Haptic.callWindow`, and the
 * player drives the rest in pieces — trimming or fitting here would silently
 * throw away the song the user asked to feel.
 */
object HapticMaker {

    data class Result(val haptic: Haptic?, val error: String?)

    /** A file read into the analyser's levels — the expensive part, done once. */
    sealed interface Analysis {
        data class Ok(val levels: Levels, val stepMs: Int, val name: String) : Analysis
        data class Failed(val message: String) : Analysis
    }

    /**
     * Read [uri] and analyse it: about eight seconds on the phone. Every dial
     * move after this is free — rebuilding a score from kept levels costs
     * milliseconds, re-reading the file costs seconds, and tuning by feel is
     * many small moves.
     */
    suspend fun analyse(ctx: Context, uri: Uri, name: String): Analysis {
        var builder: ScoreBuilder? = null
        val decoded = withContext(Dispatchers.IO) {
            AudioDecoder.decode(
                ctx,
                uri,
                onFormat = { rate, _ -> builder = ScoreBuilder(rate, name = name) },
                onMono = { samples, count -> builder?.feed(samples, count) },
            )
        }
        val built = builder
        return when {
            decoded is Decoded.Failed -> Analysis.Failed(decoded.message)
            built == null -> Analysis.Failed(ctx.getString(R.string.error_silent))
            else -> Analysis.Ok(built.levels(), built.stepMsUsed, name)
        }
    }

    /**
     * About eight seconds on the phone, the first time only. Returns the
     * haptic, or null with the decoder's own sentence in [Result.error] — and
     * marks the track **unreadable** in the library when the phone cannot
     * decode it, which is how screen 11's "This phone can't read this file"
     * row happens for real.
     *
     * The dials default to the stored ones — the call song's, which are the
     * feel every new haptic starts from. The Tune screen passes its own.
     */
    suspend fun make(
        ctx: Context,
        track: Track,
        punch: Int = Store(ctx).punch,
        distance: Int = Store(ctx).distance,
        body: Int = Store(ctx).body,
    ): Result {
        return when (val analysis = analyse(ctx, Uri.parse(track.sourceUri), track.name)) {
            is Analysis.Failed -> {
                markUnreadable(ctx, track)
                Result(haptic = null, error = analysis.message)
            }

            is Analysis.Ok -> {
                val built = ScoreBuilder.wholeScore(analysis.levels, analysis.stepMs, track.name, punch, distance, body)
                if (built.isSilent()) {
                    // Silence is not a decode failure — the file is fine, it
                    // just has nothing to feel. It stays readable; the user
                    // gets the sentence and the row keeps its place.
                    Result(haptic = null, error = ctx.getString(R.string.error_silent))
                } else {
                    Result(haptic = save(ctx, track.sourceUri, built, punch, distance, body), error = null)
                }
            }
        }
    }

    /** Write a haptic to the library — one row per track, so a retune replaces it. */
    suspend fun save(ctx: Context, trackUri: String, score: Score, punch: Int, distance: Int, body: Int): Haptic {
        val made = Haptic(
            trackUri = trackUri,
            score = score,
            punch = punch,
            distance = distance,
            bodyMs = body,
            madeAtMs = System.currentTimeMillis(),
        )
        withContext(Dispatchers.IO) {
            LibraryDb.get(ctx).dao().upsertHaptic(made.toEntity())
        }
        return made
    }

    private suspend fun markUnreadable(ctx: Context, track: Track) {
        withContext(Dispatchers.IO) {
            LibraryDb.get(ctx).dao().upsertTracks(
                listOf(track.copy(readable = false).toEntity(System.currentTimeMillis())),
            )
        }
    }
}
