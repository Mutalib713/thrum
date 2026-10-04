package com.mosman.thrum

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
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
 * The Music tab: screens 8, 9, 10, 11 and 12. Task 20.
 *
 * Music access is **asked only when "Scan for music" is tapped** — never at
 * launch — and refusing it costs almost nothing, because "Pick one song"
 * uses the phone's own file chooser, which needs no permission at all.
 *
 * Only what is on the phone is shown. The first UI drawn on these screens
 * added a "found so far" list of Mutalib's own songs under a progress bar
 * fixed at 55 %, a shelf of Thrum Originals that do not exist yet (tapping
 * one saved a broken song into the library), and the online catalog Mutalib
 * removed on 3 October. The built-in collection comes with Task 27; until it
 * has a single piece in it, it is not drawn.
 */
@Composable
fun MusicTab(onExport: () -> Unit) {
    val ctx = LocalContext.current
    val db = remember { LibraryDb.get(ctx) }
    val store = remember { Store(ctx) }
    val scope = rememberCoroutineScope()

    var tracks by remember { mutableStateOf<List<Track>?>(null) }
    var query by remember { mutableStateOf("") }
    var scanning by remember { mutableStateOf(false) }
    var scanFailed by remember { mutableStateOf(false) }
    var deniedByUser by remember { mutableStateOf(false) }
    var askMode by remember { mutableStateOf(false) }
    var choice by remember { mutableStateOf(HapticsWorker.MODE_BACKGROUND) }
    val callsUri = remember { store.sourceUri }

    val granted by rememberPolled(hasMusicAccess(ctx)) { hasMusicAccess(it) }
    // The walk's progress, polled: the worker writes it from another thread.
    val walk by rememberPolled(store.hapticsDone to store.hapticQueuePending.size) {
        val s = Store(it)
        s.hapticsDone to s.hapticQueuePending.size
    }

    LaunchedEffect(Unit) {
        db.dao().observeTracks().collect { rows -> tracks = rows.map { it.toTrack() } }
    }

    fun runScan() {
        scanning = true
        scanFailed = false
        scope.launch {
            // A scan can be refused mid-way — music access taken back while
            // it runs. That used to crash the app; now it says so.
            val found = withContext(Dispatchers.IO) { runCatching { MusicScan.scan(ctx) }.getOrNull() }
            scanning = false
            if (found == null) {
                scanFailed = true
                return@launch
            }
            val now = System.currentTimeMillis()
            withContext(Dispatchers.IO) { db.dao().upsertTracks(found.map { it.toEntity(now) }) }
            store.lastScanAtMs = now
            if (found.isNotEmpty()) {
                if (store.hapticsMode == null) {
                    askMode = true
                } else {
                    // Background mode only: enqueue() leaves the list alone in
                    // "as I play them" mode, where nothing walks it.
                    HapticsWorker.enqueue(ctx, found.map { it.sourceUri })
                }
            }
        }
    }

    val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) runScan() else deniedByUser = true
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        // Held past this session, or the row survives with an unreadable
        // source behind it.
        runCatching { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        scope.launch {
            val name = AudioDecoder.displayName(ctx, uri)
            val duration = withContext(Dispatchers.IO) { MusicScan.durationMsOf(ctx, uri) }
            val track = Track(
                sourceUri = uri.toString(),
                name = name.ifEmpty { "Picked song" },
                durationMs = duration,
                kind = TrackKind.MUSIC,
            )
            withContext(Dispatchers.IO) { db.dao().upsertTracks(listOf(track.toEntity(System.currentTimeMillis()))) }
        }
    }

    val all = tracks
    // The list, when there is one to show; every other state is a page.
    val listed = all?.takeIf { it.isNotEmpty() && !scanning && !(deniedByUser && !granted) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (listed != null) {
            SongList(
                tracks = listed,
                query = query,
                onQuery = { query = it },
                callsUri = callsUri,
                walkDone = walk.first,
                walkLeft = walk.second,
                walking = store.hapticsMode == HapticsWorker.MODE_BACKGROUND,
                onExport = onExport,
                onPlay = { track, queue ->
                    Player.play(ctx, track, queue)
                    Player.open = true
                },
            )
        } else {
            ThrumPage(overTabs = true) {
                Header(showExport = false, onExport = onExport)
                when {
                    // Screen 12. Granting in system settings flips this back by
                    // itself, because access is polled.
                    deniedByUser && !granted -> {
                        Spacer(Modifier.height(40.dp))
                        IconCircle(icon = "music", size = 64.dp, cornerRadius = 18.dp, iconSize = 28.dp)
                        Text(
                            stringResource(R.string.music_denied_title),
                            style = ThrumType.heading,
                            color = ThrumInk,
                            modifier = Modifier.padding(top = 18.dp),
                        )
                        Text(
                            stringResource(R.string.music_denied_body),
                            style = ThrumType.lead,
                            color = ThrumInkSoft,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                        Spacer(Modifier.height(Space.S6))
                        PrimaryButton(
                            text = stringResource(R.string.music_denied_settings),
                            onClick = { openAppInfo(ctx) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        ThrumTextButton(
                            text = stringResource(R.string.music_pick_instead),
                            onClick = { picker.launch(arrayOf("audio/*")) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = Space.S2),
                        )
                    }

                    // Screen 9. Android's own index answers in well under a
                    // second, so there is nothing to stream — just the wait.
                    scanning -> ThrumCard(modifier = Modifier.padding(top = Space.S4)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Space.S3),
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.5.dp, color = ThrumAccentInk)
                            Text(stringResource(R.string.music_scanning), style = ThrumType.lead, color = ThrumInk)
                        }
                    }

                    // Screen 8, before the library has loaded or after a scan
                    // that found nothing.
                    else -> {
                        ThrumCard(modifier = Modifier.padding(top = Space.S4), padding = PaddingValues(20.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Space.S3),
                            ) {
                                IconCircle(icon = "music", size = 40.dp)
                                Text(
                                    stringResource(R.string.music_intro_title),
                                    style = ThrumType.title,
                                    color = ThrumInk,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            Text(
                                stringResource(R.string.music_intro_body),
                                style = ThrumType.lead,
                                color = ThrumInkSoft,
                                modifier = Modifier.padding(top = 10.dp),
                            )
                            if (scanFailed) {
                                Text(
                                    stringResource(R.string.music_scan_failed),
                                    style = ThrumType.body,
                                    color = ThrumWarn,
                                    modifier = Modifier.padding(top = 10.dp),
                                )
                            } else if (store.lastScanAtMs > 0L && all != null) {
                                Text(
                                    stringResource(R.string.music_empty_after_scan),
                                    style = ThrumType.body,
                                    color = ThrumInk2,
                                    modifier = Modifier.padding(top = 10.dp),
                                )
                            }
                            Row(
                                modifier = Modifier.padding(top = 10.dp),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(Space.S2),
                            ) {
                                ThrumIcon(name = "check", tint = ThrumAccentInk, size = 16.dp)
                                Text(stringResource(R.string.music_intro_privacy), style = ThrumType.meta, color = ThrumInk)
                            }
                        }
                        Spacer(Modifier.height(Space.S6))
                        PrimaryButton(
                            text = stringResource(R.string.music_scan_action),
                            icon = "search",
                            // Asked here and nowhere else: tapped, never at launch.
                            onClick = { if (granted) runScan() else requestPermission.launch(musicPermission) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        ThrumTextButton(
                            text = stringResource(R.string.music_pick_instead),
                            onClick = { picker.launch(arrayOf("audio/*")) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = Space.S2),
                        )
                    }
                }
            }
        }

        // Screen 10: asked once, after the first scan that finds songs.
        if (askMode) {
            ThrumBottomSheet(onDismiss = { askMode = false }) {
                val found = all?.size ?: 0
                Text(
                    pluralStringResource(R.plurals.make_count, found, found),
                    style = ThrumType.heading,
                    color = ThrumInk,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    stringResource(R.string.make_question),
                    style = ThrumType.lead,
                    color = ThrumInkSoft,
                    modifier = Modifier.padding(top = Space.S2),
                )
                Column(
                    modifier = Modifier.padding(top = Space.S4),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ModeOption(
                        selected = choice == HapticsWorker.MODE_BACKGROUND,
                        title = stringResource(R.string.make_background),
                        help = stringResource(R.string.make_background_help),
                        onSelect = { choice = HapticsWorker.MODE_BACKGROUND },
                    )
                    ModeOption(
                        selected = choice == HapticsWorker.MODE_AS_PLAYED,
                        title = stringResource(R.string.make_as_played),
                        help = stringResource(R.string.make_as_played_help),
                        onSelect = { choice = HapticsWorker.MODE_AS_PLAYED },
                    )
                }
                Text(
                    stringResource(R.string.make_changeable),
                    style = ThrumType.meta,
                    color = ThrumInk2,
                    modifier = Modifier.padding(top = Space.S3),
                )
                PrimaryButton(
                    text = stringResource(R.string.make_continue),
                    onClick = {
                        store.hapticsMode = choice
                        if (choice == HapticsWorker.MODE_BACKGROUND) {
                            store.hapticsDone = 0
                            val uris = all.orEmpty().map { it.sourceUri }
                            scope.launch { HapticsWorker.enqueue(ctx, uris) }
                        }
                        askMode = false
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp),
                )
            }
        }
    }
}

@Composable
private fun Header(showExport: Boolean, onExport: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = Space.S1),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.tab_music),
            style = ThrumType.statement,
            color = ThrumInk,
            modifier = Modifier.semantics { heading() },
        )
        if (showExport) {
            ThrumTextButton(text = stringResource(R.string.export_all_action), icon = "export", onClick = onExport)
        }
    }
}

