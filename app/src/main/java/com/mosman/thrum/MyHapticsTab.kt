package com.mosman.thrum

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * My Haptics, screen 19, and the Create screen, 16. Task 23.
 *
 * Everything that can be felt, in one list, each row drawn with its own
 * rhythm. Videos and files arrive through the phone's own file chooser —
 * one at a time, never a scan, so Thrum never asks to see all videos. A pick
 * is made straight away; its row appears at once and says "making" until the
 * haptic lands, or the decoder's own sentence if the phone cannot read it.
 *
 * An empty list says what will live here. The first UI drawn on this screen
 * filled it with three made-up rows instead — one of them marked as the song
 * for calls — whose buttons did nothing.
 *
 * Since 2026-10-06 (Mutalib): only what the user **made** is listed here;
 * songs keep their haptics in Music. Each row is drawn as he picked from
 * the sketch: the rhythm picture plays, the name opens the page, and ⋮ has
 * the rest (Set as ringtone, Tune, Share, Export, Rename, Delete). A
 * long-press picks several to share, export or delete together. A delete
 * always asks first: one that silently destroys work is how people learn to
 * distrust a list.
 */
@Composable
fun MyHapticsTab(
    onExport: (List<String>?) -> Unit,
    onTune: (TuneTarget) -> Unit,
    makeRequested: Boolean = false,
    onMakeShown: () -> Unit = {},
) {
    val ctx = LocalContext.current
    val db = remember { LibraryDb.get(ctx) }
    val store = remember { Store(ctx) }
    val scope = rememberCoroutineScope()

    var filterIndex by remember { mutableIntStateOf(0) }
    var showMake by remember { mutableStateOf(false) }
    var errorLine by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<Set<String>?>(null) }
    var renaming by remember { mutableStateOf<MyHaptics.Row?>(null) }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    val selecting = selected.isNotEmpty()
    val actions = rememberRowActions(onTune)
    // Polled: Set as ringtone and Rename both change it from here.
    val callsUri by rememberPolled(store.sourceUri) { Store(it).sourceUri }

    // Back leaves picking several before it leaves the tab.
    BackHandler(enabled = selecting) { selected = emptySet() }

    // The haptics and the tracks they came from, arriving together or not at
    // all. Read as two lists, the tracks often landed a moment first, and for
    // that moment every video and audio file said "Making its haptic" each
    // time the tab came back (Mutalib, 2026-10-06).
    // Coming back to the tab starts from the last list shown, so it appears
    // at once and then quietly refreshes, instead of a blank moment.
    var library by remember { mutableStateOf(lastLibrary) }
    LaunchedEffect(Unit) {
        combine(db.dao().observeHaptics(), db.dao().observeTracks()) { h, t ->
            h.mapNotNull { it.toHaptic() } to t.map { it.toTrack() }
        }.collect {
            library = it
            lastLibrary = it
        }
    }
    val haptics = library?.first.orEmpty()
    val tracks = library?.second.orEmpty()

    // Home's Create card lands here with the choice already open.
    LaunchedEffect(makeRequested) {
        if (makeRequested) {
            showMake = true
            onMakeShown()
        }
    }

    fun insertPicked(uri: Uri, kind: TrackKind) {
        errorLine = null
        scope.launch {
            val name = AudioDecoder.displayName(ctx, uri)
            val duration = withContext(Dispatchers.IO) { MusicScan.durationMsOf(ctx, uri) }
            val track = Track(
                sourceUri = uri.toString(),
                name = name.ifEmpty { "Picked ${MyHaptics.kindLabel(kind).lowercase()}" },
                durationMs = duration,
                kind = kind,
            )
            withContext(Dispatchers.IO) { db.dao().upsertTracks(listOf(track.toEntity(System.currentTimeMillis()))) }
            // The row is already showing "making"; this is what lands in it.
            val made = HapticMaker.make(ctx, track)
            if (made.error != null) errorLine = made.error
        }
    }

    val videoPicker = rememberLauncherForActivityResult(OpenOnShelf(VIDEOS_SHELF)) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        showMake = false
        insertPicked(uri, TrackKind.VIDEO)
    }
    val audioPicker = rememberLauncherForActivityResult(OpenOnShelf(AUDIO_SHELF)) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        showMake = false
        insertPicked(uri, TrackKind.FILE)
    }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        showMake = false
        errorLine = null
        scope.launch {
            val text = withContext(Dispatchers.IO) { readSmallText(ctx, uri) }
            val imported = text?.let { ThrumFile.decode(it) }.orEmpty()
            if (imported.isEmpty()) {
                errorLine = ctx.getString(R.string.import_failed)
            } else {
                withContext(Dispatchers.IO) { imported.forEach { db.dao().upsertHaptic(it.toEntity()) } }
            }
        }
    }

    fun pickAudio() = audioPicker.launch(arrayOf("audio/*"))
    fun pickVideo() = videoPicker.launch(arrayOf("video/*"))

    // Any file: a Thrum file is saved as plain bytes, and the phone's chooser
    // reports such files under different types on different phones. Anything
    // that is not one is refused in a sentence.
    fun pickThrumFile() = importPicker.launch(arrayOf("*/*"))

    val filter = MyHaptics.Filter.entries[filterIndex]
    val rows = MyHaptics.rows(haptics, tracks, callsUri, filter)
    val hapticByUri = remember(haptics) { haptics.associateBy { it.trackUri } }
    val trackByUri = remember(tracks) { tracks.associateBy { it.sourceUri } }
    val playable = rows.filter { it.hasHaptic && it.readable }.map { trackByUri[it.uri] ?: it.asTrack() }

    // The row opens the haptic's page; its play button plays it here.
    fun open(row: MyHaptics.Row) {
        if (!row.hasHaptic || !row.readable) return
        Player.openSong(ctx, trackByUri[row.uri] ?: row.asTrack(), playable)
    }

    fun playHere(row: MyHaptics.Row) {
        if (!row.hasHaptic || !row.readable) return
        Player.playHere(ctx, trackByUri[row.uri] ?: row.asTrack(), playable)
    }

    fun toggle(row: MyHaptics.Row) {
        selected = if (row.uri in selected) selected - row.uri else selected + row.uri
    }

    fun trackOf(row: MyHaptics.Row): Track = trackByUri[row.uri] ?: row.asTrack()

    // The menu: what a row can do depends on what it is. An imported
    // haptic has no sound, so it can be used for calls but can't be a
    // ringtone, and has no song to tune from. A file still being made, or
    // one the phone can't read, can only be renamed or deleted.
    val menuRingtone = stringResource(R.string.menu_ringtone)
    val menuRingtoneHelp = stringResource(R.string.menu_ringtone_help)
    val menuCalls = stringResource(R.string.menu_calls)
    val menuCallsHelp = stringResource(R.string.menu_calls_help)
    val menuTune = stringResource(R.string.tune_title)
    val menuShare = stringResource(R.string.menu_share)
    val menuShareHelp = stringResource(R.string.menu_share_help)
    val menuExport = stringResource(R.string.menu_export)
    val menuExportHelp = stringResource(R.string.menu_export_help)
    val menuRename = stringResource(R.string.menu_rename)
    val menuDelete = stringResource(R.string.menu_delete)
    fun menuFor(row: MyHaptics.Row): List<RowMenuItem> {
        val imported = ThrumFile.isImported(row.uri)
        val ready = row.hasHaptic && row.readable
        return buildList {
            if (ready && imported) add(RowMenuItem("phone", menuCalls, menuCallsHelp) { actions.useForCalls(trackOf(row)) })
            if (ready && !imported) add(RowMenuItem("bell", menuRingtone, menuRingtoneHelp) { actions.setAsRingtone(trackOf(row)) })
            if (ready && !imported) add(RowMenuItem("tune", menuTune) { actions.tune(trackOf(row)) })
            if (ready) add(RowMenuItem("share", menuShare, menuShareHelp) { actions.share(listOf(trackOf(row))) })
            if (ready) add(RowMenuItem("export", menuExport, menuExportHelp) { onExport(listOf(row.uri)) })
            add(RowMenuItem("edit", menuRename) { renaming = row })
            add(RowMenuItem("trash", menuDelete, warn = true) { deleting = setOf(row.uri) })
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(ThrumField)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
            contentPadding = PaddingValues(
                start = Space.S5,
                end = Space.S5,
                top = 18.dp,
                bottom = if (Player.now != null) 100.dp else Space.S6,
            ),
        ) {
            item {
                if (selecting) {
                    SelectionBar(
                        count = selected.size,
                        total = rows.size,
                        onClose = { selected = emptySet() },
                        onSelectAll = { selected = rows.map { it.uri }.toSet() },
                        onShare = {
                            actions.share(rows.filter { it.uri in selected && it.hasHaptic }.map { trackOf(it) })
                        },
                        onExport = { onExport(selected.toList()) },
                        onDelete = { deleting = selected },
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp, bottom = Space.S1),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.tab_haptics),
                            style = ThrumType.statement,
                            color = ThrumInk,
                            modifier = Modifier.semantics { heading() },
                        )
                        val made = MyHaptics.rows(haptics, tracks, callsUri, MyHaptics.Filter.ALL).filter { it.hasHaptic }
                        if (made.isNotEmpty()) {
                            // Everything listed here: what the user made.
                            IconButtonBox(
                                icon = "export",
                                label = stringResource(R.string.export_title),
                                onClick = { onExport(made.map { it.uri }) },
                            )
                        }
                    }
                }
            }
            item {
                ThrumSegmentedControl(
                    // In MyHaptics.Filter's order: All, Audio, Video.
                    options = listOf(
                        stringResource(R.string.haptics_filter_all),
                        stringResource(R.string.haptics_filter_audio),
                        stringResource(R.string.haptics_filter_video),
                    ),
                    selectedIndex = filterIndex,
                    onSelect = { filterIndex = it },
                    modifier = Modifier.padding(top = Space.S3, bottom = Space.S4),
                )
            }
            // Making one sits at the top, and speaks for the filter it is under:
            // All asks which kind, Audio and Video go straight to that kind
            // (Mutalib, 2026-10-06). It used to wait below the whole list.
            if (!selecting) item {
                when (filter) {
                    MyHaptics.Filter.ALL -> MakeOption(
                        icon = "plus",
                        title = stringResource(R.string.haptics_make),
                        subtitle = stringResource(R.string.haptics_make_help),
                        onClick = { showMake = true },
                    )
                    MyHaptics.Filter.AUDIO -> MakeOption(
                        icon = "music",
                        title = stringResource(R.string.haptics_make_audio),
                        subtitle = stringResource(R.string.make_audio_help),
                        onClick = { pickAudio() },
                    )
                    MyHaptics.Filter.VIDEO -> MakeOption(
                        icon = "video",
                        title = stringResource(R.string.haptics_make_video),
                        subtitle = stringResource(R.string.make_video_help),
                        onClick = { pickVideo() },
                    )
                }
                Spacer(Modifier.height(Space.S4))
            }
            // Said only once the list is known: an empty list a moment before
            // the rows land is the flash this screen used to show.
            if (library != null && rows.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.haptics_empty_body),
                        style = ThrumType.lead,
                        color = ThrumInk2,
                        modifier = Modifier.padding(horizontal = Space.S1),
                    )
                }
            }
            itemsIndexed(rows, key = { _, row -> row.uri }) { index, row ->
                LibraryRow(
                    name = row.name,
                    subtitle = row.subtitle,
                    score = hapticByUri[row.uri]?.score,
                    kindIcon = kindIcon(row.kind),
                    first = index == 0,
                    last = index == rows.lastIndex,
                    canPlay = row.hasHaptic && row.readable,
                    playing = Player.now?.track?.sourceUri == row.uri && Player.now?.playing == true,
                    selecting = selecting,
                    selected = row.uri in selected,
                    onPlay = { playHere(row) },
                    onOpen = { open(row) },
                    onSelect = { toggle(row) },
                    menu = menuFor(row),
                    isCalls = row.uri == callsUri,
                    note = when {
                        !row.readable -> stringResource(R.string.player_unreadable)
                        !row.hasHaptic -> stringResource(R.string.haptics_making)
                        else -> null
                    },
                    noteWarn = !row.readable,
                )
            }
            errorLine?.let { message ->
                item {
                    Text(message, style = ThrumType.body, color = ThrumWarn, modifier = Modifier.padding(top = Space.S3))
                }
            }
            if (rows.isNotEmpty()) {
                item {
                    Text(
                        stringResource(R.string.haptics_empty_body),
                        style = ThrumType.meta,
                        color = ThrumInk2,
                        modifier = Modifier.padding(horizontal = Space.S1, vertical = Space.S3),
                    )
                }
            }
        }

        // The three ways to make one, as a pop-up over the list rather than a
        // screen of its own (Mutalib, 2026-10-06).
        if (showMake) {
            ThrumBottomSheet(onDismiss = { showMake = false }) {
                Text(
                    stringResource(R.string.haptics_make),
                    style = ThrumType.heading,
                    color = ThrumInk,
                    modifier = Modifier.semantics { heading() },
                )
                Column(
                    modifier = Modifier.padding(top = Space.S4),
                    verticalArrangement = Arrangement.spacedBy(Space.S3),
                ) {
                    MakeOption("music", stringResource(R.string.make_audio), stringResource(R.string.make_audio_help)) { pickAudio() }
                    MakeOption("video", stringResource(R.string.make_video), stringResource(R.string.make_video_help)) { pickVideo() }
                    MakeOption("export", stringResource(R.string.make_thrum_file), stringResource(R.string.make_thrum_file_help)) { pickThrumFile() }
                }
                Text(
                    stringResource(R.string.make_privacy),
                    style = ThrumType.meta,
                    color = ThrumInk2,
                    modifier = Modifier.padding(top = Space.S4, start = Space.S1, bottom = Space.S2),
                )
            }
        }
    }

    RowActionsHost(actions)

    deleting?.let { uris ->
        DeleteDialog(
            count = uris.size,
            onDismiss = { deleting = null },
            onDelete = {
                scope.launch { LibraryActions.delete(ctx, uris) }
                selected = selected - uris
                deleting = null
            },
        )
    }
    renaming?.let { row ->
        RenameDialog(
            current = row.name,
            onDismiss = { renaming = null },
            onRename = { name ->
                scope.launch { LibraryActions.rename(ctx, row.uri, name) }
                renaming = null
            },
        )
    }
}

