package com.mosman.thrum

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.webkit.MimeTypeMap
import java.io.File
import java.nio.ByteBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Set as ringtone: the phone's real ringtone, from a song or a haptic's
 * sound. Added by Mutalib on 2026-10-06 (PROFILE §4 item 17); the call
 * vibration is set alongside it by the caller, so what rings and what
 * vibrates are the same song.
 *
 * What Android allows, read from its own `RingtoneManager` source
 * (android14-release and main, 6 October 2026), not from memory:
 *
 * - Changing the ringtone needs `WRITE_SETTINGS`, which the user switches
 *   on themselves on Android's "Modify system settings" page
 *   ([permissionIntent]); [canChange] says whether they have.
 * - `setActualDefaultRingtoneUri` **quietly ignores** a file whose type it
 *   can't read, or that isn't audio — Android 14 takes any `audio/` type and
 *   `application/ogg` only; newer versions take video too. No error is
 *   thrown, so [setFrom] reads the setting back instead of trusting it.
 * - The ringtone is played later by the phone, not by Thrum, so it has to
 *   be somewhere the phone can open: Android's own media library. A file
 *   picked through the file chooser is readable by Thrum alone, so it is
 *   found in the library or copied into the phone's Ringtones folder, and
 *   a video's sound is lifted out into an audio file first.
 *
 * Nothing leaves the phone (Sacred Rule 3): the copy goes into the phone's
 * own Ringtones folder, where the phone's settings can see it.
 */
object Ringtone {

    sealed interface Outcome {
        /** The phone's ringtone is now this song. */
        data object Set : Outcome

        /** The user hasn't allowed Thrum to change system settings. */
        data object NoPermission : Outcome

        /** There is no sound Thrum can turn into a ringtone (an imported haptic, an unusual video). */
        data object NoSound : Outcome

        /** Android didn't take it: the setting reads back as something else. */
        data object Refused : Outcome
    }

    fun canChange(ctx: Context): Boolean = Settings.System.canWrite(ctx)

    /** Android's "Modify system settings" page, opened on Thrum. */
    fun permissionIntent(ctx: Context): Intent =
        Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${ctx.packageName}"))

    suspend fun setFrom(ctx: Context, track: Track): Outcome = withContext(Dispatchers.IO) {
        if (!canChange(ctx)) return@withContext Outcome.NoPermission
        val audio = runCatching { audioFor(ctx, track) }.getOrNull() ?: return@withContext Outcome.NoSound
        val asked = runCatching {
            RingtoneManager.setActualDefaultRingtoneUri(ctx, RingtoneManager.TYPE_RINGTONE, audio)
        }.isSuccess
        val now = RingtoneManager.getActualDefaultRingtoneUri(ctx, RingtoneManager.TYPE_RINGTONE)
        if (asked && sameMedia(now, audio)) Outcome.Set else Outcome.Refused
    }

    /**
     * Android may store the address with the phone's user in it
     * (`content://0@media/...`) and hand it back without. Compared with
     * that part taken out, so a ringtone that did change is never reported
     * as refused.
     */
    private fun sameMedia(a: Uri?, b: Uri): Boolean {
        fun bare(u: Uri) = u.toString().replace(Regex("^content://\\d+@"), "content://")
        return a != null && bare(a) == bare(b)
    }

    /** An address in Android's media library that holds this track's sound, or null. */
    private fun audioFor(ctx: Context, track: Track): Uri? {
        if (ThrumFile.isImported(track.sourceUri)) return null
        val source = Uri.parse(track.sourceUri)
        if (track.kind == TrackKind.VIDEO) return soundOfVideo(ctx, source, track.name)
        // A scanned song is already in the library.
        if (source.authority == MediaStore.AUTHORITY && isAudio(ctx, source)) return source
        // A file picked from the chooser's Audio shelf is the library's too,
        // under another address.
        runCatching { MediaStore.getMediaUri(ctx, source) }.getOrNull()
            ?.takeIf { isAudio(ctx, it) }
            ?.let { return it }
        val mime = ctx.contentResolver.getType(source)?.takeIf { it.startsWith("audio/") || it == "application/ogg" }
            ?: return null
        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "audio"
        return copyIntoRingtones(ctx, source, "${track.name.ifEmpty { "Thrum" }}.$ext", mime)
    }

    private fun isAudio(ctx: Context, uri: Uri): Boolean =
        runCatching { ctx.contentResolver.getType(uri) }.getOrNull()
            ?.let { it.startsWith("audio/") || it == "application/ogg" } == true

    /**
     * The file, copied into the phone's Ringtones/Thrum folder, or the copy
     * made last time under the same name. Marked as a ringtone, so the
     * Music scan (which skips ringtones) never lists it as a song.
     */
    private fun copyIntoRingtones(ctx: Context, source: Uri, fileName: String, mime: String): Uri? {
        val resolver = ctx.contentResolver
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val folder = "${Environment.DIRECTORY_RINGTONES}/$FOLDER/"
        resolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND ${MediaStore.MediaColumns.DISPLAY_NAME} = ?",
            arrayOf(folder, fileName),
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) return android.content.ContentUris.withAppendedId(collection, cursor.getLong(0))
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, folder)
            put(MediaStore.Audio.Media.IS_RINGTONE, 1)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val target = resolver.insert(collection, values) ?: return null
        val copied = runCatching {
            resolver.openInputStream(source)!!.use { input ->
                resolver.openOutputStream(target)!!.use { output -> input.copyTo(output) }
            }
        }.isSuccess
        if (!copied) {
            runCatching { resolver.delete(target, null, null) }
            return null
        }
        resolver.update(target, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        return target
    }

    /**
     * A video's sound as an `.m4a` file in Ringtones/Thrum, or null.
     *
     * Lifted out as it is, not re-encoded: the AAC sound most phone videos
     * carry goes into an MP4 audio file unchanged, which takes a moment and
     * loses nothing. A video whose sound is anything else returns null, and
     * the caller says so rather than pretending.
     */
    private fun soundOfVideo(ctx: Context, source: Uri, name: String): Uri? {
        val extractor = MediaExtractor()
        val temp = File(ctx.cacheDir, "ringtone-sound.m4a")
        try {
            extractor.setDataSource(ctx, source, null)
            val index = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME) == MediaFormat.MIMETYPE_AUDIO_AAC
            } ?: return null
            val format = extractor.getTrackFormat(index)
            extractor.selectTrack(index)
            val muxer = MediaMuxer(temp.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            try {
                val out = muxer.addTrack(format)
                muxer.start()
                val buffer = ByteBuffer.allocate(maxOf(format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 0), MIN_SAMPLE_BUFFER))
                val info = MediaCodec.BufferInfo()
                while (true) {
                    val size = extractor.readSampleData(buffer, 0)
                    if (size < 0) break
                    val key = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                    info.set(0, size, extractor.sampleTime, key)
                    muxer.writeSampleData(out, buffer, info)
                    extractor.advance()
                }
                muxer.stop()
            } finally {
                runCatching { muxer.release() }
            }
            return copyIntoRingtones(ctx, Uri.fromFile(temp), "${name.substringBeforeLast('.').ifEmpty { "Thrum" }}.m4a", "audio/mp4")
        } catch (_: Exception) {
            return null
        } finally {
            extractor.release()
            temp.delete()
        }
    }

    private const val FOLDER = "Thrum"
    private const val MIN_SAMPLE_BUFFER = 256 * 1024
}