/**
 * Screen 11: the songs, with search. A lazy list, so only the rows on screen
 * are drawn — a library of hundreds of songs used to build every row, each
 * with its own drawing, before showing anything.
 */
@Composable
private fun SongList(
    tracks: List<Track>,
    query: String,
    onQuery: (String) -> Unit,
    callsUri: String?,
    walkDone: Int,
    walkLeft: Int,
    walking: Boolean,
    onExport: () -> Unit,
    onPlay: (Track, List<Track>) -> Unit,
) {
    val shown = Track.search(tracks, query)
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
        item { Header(showExport = true, onExport = onExport) }
        item { SearchField(query = query, onQuery = onQuery) }
        if (walking && walkLeft > 0) {
            item {
                ThrumCard(
                    modifier = Modifier.padding(top = Space.S3),
                    padding = PaddingValues(horizontal = 14.dp, vertical = Space.S3),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Space.S3),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.5.dp, color = ThrumAccentInk)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.make_progress, walkDone, walkDone + walkLeft),
                                style = ThrumType.row,
                                color = ThrumInk,
                            )
                            Text(stringResource(R.string.make_progress_help), style = ThrumType.meta, color = ThrumInk2)
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(14.dp)) }
        if (shown.isEmpty()) {
            item {
                Text(stringResource(R.string.music_no_match), style = ThrumType.body, color = ThrumInk2)
            }
        }
        itemsIndexed(shown, key = { _, t -> t.sourceUri }) { index, track ->
            SongRow(
                track = track,
                first = index == 0,
                last = index == shown.lastIndex,
                isCalls = track.sourceUri == callsUri,
                onPlay = { onPlay(track, shown) },
            )
        }
    }
}

