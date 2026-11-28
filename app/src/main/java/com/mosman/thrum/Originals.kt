package com.mosman.thrum

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Thrum Originals: music built into the app, so there is something to feel
 * before anything is scanned (Mutalib, 2026-11-28; PLAN Task 27).
 *
 * **To add one:** put the audio file in `app/src/main/assets/originals/` and
 * build. The file name becomes its title ("Morning-run.mp3" shows as
 * "Morning run"). Sacred Rule 4 applies: only audio Thrum owns outright, or
 * whose licence allows shipping it inside an app.
 *
 * Android's decoder and player can't read a file inside the APK by name, so
 * each Original is copied once into the app's private storage and played from
 * there. Originals never go into the library database: they aren't the
 * phone's songs, and a scan must never touch them.
 */
object Originals {
    private const val FOLDER = "originals"
    private val AUDIO = setOf("mp3", "m4a", "aac", "ogg", "oga", "opus", "wav", "flac")

    /** The Originals, as tracks the player can play. Empty when the folder is. */
    suspend fun list(ctx: Context): List<Track> = withContext(Dispatchers.IO) {
        val names = runCatching { ctx.assets.list(FOLDER)?.toList() }.getOrNull().orEmpty()
            .filter { it.substringAfterLast('.', "").lowercase() in AUDIO }
            .sorted()
        val dir = File(ctx.filesDir, FOLDER).apply { mkdirs() }
        names.mapNotNull { name ->
            val file = File(dir, name)
            runCatching {
                if (!file.exists() || file.length() == 0L) {
                    ctx.assets.open("$FOLDER/$name").use { input -> file.outputStream().use { input.copyTo(it) } }
                }
                val uri = Uri.fromFile(file)
                Track(
                    sourceUri = uri.toString(),
                    name = titleOf(name),
                    durationMs = MusicScan.durationMsOf(ctx, uri),
                    kind = TrackKind.MUSIC,
                )
            }.getOrNull()
        }
    }

    /** "Morning-run_v2.mp3" → "Morning run v2". */
    fun titleOf(fileName: String): String =
        fileName.substringBeforeLast('.').replace('-', ' ').replace('_', ' ').trim().ifEmpty { fileName }

    /** True for a track that is one of the Originals. */
    fun isOriginal(ctx: Context, uri: String): Boolean = uri.startsWith(Uri.fromFile(File(ctx.filesDir, FOLDER)).toString())
}

/**
 * The Originals' heading in the Music tab. In test builds it shows even with
 * none added yet, with a note saying where they go; a release build shows
 * nothing until there is one.
 */
@Composable
fun OriginalsHeading(count: Int, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            stringResource(R.string.originals_title),
            style = ThrumType.heading,
            color = ThrumInk,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            stringResource(if (count == 0) R.string.originals_empty_test_build else R.string.originals_help),
            style = ThrumType.meta,
            color = ThrumInk2,
            modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
        )
    }
}
