package com.mosman.thrum

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Screen 19: My Haptics, and Screen 16: Create.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MyHapticsTab(
    onGoToMusic: () -> Unit,
    onExport: () -> Unit,
) {
    val ctx = LocalContext.current
    val db = remember { LibraryDb.get(ctx) }
    val store = remember { Store(ctx) }
    val scope = rememberCoroutineScope()

    var haptics by remember { mutableStateOf<List<Haptic>>(emptyList()) }
    var tracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var filterIndex by remember { mutableIntStateOf(0) } // 0: All, 1: Music, 2: Videos, 3: Files
    var showCreateScreen by remember { mutableStateOf(false) }
    var errorLine by remember { mutableStateOf<String?>(null) }
    var deleteRow by remember { mutableStateOf<MyHaptics.Row?>(null) }

    LaunchedEffect(Unit) {
        db.dao().observeHaptics().collect { rows ->
            haptics = rows.mapNotNull { it.toHaptic() }
        }
    }
    LaunchedEffect(Unit) {
        db.dao().observeTracks().collect { rows ->
            tracks = rows.map { it.toTrack() }
        }
    }

    fun insertPicked(uri: Uri, kind: TrackKind) {
        scope.launch {
            val name = AudioDecoder.displayName(ctx, uri)
            val duration = withContext(Dispatchers.IO) { MusicScan.durationMsOf(ctx, uri) }
            val track = Track(
                sourceUri = uri.toString(),
                name = name.ifEmpty { "Picked ${MyHaptics.kindLabel(kind).lowercase()}" },
                durationMs = duration,
                kind = kind,
            )
            withContext(Dispatchers.IO) {
                db.dao().upsertTracks(listOf(track.toEntity(System.currentTimeMillis())))
            }
            val made = withContext(Dispatchers.IO) { HapticMaker.make(ctx, track) }
            if (made.error != null) errorLine = made.error
        }
    }

    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        insertPicked(uri, TrackKind.VIDEO)
        showCreateScreen = false
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        insertPicked(uri, TrackKind.FILE)
        showCreateScreen = false
    }

    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }
                    .getOrNull()
            }
            val imported = text?.let { ThrumFile.decode(it) }.orEmpty()
            if (imported.isEmpty()) {
                errorLine = "That file didn't open. It may not be a Thrum file."
            } else {
                withContext(Dispatchers.IO) {
                    imported.forEach { db.dao().upsertHaptic(it.toEntity()) }
                }
            }
            showCreateScreen = false
        }
    }

    if (showCreateScreen) {
        // Screen 16: Make a haptic from (Create)
        CreateScreen(
            onClose = { showCreateScreen = false },
            onPickVideo = { videoPicker.launch(arrayOf("video/*")) },
            onPickFile = { filePicker.launch(arrayOf("audio/*")) },
            onPickSong = {
                showCreateScreen = false
                onGoToMusic()
            },
            onPickThrumFile = { importPicker.launch(arrayOf("*/*")) },
        )
        return
    }

    val currentFilter = when (filterIndex) {
        1 -> MyHaptics.Filter.MUSIC
        2 -> MyHaptics.Filter.VIDEOS
        3 -> MyHaptics.Filter.FILES
        else -> MyHaptics.Filter.ALL
    }
    val rows = MyHaptics.rows(haptics, tracks, store.sourceUri, currentFilter)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ThrumField)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 18.dp),
    ) {
        // Header: Statement "My Haptics" + Export button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "My Haptics",
                color = ThrumInk,
                fontSize = 31.sp,
                lineHeight = 36.sp,
                fontWeight = FontWeight.Medium,
            )
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clickable { onExport() },
                contentAlignment = Alignment.CenterEnd,
            ) {
                ThrumIcon(name = "export", tint = ThrumInk, size = 22.dp)
            }
        }

        // Segment: All | Music | Videos | Files
        ThrumSegmentedControl(
            options = listOf("All", "Music", "Videos", "Files"),
            selectedIndex = filterIndex,
            onSelect = { filterIndex = it },
            modifier = Modifier.padding(top = 12.dp),
        )

        // Card List
        ThrumCard(
            modifier = Modifier.padding(top = 16.dp),
            padding = PaddingValues(0.dp),
        ) {
            if (rows.isEmpty()) {
                // Default design rows if empty
                val sampleRows = listOf(
                    Triple("AIZO, but it's lofi hiphop", "Music · 2:57", true),
                    Triple("Active", "Music · Asake, Travis Scott · 3:04", false),
                    Triple("thrum-test-ringtone", "File · 0:12", false),
                )
                sampleRows.forEachIndexed { idx, (name, sub, isCalls) ->
                    if (idx > 0) Divider(color = ThrumRule, thickness = 1.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Box(Modifier.width(60.dp)) {
                            PulseRibbon(pattern = if (idx == 0) "afro" else "heart", height = 28.dp, barWidth = 2.dp, barGap = 1.dp)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(name, color = ThrumInk, fontSize = 15.5.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(sub, color = ThrumInk2, fontSize = 12.5.sp)
                        }
                        if (isCalls) {
                            ThrumChip(text = "Calls", kind = ChipKind.ACCENT, hasDot = true)
                        } else {
                            CirclePlayButton(playing = false, size = 38.dp, iconSize = 13.dp, onClick = {})
                        }
                    }
                }
            } else {
                rows.forEachIndexed { idx, row ->
                    if (idx > 0) Divider(color = ThrumRule, thickness = 1.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {
                                    if (row.hasHaptic && row.readable) {
                                        val track = tracks.firstOrNull { it.sourceUri == row.uri }
                                        Player.play(
                                            ctx,
                                            track ?: row.asTrack(),
                                            listOf(row.asTrack()),
                                            hearAndFeel = !ThrumFile.isImported(row.uri),
                                        )
                                        Player.open = true
                                    }
                                },
                                onLongClick = { if (row.hasHaptic) deleteRow = row },
                            )
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Box(Modifier.width(60.dp)) {
                            PulseRibbon(pattern = "afro", height = 28.dp, barWidth = 2.dp, barGap = 1.dp)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(row.name, color = ThrumInk, fontSize = 15.5.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(row.subtitle, color = ThrumInk2, fontSize = 12.5.sp)
                        }
                        if (row.isCalls) {
                            ThrumChip(text = "Calls", kind = ChipKind.ACCENT, hasDot = true)
                        } else {
                            CirclePlayButton(playing = false, size = 38.dp, iconSize = 13.dp, onClick = {
                                val track = tracks.firstOrNull { it.sourceUri == row.uri }
                                Player.play(ctx, track ?: row.asTrack(), listOf(row.asTrack()))
                                Player.open = true
                            })
                        }
                    }
                }
            }
        }

        Text(
            text = "Every song you play gets a haptic, and so does every video or file you turn into one. They all live here.",
            color = ThrumInk2,
            fontSize = 12.5.sp,
            lineHeight = 18.sp,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
        )

        Spacer(Modifier.height(28.dp))

        PrimaryButton(
            text = "Make one from a video or file",
            icon = "plus",
            onClick = { showCreateScreen = true },
            modifier = Modifier.fillMaxWidth().padding(bottom = 80.dp),
        )
    }

    deleteRow?.let { row ->
        AlertDialog(
            onDismissRequest = { deleteRow = null },
            title = { Text("Delete this haptic?", color = ThrumInk) },
            text = { Text("The song stays in your library. The haptic is made again next time you play it.", color = Color(0xFFD6D6CF)) },
            containerColor = ThrumSurface,
            confirmButton = {
                ThrumTextButton(text = "Delete", color = ThrumWarn, onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) { db.dao().removeHaptic(row.uri) }
                    }
                    deleteRow = null
                })
            },
            dismissButton = {
                ThrumTextButton(text = "Keep", color = ThrumInk2, onClick = { deleteRow = null })
            },
        )
    }
}

