package com.mosman.thrum

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
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
 * Long-press deletes a haptic, with a confirmation: a long-press that
 * silently destroys work is how people learn to distrust a list.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MyHapticsTab(onGoToMusic: () -> Unit, onExport: () -> Unit) {
    val ctx = LocalContext.current
    val db = remember { LibraryDb.get(ctx) }
    val store = remember { Store(ctx) }
    val scope = rememberCoroutineScope()

    var haptics by remember { mutableStateOf<List<Haptic>>(emptyList()) }
    var tracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var filterIndex by remember { mutableIntStateOf(0) }
    var showCreate by remember { mutableStateOf(false) }
    var errorLine by remember { mutableStateOf<String?>(null) }
    var deleteRow by remember { mutableStateOf<MyHaptics.Row?>(null) }
    val callsUri = remember { store.sourceUri }

    LaunchedEffect(Unit) {
        db.dao().observeHaptics().collect { rows -> haptics = rows.mapNotNull { it.toHaptic() } }
    }
    LaunchedEffect(Unit) {
        db.dao().observeTracks().collect { rows -> tracks = rows.map { it.toTrack() } }
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

    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        showCreate = false
        insertPicked(uri, TrackKind.VIDEO)
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        showCreate = false
        insertPicked(uri, TrackKind.FILE)
    }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        showCreate = false
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

    if (showCreate) {
        CreateScreen(
            onClose = { showCreate = false },
            onPickVideo = { videoPicker.launch(arrayOf("video/*")) },
            onPickFile = { filePicker.launch(arrayOf("audio/*")) },
            onPickSong = {
                showCreate = false
                onGoToMusic()
            },
            // Any file: a Thrum file is saved as plain bytes, and the phone's
            // chooser reports such files under different types on different
            // phones. Anything that is not one is refused in a sentence.
            onPickThrumFile = { importPicker.launch(arrayOf("*/*")) },
        )
        return
    }

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
                if (haptics.isNotEmpty()) {
                    IconButtonBox(icon = "export", label = stringResource(R.string.export_title), onClick = onExport)
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
        if (rows.isEmpty()) {
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
            HapticRow(
                row = row,
                haptic = hapticByUri[row.uri],
                first = index == 0,
                last = index == rows.lastIndex,
                onOpen = { open(row) },
                onPlayHere = { playHere(row) },
                onLongPress = { if (row.hasHaptic) deleteRow = row },
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
        item {
            Spacer(Modifier.height(Space.S5))
            PrimaryButton(
                text = stringResource(R.string.haptics_make),
                icon = "plus",
                onClick = { showCreate = true },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    deleteRow?.let { row ->
        AlertDialog(
            onDismissRequest = { deleteRow = null },
            title = { Text(stringResource(R.string.haptics_delete_title), style = ThrumType.title, color = ThrumInk) },
            text = { Text(stringResource(R.string.haptics_delete_body), style = ThrumType.body, color = ThrumInkSoft) },
            containerColor = ThrumSurface,
            confirmButton = {
                ThrumTextButton(
                    text = stringResource(R.string.haptics_delete_confirm),
                    color = ThrumWarn,
                    onClick = {
                        scope.launch { withContext(Dispatchers.IO) { db.dao().removeHaptic(row.uri) } }
                        deleteRow = null
                    },
                )
            },
            dismissButton = {
                ThrumTextButton(
                    text = stringResource(R.string.haptics_delete_cancel),
                    color = ThrumInk2,
                    onClick = { deleteRow = null },
                )
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HapticRow(
    row: MyHaptics.Row,
    haptic: Haptic?,
    first: Boolean,
    last: Boolean,
    onOpen: () -> Unit,
    onPlayHere: () -> Unit,
    onLongPress: () -> Unit,
) {
    val corner = Radius.large
    val shape = when {
        first && last -> RoundedCornerShape(corner)
        first -> RoundedCornerShape(topStart = corner, topEnd = corner)
        last -> RoundedCornerShape(bottomStart = corner, bottomEnd = corner)
        else -> RectangleShape
    }
    val canPlay = row.hasHaptic && row.readable
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(ThrumSurface)
            .combinedClickable(role = Role.Button, onClick = onOpen, onLongClick = onLongPress),
    ) {
        if (!first) HorizontalDivider(color = ThrumRule, thickness = 1.dp)
        Row(
            modifier = Modifier.padding(horizontal = Space.S4, vertical = Space.S3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.width(60.dp)) {
                if (haptic != null) {
                    PulseRibbon(score = haptic.score, limit = ROW_RIBBON_STEPS, height = 28.dp, barWidth = 2.dp, barGap = 1.dp)
                } else {
                    IconCircle(icon = kindIcon(row.kind), size = 40.dp, iconSize = 18.dp)
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    row.name,
                    style = ThrumType.row,
                    color = if (row.readable) ThrumInk else ThrumInk2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(row.subtitle, style = ThrumType.meta, color = ThrumInk2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                when {
                    !row.readable -> Text(stringResource(R.string.player_unreadable), style = ThrumType.meta, color = ThrumWarn)
                    !row.hasHaptic -> Text(stringResource(R.string.haptics_making), style = ThrumType.meta, color = ThrumInk2)
                }
            }
            if (row.isCalls) ThrumChip(text = stringResource(R.string.haptics_calls_badge), hasDot = true)
            if (canPlay) {
                CirclePlayButton(
                    playing = Player.now?.track?.sourceUri == row.uri && Player.now?.playing == true,
                    size = 38.dp,
                    iconSize = 13.dp,
                    onClick = onPlayHere,
                )
            }
        }
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

/** Screen 16: make a haptic from a video, an audio file, a song, or a Thrum file. */
@Composable
fun CreateScreen(
    onClose: () -> Unit,
    onPickVideo: () -> Unit,
    onPickFile: () -> Unit,
    onPickSong: () -> Unit,
    onPickThrumFile: () -> Unit,
) {
    BackHandler(onBack = onClose)
    ThrumPage(overTabs = true) {
        ThrumTopBar(title = stringResource(R.string.home_card_create), onBack = onClose)
        Text(
            stringResource(R.string.create_title),
            style = ThrumType.statement,
            color = ThrumInk,
            modifier = Modifier
                .padding(top = 10.dp)
                .semantics { heading() },
        )
        Column(
            modifier = Modifier.padding(top = Space.S5),
            verticalArrangement = Arrangement.spacedBy(Space.S3),
        ) {
            CreateOption("video", stringResource(R.string.create_video), stringResource(R.string.create_video_help), onPickVideo)
            CreateOption("file", stringResource(R.string.create_file), stringResource(R.string.create_file_help), onPickFile)
            CreateOption("music", stringResource(R.string.create_song), stringResource(R.string.create_song_help), onPickSong)
            CreateOption("export", stringResource(R.string.import_option), stringResource(R.string.import_option_help), onPickThrumFile)
        }
        Text(
            stringResource(R.string.create_privacy),
            style = ThrumType.meta,
            color = ThrumInk2,
            modifier = Modifier.padding(top = Space.S4, start = Space.S1),
        )
    }
}

@Composable
private fun CreateOption(icon: String, title: String, subtitle: String, onClick: () -> Unit) {
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
