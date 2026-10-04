package com.mosman.thrum

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Export, drawn in screen 17. One haptic or all of them, as Thrum pattern
 * files saved wherever the user points the phone's own save screen.
 *
 * **Only the vibration leaves** ([ThrumFile] carries no audio and no URI —
 * tested), and the screen says so in its last line, because that sentence is
 * the privacy promise made visible.
 *
 * The honest seam: songs without a haptic yet are **not** made inside the
 * export (the design's "Thrum makes them first" would hold the screen for
 * minutes on a big library). They are counted, shown, and queued into the
 * background walk Task 22 built — the line says to export again after. The
 * deviation is recorded in PLAN.md.
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
            // Songs without a haptic join the background walk now, so the
            // next export carries them — the honest version of "makes them
            // first".
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
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.S5, vertical = Space.S6),
        verticalArrangement = Arrangement.spacedBy(Space.S4),
    ) {
        TextButton(onClick = onClose, modifier = Modifier.heightIn(min = Touch.min)) {
            Text(stringResource(R.string.player_close))
        }
        Text(
            stringResource(R.string.export_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            stringResource(R.string.export_take),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Text(
            stringResource(R.string.export_what),
            style = MaterialTheme.typography.titleMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Space.S2)) {
            FilterChip(
                selected = !exportAll,
                onClick = { exportAll = false },
                label = { Text(stringResource(R.string.export_this_song)) },
            )
            FilterChip(
                selected = exportAll,
                onClick = { exportAll = true },
                label = { Text(stringResource(R.string.export_all_songs)) },
            )
        }
        if (exportAll && missing > 0) {
            Text(
                stringResource(R.string.export_missing, missing),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Text(
            stringResource(R.string.export_format_files),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            stringResource(R.string.export_format_files_help),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(R.string.export_format_ringtone),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(R.string.export_format_ringtone_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text(
            stringResource(R.string.export_privacy),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        val nothingToExport = chosen.isEmpty()
        if (saved) {
            Text(
                stringResource(R.string.export_saved),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (!nothingToExport) {
            Primary(stringResource(R.string.export_save)) {
                saved = false
                val suggested = if (exportAll) {
                    ThrumFile.fileNameFor("Thrum haptics")
                } else {
                    ThrumFile.fileNameFor(thisSong?.score?.sourceName.orEmpty())
                }
                saver.launch(suggested)
            }
        }
    }
}
