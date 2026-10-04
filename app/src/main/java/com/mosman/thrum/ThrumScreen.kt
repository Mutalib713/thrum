package com.mosman.thrum

import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Home: screens 18, 24 and 27. Task 19. Calls come first.
 *
 * The calls card answers one question honestly — will the next call vibrate
 * to your song? — from what the phone actually says: whether Thrum can see
 * call notifications, and the ringer, read through [Setup.verdict], the same
 * table the listener uses at call time. The first UI drawn on this screen
 * said "Ready" without checking either, so someone who tapped "Maybe later"
 * on call access, or who had the ring switch off, was told calls worked.
 * Sacred Rule 2.
 *
 * Everything below the card is the user's own or nothing: the last call is
 * read from the event log (only a call Thrum actually played), and Recently
 * played from the songs actually played. The first UI hard-coded both from
 * the design board — Mutalib's own call of 20 September, a song he had played.
 */
@Composable
fun ThrumApp(
    onDiagnostics: (() -> Unit)? = null,
    onOpenMusic: () -> Unit,
    onOpenCreate: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val ctx = LocalContext.current
    val store = remember { Store(ctx) }
    val capability = remember { Haptics.capability(ctx) }
    val scope = rememberCoroutineScope()

    var score by remember { mutableStateOf(store.armedScore) }
    var reading by remember { mutableStateOf<String?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    var ringMode by remember { mutableStateOf(store.fireInRingMode) }

    val permitted by rememberPolled(true) { hasCallAccess(it) }
    val mayBeLocked = remember { callAccessMayBeLocked(ctx) }
    var askedForAccess by remember { mutableStateOf(store.callAccessAsked) }
    val ringer by rememberPolled(Setup.Ringer.UNKNOWN) { Setup.Ringer.of(Haptics.ringerMode(it)) }
    val lastCall by rememberPolled<Event?>(null) { Home.lastCall(Store(it).events()) }

    // The test call's playhead, the job driving it and, with sound, the song
    // itself, so Stop stops all three.
    var progress by remember { mutableFloatStateOf(-1f) }
    var testJob by remember { mutableStateOf<Job?>(null) }
    var testAudio by remember { mutableStateOf<MediaPlayer?>(null) }
    var testHearAndFeel by remember { mutableStateOf(store.testHearAndFeel) }
    fun stopTest() {
        if (testJob == null && testAudio == null) return
        testJob?.cancel()
        testJob = null
        testAudio?.runCatching { release() }
        testAudio = null
        progress = -1f
        Haptics.stop(ctx)
    }
    // Leaving Home ends the test call. Only the test call: a song playing in
    // the player carries on, which is what the mini player is for.
    DisposableEffect(Unit) { onDispose { stopTest() } }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        stopTest()
        // Held past this session, so the song can be read again to retune it.
        runCatching {
            ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        failure = null
        val name = AudioDecoder.displayName(ctx, uri)
        reading = name
        scope.launch {
            val analysis = HapticMaker.analyse(ctx, uri, name)
            reading = null
            when (analysis) {
                // A song that can't be read leaves the armed one armed: it is
                // still what the phone will play.
                is HapticMaker.Analysis.Failed -> failure = analysis.message
                is HapticMaker.Analysis.Ok -> {
                    val built = ScoreBuilder.callScore(
                        analysis.levels, analysis.stepMs, name, store.punch, store.distance, store.body,
                    )
                    if (built.isSilent()) {
                        failure = ctx.getString(R.string.error_silent)
                    } else {
                        // One write: the score, its song and its dials can
                        // never disagree after a restart (Store.arm).
                        store.arm(built, uri.toString(), store.punch, store.distance, store.body)
                        score = built
                    }
                }
            }
        }
    }

    fun playTestCall(built: Score) {
        // The player owns the motor too; it stands down rather than fight.
        Player.stopAll()
        stopTest()
        val source = store.sourceUri
        val withSound = testHearAndFeel && source != null && !ThrumFile.isImported(source)
        testJob = scope.launch {
            if (withSound) {
                // The song's own sound, from its first second: the window a
                // call plays (Mutalib, 2026-10-04: "hear and feel and feel only").
                val audio = withContext(Dispatchers.IO) {
                    runCatching {
                        MediaPlayer().apply {
                            setAudioAttributes(
                                AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_MEDIA)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                    .build(),
                            )
                            setDataSource(ctx, Uri.parse(source))
                            prepare()
                        }
                    }.getOrNull()
                }
                if (audio == null) {
                    failure = ctx.getString(R.string.player_unreadable)
                    testJob = null
                    return@launch
                }
                testAudio = audio
                audio.start()
                // Sound first, vibration second (Task 5): the motor waits for
                // the audio to be truly running.
                val giveUp = SystemClock.elapsedRealtime() + 2_000
                while (audio.currentPosition == 0 && SystemClock.elapsedRealtime() < giveUp) delay(2)
            }
            // The same score down the same ringtone route a call uses — so on
            // Silent it stays silent, and the test never lies.
            val refused = Haptics.play(ctx, built)
            if (refused != null) {
                failure = refused
            } else {
                val started = SystemClock.elapsedRealtime()
                while (true) {
                    val f = (SystemClock.elapsedRealtime() - started).toFloat() / built.durationMs
                    if (f >= 1f) break
                    progress = f
                    delay(Motion.FRAME)
                }
            }
            progress = -1f
            testAudio?.runCatching { release() }
            testAudio = null
            testJob = null
        }
    }

    ThrumPage(overTabs = true) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.app_name).uppercase(),
                style = ThrumType.wordmark,
                color = ThrumAccentInk,
                modifier = Modifier.semantics { heading() },
            )
            IconButtonBox(icon = "gear", label = stringResource(R.string.tab_settings), onClick = onOpenSettings)
        }

        val verdict = Setup.verdict(ringer, ringMode)
        val current = score
        val blocked = !capability.usable || !permitted || (current != null && verdict.blocked)

        ThrumCard(
            modifier = Modifier.padding(top = 10.dp),
            padding = PaddingValues(20.dp),
            borderColor = if (blocked) ThrumWarn.copy(alpha = 0.5f) else ThrumRule,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Overline(
                    stringResource(R.string.home_for_calls),
                    color = if (current != null && !blocked) ThrumAccentInk else ThrumInk2,
                )
                when {
                    blocked -> ThrumChip(stringResource(R.string.setup_wont_title), kind = ChipKind.WARN, icon = "warn")
                    current != null -> ThrumChip(
                        stringResource(R.string.armed_title) + " · " + stringResource(ringerWord(ringer)),
                        kind = ChipKind.ACCENT,
                        hasDot = true,
                    )
                    else -> ThrumChip(stringResource(R.string.settings_off), kind = ChipKind.GREY)
                }
            }

            when {
                // Terminal and honest; first launch already showed this, and
                // there is still no button that could change it.
                !capability.usable -> {
                    CardTitle(stringResource(R.string.blocked_title))
                    CardBody(stringResource(R.string.blocked_body))
                }

                // Without call access nothing fires, whatever else is set.
                !permitted -> {
                    CardTitle(stringResource(R.string.permission_title))
                    CardBody(stringResource(R.string.permission_body))
                    PrimaryButton(
                        text = stringResource(R.string.permission_action),
                        small = true,
                        onClick = {
                            askedForAccess = true
                            openCallAccess(ctx)
                        },
                        modifier = Modifier.padding(top = 14.dp),
                    )
                    if (askedForAccess && mayBeLocked) {
                        CallAccessLockedHelp(modifier = Modifier.padding(top = Space.S5))
                    }
                }

                reading != null -> Row(
                    modifier = Modifier.padding(top = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.S3),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.5.dp, color = ThrumAccentInk)
                    Column {
                        Text(
                            stringResource(R.string.loading_title, reading.orEmpty()),
                            style = ThrumType.row,
                            color = ThrumInk,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(stringResource(R.string.loading_body), style = ThrumType.meta, color = ThrumInk2)
                    }
                }

                current == null -> {
                    // Screen 27: nothing chosen means Android's own buzz — no
                    // demo pattern standing in (PROFILE §4 item 3).
                    if (failure != null) {
                        CardTitle(stringResource(R.string.error_title), color = ThrumWarn)
                        CardBody(failure.orEmpty())
                    } else {
                        CardTitle(stringResource(R.string.home_none_title))
                        CardBody(stringResource(R.string.home_none_body))
                    }
                    PrimaryButton(
                        text = stringResource(if (failure != null) R.string.error_action else R.string.empty_action),
                        small = true,
                        onClick = { picker.launch(arrayOf("audio/*")) },
                        modifier = Modifier.padding(top = 14.dp),
                    )
                    ThrumTextButton(
                        text = stringResource(R.string.home_none_music),
                        onClick = onOpenMusic,
                        color = ThrumInk2,
                        modifier = Modifier.padding(top = Space.S1),
                    )
                }

                else -> ReadyCard(
                    score = current,
                    verdict = verdict,
                    progress = progress,
                    failure = failure,
                    ringMode = ringMode,
                    onRingMode = { on ->
                        ringMode = on
                        store.fireInRingMode = on
                    },
                    onTest = { if (testJob != null) stopTest() else playTestCall(current) },
                    // An imported haptic has no song to hear: feel only, and no choice to offer.
                    canHear = store.sourceUri?.let { !ThrumFile.isImported(it) } == true,
                    hearAndFeel = testHearAndFeel,
                    onHearAndFeel = { on ->
                        stopTest()
                        testHearAndFeel = on
                        store.testHearAndFeel = on
                    },
                    testing = testJob != null,
                    onChange = { picker.launch(arrayOf("audio/*")) },
                    onSoundSettings = {
                        // Guarded: an OEM build can ship without the sound
                        // page, and a crash on the screen whose job is to
                        // explain a problem would be a poor joke.
                        runCatching { ctx.startActivity(Intent(Settings.ACTION_SOUND_SETTINGS)) }
                            .onFailure { runCatching { ctx.startActivity(Intent(Settings.ACTION_SETTINGS)) } }
                    },
                )
            }
        }

        // Task 19's last-call line: a call Thrum actually played, or nothing.
        lastCall?.let { fired ->
            Text(
                stringResource(R.string.home_last_call, Home.callWords(fired.at), Home.latencyWords(fired.latencyMs)),
                style = ThrumType.meta,
                color = ThrumInk2,
                modifier = Modifier.padding(horizontal = Space.S1, vertical = 10.dp),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Space.S2),
            horizontalArrangement = Arrangement.spacedBy(Space.S3),
        ) {
            ShortcutCard(
                icon = "music",
                title = stringResource(R.string.home_card_music),
                help = stringResource(R.string.home_card_music_help),
                onClick = onOpenMusic,
                modifier = Modifier.weight(1f),
            )
            ShortcutCard(
                icon = "plus",
                title = stringResource(R.string.home_card_create),
                help = stringResource(R.string.home_card_create_help),
                onClick = onOpenCreate,
                modifier = Modifier.weight(1f),
            )
        }

        RecentlyPlayed()

        if (onDiagnostics != null) {
            Spacer(Modifier.height(Space.S4))
            TextButton(onClick = onDiagnostics) {
                Text(stringResource(R.string.diagnostics), style = ThrumType.meta, color = ThrumInk2)
            }
        }
    }
}