private fun kindIcon(kind: TrackKind): String = when (kind) {
    TrackKind.MUSIC -> "music"
    TrackKind.VIDEO -> "video"
    TrackKind.FILE -> "file"
}

/**
 * The picked file as text, or null — and null for anything over
 * [MAX_IMPORT_BYTES] rather than reading it. Any file can be picked here;
 * reading a two-gigabyte video into memory to discover it is not a Thrum
 * file would take the app down with it. A Thrum file of hundreds of whole
 * songs is a few megabytes.
 */
private fun readSmallText(ctx: Context, uri: Uri): String? = runCatching {
    ctx.contentResolver.openInputStream(uri)?.use { input ->
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            total += n
            if (total > MAX_IMPORT_BYTES) return@runCatching null
            out.write(buffer, 0, n)
        }
        out.toByteArray().decodeToString()
    }
}.getOrNull()

private const val MAX_IMPORT_BYTES = 64L * 1024 * 1024

/**
 * The phone's file chooser, opened on one kind of file and, where the phone
 * allows it, on that kind's own shelf: audio on Audio, videos on Videos
 * (Mutalib, 2026-10-06: "audio should open to audio side"). A chooser that
 * doesn't know the shelf opens where it usually does, still showing only
 * that kind.
 *
 * The shelf's **root** address, not its document address: Android's docs
 * ask for a document, but its own chooser (Android 14 emulator, 6 October)
 * opened "Recent files" for the document and the Audio or Videos shelf for
 * the root.
 */
