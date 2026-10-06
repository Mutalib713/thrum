package com.mosman.thrum

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Settings, About and Phone check: screens 20, 21, 22. Task 26.
 *
 * Every row reads its value live — from [Store] or from the system — and a
 * row only acts when it has somewhere to send you. Two rows that the first
 * UI drew here are gone on purpose: "Online catalog: On" (Mutalib removed the
 * online catalog on 3 October; there is no internet in this app) and "Clean
 * up low-quality songs" (Task 29, not built, and its row only opened Home).
 */
@Composable
fun SettingsTab(onOpenMusic: () -> Unit, onOpenHome: () -> Unit, onOpenTune: () -> Unit) {
    val ctx = LocalContext.current
    val store = remember { Store(ctx) }
    val db = remember { LibraryDb.get(ctx) }
    val scope = rememberCoroutineScope()
    var showAbout by remember { mutableStateOf(false) }
    var showPhoneCheck by remember { mutableStateOf(false) }

    if (showAbout) {
        AboutScreen(onClose = { showAbout = false })
        return
    }
    if (showPhoneCheck) {
        PhoneCheckScreen(onClose = { showPhoneCheck = false })
        return
    }

    val callAccess by rememberPolled(hasCallAccess(ctx)) { hasCallAccess(it) }
    val musicAccess by rememberPolled(hasMusicAccess(ctx)) { hasMusicAccess(it) }
    // Held here as well as written to the store, so a tap redraws at once.
    // The first build wrote the store and nothing else, and the switch looked
    // as if it ignored the tap.
    var ringMode by remember { mutableStateOf(store.fireInRingMode) }
    var background by remember { mutableStateOf(store.hapticsMode == HapticsWorker.MODE_BACKGROUND) }
    val callSong = remember { store.armedScore?.sourceName }
    val body = remember { store.body }
    var scanning by remember { mutableStateOf(false) }
    var scanLine by remember { mutableStateOf<String?>(null) }
    var lastScanAtMs by remember { mutableLongStateOf(store.lastScanAtMs) }

    ThrumPage(overTabs = true) {
        Text(
            stringResource(R.string.tab_settings),
            style = ThrumType.statement,
            color = ThrumInk,
            modifier = Modifier
                .padding(top = 10.dp, bottom = Space.S4)
                .semantics { heading() },
        )

        SettingsSection(stringResource(R.string.settings_calls_section)) {
            // The song's name under the label, where a long name has room.
            SettingsRow(
                name = stringResource(R.string.settings_song_for_calls),
                sub = callSong ?: stringResource(R.string.settings_not_set),
                onClick = onOpenHome,
            )
            SettingsDivider()
            // Always a way in: Android switches access on and off, Thrum
            // can't, so the row opens Android's own switch either way. On
            // used to do nothing, and looked stuck (Mutalib, 2026-10-06).
            SettingsRow(
                name = stringResource(R.string.settings_call_access),
                sub = stringResource(R.string.settings_access_change),
                value = stringResource(if (callAccess) R.string.settings_on else R.string.settings_off),
                onClick = { openCallAccess(ctx) },
            )
            SettingsDivider()
            ThrumSwitchRow(
                name = stringResource(R.string.ring_mode_label),
                help = stringResource(R.string.ring_mode_help),
                checked = ringMode,
                onChange = {
                    ringMode = it
                    store.fireInRingMode = it
                },
            )
        }

        SettingsSection(stringResource(R.string.settings_music_section)) {
            SettingsRow(
                name = stringResource(R.string.settings_music_access),
                sub = stringResource(R.string.settings_access_change),
                value = stringResource(if (musicAccess) R.string.settings_on else R.string.settings_off),
                onClick = { openAppInfo(ctx) },
            )
            SettingsDivider()
            SettingsRow(
                name = stringResource(R.string.settings_scan_new),
                sub = when {
                    scanning -> stringResource(R.string.settings_scanning)
                    scanLine != null -> scanLine
                    lastScanAtMs > 0 -> stringResource(
                        R.string.settings_last_scanned,
                        SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(lastScanAtMs)),
                    )
                    else -> stringResource(R.string.settings_never_scanned)
                },
                onClick = {
                    when {
                        scanning -> Unit
                        // The first scan asks how haptics get made, and without
                        // music access it has to ask for that too: both live
                        // in the Music tab.
                        !musicAccess || store.hapticsMode == null -> onOpenMusic()
                        else -> {
                            scanning = true
                            scanLine = null
                            scope.launch {
                                val result = MusicScan.intoLibrary(ctx)
                                scanning = false
                                lastScanAtMs = store.lastScanAtMs
                                scanLine = if (result == null) {
                                    ctx.getString(R.string.music_scan_failed)
                                } else {
                                    ctx.resources.getQuantityString(R.plurals.settings_scan_found, result.found, result.found) +
                                        " · " +
                                        if (result.new == 0) {
                                            ctx.getString(R.string.settings_scan_nothing_new)
                                        } else {
                                            ctx.getString(R.string.settings_scan_new_count, result.new)
                                        }
                                }
                            }
                        }
                    }
                },
            )
            SettingsDivider()
            SettingsRow(
                name = stringResource(R.string.settings_make_haptics),
                value = stringResource(if (background) R.string.settings_mode_background else R.string.settings_mode_as_played),
                onClick = {
                    background = !background
                    store.hapticsDone = 0
                    if (background) {
                        store.hapticsMode = HapticsWorker.MODE_BACKGROUND
                        // Everything without a haptic joins the walk now.
                        scope.launch {
                            val all = withContext(Dispatchers.IO) { db.dao().allTrackUris() }
                            HapticsWorker.enqueue(ctx, all)
                        }
                    } else {
                        store.hapticsMode = HapticsWorker.MODE_AS_PLAYED
                        // Nothing walks the list in "as I play them" mode.
                        store.editQueue(emptySet()) { HapticQueue() }
                    }
                },
            )
        }

        SettingsSection(stringResource(R.string.settings_haptics_section)) {
            SettingsRow(
                name = stringResource(R.string.settings_default_feel),
                value = stringResource(Tuning.matching(body)?.nameRes ?: R.string.settings_custom),
                onClick = onOpenTune,
            )
        }

        SettingsSection(stringResource(R.string.settings_phone_section)) {
            SettingsRow(name = stringResource(R.string.phonecheck_title), onClick = { showPhoneCheck = true })
            SettingsDivider()
            SettingsRow(name = stringResource(R.string.about_title), onClick = { showAbout = true })
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Overline(title, modifier = Modifier.padding(top = Space.S2, bottom = Space.S2, start = 2.dp))
    ThrumCard(padding = PaddingValues(0.dp)) { content() }
    Spacer(Modifier.height(18.dp))
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(color = ThrumRule, thickness = 1.dp)
}

/** A setting: its name, its value, and a way in when [onClick] is given. */
@Composable
private fun SettingsRow(
    name: String,
    sub: String? = null,
    value: String? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Touch.min)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = Space.S4, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The name keeps most of the row, whatever the value says. A long
        // value used to be measured first and take the whole width, squeezing
        // the name until its letters stacked one per line (Mutalib,
        // 2026-10-06: "breaks into vertical"). Now a long value is cut short.
        Column(modifier = Modifier.weight(1.6f)) {
            Text(name, style = ThrumType.row, color = ThrumInk)
            if (sub != null) Text(sub, style = ThrumType.meta, color = ThrumInk2, modifier = Modifier.padding(top = 2.dp))
        }
        if (value != null) {
            Text(
                value,
                style = ThrumType.body,
                color = ThrumInk2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .padding(start = Space.S3),
            )
        }
        if (onClick != null) {
            ThrumIcon(name = "chev", tint = ThrumInk2, size = 16.dp, modifier = Modifier.padding(start = Space.S2))
        }
    }
}

