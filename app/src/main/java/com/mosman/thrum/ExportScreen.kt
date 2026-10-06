package com.mosman.thrum

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Screen 17: Export. Task 25.
 *
 * Saves Thrum pattern files through the phone's own save screen. **Only the
 * vibration goes in the file, never the song** — Sacred Rule 3's export
 * exception, asserted in a test, not just promised.
 *
 * One recorded deviation, kept honest on screen: PROFILE §4 item 10 has
 * songs without a haptic made first, but on a big library that would hold
 * this screen for minutes. They are counted, said, and left to the background
 * walk (or to being played), and the line says to export again after. The
 * first UI drawn here promised "Thrum makes them first, then exports", which
 * it did not do.
 */
@Composable
fun ExportScreen(thisSongFirst: Boolean, onClose: () -> Unit, uris: List<String>? = null) {
    val ctx = LocalContext.current
    val db = remember { LibraryDb.get(ctx) }
    val store = remember { Store(ctx) }
    val scope = rememberCoroutineScope()

    BackHandler(onBack = onClose)

    var haptics by remember { mutableStateOf<List<Haptic>>(emptyList()) }
    var tracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    // "This song" exists only when a song with a haptic is open. Opened from
    // its page it starts there; from "Export all", on all of them.
    val thisSong = Player.haptic
    var exportAll by remember { mutableStateOf(!thisSongFirst || thisSong == null) }
    var saved by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        db.dao().observeHaptics().collect { rows -> haptics = rows.mapNotNull { it.toHaptic() } }
    }
    LaunchedEffect(Unit) {
        db.dao().observeTracks().collect { rows -> tracks = rows.map { it.toTrack() } }
    }

    val madeUris = haptics.map { it.trackUri }.toSet()
    val missing = if (uris != null) 0 else tracks.count { it.readable && it.sourceUri !in madeUris }
    val chosen: List<Haptic> = when {
        uris != null -> haptics.filter { it.trackUri in uris }
        exportAll -> haptics
        else -> listOfNotNull(thisSong)
    }

    // Chosen rows (a ⋮ menu, or several picked at once): a song without a
    // haptic gets one made here first, one at a time, because the user asked
    // for exactly these. "Export all" over a whole library still leaves that
    // to the background, where it can take as long as it needs.
    var making by remember { mutableStateOf(0) }
    LaunchedEffect(uris) {
        if (uris == null) return@LaunchedEffect
        val dao = db.dao()
        val todo = withContext(Dispatchers.IO) {
            uris.filter { dao.hapticFor(it) == null }.mapNotNull { dao.trackFor(it)?.toTrack() }.filter { it.readable }
        }
        todo.forEachIndexed { index, track ->
            making = todo.size - index
            HapticMaker.make(ctx, track)
        }
        making = 0
    }
    val background = store.hapticsMode == HapticsWorker.MODE_BACKGROUND

    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val toWrite = chosen
        scope.launch {
            // A save can fail for reasons outside the app — storage full, the
            // chosen place gone. That used to crash the app; now it is said.
            val wrote = withContext(Dispatchers.IO) {
                runCatching {
                    ctx.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(ThrumFile.encode(toWrite).toByteArray())
                    } != null
                }.getOrDefault(false)
            }
            if (uris == null && exportAll && missing > 0) {
                HapticsWorker.enqueue(ctx, tracks.map { it.sourceUri })
            }
            saved = wrote
            failed = !wrote
        }
    }

    ThrumPage {
        ThrumTopBar(title = stringResource(R.string.export_title), onBack = onClose)

        Text(
            stringResource(R.string.export_take),
            style = ThrumType.statement,
            color = ThrumInk,
            modifier = Modifier
                .padding(top = Space.S2, bottom = 20.dp)
                .semantics { heading() },
        )

        Overline(stringResource(R.string.export_what), modifier = Modifier.padding(bottom = Space.S2))
        if (thisSong != null && uris == null) {
            ThrumSegmentedControl(
                options = listOf(stringResource(R.string.export_this_song), stringResource(R.string.export_all_songs)),
                selectedIndex = if (exportAll) 1 else 0,
                onSelect = {
                    exportAll = it == 1
                    saved = false
                    failed = false
                },
            )
        }
        Text(
            when {
                uris?.size == 1 -> chosen.firstOrNull()?.score?.sourceName.orEmpty()
                uris != null -> pluralStringResource(R.plurals.export_count_all, chosen.size, chosen.size)
                exportAll -> pluralStringResource(R.plurals.export_count_all, haptics.size, haptics.size)
                else -> thisSong?.score?.sourceName.orEmpty()
            },
            style = ThrumType.body,
            color = ThrumInk2,
            modifier = Modifier.padding(top = Space.S2, start = Space.S1),
        )
        if (exportAll && missing > 0) {
            Text(
                pluralStringResource(
                    if (background) R.plurals.export_missing else R.plurals.export_missing_as_played,
                    missing,
                    missing,
                ),
                style = ThrumType.meta,
                color = ThrumInk2,
                modifier = Modifier.padding(top = Space.S2, start = Space.S1),
            )
        }

        // One kind of file, so a card that says what you get rather than a
        // choice of one. "Ringtones with the vibration inside" used to sit
        // under it greyed out; Mutalib agreed on 2026-10-06 to hide it until
        // it exists (v2, option B in PLAN.md) — an option that does nothing
        // only confuses.
        Overline(stringResource(R.string.export_as), modifier = Modifier.padding(top = 22.dp, bottom = Space.S2))
        ThrumCard {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                ThrumIcon(name = "file", tint = ThrumAccentInk, size = 20.dp, modifier = Modifier.padding(top = 2.dp))
                Column {
                    Text(stringResource(R.string.export_format_files), style = ThrumType.row, color = ThrumInk)
                    Text(
                        stringResource(R.string.export_format_files_help),
                        style = ThrumType.meta,
                        color = ThrumInk2,
                        modifier = Modifier.padding(top = Space.S1),
                    )
                }
            }
        }

        Row(
            modifier = Modifier.padding(top = 18.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ThrumIcon(name = "check", tint = ThrumAccentInk, size = 16.dp)
            Text(stringResource(R.string.export_privacy), style = ThrumType.meta, color = ThrumInk2)
        }

        Spacer(Modifier.height(Space.S6))

        when {
            making > 0 -> Row(
                modifier = Modifier.padding(bottom = Space.S3),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.S3),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.5.dp, color = ThrumAccentInk)
                Text(pluralStringResource(R.plurals.export_making, making, making), style = ThrumType.body, color = ThrumInk2)
            }
            saved -> Text(stringResource(R.string.export_saved), style = ThrumType.row, color = ThrumAccentInk, modifier = Modifier.padding(bottom = Space.S3))
            failed -> Text(stringResource(R.string.export_failed), style = ThrumType.body, color = ThrumWarn, modifier = Modifier.padding(bottom = Space.S3))
            chosen.isEmpty() -> Text(stringResource(R.string.export_nothing), style = ThrumType.body, color = ThrumInk2, modifier = Modifier.padding(bottom = Space.S3))
        }

        PrimaryButton(
            text = stringResource(R.string.export_save),
            enabled = chosen.isNotEmpty() && making == 0,
            onClick = {
                saved = false
                failed = false
                val suggested = when {
                    chosen.size == 1 -> ThrumFile.fileNameFor(chosen[0].score.sourceName)
                    uris != null || exportAll -> ThrumFile.fileNameFor(ctx.getString(R.string.export_file_name))
                    else -> ThrumFile.fileNameFor(thisSong?.score?.sourceName.orEmpty())
                }
                saver.launch(suggested)
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
