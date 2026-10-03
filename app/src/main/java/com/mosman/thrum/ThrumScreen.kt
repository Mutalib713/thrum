package com.mosman.thrum

import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Thrum's one screen. Task 7.
 *
 * Seven states, all designed rather than discovered later — `design-brief.md`
 * Phase 1 lists them and this file builds every branch. The layout is a single
 * vertical column with no top app bar: there is one screen, and a wordmark is
 * not navigation.
 */
sealed interface UiState {
    /** The motor cannot vary strength. Terminal, honest, no way forward. */
    data object Blocked : UiState

    /** Capable, but Android has not bound the notification listener. */
    data object NeedsPermission : UiState

    data object Empty : UiState
    data class Reading(val name: String) : UiState
    data class Failed(val message: String) : UiState
    data class Ready(val score: Score, val armed: Boolean) : UiState
}

@Composable
fun ThrumApp(onDiagnostics: (() -> Unit)? = null) {
    val ctx = LocalContext.current
    val store = remember { Store(ctx) }
    val capability = remember { Haptics.capability(ctx) }
    val scope = rememberCoroutineScope()

    var score by remember { mutableStateOf(store.armedScore) }
    var armed by remember { mutableStateOf(store.armedScore != null) }
    var reading by remember { mutableStateOf<String?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    // Seeded from the stored source, not null. On a restart the armed score is
    // restored but the levels are gone, and Preview used to be a dead button
    // because `pickedUri` had nothing in it — the file was right there in
    // storage the whole time. The draft pick lives here until Arm writes it.
    var pickedUri by remember { mutableStateOf(store.sourceUri?.let { Uri.parse(it) }) }
    var progress by remember { mutableStateOf(-1f) }
    var ringMode by remember { mutableStateOf(store.fireInRingMode) }
    var levels by remember { mutableStateOf<Levels?>(null) }
    var levelStepMs by remember { mutableStateOf(Demo.STEP_MS) }
    var trackName by remember { mutableStateOf(store.armedScore?.sourceName ?: "") }
    var punch by remember { mutableStateOf(store.punch) }
    var distance by remember { mutableStateOf(store.distance) }
    var body by remember { mutableStateOf(store.body) }
    var ratePlaying by remember { mutableStateOf(0) }

    /**
     * True while the phone's own ringtone buzz is playing.
     *
     * Deliberately separate from [progress]: the ribbon draws the armed score, and
     * animating it while a *different* vibration plays is exactly the class of lie
     * this screen keeps having to fix. The ribbon stays on the score, and this
     * flag only drives the button's own label.
     */
    var stockPlaying by remember { mutableStateOf(false) }
    // Set when a dial cannot be honoured, and shown beside the dials rather than
    // replacing the screen: the armed score is still armed and still plays, so
    // throwing the user out to an error state would overstate the problem.
    var rebuildProblem by remember { mutableStateOf<String?>(null) }

    // Polled rather than observed: the user leaves for system settings and
    // comes back, and a screen still showing "grant permission" after they
    // granted it is the kind of thing people uninstall over.
    val permitted by produceState(initialValue = true) {
        while (true) {
            value = NotificationManagerCompat.getEnabledListenerPackages(ctx)
                .contains(ctx.packageName)
            delay(POLL_MS)
        }
    }

    // The ringer mode, polled for the same reason the permission is: the user
    // leaves for Settings and comes back, and a verdict one screen out of date is
    // worse than none. This is the whole of Task 9 — see [Setup] for why there is
    // nothing else left to ask the user to change.
    val ringer by produceState(initialValue = Setup.Ringer.UNKNOWN) {
        while (true) {
            value = Setup.Ringer.of(Haptics.ringerMode(ctx))
            delay(POLL_MS)
        }
    }

    val player = remember { mutableStateOf<MediaPlayer?>(null) }

    // The coroutine driving the ribbon's playhead, held so it can be cancelled.
    //
    // Found on the device: without this, Stop silenced the motor but the sweep
    // kept running and immediately wrote `progress` back, so the playhead
    // carried on travelling and the Stop button stayed on screen for the rest of
    // the track — nearly three minutes on a 2:44 song. The vibrator's own record
    // said `CurrentVibration: null` while the UI still claimed to be playing,
    // which is this project's recurring bug in its newest costume: the screen
    // reporting an intention rather than a fact.
    val sweepJob = remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    fun stopEverything() {
        sweepJob.value?.cancel()
        sweepJob.value = null
        Haptics.stop(ctx)
        stockPlaying = false
        player.value?.runCatching { if (isPlaying) stop() }
        player.value?.runCatching { release() }
        player.value = null
        progress = -1f
    }
    DisposableEffect(Unit) { onDispose { stopEverything() } }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        stopEverything()
        // Hold the permission past this session, so the file can be analysed
        // again after a restart. Without it the URI survives and the access
        // does not, which is worse than not storing it.
        runCatching {
            ctx.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        // The draft pick only — not written to storage here.
        //
        // It used to be, and that was a Task 8 defect: choosing a file and then
        // walking away left the *armed* score's stored URI pointing at the file
        // that was merely being auditioned. After a restart the score was A's but
        // the file was B's, so the tuning dials rebuilt B's amplitudes under A's
        // name. The source is written when the user actually arms, by [Store.arm].
        pickedUri = uri
        failure = null
        rebuildProblem = null
        val name = AudioDecoder.displayName(ctx, uri)
        reading = name
        scope.launch {
            var builder: ScoreBuilder? = null
            val result = withContext(Dispatchers.IO) {
                AudioDecoder.decode(
                    ctx,
                    uri,
                    onFormat = { rate, _ -> builder = ScoreBuilder(rate, name = name) },
                    onMono = { samples, count -> builder?.feed(samples, count) },
                )
            }
            reading = null
            when (result) {
                is Decoded.Failed -> {
                    failure = result.message
                    // A failed draft pick must not blank a rhythm that is still
                    // armed and still what the phone will play. Storage is
                    // untouched either way; this keeps the screen honest about it.
                    if (!armed) score = null
                }

                is Decoded.Ok -> {
                    // Keep the analysed levels, not just the finished score:
                    // moving a slider then costs nothing, where re-decoding
                    // costs seven seconds. Tuning by feel is many small
                    // adjustments, and a wait between each is how tuning stops
                    // happening.
                    levels = builder?.levels()
                    levelStepMs = builder?.stepMsUsed ?: Demo.STEP_MS
                    trackName = name
                    // Trim before fitting. Trimming keeps 20 ms steps; fitting
                    // would have halved them to 40 ms across the whole track,
                    // which is the chunkiness Mutalib felt. fitWithin stays as
                    // the backstop for anything the trim does not catch.
                    val built = levels?.let {
                        ScoreBuilder.toScore(
                            it, levelStepMs, name,
                            minFelt = punch, bodyMs = body,
                            bodyCeiling = ceilingFor(punch, distance),
                        ).firstSeconds(RINGTONE_SECONDS).fitWithin(Haptics.MAX_STEPS)
                    }
                    if (built == null || built.isSilent()) {
                        failure = ctx.getString(R.string.error_silent)
                        score = null
                    } else {
                        score = built
                        armed = false
                    }
                }
            }
        }
    }

    /**
     * Rebuild from the kept levels after a slider moves.
     *
     * If the score is already armed, the armed copy is replaced too. A screen
     * showing one rhythm while the phone would play another is exactly the class
     * of lie this project keeps having to fix.
     */
    fun rescore() {
        val source = levels ?: run {
            // Levels are gone — this is a restart, with the score restored from
            // storage but nothing to rebuild it from. Analyse the saved file
            // again in the background and try once more.
            val saved = store.sourceUri ?: run {
                // Nothing to rebuild from at all. Say so rather than returning
                // quietly: a dial that moves and changes nothing is
                // indistinguishable from a broken dial, which is the same failure
                // this project keeps meeting in new costumes.
                rebuildProblem = ctx.getString(R.string.tune_no_source)
                return
            }
            if (reading != null) return
            reading = trackName.ifEmpty { score?.sourceName.orEmpty() }
            scope.launch {
                var builder: ScoreBuilder? = null
                val uri = Uri.parse(saved)
                val result = withContext(Dispatchers.IO) {
                    AudioDecoder.decode(
                        ctx,
                        uri,
                        onFormat = { rate, _ -> builder = ScoreBuilder(rate, name = trackName) },
                        onMono = { samples, count -> builder?.feed(samples, count) },
                    )
                }
                reading = null
                when (result) {
                    is Decoded.Ok -> {
                        pickedUri = uri
                        levels = builder?.levels()
                        levelStepMs = builder?.stepMsUsed ?: Demo.STEP_MS
                        rescore()
                    }

                    // Task 11's attack: the file was deleted after it was picked.
                    // This used to fall through silently, so moving a dial did
                    // nothing at all and said nothing at all — on a screen whose
                    // whole job is to never report an intention as a fact. The
                    // decoder's own sentence is the explanation, so it is shown
                    // as-is.
                    is Decoded.Failed -> rebuildProblem = result.message
                }
            }
            return
        }
        val rebuilt = ScoreBuilder.toScore(
            source, levelStepMs, trackName,
            minFelt = punch, bodyMs = body,
            bodyCeiling = ceilingFor(punch, distance),
        ).firstSeconds(RINGTONE_SECONDS).fitWithin(Haptics.MAX_STEPS)
        score = rebuilt
        rebuildProblem = null
        // Rewritten atomically with its source and tuning, so a restart between
        // a dial move and this line cannot restore a score that disagrees with
        // the settings beside it.
        if (armed) {
            store.arm(rebuilt, pickedUri?.toString() ?: store.sourceUri, punch, distance, body)
        }
    }

    /**
     * Play the same tap at rising speeds and let a hand find where they merge.
     *
     * The rates are announced on screen as they play, so the answer is a number
     * Mutalib can read off rather than a feeling he has to describe.
     */
    fun runRateTest() {
        stopEverything()
        sweepJob.value = scope.launch {
            for (rate in RATE_LADDER) {
                ratePlaying = rate
                val train = Demo.pulseTrain(rate)
                Haptics.play(ctx, train)
                delay(train.durationMs + RATE_GAP_MS)
                Haptics.stop(ctx)
                delay(RATE_GAP_MS)
            }
            ratePlaying = 0
        }
    }

    fun playAlone(built: Score) {
        stopEverything()
        val failed = Haptics.play(ctx, built)
        if (failed != null) {
            failure = failed
            return
        }
        sweepJob.value = scope.launch { sweep(built.durationMs) { progress = it } }
    }

    /**
     * Play the phone's own ringtone buzz, so it can be held up against Thrum's
     * rhythm without trusting anyone's memory of what the stock buzz feels like.
     *
     * The stock pattern repeats for ever, so the safety cap is not optional — see
     * the rule on [Haptics.play]. It also leaves [progress] alone on purpose; see
     * [stockPlaying].
     */
    fun playStockBuzz() {
        // The label reads "Stop the buzz" while it plays, so the stop has to be
        // decided BEFORE anything is cleared. stopEverything() resets
        // stockPlaying, and the check used to sit after it — dead code, so every
        // press while the buzz played started a fresh one instead of stopping
        // it. Task 15 names this bug; it is the comparison button the whole
        // feel-fix depends on.
        val wasPlaying = stockPlaying
        stopEverything()
        if (wasPlaying) return
        val failed = Haptics.playStockRingtoneBuzz(ctx)
        if (failed != null) {
            failure = failed
            return
        }
        stockPlaying = true
        sweepJob.value = scope.launch {
            delay(Haptics.STOCK_BUZZ_MS)
            stockPlaying = false
            Haptics.stop(ctx)
        }
    }

    fun playWithSong(built: Score) {
        val uri = pickedUri ?: return
        stopEverything()
        val mp = MediaPlayer()
        player.value = mp
        mp.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build(),
        )
        mp.setOnCompletionListener { stopEverything() }
        mp.setOnPreparedListener { ready ->
            ready.start()
            sweepJob.value = scope.launch {
                // Start the vibration when sound actually leaves the speaker.
                // Asking a player to play and assuming it has is the same
                // mistake as assuming a vibration happened.
                var at = 0
                val giveUp = SystemClock.elapsedRealtime() + START_WAIT_MS
                while (at == 0 && SystemClock.elapsedRealtime() < giveUp) {
                    at = runCatching { ready.currentPosition }.getOrDefault(0)
                    delay(2)
                }
                val aligned = built.from(at.toLong())
                Haptics.play(ctx, aligned)
                sweep(aligned.durationMs) { progress = it }
            }
        }
        val broke = runCatching {
            mp.setDataSource(ctx, uri)
            mp.prepareAsync()
        }.exceptionOrNull()
        if (broke != null) failure = ctx.getString(R.string.error_title)
    }

    val state: UiState = when {
        !capability.usable -> UiState.Blocked
        !permitted -> UiState.NeedsPermission
        reading != null -> UiState.Reading(reading!!)
        failure != null -> UiState.Failed(failure!!)
        score != null -> UiState.Ready(score!!, armed)
        else -> UiState.Empty
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.S5, vertical = Space.S6),
            verticalArrangement = Arrangement.spacedBy(Space.S5),
        ) {
            Masthead(armed = state is UiState.Ready && state.armed)

            when (state) {
                UiState.Blocked -> Blocked(capability)
                UiState.NeedsPermission -> NeedsPermission {
                    ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }

                UiState.Empty -> Empty { picker.launch(arrayOf("audio/*")) }
                is UiState.Reading -> Reading(state.name)
                is UiState.Failed -> Failed(state.message) { picker.launch(arrayOf("audio/*")) }
                is UiState.Ready -> Ready(
                    score = state.score,
                    armed = state.armed,
                    progress = progress,
                    ringer = ringer,
                    ringMode = ringMode,
                    onRingMode = { on -> ringMode = on; store.fireInRingMode = on },
                    onSoundSettings = {
                        // The public action for Sound & vibration, which is where
                        // the ringer mode lives on every device this app targets.
                        // Guarded because an OEM build can ship without it, and a
                        // crash on the one screen whose job is to explain a
                        // problem would be a poor joke.
                        runCatching { ctx.startActivity(Intent(Settings.ACTION_SOUND_SETTINGS)) }
                            .onFailure {
                                runCatching { ctx.startActivity(Intent(Settings.ACTION_SETTINGS)) }
                            }
                    },
                    punch = punch,
                    texture = distance,
                    body = body,
                    ratePlaying = ratePlaying,
                    stockPlaying = stockPlaying,
                    rebuildProblem = rebuildProblem,
                    onRateTest = { runRateTest() },
                    // Deliberately no `store.punch = v` (and the same for the
                    // other two) on these lines. The stored tuning describes the
                    // **armed** score, and [Store.arm] is its only writer.
                    //
                    // Writing a dial straight to the store is how the phone came
                    // to be armed at a 100 ms Body while the screen said 400 ms:
                    // picking a file sets `armed = false` (see the draft path
                    // above), so `rescore()` rebuilt the score on screen and then
                    // skipped `store.arm` — while the pref had already moved. The
                    // motor kept playing the old rhythm and a restart restored
                    // dials that no score had ever been built with. Measured, not
                    // guessed: `shared_prefs` said `body=400` and the armed score
                    // reproduced exactly at `body=100`, 99.82 % of steps.
                    //
                    // A dial the phone will not play must not be what survives.
                    onPunch = { v -> punch = v; rescore() },
                    onTexture = { v -> distance = v; rescore() },
                    onBody = { v -> body = v; rescore() },
                    onArm = {
                        // One write, so the score, the file it came from, and the
                        // tuning can never disagree after a restart. See Store.arm.
                        store.arm(state.score, pickedUri?.toString(), punch, distance, body)
                        armed = true
                    },
                    onPreview = { playWithSong(state.score) },
                    onFeel = { playAlone(state.score) },
                    onStockBuzz = { playStockBuzz() },
                    onStop = { stopEverything() },
                    onChange = { picker.launch(arrayOf("audio/*")) },
                )
            }

            if (onDiagnostics != null) {
                Spacer(Modifier.width(Space.S1))
                TextButton(onClick = onDiagnostics) {
                    Text(
                        stringResource(R.string.diagnostics),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Runs the ribbon's playhead across [totalMs]. */
private suspend fun sweep(totalMs: Long, onProgress: (Float) -> Unit) {
    if (totalMs <= 0) return
    val started = SystemClock.elapsedRealtime()
    while (true) {
        val fraction = (SystemClock.elapsedRealtime() - started).toFloat() / totalMs
        onProgress(fraction.coerceIn(0f, 1f))
        if (fraction >= 1f) break
        delay(Motion.FRAME)
    }
    onProgress(-1f)
}

@Composable
private fun Masthead(armed: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.S1)) {
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                stringResource(R.string.tagline),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // The armed light. Critique pass: this was colour and nothing else,
        // which means it did not exist for anyone using TalkBack, or for anyone
        // who cannot separate the accent from the field. Colour is never the
        // only signal.
        AnimatedVisibility(visible = armed, enter = fadeIn(), exit = fadeOut()) {
            val label = stringResource(R.string.armed_indicator)
            Box(
                Modifier
                    .size(Space.S3)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .semantics { contentDescription = label },
            )
        }
    }
}

@Composable
private fun Blocked(capability: Haptics.Capability) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.S3)) {
        Text(
            stringResource(R.string.blocked_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            stringResource(R.string.blocked_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            stringResource(
                R.string.blocked_detail,
                if (capability.hasVibrator) "yes" else "none",
                if (capability.amplitudeControl) "yes" else "no",
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    // Deliberately no action. There is nothing this app can offer this phone,
    // and a "try anyway" button would be a lie with a tap target.
}

@Composable
private fun NeedsPermission(onGrant: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.S4)) {
        Text(
            stringResource(R.string.permission_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            stringResource(R.string.permission_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Primary(stringResource(R.string.permission_action), onGrant)
    }
}

@Composable
private fun Empty(onPick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.S4)) {
        PulseRibbon(
            score = null,
            progress = -1f,
            barColour = MaterialTheme.colorScheme.primary,
            silentColour = MaterialTheme.colorScheme.outline,
            playedColour = MaterialTheme.colorScheme.primary,
        )
        Text(
            stringResource(R.string.empty_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            stringResource(R.string.empty_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Primary(stringResource(R.string.empty_action), onPick)
    }
}

@Composable
private fun Reading(name: String) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.S4)) {
        PulseRibbon(
            score = null,
            progress = -1f,
            barColour = MaterialTheme.colorScheme.primary,
            silentColour = MaterialTheme.colorScheme.outline,
            playedColour = MaterialTheme.colorScheme.primary,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.S3),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(Space.S5),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 2.dpOf(),
            )
            Column {
                Text(
                    stringResource(R.string.loading_title, name),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(R.string.loading_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Failed(message: String, onPick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.S4)) {
        Text(
            stringResource(R.string.error_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.semantics { heading() },
        )
        // The decoder's own words. They say what happened and what would work
        // instead, which is more useful than a generic apology.
        Text(
            message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Primary(stringResource(R.string.error_action), onPick)
    }
}

@Composable
private fun Ready(
    score: Score,
    armed: Boolean,
    progress: Float,
    ringer: Setup.Ringer,
    ringMode: Boolean,
    onRingMode: (Boolean) -> Unit,
    onSoundSettings: () -> Unit,
    punch: Int,
    texture: Int,
    body: Int,
    ratePlaying: Int,
    stockPlaying: Boolean,
    rebuildProblem: String?,
    onRateTest: () -> Unit,
    onPunch: (Int) -> Unit,
    onTexture: (Int) -> Unit,
    onBody: (Int) -> Unit,
    onArm: () -> Unit,
    onPreview: () -> Unit,
    onFeel: () -> Unit,
    onStockBuzz: () -> Unit,
    onStop: () -> Unit,
    onChange: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.S4)) {
        PulseRibbon(
            score = score,
            progress = progress,
            barColour = MaterialTheme.colorScheme.primary,
            silentColour = MaterialTheme.colorScheme.outline,
            playedColour = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            score.sourceName,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            stringResource(
                R.string.ready_meta,
                clockOf(score.durationMs),
                score.pulseCount(),
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (armed) {
            // Replaces two static lines that used to sit here — "Put your phone
            // on vibrate", and a note describing what happens when the ringer is
            // on. Both were instructions this screen could not check, and the
            // second was wrong whenever the ringer was not actually on. This
            // reads the phone's real ringer mode and states what will happen.
            //
            // It is the same distinction R2 taught the hard way: the app spent a
            // day trusting its own `FIRED` event while the system was throwing
            // the vibration away, and only a hand on the phone disproved it. A
            // screen must not describe an intention.
            SetupVerdict(
                verdict = Setup.verdict(ringer, ringMode),
                onSoundSettings = onSoundSettings,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.fillMaxWidth(0.76f)) {
                    Text(
                        stringResource(R.string.ring_mode_label),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        stringResource(R.string.ring_mode_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = ringMode, onCheckedChange = onRingMode)
            }
        } else {
            Primary(stringResource(R.string.ready_action), onArm)
        }

        // One filled button per screen. Everything here is secondary by design:
        // a screen of three filled buttons has no primary action.
        //
        // Critique pass: this was four buttons of equal weight in two rows, one
        // of which ("Stop") did nothing at all unless something was playing.
        // A dead control teaches people to distrust the live ones.
        val playing = progress >= 0f
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Space.S2),
        ) {
            if (playing) {
                Secondary(stringResource(R.string.ready_stop), Modifier.weight(1f), onStop)
            } else {
                Secondary(stringResource(R.string.ready_preview), Modifier.weight(1f), onPreview)
                Secondary(stringResource(R.string.ready_feel), Modifier.weight(1f), onFeel)
            }
        }

        // The comparison button. It gets its own row rather than becoming a third
        // `weight(1f)` above: three buttons across 411 dp is how "Play it with the
        // song" already lost its last word, and a comparison you cannot read the
        // label of is not much of a comparison.
        //
        // It plays the phone's real ringtone vibration, read off `dumpsys
        // vibrator_manager` — one second at full amplitude, then one second of
        // silence. Answering "is it strong enough?" needs the actual thing beside
        // it, not a memory of it.
        Secondary(
            label = stringResource(
                if (stockPlaying) R.string.ready_stock_stop else R.string.ready_stock,
            ),
            modifier = Modifier.fillMaxWidth(),
            onClick = onStockBuzz,
        )
        Text(
            stringResource(R.string.ready_stock_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        TextButton(onClick = onChange, modifier = Modifier.heightIn(min = Touch.min)) {
            Text(
                stringResource(if (armed) R.string.armed_change else R.string.error_action),
                style = MaterialTheme.typography.labelLarge,
            )
        }

        // Steps and stillness, not "strength": the strongest amplitude is 255 on
        // every score by construction, so printing it said nothing. How much of
        // a track is *still* is the number that separates a rhythm from a buzz,
        // and it is the one that was wrong for most of Task 4.
        val still = score.amplitudes.count { it == 0 } * 100 / score.amplitudes.size.coerceAtLeast(1)
        Text(
            stringResource(R.string.tech_row, score.amplitudes.size, score.stepMs, still),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // The tuning, on the product screen rather than hidden in diagnostics.
        // The plan called this taste from the start, and taste belongs to the
        // person holding the phone.
        Text(
            stringResource(R.string.tune_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        Dial(
            label = stringResource(R.string.tune_punch, punch),
            help = stringResource(R.string.tune_punch_help),
            value = punch.toFloat(),
            // Never to 255: see ScoreBuilder.MIN_HEADROOM. A floor at the
            // ceiling leaves no room for a track to have loud and quiet beats.
            range = 120f..(Score.MAX_AMPLITUDE - ScoreBuilder.MIN_HEADROOM).toFloat(),
            onChange = onPunch,
        )
        Dial(
            label = stringResource(R.string.tune_body, body),
            help = stringResource(R.string.tune_body_help),
            value = body.toFloat(),
            // Milliseconds, not an invented 0–100. Punch and Distance show
            // abstract numbers because they map onto internal amplitudes; this
            // one is a duration, and "240 ms" is a fact the person tuning it can
            // reason about. The trade is legible in the same units: longer
            // drives the motor harder against a table, shorter keeps the gaps
            // between beats that make it read as a rhythm.
            range = ScoreBuilder.BODY_MIN_MS.toFloat()..ScoreBuilder.BODY_MAX_MS.toFloat(),
            onChange = onBody,
        )
        Dial(
            label = stringResource(R.string.tune_texture, texture),
            help = stringResource(R.string.tune_texture_help),
            value = texture.toFloat(),
            // A plain 0–100, where 0 is closest to the music.
            //
            // This dial used to show raw motor amplitudes, whose maximum moved
            // whenever Punch moved — which is why "the max is 161" kept needing
            // explaining, and why the scale meant nothing to anyone holding the
            // phone. The amplitude arithmetic belongs in [ceilingFor]; the
            // number on screen belongs to the person tuning it.
            range = 0f..100f,
            onChange = onTexture,
        )
        Text(
            stringResource(R.string.tune_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Why a dial is not doing anything, next to the dials. Task 11: the source
        // file can be deleted or have its permission withdrawn between the pick and
        // the next nudge of a slider, and the honest answer is to say so rather
        // than let the control move and nothing happen.
        if (rebuildProblem != null) {
            Text(
                stringResource(R.string.tune_problem, rebuildProblem),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        // Measuring the instrument rather than tuning blind against it. If the
        // motor cannot separate taps at the rate a score asks for, no amount of
        // analyser work will be felt, and several rounds of tuning have now
        // produced real changes in the data and almost none in the hand.
        Text(
            stringResource(R.string.rate_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            stringResource(R.string.rate_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (ratePlaying > 0) {
            Text(
                stringResource(R.string.rate_playing, ratePlaying),
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Secondary(stringResource(R.string.rate_run), Modifier.fillMaxWidth(), onRateTest)
    }
}

/**
 * The one honest answer to "will this work?" — Task 9.
 *
 * Shown only once a score is armed, because that is the moment the claim becomes
 * real: before arming, "will it work" is a question about a setting, and after,
 * it is a promise about the next call.
 *
 * Three things are deliberately absent.
 *
 * - **No settings this app changes.** `PROFILE.md` §9: where a system setting has
 *   to move, the app explains why and sends the user to do it. Silent mode is the
 *   only one, and it gets a button that opens Settings, not a button that fixes it.
 * - **No advice about `vibrate_when_ringing` or vibration intensity.** Task 2 left
 *   both alone through thirteen real calls with no effect; the last `RINGTONE`
 *   vibration wins regardless. Telling a user to change them would be teaching a
 *   superstition.
 * - **Colour is never the only signal.** The headline says the verdict in words —
 *   "Ready" or "Won't work yet" — and the colour agrees with it. The armed dot in
 *   the masthead got that same critique pass.
 */
@Composable
private fun SetupVerdict(verdict: Setup.Verdict, onSoundSettings: () -> Unit) {
    Text(
        stringResource(if (verdict.blocked) R.string.setup_wont_title else R.string.armed_title),
        style = MaterialTheme.typography.headlineMedium,
        color = if (verdict.blocked) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.primary
        },
        modifier = Modifier.semantics { heading() },
    )
    Text(
        stringResource(
            when (verdict) {
                Setup.Verdict.WILL_FIRE -> R.string.setup_vibrate
                Setup.Verdict.WILL_FIRE_IN_RING -> R.string.setup_ring_on
                Setup.Verdict.WONT_FIRE_SILENT -> R.string.setup_silent
                Setup.Verdict.WONT_FIRE_RING_OFF -> R.string.setup_ring_off
                Setup.Verdict.UNKNOWN -> R.string.setup_unknown
            },
        ),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onBackground,
    )
    // The remedy, and only when there is one worth reading. The ring-mode fix is
    // the switch immediately below this, so it needs a sentence, not a paragraph.
    when (verdict) {
        Setup.Verdict.WONT_FIRE_SILENT -> Text(
            stringResource(R.string.setup_silent_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Setup.Verdict.WONT_FIRE_RING_OFF -> Text(
            stringResource(R.string.setup_ring_off_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        else -> Unit
    }
    if (verdict.needsSoundSettings) {
        Secondary(
            stringResource(R.string.setup_sound_action),
            Modifier.fillMaxWidth(),
            onSoundSettings,
        )
    }
}

@Composable
private fun Dial(
    label: String,
    help: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.S1)) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(
            help,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = value.coerceIn(range),
            onValueChange = { onChange(it.toInt()) },
            valueRange = range,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Touch.min),
        )
    }
}

@Composable
private fun Primary(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Touch.min),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun Secondary(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = Touch.min),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

/** `3:58`, the way a music player writes it. */
private fun clockOf(ms: Long): String {
    val total = ms / 1000
    return "%d:%02d".format(total / 60, total % 60)
}

@Composable
private fun Int.dpOf() = androidx.compose.ui.unit.Dp(this.toFloat())

private const val POLL_MS = 800L
private const val START_WAIT_MS = 2000L

/**
 * How much of a track becomes the ringtone, and the normalisation window.
 * Lives in [ScoreBuilder] now — it is analysis, not presentation, and it has to
 * be reachable from the unit tests.
 */
private const val RINGTONE_SECONDS = ScoreBuilder.RINGTONE_SECONDS

/** Taps per second, slowest first. Thrum's own scores currently sit near 3. */
private val RATE_LADDER = listOf(2, 3, 4, 6, 8, 12)
private const val RATE_GAP_MS = 900L

/**
 * Turn "distance from the music", 0–100, into the amplitude ceiling the detail
 * layer may reach.
 *
 * Moved to [ScoreBuilder.ceilingFor] so it can be unit-tested — it is
 * arithmetic, not presentation, and the dead-dial bug it now fixes was exactly
 * the kind that a test on the PC catches and a hand on a phone does not.
 */
private fun ceilingFor(punch: Int, distance: Int): Int = ScoreBuilder.ceilingFor(punch, distance)