/**
 * About Thrum, screen 21. The founder story is the agreed text from the
 * final screens. The Thrum Originals section waits for the collection
 * (Task 27): the first UI printed "Made for Thrum by ." with no name in it.
 */
@Composable
private fun AboutScreen(onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    ThrumPage(overTabs = true) {
        ThrumTopBar(title = stringResource(R.string.about_title), onBack = onClose)

        Text(
            stringResource(R.string.app_name).uppercase(),
            style = ThrumType.statement.copy(letterSpacing = ThrumType.wordmark.letterSpacing),
            color = ThrumAccentInk,
            modifier = Modifier.padding(top = Space.S4),
        )
        Text(stringResource(R.string.onboarding_tagline), style = ThrumType.lead, color = ThrumInk, modifier = Modifier.padding(top = 6.dp))

        Overline(stringResource(R.string.about_why), modifier = Modifier.padding(top = 28.dp, bottom = Space.S2))
        Text(stringResource(R.string.about_story_1), style = ThrumType.lead, color = ThrumInkSoft)
        Text(stringResource(R.string.about_story_2), style = ThrumType.lead, color = ThrumInkSoft, modifier = Modifier.padding(top = Space.S3))
        Text(stringResource(R.string.about_founder), style = ThrumType.meta, color = ThrumInk2, modifier = Modifier.padding(top = Space.S3))

        Spacer(Modifier.height(28.dp))
        ThrumCard(padding = PaddingValues(0.dp)) {
            SettingsRow(name = stringResource(R.string.about_version, BuildConfig.VERSION_NAME))
            SettingsDivider()
            SettingsRow(
                name = stringResource(R.string.about_no_internet),
                sub = stringResource(R.string.about_no_internet_help),
            )
        }
    }
}

