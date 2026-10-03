package com.mosman.thrum

/**
 * Where a track came from — the three sources §8 names. The collection's
 * pieces are [MUSIC]: they are songs, and the My Haptics filters treat them as
 * music; their source URI is an `asset://` name inside the APK.
 */
enum class TrackKind { MUSIC, VIDEO, FILE }

/**
 * A song or audio file the app knows about — found by the scan, picked one at
 * a time, or bundled in the collection. PROFILE.md §8.
 *
 * Pure Kotlin, like [Score]: the My Haptics filters, the queue arithmetic and
 * the scan's merge logic all live here rather than in Android code, because
 * this is the only layer that can be tested on the PC. A [Track] is a fact
 * about a file, not about the haptic built from it — that is [Haptic].
 *
 * [sourceUri] is the identity. The same file found twice by the scan is one
 * track; the queue and the library key on this string, and a video or file the
 * user picked is keyed by its content URI, which the file chooser hands out
 * once and which the app holds a persistable grant for.
 *
 * [readable] is the honest flag: a file Android cannot decode — the Dolby
 * Atmos `Over_the_Horizon` from Task 3, which even Mutalib's music player
 * cannot play — stays in the list, marked unreadable, instead of failing
 * quietly the first time someone taps it. Task 20's scan sets it; the decoder
 * is the witness, not the extension.
 */
data class Track(
    val sourceUri: String,
    val name: String,
    val artist: String = "",
    val durationMs: Long,
    val kind: TrackKind,
    val readable: Boolean = true,
) {
    init {
        require(sourceUri.isNotEmpty()) { "a track needs a source" }
        require(name.isNotEmpty()) { "a track needs a name" }
        require(durationMs >= 0) { "durationMs was $durationMs" }
    }
}