/** Screen 18's card once a song is chosen, with the verdict's own words when it needs them. */
@Composable
private fun ColumnScope.ReadyCard(
    score: Score,
    verdict: Setup.Verdict,
    progress: Float,
    failure: String?,
    ringMode: Boolean,
    onRingMode: (Boolean) -> Unit,
    onTest: () -> Unit,
    onChange: () -> Unit,
    onSoundSettings: () -> Unit,
    canHear: Boolean,
    hearAndFeel: Boolean,
    onHearAndFeel: (Boolean) -> Unit,
    testing: Boolean,
) {
    Text(
        score.sourceName,
        style = ThrumType.title,
        color = ThrumInk,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = 10.dp),
    )
    Text(
        stringResource(R.string.ready_meta, clockOf(score.durationMs), score.pulseCount()),
        style = ThrumType.meta,
        color = ThrumInk2,
    )
    PulseRibbon(
        score = score,
        progress = progress,
        height = 72.dp,
        barWidth = 3.dp,
        barGap = 1.dp,
        modifier = Modifier.padding(top = Space.S3),
    )

    // Said only when the chip's word is not enough. Silent mode is the case
    // that matters: to most people silent and vibrate both mean "no sound".
    when (verdict) {
        Setup.Verdict.WILL_FIRE -> Unit
        Setup.Verdict.WILL_FIRE_IN_RING -> CardBody(stringResource(R.string.setup_ring_on))
        Setup.Verdict.UNKNOWN -> CardBody(stringResource(R.string.setup_unknown))
        Setup.Verdict.WONT_FIRE_SILENT -> {
            CardBody(stringResource(R.string.setup_silent))
            CardNote(stringResource(R.string.setup_silent_help))
            SecondaryButton(
                text = stringResource(R.string.setup_sound_action),
                small = true,
                onClick = onSoundSettings,
                modifier = Modifier.padding(top = Space.S3),
            )
        }
        Setup.Verdict.WONT_FIRE_RING_OFF -> {
            // The sentence says the switch is "below" — so it is, right here.
            CardBody(stringResource(R.string.setup_ring_off))
            SwitchLine(
                title = stringResource(R.string.ring_mode_label),
                checked = ringMode,
                onChange = onRingMode,
            )
            CardNote(stringResource(R.string.setup_ring_off_help))
        }
    }

    // The player's own choice, for the test call too. Same words, same order.
    if (canHear) {
        ThrumSegmentedControl(
            options = listOf(stringResource(R.string.player_hear_feel), stringResource(R.string.player_feel_only)),
            selectedIndex = if (hearAndFeel) 0 else 1,
            onSelect = { onHearAndFeel(it == 0) },
            modifier = Modifier.padding(top = Space.S4),
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Space.S3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.S2),
    ) {
        SecondaryButton(
            text = stringResource(if (testing) R.string.ready_stop else R.string.ready_feel),
            icon = if (testing) null else "play",
            small = true,
            onClick = onTest,
            modifier = Modifier.weight(1f),
        )
        ThrumTextButton(text = stringResource(R.string.home_change), onClick = onChange)
    }
    // Hearing the song in a test must not suggest a call will play it.
    if (canHear && hearAndFeel) CardNote(stringResource(R.string.home_test_sound_note))
    CardNote(stringResource(R.string.home_calls_window))
    failure?.let { CardNote(it, color = ThrumWarn) }
}