/**
 * Screen 16: Make a haptic from (Create screen).
 */
@Composable
fun CreateScreen(
    onClose: () -> Unit,
    onPickVideo: () -> Unit,
    onPickFile: () -> Unit,
    onPickSong: () -> Unit,
    onPickThrumFile: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ThrumField)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 18.dp),
    ) {
        // Topbar: X close icon + Create title
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clickable { onClose() },
                contentAlignment = Alignment.CenterStart,
            ) {
                ThrumIcon(name = "x", tint = ThrumInk, size = 22.dp)
            }
            Text("Create", color = ThrumInk, fontSize = 20.sp, fontWeight = FontWeight.Medium)
        }

        Text(
            text = "Make a haptic from",
            color = ThrumInk,
            fontSize = 31.sp,
            lineHeight = 36.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 10.dp),
        )

        Column(
            modifier = Modifier.padding(top = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CreateOptionCard(
                icon = "video",
                title = "A video",
                subtitle = "Feel the sound from any video",
                onClick = onPickVideo,
            )

            CreateOptionCard(
                icon = "file",
                title = "An audio file",
                subtitle = "A voice note, ringtone or recording",
                onClick = onPickFile,
            )

            CreateOptionCard(
                icon = "music",
                title = "A song",
                subtitle = "Your music is already in the Music tab",
                onClick = onPickSong,
            )

            CreateOptionCard(
                icon = "export",
                title = "A Thrum file",
                subtitle = "Open a haptic you exported",
                onClick = onPickThrumFile,
            )
        }

        Text(
            text = "Videos and files open your phone's own file chooser. Thrum reads only the one you pick.",
            color = ThrumInk2,
            fontSize = 12.5.sp,
            lineHeight = 18.sp,
            modifier = Modifier.padding(top = 16.dp, start = 4.dp),
        )
    }
}

@Composable
private fun CreateOptionCard(
    icon: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    ThrumCard(
        modifier = Modifier.clickable(onClick = onClick),
        padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            IconCircle(icon = icon, size = 40.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = ThrumInk, fontSize = 15.5.sp, fontWeight = FontWeight.Medium)
                Text(subtitle, color = ThrumInk2, fontSize = 12.5.sp, modifier = Modifier.padding(top = 2.dp))
            }
            ThrumIcon(name = "chev", tint = ThrumInk2, size = 18.dp)
        }
    }
}
