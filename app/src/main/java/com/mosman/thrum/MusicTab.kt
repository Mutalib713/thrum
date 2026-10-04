package com.mosman.thrum

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The Music tab: Screens 8, 9, 10, 11, 12, plus Catalog 28, 29, 30, 31.
 */
@Composable
fun MusicTab(
    onExport: () -> Unit,
) {
    val ctx = LocalContext.current
    val db = remember { LibraryDb.get(ctx) }
    val store = remember { Store(ctx) }
    val scope = rememberCoroutineScope()

    var tracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var scanning by remember { mutableStateOf(false) }
    var deniedByUser by remember { mutableStateOf(false) }
    var askMode by remember { mutableStateOf(false) }
    var choice by remember { mutableStateOf<String?>(HapticsWorker.MODE_BACKGROUND) }
    var activeTabSegment by remember { mutableStateOf(0) } // 0: On this phone, 1: Catalog
    var catalogQuery by remember { mutableStateOf("") }
    var catalogOffline by remember { mutableStateOf(false) }

    val permission = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }

    val granted by produceState(initialValue = false) {
        while (true) {
            value = ContextCompat.checkSelfPermission(ctx, permission) ==
                PackageManager.PERMISSION_GRANTED
            delay(POLL_MS)
        }
    }

    LaunchedEffect(Unit) {
        db.dao().observeTracks().collect { rows ->
            tracks = rows.map { it.toTrack() }
        }
    }

    fun runScan() {
        scanning = true
        scope.launch {
            // A scan can be refused mid-way — music access taken back while
            // it runs. That used to crash the app; now the scan just stops.
            val found = withContext(Dispatchers.IO) { runCatching { MusicScan.scan(ctx) }.getOrNull() }
            scanning = false
            if (found == null) return@launch
            val now = System.currentTimeMillis()
            withContext(Dispatchers.IO) {
                db.dao().upsertTracks(found.map { it.toEntity(now) })
            }
            store.lastScanAtMs = now
            if (found.isNotEmpty()) {
                if (store.hapticsMode == null) {
                    askMode = true
                } else {
                    // Background mode only: enqueue() leaves the list alone
                    // in "as I play them" mode, where nothing walks it.
                    HapticsWorker.enqueue(ctx, found.map { it.sourceUri })
                }
            }
        }
    }

    val requestPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        if (isGranted) runScan() else deniedByUser = true
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            ctx.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        scope.launch {
            val name = AudioDecoder.displayName(ctx, uri)
            val duration = withContext(Dispatchers.IO) { MusicScan.durationMsOf(ctx, uri) }
            val track = Track(
                sourceUri = uri.toString(),
                name = name.ifEmpty { "Picked song" },
                durationMs = duration,
                kind = TrackKind.MUSIC,
            )
            withContext(Dispatchers.IO) {
                db.dao().upsertTracks(listOf(track.toEntity(System.currentTimeMillis())))
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 18.dp),
        ) {
            // Header row: Title + Export action
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Music",
                    color = ThrumInk,
                    fontSize = 31.sp,
                    lineHeight = 36.sp,
                    fontWeight = FontWeight.Medium,
                )
                if (activeTabSegment == 0 && tracks.isNotEmpty() && granted) {
                    ThrumTextButton(
                        text = "Export all",
                        icon = "export",
                        color = ThrumAccent,
                        onClick = onExport,
                    )
                } else if (activeTabSegment == 1) {
                    ThrumChip(text = "Online", kind = ChipKind.ONLINE, icon = "globe")
                }
            }

            // Segment: On this phone | Catalog
            ThrumSegmentedControl(
                options = listOf("On this phone", "Catalog"),
                selectedIndex = activeTabSegment,
                onSelect = { activeTabSegment = it },
                modifier = Modifier.padding(top = 10.dp, bottom = 12.dp),
            )

            if (activeTabSegment == 1) {
                // Online Catalog Views (Screens 28, 29, 31)
                CatalogView(
                    query = catalogQuery,
                    onQueryChange = { catalogQuery = it },
                    isOffline = catalogOffline,
                    onGoToMyMusic = { activeTabSegment = 0 },
                )
            } else {
                // Local Music Views (Screens 8, 9, 11, 12)
                when {
                    // Screen 12: Music - no access
                    deniedByUser && !granted -> {
                        Spacer(Modifier.height(40.dp))
                        IconCircle(icon = "music", size = 64.dp, cornerRadius = 18.dp, iconSize = 28.dp)
                        Text(
                            text = "Thrum can't see your music",
                            color = ThrumInk,
                            fontSize = 25.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(top = 18.dp),
                        )
                        Text(
                            text = "You chose not to give music access. You can still pick one song at a time, or turn access on in Settings.",
                            color = Color(0xFFD6D6CF),
                            fontSize = 16.sp,
                            lineHeight = 24.sp,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                        Spacer(Modifier.height(32.dp))
                        PrimaryButton(
                            text = "Turn on music access",
                            onClick = {
                                runCatching {
                                    ctx.startActivity(
                                        Intent(
                                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            Uri.fromParts("package", ctx.packageName, null),
                                        ),
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        ThrumTextButton(
                            text = "Pick one song",
                            onClick = { picker.launch(arrayOf("audio/*")) },
                            color = ThrumAccent,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    // Screen 9: Scanning
                    scanning -> {
                        ThrumCard(
                            modifier = Modifier.padding(top = 16.dp),
                            padding = PaddingValues(16.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.5.dp,
                                    color = ThrumAccent,
                                )
                                Text("Scanning your phone…", color = ThrumInk, fontSize = 16.sp)
                            }
                            Box(
                                modifier = Modifier
                                    .padding(top = 12.dp)
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(ThrumRule),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.55f)
                                        .height(6.dp)
                                        .background(ThrumAccent),
                                )
                            }
                        }

                        Text(
                            text = "FOUND SO FAR",
                            color = ThrumInk2,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.4.sp,
                            modifier = Modifier.padding(top = 18.dp, bottom = 8.dp),
                        )

                        ThrumCard(padding = PaddingValues(0.dp)) {
                            listOf(
                                Pair("AIZO, but it's lofi hiphop", "Jujutsu Kaisen · 2:57"),
                                Pair("Active", "Asake, Travis Scott · 3:04"),
                                Pair("No Dulling", "Keche · 3:58"),
                                Pair("Eid Mubarak", "Harris J · 4:32"),
                                Pair("Antassalam", "Maher Zain"),
                            ).forEachIndexed { i, (name, sub) ->
                                if (i > 0) Divider(color = ThrumRule, thickness = 1.dp)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                ) {
                                    IconCircle(icon = "music", size = 40.dp, iconSize = 18.dp)
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(name, color = ThrumInk, fontSize = 15.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(sub, color = ThrumInk2, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(24.dp))
                        ThrumTextButton(
                            text = "Stop scanning",
                            onClick = { scanning = false },
                            color = ThrumInk2,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    // Screen 8: First time (empty library)
                    tracks.isEmpty() -> {
                        Text(
                            text = "THRUM ORIGINALS",
                            color = ThrumInk2,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.4.sp,
                            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                        )
                        OriginalsShelf(
                            onSelect = { name ->
                                val rhythm = Demo.rhythm()
                                val fakeTrack = Track(
                                    sourceUri = "original://$name",
                                    name = name,
                                    artist = "Thrum Original",
                                    durationMs = 45000L,
                                    kind = TrackKind.MUSIC,
                                )
                                Player.play(ctx, fakeTrack, listOf(fakeTrack))
                                Player.open = true
                            },
                        )

                        ThrumCard(
                            modifier = Modifier.padding(top = 18.dp),
                            padding = PaddingValues(20.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                IconCircle(icon = "music", size = 40.dp)
                                Text(
                                    text = "Your music isn't here yet",
                                    color = ThrumInk,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                            Text(
                                text = "Scan your phone to find your songs, then play any of them here and feel every beat.",
                                color = Color(0xFFD6D6CF),
                                fontSize = 16.sp,
                                lineHeight = 24.sp,
                                modifier = Modifier.padding(top = 10.dp),
                            )
                            Row(
                                modifier = Modifier.padding(top = 10.dp),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                ThrumIcon(name = "check", tint = ThrumAccent, size = 16.dp)
                                Text(
                                    text = "Thrum reads music and audio files only. Nothing leaves your phone.",
                                    color = ThrumInk,
                                    fontSize = 12.5.sp,
                                    lineHeight = 17.sp,
                                )
                            }
                        }

                        Spacer(Modifier.height(32.dp))
                        PrimaryButton(
                            text = "Scan for music",
                            icon = "search",
                            onClick = { requestPermission.launch(permission) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        ThrumTextButton(
                            text = "Pick one song instead",
                            onClick = { picker.launch(arrayOf("audio/*")) },
                            color = ThrumAccent,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    // Screen 11: Song list
                    else -> {
                        // Search bar input
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(ThrumSurface2)
                                .border(1.dp, ThrumLine, RoundedCornerShape(12.dp))
                                .padding(horizontal = 14.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                ThrumIcon(name = "search", tint = ThrumInk2, size = 18.dp)
                                if (query.isEmpty()) {
                                    Text("Search your music", color = ThrumInk2, fontSize = 15.sp)
                                }
                            }
                            BasicTextField(
                                value = query,
                                onValueChange = { query = it },
                                textStyle = TextStyle(color = ThrumInk, fontSize = 15.sp),
                                cursorBrush = SolidColor(ThrumAccent),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 28.dp),
                            )
                        }

                        // Originals Shelf
                        Text(
                            text = "THRUM ORIGINALS",
                            color = ThrumInk2,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.4.sp,
                            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                        )
                        OriginalsShelf(
                            onSelect = { name ->
                                val rhythm = Demo.rhythm()
                                val fakeTrack = Track(
                                    sourceUri = "original://$name",
                                    name = name,
                                    artist = "Thrum Original",
                                    durationMs = 45000L,
                                    kind = TrackKind.MUSIC,
                                )
                                Player.play(ctx, fakeTrack, listOf(fakeTrack))
                                Player.open = true
                            },
                        )

                        // Progress card if making haptics
                        val pendingCount = store.hapticQueuePending.size
                        if (store.hapticsMode == HapticsWorker.MODE_BACKGROUND && pendingCount > 0) {
                            ThrumCard(
                                modifier = Modifier.padding(top = 12.dp),
                                padding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.5.dp,
                                        color = ThrumAccent,
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "Making haptics · ${store.hapticsDone} of ${store.hapticsDone + pendingCount} done",
                                            color = ThrumInk,
                                            fontSize = 14.5.sp,
                                            fontWeight = FontWeight.Medium,
                                        )
                                        Text(
                                            "Carries on in the background. Songs you play go first.",
                                            color = ThrumInk2,
                                            fontSize = 12.sp,
                                        )
                                    }
                                }
                            }
                        }

                        // Song list
                        val filteredTracks = if (query.isBlank()) {
                            tracks
                        } else {
                            val terms = query.trim().lowercase().split("\\s+".toRegex())
                            tracks.filter { t ->
                                val target = "${t.name} ${t.artist}".lowercase()
                                terms.all { target.contains(it) }
                            }
                        }

                        ThrumCard(
                            modifier = Modifier.padding(top = 14.dp, bottom = 80.dp),
                            padding = PaddingValues(0.dp),
                        ) {
                            filteredTracks.forEachIndexed { idx, track ->
                                if (idx > 0) Divider(color = ThrumRule, thickness = 1.dp)
                                val isCallsSong = store.sourceUri == track.sourceUri

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            Player.play(ctx, track, tracks)
                                            Player.open = true
                                        }
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                ) {
                                    Box(Modifier.width(60.dp)) {
                                        PulseRibbon(pattern = "afro", height = 26.dp, barWidth = 2.dp, barGap = 1.dp)
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(track.name, color = ThrumInk, fontSize = 15.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            "${track.artist.ifEmpty { "Audio" }} · ${clockOf(track.durationMs)}",
                                            color = ThrumInk2,
                                            fontSize = 12.5.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    if (isCallsSong) {
                                        ThrumChip(text = "Calls", kind = ChipKind.ACCENT, hasDot = true)
                                    } else {
                                        CirclePlayButton(
                                            playing = Player.now?.track?.sourceUri == track.sourceUri && Player.now?.playing == true,
                                            size = 38.dp,
                                            iconSize = 13.dp,
                                            onClick = {
                                                Player.play(ctx, track, tracks)
                                                Player.open = true
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Screen 10 Bottom Sheet: Found your music: make haptics?
        if (askMode) {
            ThrumBottomSheet(
                onDismiss = { askMode = false },
            ) {
                Text("Found ${tracks.size} songs", color = ThrumInk, fontSize = 25.sp, fontWeight = FontWeight.Medium)
                Text(
                    "Make their haptics now? Each song takes about 8 seconds on your phone.",
                    color = Color(0xFFD6D6CF),
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Column(
                    modifier = Modifier.padding(top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ThrumCard(
                        modifier = Modifier.clickable { choice = HapticsWorker.MODE_BACKGROUND },
                        borderColor = if (choice == HapticsWorker.MODE_BACKGROUND) ThrumAccent else ThrumRule,
                        padding = PaddingValues(16.dp),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ThrumRadio(selected = choice == HapticsWorker.MODE_BACKGROUND)
                            Column {
                                Text("All of them, in the background", color = ThrumInk, fontSize = 15.5.sp, fontWeight = FontWeight.Medium)
                                Text(
                                    "Thrum works through them while you use your phone. It uses some battery while it does.",
                                    color = ThrumInk2,
                                    fontSize = 12.5.sp,
                                    lineHeight = 17.sp,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                        }
                    }

                    ThrumCard(
                        modifier = Modifier.clickable { choice = HapticsWorker.MODE_AS_PLAYED },
                        borderColor = if (choice == HapticsWorker.MODE_AS_PLAYED) ThrumAccent else ThrumRule,
                        padding = PaddingValues(16.dp),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ThrumRadio(selected = choice == HapticsWorker.MODE_AS_PLAYED)
                            Column {
                                Text("One at a time, as I play them", color = ThrumInk, fontSize = 15.5.sp, fontWeight = FontWeight.Medium)
                                Text(
                                    "Each song waits about 8 seconds the first time you play it.",
                                    color = ThrumInk2,
                                    fontSize = 12.5.sp,
                                    lineHeight = 17.sp,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                        }
                    }
                }

                Text(
                    "You can change this later in Settings.",
                    color = ThrumInk2,
                    fontSize = 12.5.sp,
                    modifier = Modifier.padding(top = 12.dp),
                )

                PrimaryButton(
                    text = "Continue",
                    onClick = {
                        store.hapticsMode = choice
                        if (choice == HapticsWorker.MODE_BACKGROUND) {
                            store.hapticsDone = 0
                            scope.launch { HapticsWorker.enqueue(ctx, tracks.map { it.sourceUri }) }
                        }
                        askMode = false
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                )
            }
        }
    }
}

@Composable
private fun CatalogView(
    query: String,
    onQueryChange: (String) -> Unit,
    isOffline: Boolean,
    onGoToMyMusic: () -> Unit,
) {
    if (isOffline) {
        // Screen 31: Catalog - offline
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            IconCircle(icon = "globe", size = 64.dp, cornerRadius = 18.dp, iconSize = 28.dp)
            Text(
                "You're offline",
                color = ThrumInk,
                fontSize = 25.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 18.dp),
            )
            Text(
                "The catalog needs the internet. Your own music, the Thrum Originals, your haptics and your calls all keep working without it.",
                color = Color(0xFFD6D6CF),
                fontSize = 16.sp,
                lineHeight = 24.sp,
                modifier = Modifier.padding(top = 10.dp),
            )
            Spacer(Modifier.height(32.dp))
            SecondaryButton(
                text = "Go to my music",
                onClick = onGoToMyMusic,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        return
    }

    // Screen 28 & 29: Catalog search & browse
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(ThrumSurface2)
            .border(1.dp, ThrumLine, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ThrumIcon(name = "search", tint = ThrumInk2, size = 18.dp)
            if (query.isEmpty()) {
                Text("Search songs, artists or sounds", color = ThrumInk2, fontSize = 15.sp)
            }
        }
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            textStyle = TextStyle(color = ThrumInk, fontSize = 15.sp),
            cursorBrush = SolidColor(ThrumAccent),
            modifier = Modifier.fillMaxWidth().padding(start = 28.dp),
        )
    }

    if (query.isNotBlank()) {
        // Screen 29: Catalog search results
        Text(
            text = "SONGS",
            color = ThrumInk2,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.4.sp,
            modifier = Modifier.padding(top = 18.dp, bottom = 8.dp),
        )
        ThrumCard(padding = PaddingValues(0.dp)) {
            listOf("3:12", "2:48", "3:35", "4:01").forEachIndexed { i, dur ->
                if (i > 0) Divider(color = ThrumRule, thickness = 1.dp)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    IconCircle(icon = "music", size = 40.dp, iconSize = 18.dp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Sample result", color = ThrumInk2, fontSize = 15.5.sp)
                        Text("Artist · Afrobeats · $dur", color = ThrumInk2, fontSize = 12.5.sp)
                    }
                    CirclePlayButton(playing = false, size = 38.dp, iconSize = 13.dp, onClick = {})
                }
            }
        }

        Text(
            text = "ARTISTS",
            color = ThrumInk2,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.4.sp,
            modifier = Modifier.padding(top = 18.dp, bottom = 8.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(3) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(ThrumSurface2)
                        .border(1.dp, ThrumRule, CircleShape),
                )
            }
        }
    } else {
        // Screen 28: Browse genres & Originals
        Text(
            text = "BROWSE",
            color = ThrumInk2,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.4.sp,
            modifier = Modifier.padding(top = 18.dp, bottom = 8.dp),
        )
        val genres = listOf("Afrobeats", "Amapiano", "Highlife", "Gospel", "Hip-hop", "Electronic")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            genres.take(3).forEach { g ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(ThrumSurface)
                        .border(1.dp, ThrumRule, RoundedCornerShape(20.dp))
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                ) {
                    Text(g, color = ThrumInk, fontSize = 14.sp)
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            genres.drop(3).forEach { g ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(ThrumSurface)
                        .border(1.dp, ThrumRule, RoundedCornerShape(20.dp))
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                ) {
                    Text(g, color = ThrumInk, fontSize = 14.sp)
                }
            }
        }

        Text(
            text = "MADE FOR FEELING",
            color = ThrumInk2,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.4.sp,
            modifier = Modifier.padding(top = 22.dp, bottom = 8.dp),
        )
        OriginalsShelf()
    }
}
