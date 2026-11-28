package com.mosman.thrum

import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Thrum Originals: music built into the app, so there is something to feel
 * before anything is scanned (Mutalib, 2026-11-28; PLAN Task 27).
 *
 * **Two ways in:**
 * - Built in: an audio file in `app/src/main/assets/originals/` ships inside
 *   the app. The file name becomes its title ("Morning-run.mp3" shows as
 *   "Morning run").
 * - Test builds only: "Copy to Thrum Originals" in a song's ⋮ menu copies it
 *   into the Originals on that phone, so Mutalib can try candidates before
 *   choosing what ships. A copied one is on that phone only; to ship it, its
 *   file goes into the assets folder.
 *
 * Sacred Rule 4 applies to what ships: only audio Thrum owns outright, or whose
 * licence allows shipping it inside an app.
 *
 * Android's decoder and player can't read a file inside the APK by name, so
 * each built-in Original is copied once into the app's private storage and
 * played from there. Originals never go into the library database: they aren't
 * the phone's songs, and a scan must never touch them.
 */
object Originals {
    private const val FOLDER = "originals"
    private val AUDIO = setOf("mp3", "m4a", "aac", "ogg", "oga", "opus", "wav", "flac")

    /** Bumped whenever the Originals change, so every list showing them reloads. */
    var version by mutableIntStateOf(0)
        private set

    private fun dir(ctx: Context) = File(ctx.filesDir, FOLDER).apply { mkdirs() }

    private fun builtIn(ctx: Context): List<String> =
        runCatching { ctx.assets.list(FOLDER)?.toList() }.getOrNull().orEmpty()
            .filter { it.substringAfterLast('.', "").lowercase() in AUDIO }

