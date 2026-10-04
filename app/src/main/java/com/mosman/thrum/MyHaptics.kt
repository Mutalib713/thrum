package com.mosman.thrum

/**
 * The model behind the My Haptics list. Task 23, drawn in screen 19.
 *
 * Pure Kotlin, like [Home] and [Setup]: which rows appear, what they say and
 * how the filters behave are decisions, and decisions live where the PC can
 * check them. The composable only draws what this returns.
 *
 * The list is mostly haptics — one per song, video or file — but a video or
 * file **picked and not yet made** appears too, saying "making" or "can't
 * read": a pick that vanishes into nothing is the dead-control problem in
 * its oldest costume.
 */
object MyHaptics {

    enum class Filter { ALL, MUSIC, VIDEOS, FILES }

    data class Row(
        val uri: String,
        val name: String,
        val subtitle: String,
        val kind: TrackKind,
        val isCalls: Boolean,
        val hasHaptic: Boolean,
        val readable: Boolean,
    )

    fun kindLabel(kind: TrackKind): String = when (kind) {
        TrackKind.MUSIC -> "Music"
        TrackKind.VIDEO -> "Video"
        TrackKind.FILE -> "File"
    }

    /**
     * The rows for one filter, in the order the screen shows them: made
     * haptics first (newest first, the order the database returns), then
     * videos and files picked but not yet made, alphabetically.
     */
    fun rows(
        haptics: List<Haptic>,
        tracks: List<Track>,
        callsUri: String?,
        filter: Filter,
    ): List<Row> {
        val byUri = tracks.associateBy { it.sourceUri }
        val made = haptics.map { haptic ->
            val track = byUri[haptic.trackUri]
            Row(
                uri = haptic.trackUri,
                name = track?.name ?: haptic.score.sourceName.ifEmpty { "Untitled" },
                subtitle = subtitle(track, haptic.score.durationMs),
                kind = track?.kind ?: TrackKind.FILE,
                isCalls = haptic.trackUri == callsUri,
                hasHaptic = true,
                readable = track?.readable ?: true,
            )
        }
        val madeUris = made.map { it.uri }.toSet()
        val picked = tracks
            .filter { (it.kind == TrackKind.VIDEO || it.kind == TrackKind.FILE) && it.sourceUri !in madeUris }
            .map { track ->
                Row(
                    uri = track.sourceUri,
                    name = track.name,
                    subtitle = subtitle(track, track.durationMs),
                    kind = track.kind,
                    isCalls = track.sourceUri == callsUri,
                    hasHaptic = false,
                    readable = track.readable,
                )
            }
        val all = made + picked
        return all.filter { row ->
            when (filter) {
                Filter.ALL -> true
                Filter.MUSIC -> row.kind == TrackKind.MUSIC
                Filter.VIDEOS -> row.kind == TrackKind.VIDEO
                Filter.FILES -> row.kind == TrackKind.FILE
            }
        }
    }

    private fun subtitle(track: Track?, fallbackDurationMs: Long): String {
        val kind = kindLabel(track?.kind ?: TrackKind.FILE)
        val bits = buildList {
            add(kind)
            if (track?.artist?.isNotEmpty() == true) add(track.artist)
            val duration = track?.durationMs?.takeIf { it > 0 } ?: fallbackDurationMs
            if (duration > 0) add(clockOf(duration))
        }
        return bits.joinToString(" · ")
    }
}
