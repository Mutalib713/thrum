package com.mosman.thrum

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Screen 17: Export.
 *
 * "Take it with you"
 * Allows exporting this song or all songs as Thrum pattern files.
 * Only the vibration is exported — songs remain untouched on device.
 */
@Composable
fun ExportScreen(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val db = remember { LibraryDb.get(ctx) }
    val store = remember { Store(ctx) }
    val scope = rememberCoroutineScope()

    var haptics by remember { mutableStateOf<List<Haptic>>(emptyList()) }
    var tracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var exportAll by remember { mutableStateOf(true) }
    var saved by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        db.dao().observeHaptics().collect { rows -> haptics = rows.mapNotNull { it.toHaptic() } }
    }
    LaunchedEffect(Unit) {
        db.dao().observeTracks().collect { rows -> tracks = rows.map { it.toTrack() } }
    }

    val missing = tracks.count { track -> haptics.none { it.trackUri == track.sourceUri } }
    val thisSong = Player.haptic
    val chosen: List<Haptic> = if (exportAll) haptics else listOfNotNull(thisSong)

    val saver = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            withContext(Dispatchers.IO) {
                ctx.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(ThrumFile.encode(chosen).toByteArray())
                }
            }
            if (exportAll && missing > 0) {
                val made = withContext(Dispatchers.IO) { db.dao().madeTrackUris() }.toSet()
                val queued = HapticQueue(store.hapticQueuePending, made)
                    .enqueued(tracks.map { it.sourceUri })
                if (queued.pending != store.hapticQueuePending) {
                    store.hapticQueuePending = queued.pending
                    store.hapticsDone = 0
                    store.hapticsTotal = queued.pending.size
                    HapticsWorker.ensureEnqueued(ctx)
                }
            }
            saved = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF131312))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 22.dp, vertical = 12.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        // Topbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.CenterStart,
            ) {
                ThrumIcon.Back(tint = Color(0xFFF5F5F0), size = 20.dp)
            }
            Text(
                "Export",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFF5F5F0),
            )
        }

        Spacer(Modifier.height(8.dp))

        // Statement
        Text(
            "Take it with you",
            fontSize = 31.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFD8C513),
            lineHeight = 36.sp,
        )

        Spacer(Modifier.height(20.dp))

        // Sect: What to export
        Text(
            "What to export",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF8E8E86),
            letterSpacing = 0.5.sp,
        )

        Spacer(Modifier.height(8.dp))

        val allCount = tracks.size.coerceAtLeast(1)
        ThrumSegmentedControl(
            items = listOf("This song", "All $allCount songs"),
            selectedIndex = if (exportAll) 1 else 0,
            onSelect = { exportAll = (it == 1) },
        )

        if (exportAll && missing > 0) {
            Spacer(Modifier.height(8.dp))
            Text(
                "$missing songs don't have a haptic yet. Thrum makes them first, then exports.",
                fontSize = 12.sp,
                color = Color(0xFF8E8E86),
                lineHeight = 16.sp,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }

        Spacer(Modifier.height(20.dp))

        // Sect: As
        Text(
            "As",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF8E8E86),
            letterSpacing = 0.5.sp,
        )

        Spacer(Modifier.height(10.dp))

        // Format 1: Thrum pattern files (Active)
        ThrumCard(
            borderColor = Color(0xFFD8C513),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                ThrumRadio(selected = true, modifier = Modifier.padding(top = 2.dp))
                Column {
                    Text(
                        "Thrum pattern files",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFF5F5F0),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Back them up, or open them in Thrum on another phone.",
                        fontSize = 13.sp,
                        color = Color(0xFF8E8E86),
                        lineHeight = 18.sp,
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Format 2: Ringtones (Disabled)
        ThrumCard(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(0.55f),
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                ThrumRadio(selected = false, modifier = Modifier.padding(top = 2.dp))
                Column {
                    Text(
                        "Ringtones with the vibration inside",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFF5F5F0),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Not built yet. It's the hardest part of Thrum, planned for later.",
                        fontSize = 13.sp,
                        color = Color(0xFF8E8E86),
                        lineHeight = 18.sp,
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        // Check note
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ThrumIcon.Check(tint = Color(0xFFD8C513), size = 16.dp)
            Text(
                "Only the vibration is exported. Your songs stay where they are.",
                fontSize = 13.sp,
                color = Color(0xFF8E8E86),
                lineHeight = 18.sp,
            )
        }

        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(24.dp))

        if (saved) {
            Text(
                "Saved to your phone",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFD8C513),
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 12.dp),
            )
        }

        PrimaryButton(
            text = "Save to my phone",
            onClick = {
                saved = false
                val suggested = if (exportAll) {
                    ThrumFile.fileNameFor("Thrum haptics")
                } else {
                    ThrumFile.fileNameFor(thisSong?.score?.sourceName.orEmpty())
                }
                saver.launch(suggested)
            },
        )

        Spacer(Modifier.height(16.dp))
    }
}