/**
 * Phone check, screen 22: what this phone can do. It measures the
 * instrument, so the two hands-on tests live here: the tap test, and the
 * phone's own buzz to hold Thrum's rhythm against — the comparison Task 15
 * (fixing the hum) is judged by.
 */
@Composable
private fun PhoneCheckScreen(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val capability = remember { Haptics.capability(ctx) }
    val scope = rememberCoroutineScope()
    val callAccess by rememberPolled(hasCallAccess(ctx)) { hasCallAccess(it) }
    val musicAccess by rememberPolled(hasMusicAccess(ctx)) { hasMusicAccess(it) }
    val calls by rememberPolled(emptyList<Home.CallLine>()) { Home.recentCalls(Store(it).events()) }

    // One test at a time owns the motor; leaving the screen ends it.
    var ratePlaying by remember { mutableIntStateOf(0) }
    var stockPlaying by remember { mutableStateOf(false) }
    var testJob by remember { mutableStateOf<Job?>(null) }
    fun stopTests() {
        testJob?.cancel()
        testJob = null
        ratePlaying = 0
        stockPlaying = false
        Haptics.stop(ctx)
    }
    DisposableEffect(Unit) { onDispose { stopTests() } }
    BackHandler(onBack = onClose)

    ThrumPage(overTabs = true) {
        ThrumTopBar(title = stringResource(R.string.phonecheck_title), onBack = onClose)

        Text(
            stringResource(if (capability.usable) R.string.phonecheck_good_title else R.string.phonecheck_bad_title),
            style = ThrumType.statement,
            color = if (capability.usable) ThrumAccentInk else ThrumWarn,
            modifier = Modifier
                .padding(top = Space.S2, bottom = 20.dp)
                .semantics { heading() },
        )

        ThrumCard(padding = PaddingValues(0.dp)) {
            CheckRow(
                name = stringResource(R.string.phonecheck_motor),
                value = stringResource(if (capability.hasVibrator) R.string.phonecheck_yes else R.string.phonecheck_none),
                ok = capability.hasVibrator,
            )
            SettingsDivider()
            CheckRow(
                name = stringResource(R.string.phonecheck_amplitude),
                sub = stringResource(R.string.phonecheck_amplitude_help),
                value = stringResource(if (capability.amplitudeControl) R.string.phonecheck_yes else R.string.phonecheck_no),
                ok = capability.amplitudeControl,
            )
            SettingsDivider()
            CheckRow(
                name = stringResource(R.string.settings_call_access),
                value = stringResource(if (callAccess) R.string.settings_on else R.string.settings_off),
                ok = callAccess,
            )
            SettingsDivider()
            CheckRow(
                name = stringResource(R.string.settings_music_access),
                value = stringResource(if (musicAccess) R.string.settings_on else R.string.settings_off),
                ok = musicAccess,
            )
        }

        // What Thrum did on each recent call — moved here from the old
        // diagnostics page, which only a test build could open. "Is it
        // working?" is a question every user can have.
        Overline(stringResource(R.string.phonecheck_calls_title), modifier = Modifier.padding(top = Space.S5, bottom = Space.S2, start = 2.dp))
        if (calls.isEmpty()) {
            Text(
                stringResource(R.string.phonecheck_calls_empty),
                style = ThrumType.body,
                color = ThrumInk2,
                modifier = Modifier.padding(horizontal = Space.S1),
            )
        } else {
            ThrumCard(padding = PaddingValues(0.dp)) {
                calls.forEachIndexed { index, call ->
                    if (index > 0) SettingsDivider()
                    CallRow(call)
                }
            }
        }

        // The tap test. Nothing here claims a speed: the user's own hand
        // finds where the taps blur. The first UI printed "up to 8 a second,
        // measured on this phone" — the 8 was measured on Mutalib's Pixel.
        Overline(stringResource(R.string.rate_title), modifier = Modifier.padding(top = Space.S5, bottom = Space.S2, start = 2.dp))
        ThrumCard(padding = PaddingValues(18.dp)) {
            Text(stringResource(R.string.rate_help), style = ThrumType.body, color = ThrumInkSoft)
            if (ratePlaying > 0) {
                Text(
                    stringResource(R.string.rate_playing, ratePlaying),
                    style = ThrumType.figure,
                    color = ThrumAccentInk,
                    modifier = Modifier.padding(top = Space.S3),
                )
            }
            SecondaryButton(
                text = stringResource(if (ratePlaying > 0) R.string.ready_stop else R.string.rate_run),
                small = true,
                onClick = {
                    if (ratePlaying > 0) {
                        stopTests()
                    } else {
                        stopTests()
                        testJob = scope.launch {
                            for (rate in RATE_LADDER) {
                                ratePlaying = rate
                                val train = Demo.pulseTrain(rate)
                                Haptics.play(ctx, train)
                                delay(train.durationMs + RATE_GAP_MS)
                                Haptics.stop(ctx)
                                delay(RATE_GAP_MS)
                            }
                            ratePlaying = 0
                            testJob = null
                        }
                    }
                },
                modifier = Modifier.padding(top = 14.dp),
            )
        }

        // The phone's own call vibration, read off this phone's own record:
        // one second full, one second still, repeating.
        Overline(stringResource(R.string.phonecheck_buzz_title), modifier = Modifier.padding(top = Space.S5, bottom = Space.S2, start = 2.dp))
        ThrumCard(padding = PaddingValues(18.dp)) {
            Text(stringResource(R.string.ready_stock_help), style = ThrumType.body, color = ThrumInkSoft)
            SecondaryButton(
                text = stringResource(if (stockPlaying) R.string.ready_stock_stop else R.string.ready_stock),
                small = true,
                onClick = {
                    // Decided before anything is cleared: the label says
                    // "Stop the buzz" while it plays. Checking after the
                    // clear-up was the dead-stop bug fixed on 3 October.
                    val wasPlaying = stockPlaying
                    stopTests()
                    if (wasPlaying) return@SecondaryButton
                    if (Haptics.playStockRingtoneBuzz(ctx) == null) {
                        stockPlaying = true
                        // It repeats for ever, so it always gets a cap.
                        testJob = scope.launch {
                            delay(Haptics.STOCK_BUZZ_MS)
                            stopTests()
                        }
                    }
                },
                modifier = Modifier.padding(top = 14.dp),
            )
        }
    }
}