@Composable
private fun CardTitle(text: String, color: androidx.compose.ui.graphics.Color = ThrumInk) {
    Text(text, style = ThrumType.title, color = color, modifier = Modifier.padding(top = 10.dp))
}

@Composable
private fun CardBody(text: String) {
    Text(text, style = ThrumType.lead, color = ThrumInkSoft, modifier = Modifier.padding(top = Space.S2))
}

@Composable
private fun CardNote(text: String, color: androidx.compose.ui.graphics.Color = ThrumInk2) {
    Text(text, style = ThrumType.meta, color = color, modifier = Modifier.padding(top = 10.dp))
}

@Composable
private fun SwitchLine(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Space.S3),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = ThrumType.row, color = ThrumInk, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(Space.S3))
        ThrumSwitch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ShortcutCard(icon: String, title: String, help: String, onClick: () -> Unit, modifier: Modifier) {
    ThrumCard(modifier = modifier, padding = PaddingValues(Space.S4), onClick = onClick) {
        ThrumIcon(name = icon, tint = ThrumAccentInk, size = 22.dp)
        Text(title, style = ThrumType.row, color = ThrumInk, modifier = Modifier.padding(top = Space.S2))
        Text(help, style = ThrumType.meta, color = ThrumInk2, modifier = Modifier.padding(top = 2.dp))
    }
}

