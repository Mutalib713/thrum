package com.mosman.thrum

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * What the ⋮ menu and a selection do to haptics and songs (Mutalib,
 * 2026-10-06): use for calls, share, rename, delete. Set as ringtone lives
 * in [Ringtone]; export keeps its own screen.
 */
object LibraryActions {

    /** This track's haptic, made now (about eight seconds) if it has none yet. */
    suspend fun hapticFor(ctx: Context, track: Track): Haptic? {
        val dao = LibraryDb.get(ctx).dao()
        withContext(Dispatchers.IO) { dao.hapticFor(track.sourceUri) }?.toHaptic()?.let { return it }
        if (ThrumFile.isImported(track.sourceUri) || !track.readable) return null
        return HapticMaker.make(ctx, track).haptic
    }

    /** The haptic's first 45 seconds become what a call plays. */
    fun useForCalls(ctx: Context, haptic: Haptic) {
        Store(ctx).arm(haptic.callWindow(), haptic.trackUri, haptic.punch, haptic.distance, haptic.bodyMs)
    }

    /**
     * The haptics as one Thrum file, handed to Android's share list — the
     * app the user picks there does the sending; Thrum never touches the
     * internet (Sacred Rule 3). Only the vibration is in the file, as with
     * Export.
     */
    suspend fun share(ctx: Context, haptics: List<Haptic>, title: String) {
        if (haptics.isEmpty()) return
        val name = if (haptics.size == 1) {
            haptics[0].score.sourceName
        } else {
            ctx.getString(R.string.export_file_name)
        }
        val file = withContext(Dispatchers.IO) {
            val dir = File(ctx.cacheDir, SHARED_DIR).apply { mkdirs() }
            // Last time's files go: they were only ever on their way out.
            dir.listFiles()?.forEach { it.delete() }
            File(dir, ThrumFile.fileNameFor(name)).apply { writeText(ThrumFile.encode(haptics)) }
        }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType(THRUM_MIME)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(file.name, uri)
        ctx.startActivity(Intent.createChooser(send, title))
    }

    /**
     * A new name for something the user made. A picked file or video keeps
     * its name in the library's track; an imported haptic has no track and
     * carries its name in the rhythm itself. Either way the rhythm's own
     * name follows, so Home and the call song say the new one too.
     */
    suspend fun rename(ctx: Context, uri: String, newName: String) {
        val name = newName.trim()
        if (name.isEmpty()) return
        val dao = LibraryDb.get(ctx).dao()
        val store = Store(ctx)
        withContext(Dispatchers.IO) {
            dao.trackFor(uri)?.toTrack()?.let { track ->
                dao.upsertTracks(listOf(track.copy(name = name).toEntity(System.currentTimeMillis())))
            }
            dao.hapticFor(uri)?.toHaptic()?.let { haptic ->
                val renamed = haptic.copy(score = haptic.score.copy(sourceName = name))
                dao.upsertHaptic(renamed.toEntity())
                if (store.sourceUri == uri) useForCalls(ctx, renamed)
            }
        }
    }

    /**
     * Delete what the user made: the haptic, and the picked file's or
     * video's row with it, or it would sit in My Haptics saying "making"
     * for good. The file itself stays on the phone: Thrum only ever read
     * it. A scanned song is never deleted here (no Delete in Music,
     * Mutalib's pick). The song for calls keeps working: its rhythm is
     * kept apart, in Store.
     */
    suspend fun delete(ctx: Context, uris: Collection<String>) {
        val dao = LibraryDb.get(ctx).dao()
        withContext(Dispatchers.IO) {
            uris.forEach { uri ->
                dao.removeHaptic(uri)
                val track = dao.trackFor(uri)?.toTrack()
                if (track != null && track.kind != TrackKind.MUSIC) dao.removeTrack(uri)
            }
        }
    }

    private const val SHARED_DIR = "shared"
    private const val THRUM_MIME = "application/octet-stream"
}
