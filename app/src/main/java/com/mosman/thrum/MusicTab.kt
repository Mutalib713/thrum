package com.mosman.thrum

import android.content.Intent
import androidx.activity.compose.BackHandler
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
fun MusicTab(onExport: (List<String>?) -> Unit, onTune: (TuneTarget) -> Unit) {
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
    // Polled: Set as ringtone changes it from this tab's own menu.
    val callsUri by rememberPolled(store.sourceUri) { Store(it).sourceUri }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    val actions = rememberRowActions(onTune)
    BackHandler(enabled = selected.isNotEmpty()) { selected = emptySet() }

    // Each song's haptic, so its row can draw its rhythm (since 2026-10-06,
    // songs' haptics live here rather than in My Haptics).
    var haptics by remember { mutableStateOf(emptyMap<String, Haptic>()) }
    LaunchedEffect(Unit) {
        db.dao().observeHaptics().collect { rows -> haptics = rows.mapNotNull { it.toHaptic() }.associateBy { it.trackUri } }
    }

    val granted by rememberPolled(hasMusicAccess(ctx)) { hasMusicAccess(it) }
    // The walk's progress, polled: the worker writes it from another thread.
    val walk by rememberPolled(store.hapticsDone to store.hapticQueuePending.size) {
        val s = Store(it)
        s.hapticsDone to s.hapticQueuePending.size
    }

    LaunchedEffect(Unit) {
        // Songs only: an audio file or video picked in My Haptics is listed
        // there, with what the user made (Mutalib, 2026-10-06).
        db.dao().observeTracks().collect { rows -> tracks = rows.map { it.toTrack() }.filter { it.kind == TrackKind.MUSIC } }
    }

    fun runScan() {
        scanning = true
        scanFailed = false
        scope.launch {
            // A scan can be refused mid-way — music access taken back while
            // it runs. That used to crash the app; now it says so.
            val result = MusicScan.intoLibrary(ctx)
            scanning = false
            if (result == null) {
                scanFailed = true
                return@launch
            }
            // The first scan asks how haptics get made; until it is answered,
            // nothing is queued (see MusicScan.intoLibrary).
            if (result.found > 0 && store.hapticsMode == null) askMode = true
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
                haptics = haptics,
                query = query,
                onQuery = { query = it },
                callsUri = callsUri,
                walkDone = walk.first,
                walkLeft = walk.second,
                walking = store.hapticsMode == HapticsWorker.MODE_BACKGROUND,
                onExportAll = { onExport(null) },
                onOpen = { track, queue -> Player.openSong(ctx, track, queue) },
                onPlayHere = { track, queue -> Player.playHere(ctx, track, queue) },
                actions = actions,
                onExportSome = onExport,
                selected = selected,
                onSelected = { selected = it },
            )
        } else {
            ThrumPage(overTabs = true) {
                Header(showExport = false, onExport = { onExport(null) })
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

        RowActionsHost(actions)

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
    haptics: Map<String, Haptic>,
    query: String,
    onQuery: (String) -> Unit,
    callsUri: String?,
    walkDone: Int,
    walkLeft: Int,
    walking: Boolean,
    onExportAll: () -> Unit,
    onOpen: (Track, List<Track>) -> Unit,
    onPlayHere: (Track, List<Track>) -> Unit,
    actions: RowActions,
    onExportSome: (List<String>) -> Unit,
    selected: Set<String>,
    onSelected: (Set<String>) -> Unit,
) {
    val shown = Track.search(tracks, query)
    val selecting = selected.isNotEmpty()
    // A song's menu: no Rename and no Delete here, Mutalib's pick. These are
    // the phone's own songs, and a scan would bring a renamed one back.
    val menuRingtone = stringResource(R.string.menu_ringtone)
    val menuRingtoneHelp = stringResource(R.string.menu_ringtone_help)
    val menuTune = stringResource(R.string.tune_title)
    val menuShare = stringResource(R.string.menu_share)
    val menuShareHelp = stringResource(R.string.menu_share_help)
    val menuExport = stringResource(R.string.menu_export)
    val menuExportHelp = stringResource(R.string.menu_export_help)
    fun menuFor(track: Track): List<RowMenuItem> = if (!track.readable) {
        emptyList()
    } else {
        listOf(
            RowMenuItem("bell", menuRingtone, menuRingtoneHelp) { actions.setAsRingtone(track) },
            RowMenuItem("tune", menuTune) { actions.tune(track) },
            RowMenuItem("share", menuShare, menuShareHelp) { actions.share(listOf(track)) },
            RowMenuItem("export", menuExport, menuExportHelp) { onExportSome(listOf(track.sourceUri)) },
        )
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
            if (selecting) {
                SelectionBar(
                    count = selected.size,
                    total = shown.size,
                    onClose = { onSelected(emptySet()) },
                    onSelectAll = { onSelected(shown.filter { it.readable }.map { it.sourceUri }.toSet()) },
                    onShare = { actions.share(shown.filter { it.sourceUri in selected }) },
                    onExport = { onExportSome(selected.toList()) },
                    onDelete = null,
                )
            } else {
                Header(showExport = true, onExport = onExportAll)
            }
        }
        if (!selecting) item { SearchField(query = query, onQuery = onQuery) }
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
            val sub = listOfNotNull(track.artist.ifEmpty { null }, track.durationMs.takeIf { it > 0 }?.let(::clockOf))
            LibraryRow(
                name = track.name,
                subtitle = sub.joinToString(" · "),
                score = haptics[track.sourceUri]?.score,
                kindIcon = "music",
                first = index == 0,
                last = index == shown.lastIndex,
                canPlay = track.readable,
                playing = Player.now?.track?.sourceUri == track.sourceUri && Player.now?.playing == true,
                selecting = selecting,
                selected = track.sourceUri in selected,
                onPlay = { onPlayHere(track, shown) },
                onOpen = { if (track.readable) onOpen(track, shown) },
                onSelect = {
                    if (track.readable) {
                        onSelected(if (track.sourceUri in selected) selected - track.sourceUri else selected + track.sourceUri)
                    }
                },
                menu = menuFor(track),
                isCalls = track.sourceUri == callsUri,
                note = if (!track.readable) stringResource(R.string.player_unreadable) else null,
                noteWarn = true,
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
