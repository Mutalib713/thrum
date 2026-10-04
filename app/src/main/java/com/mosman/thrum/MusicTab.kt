package com.mosman.thrum

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The Music tab. Task 20, drawn in screens 8, 9 and 12.
 *
 * The permission story is the whole point of the screen's shape: access is
 * **asked only when the user taps "Scan for music"** — never at launch, never
 * as a gate — and refusing it costs almost nothing, because "pick one song"
 * uses the file chooser, which needs no permission at all. Screen 12 says so
 * in exactly those terms.
 *
 * The scan itself writes [Track]s into the library ([LibraryDb], Mutalib's
 * Room pick), where Task 22's background haptics will find them. Rows are
 * deliberately not tappable yet: the player that would open is Task 21, and a
 * row that looks like a button today would be a dead control.
 */
@Composable
fun MusicTab() {
    val ctx = LocalContext.current
    val db = remember { LibraryDb.get(ctx) }
    val scope = rememberCoroutineScope()

    var tracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var scanning by remember { mutableStateOf(false) }
    var deniedByUser by remember { mutableStateOf(false) }
    val store = remember { Store(ctx) }

    // Screen 10: the question is asked once, after the first scan that finds
    // something, and the answer lives in [Store.hapticsMode].
    var askMode by remember { mutableStateOf(false) }
    var choice by remember { mutableStateOf<String?>(null) }

    val permission = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }

    // Polled rather than observed, for the same reason Home polls everything:
    // the user leaves for system settings, flips the grant, and comes back.
    // A tab still claiming it was refused is how an app looks broken.
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

    /** A re-scan's new songs join the background walk; already-made never re-join. */
    fun topUpQueue(found: List<Track>) {
        scope.launch {
            val made = withContext(Dispatchers.IO) { db.dao().madeTrackUris() }.toSet()
            val added = HapticQueue(store.hapticQueuePending, made)
                .enqueued(found.map { it.sourceUri })
            if (added.pending != store.hapticQueuePending) {
                store.hapticQueuePending = added.pending
                store.hapticsDone = 0
                store.hapticsTotal = added.pending.size
                HapticsWorker.ensureEnqueued(ctx)
            }
        }
    }

    fun runScan() {
        scanning = true
        scope.launch {
            val found = withContext(Dispatchers.IO) { MusicScan.scan(ctx) }
            val now = System.currentTimeMillis()
            withContext(Dispatchers.IO) {
                db.dao().upsertTracks(found.map { it.toEntity(now) })
            }
            store.lastScanAtMs = now
            scanning = false
            if (found.isNotEmpty()) {
                if (store.hapticsMode == null) {
                    askMode = true
                } else {
                    topUpQueue(found)
                }
            }
        }
    }

    // The request is made here and nowhere else — tapped, not at launch. A
    // refusal is remembered only to choose which honest screen to draw; the
    // poll above means a grant made in system settings is seen on return.
    val requestPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) runScan() else deniedByUser = true
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        // Same rule as Home's picker: hold the grant past this session, or the
        // row survives with an unreadable source behind it.
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.S5, vertical = Space.S6),
        verticalArrangement = Arrangement.spacedBy(Space.S4),
    ) {
        Text(
            stringResource(R.string.tab_music),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
        )

        when {
            // Screen 12. Reached only by refusing the request; granting in
            // system settings flips this back automatically, because `granted`
            // is polled.
            deniedByUser && !granted -> {
                Text(
                    stringResource(R.string.music_denied_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.error,
                )
                Text(
                    stringResource(R.string.music_denied_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Primary(stringResource(R.string.music_denied_settings)) {
                    runCatching {
                        ctx.startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", ctx.packageName, null),
                            ),
                        )
                    }
                }
                Secondary(stringResource(R.string.music_pick_instead)) {
                    picker.launch(arrayOf("audio/*"))
                }
            }

            granted && scanning -> {
                Text(
                    stringResource(R.string.music_scanning),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                CircularProgressIndicator(
                    modifier = Modifier.size(Space.S5),
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            granted && askMode && tracks.isNotEmpty() -> {
                // Screen 10. Two ways to answer, one Continue, and the choice
                // is changeable later in Settings (Task 26 builds that screen;
                // the setting exists from today).
                Text(
                    stringResource(R.string.make_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    stringResource(R.string.make_count, tracks.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.make_question),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                ChoiceOption(
                    selected = choice == HapticsWorker.MODE_BACKGROUND,
                    title = stringResource(R.string.make_background),
                    help = stringResource(R.string.make_background_help),
                    onSelect = { choice = HapticsWorker.MODE_BACKGROUND },
                )
                ChoiceOption(
                    selected = choice == HapticsWorker.MODE_AS_PLAYED,
                    title = stringResource(R.string.make_as_played),
                    help = stringResource(R.string.make_as_played_help),
                    onSelect = { choice = HapticsWorker.MODE_AS_PLAYED },
                )
                Text(
                    stringResource(R.string.make_changeable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (choice != null) {
                    Primary(stringResource(R.string.make_continue)) {
                        store.hapticsMode = choice
                        if (choice == HapticsWorker.MODE_BACKGROUND) {
                            scope.launch {
                                val made = withContext(Dispatchers.IO) {
                                    db.dao().madeTrackUris()
                                }.toSet()
                                val queued = HapticQueue(emptyList(), made)
                                    .enqueued(tracks.map { it.sourceUri })
                                store.hapticQueuePending = queued.pending
                                store.hapticsDone = 0
                                store.hapticsTotal = queued.pending.size
                                HapticsWorker.ensureEnqueued(ctx)
                            }
                        }
                        askMode = false
                    }
                }
            }

            granted && tracks.isEmpty() -> {
                // Screen 8 — and the same layout serves a re-scan that found
                // nothing, with one honest extra line for it.
                Text(
                    stringResource(R.string.music_intro_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    stringResource(R.string.music_intro_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                if (Store(ctx).lastScanAtMs > 0L) {
                    Text(
                        stringResource(R.string.music_empty_after_scan),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    stringResource(R.string.music_intro_privacy),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Primary(stringResource(R.string.music_scan_action)) {
                    requestPermission.launch(permission)
                }
                Secondary(stringResource(R.string.music_pick_instead)) {
                    picker.launch(arrayOf("audio/*"))
                }
            }

            granted -> {
                // Screen 11's list with search. The Originals row that the
                // design puts above this is Task 27's, once the collection
                // exists; a placeholder named "Afro Groove" today would be a
                // screen pretending.
                val walk by produceState(initialValue = Triple(0, 0, 0)) {
                    while (true) {
                        value = Triple(
                            store.hapticsDone,
                            store.hapticsTotal,
                            store.hapticQueuePending.size,
                        )
                        delay(POLL_MS)
                    }
                }
                val (doneCount, totalCount, pendingCount) = walk
                if (store.hapticsMode == HapticsWorker.MODE_BACKGROUND && pendingCount > 0) {
                    Text(
                        stringResource(R.string.make_progress, doneCount, totalCount),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        stringResource(R.string.make_progress_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.music_search_hint)) },
                )
                val shown = Track.search(tracks, query)
                for (track in shown) {
                    // Rows are tappable since Task 21: the player exists, so a
                    // tap has somewhere to go. An unreadable file's row stays
                    // inert and says so — tapping it could only fail.
                    if (track.readable) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = Touch.min)
                                .clickable {
                                    Player.play(ctx, track, shown, hearAndFeel = true)
                                    Player.open = true
                                }
                                .padding(vertical = Space.S1),
                        ) {
                            Text(
                                track.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                            rowSubtitle(track)
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = Space.S1),
                        ) {
                            Text(
                                track.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            rowSubtitle(track)
                            Text(
                                stringResource(R.string.player_unreadable),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
                if (shown.isEmpty() && query.isNotEmpty()) {
                    Text(
                        stringResource(R.string.music_no_match),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Not granted, not refused — the first-time screen. Tapping the
            // scan button is the moment the request is made, never before.
            else -> {
                Text(
                    stringResource(R.string.music_intro_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    stringResource(R.string.music_intro_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    stringResource(R.string.music_intro_privacy),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Primary(stringResource(R.string.music_scan_action)) {
                    requestPermission.launch(permission)
                }
                Secondary(stringResource(R.string.music_pick_instead)) {
                    picker.launch(arrayOf("audio/*"))
                }
            }
        }
    }
}

/** `artist · 3:04`, whichever parts the row actually has. */
@Composable
private fun rowSubtitle(track: Track) {
    val bits = buildList {
        if (track.artist.isNotEmpty()) add(track.artist)
        if (track.durationMs > 0) add(clockOf(track.durationMs))
    }
    if (bits.isNotEmpty()) {
        Text(
            bits.joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * One answer on the screen-10 question. Selection is a word-and-colour pair
 * — colour is never the only signal — announced to screen readers through
 * `selected`.
 */
@Composable
private fun ChoiceOption(selected: Boolean, title: String, help: String, onSelect: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Touch.min)
            .clickable(onClick = onSelect)
            .padding(vertical = Space.S3)
            .semantics { this.selected = selected },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (selected) "●" else "○",
                style = MaterialTheme.typography.bodyLarge,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Text(
                "  $title",
                style = MaterialTheme.typography.titleMedium,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onBackground
                },
            )
        }
        Text(
            help,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private const val POLL_MS = 800L
