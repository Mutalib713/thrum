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
fun ExportScreen(thisSongFirst: Boolean, onClose: () -> Unit) {
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
    val missing = tracks.count { it.readable && it.sourceUri !in madeUris }
    val chosen: List<Haptic> = if (exportAll) haptics else listOfNotNull(thisSong)
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
            if (exportAll && missing > 0) {
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
        if (thisSong != null) {
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
            if (exportAll) {
                pluralStringResource(R.plurals.export_count_all, haptics.size, haptics.size)
            } else {
                thisSong?.score?.sourceName.orEmpty()
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

        Overline(stringResource(R.string.export_as), modifier = Modifier.padding(top = 22.dp, bottom = Space.S2))
        ThrumCard(borderColor = ThrumAccentInk) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                ThrumRadio(selected = true, modifier = Modifier.padding(top = 2.dp))
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
        Spacer(Modifier.height(Space.S3))
        // The v2 encoder, shown as what it is: not built yet. Not a choice.
        ThrumCard(modifier = Modifier.alpha(0.55f)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                ThrumRadio(selected = false, modifier = Modifier.padding(top = 2.dp))
                Column {
                    Text(stringResource(R.string.export_format_ringtone), style = ThrumType.row, color = ThrumInk)
                    Text(
                        stringResource(R.string.export_format_ringtone_help),
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
            saved -> Text(stringResource(R.string.export_saved), style = ThrumType.row, color = ThrumAccentInk, modifier = Modifier.padding(bottom = Space.S3))
            failed -> Text(stringResource(R.string.export_failed), style = ThrumType.body, color = ThrumWarn, modifier = Modifier.padding(bottom = Space.S3))
            chosen.isEmpty() -> Text(stringResource(R.string.export_nothing), style = ThrumType.body, color = ThrumInk2, modifier = Modifier.padding(bottom = Space.S3))
        }

        PrimaryButton(
            text = stringResource(R.string.export_save),
            enabled = chosen.isNotEmpty(),
            onClick = {
                saved = false
                failed = false
                val suggested = if (exportAll) {
                    ThrumFile.fileNameFor(ctx.getString(R.string.export_file_name))
                } else {
                    ThrumFile.fileNameFor(thisSong?.score?.sourceName.orEmpty())
                }
                saver.launch(suggested)
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