    /** The Originals, as tracks the player can play: the built-in ones first, then any copied in. */
    suspend fun list(ctx: Context): List<Track> = withContext(Dispatchers.IO) {
        val dir = dir(ctx)
        val shipped = builtIn(ctx).sorted()
        val copied = dir.listFiles().orEmpty()
            .map { it.name }
            .filter { it !in shipped && it.substringAfterLast('.', "").lowercase() in AUDIO }
            .sorted()
        (shipped + copied).mapNotNull { name ->
            val file = File(dir, name)
            runCatching {
                if (name in shipped && (!file.exists() || file.length() == 0L)) {
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

    /**
     * Test builds only: copies [track]'s audio into the Originals on this phone.
     * Returns the new Original's title, or null when the file couldn't be read.
     */
    suspend fun copyIn(ctx: Context, track: Track): String? = withContext(Dispatchers.IO) {
        runCatching {
            val source = Uri.parse(track.sourceUri)
            val ext = extensionOf(ctx, source) ?: return@runCatching null
            val base = track.name.substringBeforeLast('.').replace(Regex("[\\\\/:*?\"<>|]"), " ").trim().ifEmpty { "Original" }
            var file = File(dir(ctx), "$base.$ext")
            var n = 2
            while (file.exists()) file = File(dir(ctx), "$base $n.$ext").also { n++ }
            ctx.contentResolver.openInputStream(source)?.use { input -> file.outputStream().use { input.copyTo(it) } }
                ?: return@runCatching null
            version++
            titleOf(file.name)
        }.getOrNull()
    }

    /** True for an Original that was copied in on this phone (built-in ones can't be removed). */
    fun isCopied(ctx: Context, uri: String): Boolean {
        val name = Uri.parse(uri).lastPathSegment ?: return false
        return isOriginal(ctx, uri) && name !in builtIn(ctx)
    }

    /** Test builds only: takes a copied-in Original back out. */
    suspend fun remove(ctx: Context, uri: String) = withContext(Dispatchers.IO) {
        if (!isCopied(ctx, uri)) return@withContext
        Uri.parse(uri).path?.let { File(it).delete() }
        version++
    }

    /** "Morning-run_v2.mp3" → "Morning run v2". */
    fun titleOf(fileName: String): String =
        fileName.substringBeforeLast('.').replace('-', ' ').replace('_', ' ').trim().ifEmpty { fileName }

    /** True for a track that is one of the Originals. */
    fun isOriginal(ctx: Context, uri: String): Boolean = uri.startsWith(Uri.fromFile(dir(ctx)).toString())

    private fun extensionOf(ctx: Context, uri: Uri): String? {
        val fromName = AudioDecoder.displayName(ctx, uri).substringAfterLast('.', "").lowercase()
        if (fromName in AUDIO) return fromName
        return when (ctx.contentResolver.getType(uri)) {
            "audio/mpeg" -> "mp3"
            "audio/mp4", "audio/x-m4a", "audio/m4a" -> "m4a"
            "audio/aac" -> "aac"
            "audio/ogg", "application/ogg" -> "ogg"
            "audio/opus" -> "opus"
            "audio/wav", "audio/x-wav" -> "wav"
            "audio/flac", "audio/x-flac" -> "flac"
            else -> null
        }
    }
}

/** The Originals, reloaded whenever one is added or removed. */
@Composable
fun rememberOriginals(): List<Track> {
    val ctx = LocalContext.current
    val version = Originals.version
    val list by produceState(emptyList<Track>(), version) { value = Originals.list(ctx) }
    return list
}

/**
 * The Originals' heading in the Music tab, opening their page. In test builds
 * it shows even with none yet, with a note saying where they go; a release
 * build shows nothing until there is one.
 */
@Composable
fun OriginalsHeading(count: Int, modifier: Modifier = Modifier, onOpen: (() -> Unit)? = null) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onOpen != null) Modifier.clickable(role = Role.Button, onClick = onOpen) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
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
        if (onOpen != null) {
            Text(stringResource(R.string.originals_see_all), style = ThrumType.meta, color = ThrumAccentInk)
            ThrumIcon(name = "chev", tint = ThrumAccentInk, size = 16.dp)
        }
    }
}

/**
 * The Originals' own page: every Original, playable, with "Remove from Thrum
 * Originals" on the ones copied in (test builds only).
 */
@Composable
fun OriginalsScreen(haptics: Map<String, Haptic>, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val originals = rememberOriginals()
    BackHandler(onBack = onClose)
    val removeTitle = stringResource(R.string.originals_remove)
    ThrumPage {
        ThrumTopBar(title = stringResource(R.string.originals_title), onBack = onClose)
        Text(
            stringResource(R.string.originals_page_body),
            style = ThrumType.body,
            color = ThrumInk2,
            modifier = Modifier.padding(start = Space.S1, end = Space.S1, bottom = Space.S4),
        )
        if (originals.isEmpty()) {
            Text(
                stringResource(R.string.originals_empty_test_build),
                style = ThrumType.meta,
                color = ThrumInk2,
                modifier = Modifier.padding(horizontal = Space.S1),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            originals.forEachIndexed { index, track ->
                val copied = BuildConfig.DEBUG && Originals.isCopied(ctx, track.sourceUri)
                OriginalRow(
                    track = track,
                    index = index,
                    count = originals.size,
                    haptics = haptics,
                    onOpen = { Player.openSong(ctx, track, originals) },
                    onPlayHere = { Player.playHere(ctx, track, originals) },
                    menu = if (copied) {
                        listOf(
                            RowMenuItem("trash", removeTitle, warn = true) {
                                if (Player.now?.track?.sourceUri == track.sourceUri) Player.stop(ctx)
                                scope.launch { Originals.remove(ctx, track.sourceUri) }
                            },
                        )
                    } else {
                        emptyList()
                    },
                )
            }
        }
    }
}

/**
 * One Original: the same row as a song. Ringtone, sharing and exporting aren't
 * offered yet: they are built for the phone's own files, and haven't been tried
 * on a file that lives inside the app.
 */
@Composable
fun OriginalRow(
    track: Track,
    index: Int,
    count: Int,
    haptics: Map<String, Haptic>,
    onOpen: () -> Unit,
    onPlayHere: () -> Unit,
    menu: List<RowMenuItem> = emptyList(),
) {
    LibraryRow(
        name = track.name,
        subtitle = track.durationMs.takeIf { it > 0 }?.let(::clockOf).orEmpty(),
        score = haptics[track.sourceUri]?.score,
        kindIcon = "music",
        first = index == 0,
        last = index == count - 1,
        canPlay = track.readable,
        playing = Player.now?.track?.sourceUri == track.sourceUri && Player.now?.playing == true,
        selecting = false,
        selected = false,
        onPlay = onPlayHere,
        onOpen = onOpen,
        onSelect = {},
        menu = menu,
    )
}

/** Test builds only: the ⋮ menu item that copies a song into the Originals on this phone. */
fun copyToOriginalsItem(ctx: Context, scope: kotlinx.coroutines.CoroutineScope, track: Track, title: String, help: String): RowMenuItem =
    RowMenuItem("spark", title, help) {
        scope.launch {
            val added = Originals.copyIn(ctx, track)
            say(ctx, if (added != null) ctx.getString(R.string.originals_copied, added) else ctx.getString(R.string.originals_copy_failed))
        }
    }