@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(shape)
            .background(ThrumSurface2)
            .border(1.dp, ThrumLine, shape)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ThrumIcon(name = "search", tint = ThrumInk2, size = 18.dp)
            if (query.isEmpty()) {
                Text(stringResource(R.string.music_search_hint), style = ThrumType.lead, color = ThrumInk2)
            }
        }
        BasicTextField(
            value = query,
            onValueChange = onQuery,
            singleLine = true,
            textStyle = ThrumType.lead.copy(color = ThrumInk),
            cursorBrush = SolidColor(ThrumAccentInk),
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 28.dp),
        )
    }
}

/**
 * One song. The first and last rows round the list's corners, so the rows
 * read as one card the way the design draws them. A file the phone cannot
 * read stays in the list and says so; tapping it could only fail.
 */
@Composable
private fun SongRow(track: Track, first: Boolean, last: Boolean, isCalls: Boolean, onPlay: () -> Unit) {
    val corner = Radius.large
    val shape = when {
        first && last -> RoundedCornerShape(corner)
        first -> RoundedCornerShape(topStart = corner, topEnd = corner)
        last -> RoundedCornerShape(bottomStart = corner, bottomEnd = corner)
        else -> RectangleShape
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(ThrumSurface)
            .then(if (track.readable) Modifier.clickable(role = Role.Button, onClick = onPlay) else Modifier),
    ) {
        if (!first) HorizontalDivider(color = ThrumRule, thickness = 1.dp)
        Row(
            modifier = Modifier.padding(horizontal = Space.S4, vertical = Space.S3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            IconCircle(icon = "music", size = 40.dp, iconSize = 18.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    track.name,
                    style = ThrumType.row,
                    color = if (track.readable) ThrumInk else ThrumInk2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val sub = listOfNotNull(track.artist.ifEmpty { null }, track.durationMs.takeIf { it > 0 }?.let(::clockOf))
                if (sub.isNotEmpty()) {
                    Text(sub.joinToString(" · "), style = ThrumType.meta, color = ThrumInk2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (!track.readable) {
                    Text(stringResource(R.string.player_unreadable), style = ThrumType.meta, color = ThrumWarn)
                }
            }
            when {
                isCalls -> ThrumChip(text = stringResource(R.string.haptics_calls_badge), hasDot = true)
                track.readable -> CirclePlayButton(
                    playing = Player.now?.track?.sourceUri == track.sourceUri && Player.now?.playing == true,
                    size = 38.dp,
                    iconSize = 13.dp,
                    onClick = onPlay,
                )
            }
        }
    }
}

/** One answer to screen 10's question; the whole card is the control. */
@Composable
private fun ModeOption(selected: Boolean, title: String, help: String, onSelect: () -> Unit) {
    val shape = RoundedCornerShape(Radius.large)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(ThrumSurface2)
            .border(1.dp, if (selected) ThrumAccentInk else ThrumRule, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(Space.S4),
        horizontalArrangement = Arrangement.spacedBy(Space.S3),
    ) {
        ThrumRadio(selected = selected)
        Column {
            Text(title, style = ThrumType.row, color = ThrumInk)
            Text(help, style = ThrumType.meta, color = ThrumInk2, modifier = Modifier.padding(top = 2.dp))
        }
    }
}