/**
 * The songs most recently played, from the player's own history (PROFILE §4
 * item 11), with their real rhythms. Nothing played yet means no section.
 */
@Composable
private fun RecentlyPlayed() {
    val ctx = LocalContext.current
    val uris = remember { Store(ctx).recentlyPlayed }
    var rows by remember { mutableStateOf<List<Pair<Track, Haptic?>>>(emptyList()) }
    LaunchedEffect(uris) {
        val dao = LibraryDb.get(ctx).dao()
        rows = withContext(Dispatchers.IO) {
            uris.mapNotNull { uri -> dao.trackFor(uri)?.toTrack()?.let { it to dao.hapticFor(uri)?.toHaptic() } }
        }
    }
    if (rows.isEmpty()) return

    Overline(stringResource(R.string.home_recent), modifier = Modifier.padding(top = 22.dp, bottom = Space.S2, start = 2.dp))
    ThrumCard(padding = PaddingValues(0.dp)) {
        val queue = rows.map { it.first }
        rows.forEachIndexed { index, (track, haptic) ->
            if (index > 0) HorizontalDivider(color = ThrumRule, thickness = 1.dp)
            // The row opens the song's page; its play button plays it here.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) { Player.openSong(ctx, track, queue) }
                    .padding(horizontal = Space.S3, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(Modifier.width(64.dp)) {
                    if (haptic != null) {
                        PulseRibbon(score = haptic.score, limit = ROW_RIBBON_STEPS, height = 26.dp, barWidth = 2.dp, barGap = 1.dp)
                    } else {
                        IconCircle(icon = "music", size = 40.dp, iconSize = 18.dp)
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(track.name, style = ThrumType.row, color = ThrumInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(track.artist.ifEmpty { null }, track.durationMs.takeIf { it > 0 }?.let(::clockOf))
                            .joinToString(" · "),
                        style = ThrumType.meta,
                        color = ThrumInk2,
                        maxLines = 1,
                    )
                }
                CirclePlayButton(
                    playing = Player.now?.track?.sourceUri == track.sourceUri && Player.now?.playing == true,
                    size = 38.dp,
                    iconSize = 13.dp,
                    onClick = { Player.playHere(ctx, track, queue) },
                )
            }
        }
    }
}

private fun ringerWord(ringer: Setup.Ringer): Int = when (ringer) {
    Setup.Ringer.VIBRATE -> R.string.home_ringer_vibrate
    Setup.Ringer.RING -> R.string.home_ringer_ring
    Setup.Ringer.SILENT -> R.string.home_ringer_silent
    Setup.Ringer.UNKNOWN -> R.string.home_ringer_unknown
}

/** About eight seconds of a song at 20 ms: enough to see its shape in a row. */
internal const val ROW_RIBBON_STEPS = 400
