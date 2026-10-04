package com.mosman.thrum

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * My Haptics: everything that can be felt, in one list. Task 23, screen 19.
 *
 * Videos and files arrive through the phone's own file chooser — one at a
 * time, never a scan, so Thrum never asks to see all videos. A pick is made
 * straight away (about eight seconds); its row appears immediately and says
 * "making" until the haptic lands, or the decoder's own sentence if the
 * phone cannot read it.
 *
 * Long-press deletes the haptic — with a confirmation, because a long-press
 * that silently destroys work is how people learn to distrust the list. The
 * song itself stays in the library; the haptic is made again on next play.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MyHapticsTab(onGoToMusic: () -> Unit) {
    val ctx = LocalContext.current
    val db = remember { LibraryDb.get(ctx) }
    val store = remember { Store(ctx) }
    val scope = rememberCoroutineScope()

    var haptics by remember { mutableStateOf<List<Haptic>>(emptyList()) }
    var tracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var filter by remember { mutableStateOf(MyHaptics.Filter.ALL) }
    var choosing by remember { mutableStateOf(false) }
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
            // One-off picks are made straight away — the row appears at once
            // and says "making" until this lands.
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
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        insertPicked(uri, TrackKind.FILE)
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
                errorLine = ctx.getString(R.string.import_failed)
            } else {
                withContext(Dispatchers.IO) {
                    imported.forEach { db.dao().upsertHaptic(it.toEntity()) }
                }
            }
        }
    }

    val rows = MyHaptics.rows(haptics, tracks, store.sourceUri, filter)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = Space.S5, vertical = Space.S6),
        verticalArrangement = Arrangement.spacedBy(Space.S4),
    ) {
        Text(
            stringResource(R.string.tab_haptics),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(Space.S2)) {
            MyHaptics.Filter.entries.forEach { f ->
                FilterChip(
                    selected = filter == f,
                    onClick = { filter = f },
                    label = {
                        Text(
                            stringResource(
                                when (f) {
                                    MyHaptics.Filter.ALL -> R.string.haptics_filter_all
                                    MyHaptics.Filter.MUSIC -> R.string.haptics_filter_music
                                    MyHaptics.Filter.VIDEOS -> R.string.haptics_filter_videos
                                    MyHaptics.Filter.FILES -> R.string.haptics_filter_files
                                },
                            ),
                        )
                    },
                )
            }
        }

        if (rows.isEmpty()) {
            Text(
                stringResource(R.string.haptics_empty_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(rows, key = { it.uri }) { row ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = Touch.min)
                            .combinedClickable(
                                onClick = {
                                    if (row.hasHaptic && row.readable) {
                                        val track = tracks.firstOrNull { it.sourceUri == row.uri }
                                        Player.play(
                                            ctx,
                                            track ?: row.asTrack(),
                                            listOf(row.asTrack()),
                                            // An imported haptic has no song behind
                                            // it — it opens feel-only, by nature.
                                            hearAndFeel = !ThrumFile.isImported(row.uri),
                                        )
                                        Player.open = true
                                    }
                                },
                                onLongClick = { if (row.hasHaptic) deleteRow = row },
                            )
                            .padding(vertical = Space.S2),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                row.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (row.readable) {
                                    MaterialTheme.colorScheme.onBackground
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            if (row.isCalls) {
                                Text(
                                    stringResource(R.string.haptics_calls_badge),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        Text(
                            row.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        when {
                            !row.readable -> Text(
                                stringResource(R.string.player_unreadable),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )

                            !row.hasHaptic -> Text(
                                stringResource(R.string.haptics_making),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        errorLine?.let { message ->
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        if (choosing) {
            // Screen 16's four options, all live: import went real in Task 25.
            Text(
                stringResource(R.string.create_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Primary(stringResource(R.string.create_video)) {
                choosing = false
                videoPicker.launch(arrayOf("video/*"))
            }
            Primary(stringResource(R.string.create_file)) {
                choosing = false
                filePicker.launch(arrayOf("audio/*"))
            }
            Secondary(stringResource(R.string.create_song)) {
                choosing = false
                onGoToMusic()
            }
            Secondary(stringResource(R.string.import_option)) {
                choosing = false
                importPicker.launch(arrayOf("application/octet-stream", "text/plain"))
            }
            Text(
                stringResource(R.string.create_privacy),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            TextButton(
                onClick = { choosing = true },
                modifier = Modifier.heightIn(min = Touch.min),
            ) {
                Text(stringResource(R.string.haptics_make))
            }
        }
    }

    deleteRow?.let { row ->
        AlertDialog(
            onDismissRequest = { deleteRow = null },
            title = { Text(stringResource(R.string.haptics_delete_title)) },
            text = { Text(stringResource(R.string.haptics_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { withContext(Dispatchers.IO) { db.dao().removeHaptic(row.uri) } }
                    deleteRow = null
                }) {
                    Text(stringResource(R.string.haptics_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteRow = null }) {
                    Text(stringResource(R.string.haptics_delete_cancel))
                }
            },
        )
    }
}

/** The player can play a row even when its Track row is gone — the score carries the name. */
private fun MyHaptics.Row.asTrack(): Track = Track(
    sourceUri = uri,
    name = name,
    durationMs = 0,
    kind = kind,
    readable = readable,
)