@Composable
private fun CheckRow(name: String, value: String, ok: Boolean, sub: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.S4, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.S3),
    ) {
        ThrumIcon(name = if (ok) "check" else "x", tint = if (ok) ThrumAccentInk else ThrumInk2, size = 18.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(name, style = ThrumType.row, color = ThrumInk)
            if (sub != null) Text(sub, style = ThrumType.meta, color = ThrumInk2, modifier = Modifier.padding(top = 2.dp))
        }
        Text(value, style = ThrumType.body, color = ThrumInk2)
    }
}

/** One call: what happened, in a sentence, and when. */
@Composable
private fun CallRow(call: Home.CallLine) {
    val played = call.outcome == Home.CallOutcome.PLAYED
    val what = when (call.outcome) {
        Home.CallOutcome.PLAYED -> stringResource(R.string.call_played, Home.latencyWords(call.latencyMs))
        Home.CallOutcome.PLAYED_ON_SILENT -> stringResource(R.string.call_played_silent)
        Home.CallOutcome.RINGER_ON -> stringResource(R.string.call_ringer_on)
        Home.CallOutcome.NO_SONG -> stringResource(R.string.call_no_song)
        Home.CallOutcome.PHONE_CANNOT -> stringResource(R.string.call_phone_cannot)
        Home.CallOutcome.FAILED -> stringResource(R.string.call_failed)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.S4, vertical = 14.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(Space.S3),
    ) {
        ThrumIcon(
            name = if (played) "check" else "x",
            tint = if (played) ThrumAccentInk else ThrumInk2,
            size = 18.dp,
            modifier = Modifier.padding(top = 2.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(what, style = ThrumType.row, color = ThrumInk)
            Text(
                Home.callWords(call.atMs),
                style = ThrumType.meta,
                color = ThrumInk2,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

private val RATE_LADDER = listOf(2, 3, 4, 6, 8, 12)
private const val RATE_GAP_MS = 900L
