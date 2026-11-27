package com.mosman.thrum

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.webkit.MimeTypeMap
import androidx.core.net.toUri
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Set as ringtone: the phone's real ringtone, from a song or a haptic's
 * sound. Added by Mutalib on 2026-10-06 (PROFILE §4 item 17), and on
 * 2026-10-07 made the **only** way to choose what calls play: the ringtone
 * and the vibration are always one song (Task 31). The ringtone is the
 * song's first 45 seconds ([RingtoneClip]), the window the vibration repeats.
 *
 * What Android allows, read from its own `RingtoneManager` source
 * (android14-release and main, 6 October 2026), not from memory:
 *
 * - Changing the ringtone needs `WRITE_SETTINGS`, which the user switches
 *   on themselves on Android's "Modify system settings" page
 *   ([permissionIntent]); [canChange] says whether they have. No app can
 *   skip that page, only reach it once.
 * - `setActualDefaultRingtoneUri` **quietly ignores** a file whose type it
 *   can't read, or that isn't audio — Android 14 takes any `audio/` type and
 *   `application/ogg` only; newer versions take video too. No error is
 *   thrown, so [setFrom] reads the setting back instead of trusting it.
 * - The ringtone is played later by the phone, not by Thrum, so it has to
 *   be somewhere the phone can open: Android's own media library. The clip
 *   goes into the phone's Ringtones/Thrum folder.
 *
 * Nothing leaves the phone (Sacred Rule 3).
 */
object Ringtone {

    sealed interface Outcome {
        /** The phone's ringtone is now this song. */
        data object Set : Outcome

        /** The user hasn't allowed Thrum to change system settings. */
        data object NoPermission : Outcome

        /** There is no sound Thrum can turn into a ringtone (an imported haptic, an unreadable file). */
        data object NoSound : Outcome

        /** Android didn't take it: the setting reads back as something else. */
        data object Refused : Outcome
    }

    fun canChange(ctx: Context): Boolean = Settings.System.canWrite(ctx)

