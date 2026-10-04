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

    /**
     * All, Audio, Video: Mutalib's three on 2026-10-04 ("remove the file …
     * it's just the audio and video"). Audio is every sound a haptic came
     * from, scanned songs and audio files picked one at a time alike. A
     * haptic imported from a Thrum file came with no sound or picture at
     * all, so only All shows it.
     */
    enum class Filter { ALL, AUDIO, VIDEO }

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
        TrackKind.FILE -> "Audio"
    }

    /** What a row says it came from: an imported haptic has no source to name. */
    private fun sourceLabel(uri: String, kind: TrackKind): String =
        if (ThrumFile.isImported(uri)) "Imported" else kindLabel(kind)

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
                subtitle = subtitle(haptic.trackUri, track, haptic.score.durationMs),
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
                    subtitle = subtitle(track.sourceUri, track, track.durationMs),
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
                Filter.AUDIO -> !ThrumFile.isImported(row.uri) && row.kind != TrackKind.VIDEO
                Filter.VIDEO -> row.kind == TrackKind.VIDEO
            }
        }
    }

    private fun subtitle(uri: String, track: Track?, fallbackDurationMs: Long): String {
        val kind = sourceLabel(uri, track?.kind ?: TrackKind.FILE)
        val bits = buildList {
            add(kind)
            if (track?.artist?.isNotEmpty() == true) add(track.artist)
            val duration = track?.durationMs?.takeIf { it > 0 } ?: fallbackDurationMs
            if (duration > 0) add(clockOf(duration))
        }
        return bits.joinToString(" · ")
    }
}