private class OpenOnShelf(private val shelf: String) : ActivityResultContracts.OpenDocument() {
    override fun createIntent(context: Context, input: Array<String>): Intent =
        super.createIntent(context, input).putExtra(
            DocumentsContract.EXTRA_INITIAL_URI,
            DocumentsContract.buildRootUri(MEDIA_DOCUMENTS, shelf),
        )
}

/** The list My Haptics last showed, kept while the app runs (see MyHapticsTab). */
private var lastLibrary: Pair<List<Haptic>, List<Track>>? = null

/** Android's own media shelves, as its file chooser names them. */
private const val MEDIA_DOCUMENTS = "com.android.providers.media.documents"
private const val AUDIO_SHELF = "audio_root"
private const val VIDEOS_SHELF = "videos_root"

/** One way to make a haptic: in the pop-up, and at the top of the list. */
@Composable
private fun MakeOption(icon: String, title: String, subtitle: String, onClick: () -> Unit) {
    ThrumCard(padding = PaddingValues(horizontal = Space.S4, vertical = 14.dp), onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            IconCircle(icon = icon, size = 40.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = ThrumType.row, color = ThrumInk)
                Text(subtitle, style = ThrumType.meta, color = ThrumInk2, modifier = Modifier.padding(top = 2.dp))
            }
            ThrumIcon(name = "chev", tint = ThrumInk2, size = 18.dp)
        }
    }
}