    /** Android's "Modify system settings" page, opened on Thrum. */
    fun permissionIntent(ctx: Context): Intent =
        Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, "package:${ctx.packageName}".toUri())

    /** The phone's ringtone now, or null when it is "None" or can't be read. */
    fun current(ctx: Context): String? = readSetting(ctx)?.takeIf { it != RingtoneRules.NO_RINGTONE }

    /**
     * Whether the phone rings with the song calls vibrate to. Asked when a
     * call comes in Ring mode, and by Home to say what will happen.
     */
    fun isTheSong(ctx: Context, store: Store = Store(ctx)): Boolean =
        RingtoneRules.ringtoneIsTheSong(current(ctx), store.thrumRingtone, store.thrumRingtoneFor, store.sourceUri)

    /**
     * Make [track]'s opening the phone's ringtone.
     *
     * @param previousSong what calls vibrated to before this, so a ringtone an
     *   earlier build set straight from that song isn't noted as the user's own.
     */
    suspend fun setFrom(ctx: Context, track: Track, previousSong: String?): Outcome = withContext(Dispatchers.IO) {
        if (!canChange(ctx)) return@withContext Outcome.NoPermission
        val audio = runCatching { audioFor(ctx, track) }.getOrNull() ?: return@withContext Outcome.NoSound
        val store = Store(ctx)
        val before = readSetting(ctx)
        val asked = runCatching {
            RingtoneManager.setActualDefaultRingtoneUri(ctx, RingtoneManager.TYPE_RINGTONE, audio)
        }.isSuccess
        val now = current(ctx)
        if (!asked || !RingtoneRules.sameAddress(now, audio.toString())) return@withContext Outcome.Refused

        // Noted only once it worked: "Go back" should never show for a
        // ringtone that didn't change.
        if (before != null) {
            // A song picked through the file chooser has a second address in
            // the media library, and that is the one a ringtone would hold.
            val previousInLibrary = previousSong?.let { song ->
                runCatching { MediaStore.getMediaUri(ctx, song.toUri())?.toString() }.getOrNull()
            }
            val thrums = isThrumClip(ctx, before) ||
                RingtoneRules.sameAddress(before, store.thrumRingtone) ||
                RingtoneRules.sameAddress(before, previousSong) ||
                RingtoneRules.sameAddress(before, previousInLibrary)
            if (RingtoneRules.shouldRemember(store.oldRingtone, thrums)) store.oldRingtone = before
        }
        store.thrumRingtone = now
        store.thrumRingtoneFor = track.sourceUri
        deleteUnusedClips(ctx)
        Outcome.Set
    }

    /**
     * Put the user's own ringtone back, and stop Thrum vibrating on calls, so
     * the phone is as it was before Thrum. False when Android didn't take it
     * (the old file may have been deleted); nothing else changes then.
     */
    suspend fun restoreOld(ctx: Context): Boolean = withContext(Dispatchers.IO) {
        val store = Store(ctx)
        val old = store.oldRingtone ?: return@withContext false
        if (!canChange(ctx)) return@withContext false
        val target = if (old == RingtoneRules.NO_RINGTONE) null else old.toUri()
        runCatching { RingtoneManager.setActualDefaultRingtoneUri(ctx, RingtoneManager.TYPE_RINGTONE, target) }
        val back = readSetting(ctx)
        val restored = if (target == null) back == RingtoneRules.NO_RINGTONE else RingtoneRules.sameAddress(back, old)
        if (restored) {
            store.oldRingtone = null
            store.thrumRingtone = null
            store.thrumRingtoneFor = null
            store.disarm()
            // As before Thrum: its ringtone files go too.
            deleteUnusedClips(ctx)
        }
        restored
    }

    /**
     * Delete the files in Ringtones/Thrum that no phone sound uses any more:
     * not the ringtone, the notification sound or the alarm. They used to
     * pile up, one per song ever set, even after "Go back".
     *
     * Only Thrum's own folder, and only files Android lets Thrum delete
     * without asking (the ones it made itself, this install). A song the
     * ringtone points at directly, in the user's music, is never touched.
     */
    private fun deleteUnusedClips(ctx: Context) {
        val inUse = listOf(RingtoneManager.TYPE_RINGTONE, RingtoneManager.TYPE_NOTIFICATION, RingtoneManager.TYPE_ALARM)
            .mapNotNull { type ->
                runCatching { RingtoneManager.getActualDefaultRingtoneUri(ctx, type)?.toString() }.getOrNull()
            }
            .mapNotNull(RingtoneRules::mediaId)
            .toSet()
        val resolver = ctx.contentResolver
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val ids = runCatching {
            resolver.query(
                collection,
                arrayOf(MediaStore.MediaColumns._ID),
                "${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
                arrayOf(FOLDER_PATH),
                null,
            )?.use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getLong(0)) } }
        }.getOrNull().orEmpty()
        for (id in ids) {
            if (id in inUse) continue
            runCatching { resolver.delete(ContentUris.withAppendedId(collection, id), null, null) }
        }
    }

    /** The old ringtone's name as Android shows it, or null if there's none noted. */
    suspend fun oldRingtoneName(ctx: Context): String? = withContext(Dispatchers.IO) {
        val old = Store(ctx).oldRingtone ?: return@withContext null
        if (old == RingtoneRules.NO_RINGTONE) return@withContext ctx.getString(R.string.ringtone_none)
        runCatching { RingtoneManager.getRingtone(ctx, old.toUri())?.getTitle(ctx) }.getOrNull()
            ?: ctx.getString(R.string.ringtone_unnamed)
    }

    /** The setting as stored, [RingtoneRules.NO_RINGTONE] for "None", or null when it can't be read. */
    private fun readSetting(ctx: Context): String? = runCatching {
        RingtoneManager.getActualDefaultRingtoneUri(ctx, RingtoneManager.TYPE_RINGTONE)?.toString()
            ?: RingtoneRules.NO_RINGTONE
    }.getOrNull()

    /**
     * An address in Android's media library that holds this track's opening,
     * or null. A song that already fits in 45 seconds is used as it is; a
     * longer one, any video and anything of unknown length gets a clip.
     */
    private fun audioFor(ctx: Context, track: Track): Uri? {
        if (ThrumFile.isImported(track.sourceUri)) return null
        val source = track.sourceUri.toUri()
        if (track.kind != TrackKind.VIDEO && !RingtoneRules.needsCut(track.durationMs)) {
            // A scanned song is already in the library.
            if (source.authority == MediaStore.AUTHORITY && isAudio(ctx, source)) return source
            // A file picked from the chooser's Audio shelf is the library's
            // too, under another address.
            runCatching { MediaStore.getMediaUri(ctx, source) }.getOrNull()
                ?.takeIf { isAudio(ctx, it) }
                ?.let { return it }
            val mime = ctx.contentResolver.getType(source)?.takeIf { it.startsWith("audio/") || it == "application/ogg" }
            if (mime != null) {
                val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "audio"
                return copyIntoRingtones(ctx, source, RingtoneRules.clipFileName(track.name, track.sourceUri, ext), mime)
            }
        }
        return clipOf(ctx, source, track)
    }

    /**
     * The opening as "Name (Thrum 3f2a1b).m4a" in Ringtones/Thrum: the one
     * made from this same song before, or a new one. Named by song, not just
     * by title, so two songs called "Intro" never share a ringtone.
     */
    private fun clipOf(ctx: Context, source: Uri, track: Track): Uri? {
        val fileName = RingtoneRules.clipFileName(track.name, track.sourceUri, "m4a")
        findInRingtones(ctx, fileName)?.let { return it }
        val temp = File(ctx.cacheDir, "ringtone-clip.m4a")
        try {
            if (!RingtoneClip.make(ctx, source, temp)) return null
            return copyIntoRingtones(ctx, Uri.fromFile(temp), fileName, "audio/mp4")
        } finally {
            temp.delete()
        }
    }

    private fun isAudio(ctx: Context, uri: Uri): Boolean =
        runCatching { ctx.contentResolver.getType(uri) }.getOrNull()
            ?.let { it.startsWith("audio/") || it == "application/ogg" } == true

    /** Whether [address] is a file in Thrum's own Ringtones folder. */
    private fun isThrumClip(ctx: Context, address: String): Boolean = runCatching {
        ctx.contentResolver.query(address.toUri(), arrayOf(MediaStore.MediaColumns.RELATIVE_PATH), null, null, null)
            ?.use { cursor -> cursor.moveToFirst() && cursor.getString(0) == FOLDER_PATH }
    }.getOrNull() == true

    private fun findInRingtones(ctx: Context, fileName: String): Uri? {
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        ctx.contentResolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND ${MediaStore.MediaColumns.DISPLAY_NAME} = ?",
            arrayOf(FOLDER_PATH, fileName),
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) return ContentUris.withAppendedId(collection, cursor.getLong(0))
        }
        return null
    }

    /**
     * The file, copied into the phone's Ringtones/Thrum folder, or the copy
     * made last time under the same name. Marked as a ringtone, so the
     * Music scan (which skips ringtones) never lists it as a song.
     */
    private fun copyIntoRingtones(ctx: Context, source: Uri, fileName: String, mime: String): Uri? {
        findInRingtones(ctx, fileName)?.let { return it }
        val resolver = ctx.contentResolver
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, FOLDER_PATH)
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

    private val FOLDER_PATH = "${Environment.DIRECTORY_RINGTONES}/Thrum/"
}
