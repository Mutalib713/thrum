package com.mosman.thrum

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore

/**
 * The scan: Android's own index of the music on the phone, read into [Track]s.
 * Task 20.
 *
 * The list comes from **MediaStore** — Android's catalog of media, the thing
 * every music app reads — rather than from walking folders. It is fast (a
 * few hundred songs read in well under a second, which is why the design's
 * "Found so far" streaming list is not built: there is nothing to stream),
 * and it already carries each song's title, artist and duration, so the scan
 * never decodes anything.
 *
 * Alarms, notifications and ringtones are excluded by the query itself: they
 * are the system's sounds, not the user's songs, and a scan that lists every
 * alarm tone buries the music. A ringtone can still arrive through "pick one
 * song" (screen 12), whose file chooser reaches anywhere.
 *
 * Android-bound and therefore thin, like [Haptics]: every decision about the
 * Tracks it produces lives in pure code.
 */
object MusicScan {

    fun scan(ctx: Context): List<Track> {
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
        )
        val selection = "${MediaStore.Audio.Media.IS_ALARM} = 0 AND " +
            "${MediaStore.Audio.Media.IS_NOTIFICATION} = 0 AND " +
            "${MediaStore.Audio.Media.IS_RINGTONE} = 0"
        val order = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        val out = mutableListOf<Track>()
        ctx.contentResolver.query(collection, projection, selection, null, order)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            while (cursor.moveToNext()) {
                val title = cursor.getString(titleCol).orEmpty().ifEmpty { "Untitled" }
                // Some audio carries no artist (a recording, a downloaded
                // single). Empty reads better than "unknown artist" repeated
                // down the list — the row is the song's.
                val artist = cursor.getString(artistCol).orEmpty()
                val uri = ContentUris.withAppendedId(collection, cursor.getLong(idCol))
                out.add(
                    Track(
                        sourceUri = uri.toString(),
                        name = title,
                        artist = artist,
                        durationMs = cursor.getLong(durationCol).coerceAtLeast(0),
                        kind = TrackKind.MUSIC,
                    ),
                )
            }
        }
        return out
    }

    /**
     * One picked file's length, read from its metadata without decoding the
     * audio — a container tag read costs milliseconds where a decode costs
     * seconds, and "pick one song" runs on a phone the user is holding.
     *
     * A zero means the file declares nothing usable; the row shows no
     * duration, and whether the file can really be decoded is answered when
     * something actually tries — the same honest unknown the scan's rows carry.
     */
    fun durationMsOf(ctx: Context, uri: Uri): Long = runCatching {
        val retriever = android.media.MediaMetadataRetriever()
        try {
            retriever.setDataSource(ctx, uri)
            retriever.extractMetadata(
                android.media.MediaMetadataRetriever.METADATA_KEY_DURATION,
            )?.toLongOrNull() ?: 0L
        } finally {
            retriever.release()
        }
    }.getOrDefault(0L)
}
