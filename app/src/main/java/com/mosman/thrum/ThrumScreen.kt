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
    var pickedUri by remember { mutableStateOf<Uri?>(null) }
    var progress by remember { mutableStateOf(-1f) }
    var ringMode by remember { mutableStateOf(store.fireInRingMode) }
    var levels by remember { mutableStateOf<Levels?>(null) }
    var levelStepMs by remember { mutableStateOf(Demo.STEP_MS) }
    var trackName by remember { mutableStateOf(store.armedScore?.sourceName ?: "") }
    var punch by remember { mutableStateOf(store.punch) }
    var distance by remember { mutableStateOf(store.distance) }
    var ratePlaying by remember { mutableStateOf(0) }

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
        pickedUri = uri
        store.sourceUri = uri.toString()
        failure = null
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
                    score = null
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
                            minFelt = punch, bodyCeiling = ceilingFor(punch, distance),
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
            // again in the background and try once more. A dial that quietly
            // does nothing is worse than one that takes a moment.
            val saved = store.sourceUri ?: return
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
                if (result is Decoded.Ok) {
                    pickedUri = uri
                    levels = builder?.levels()
                    levelStepMs = builder?.stepMsUsed ?: Demo.STEP_MS
                    rescore()
                }
            }
            return
        }
        val rebuilt = ScoreBuilder.toScore(
            source, levelStepMs, trackName,
            minFelt = punch, bodyCeiling = ceilingFor(punch, distance),
        ).firstSeconds(RINGTONE_SECONDS).fitWithin(Haptics.MAX_STEPS)
        score = rebuilt
        if (armed) store.armedScore = rebuilt
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
                    ringMode = ringMode,
                    onRingMode = { on -> ringMode = on; store.fireInRingMode = on },
                    punch = punch,
                    texture = distance,
                    ratePlaying = ratePlaying,
                    onRateTest = { runRateTest() },
                    onPunch = { v -> punch = v; store.punch = v; rescore() },
                    onTexture = { v -> distance = v; store.distance = v; rescore() },
                    onArm = {
                        store.armedScore = state.score
                        armed = true
                    },
                    onPreview = { playWithSong(state.score) },
                    onFeel = { playAlone(state.score) },
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
    ringMode: Boolean,
    onRingMode: (Boolean) -> Unit,
    punch: Int,
    texture: Int,
    ratePlaying: Int,
    onRateTest: () -> Unit,
    onPunch: (Int) -> Unit,
    onTexture: (Int) -> Unit,
    onArm: () -> Unit,
    onPreview: () -> Unit,
    onFeel: () -> Unit,
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
            Text(
                stringResource(R.string.armed_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                stringResource(R.string.armed_body),
                style = MaterialTheme.typography.bodyLarge,
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
            if (!ringMode) {
                Text(
                    stringResource(R.string.armed_ring_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
 * How much of a track becomes the ringtone.
 *
 * A phone rings for roughly thirty seconds, so this is the part anyone will
 * ever feel, plus room. Keeping it short is what allows 20 ms steps instead of
 * the 40 ms a whole song would be coarsened to.
 */
private const val RINGTONE_SECONDS = 45

/** Taps per second, slowest first. Thrum's own scores currently sit near 3. */
private val RATE_LADDER = listOf(2, 3, 4, 6, 8, 12)
private const val RATE_GAP_MS = 900L

/**
 * Turn "distance from the music", 0–100, into the amplitude ceiling the detail
 * layer may reach.
 *
 * Counted downward on purpose — 0 is closest, where the detail layer is allowed
 * all the way up to the kick's own floor and the whole kit comes through. At 100
 * the ceiling is nothing and only the bare beat is left. The layer switches off
 * on its own once the ceiling falls below what the motor can actually produce.
 */
private fun ceilingFor(punch: Int, distance: Int): Int {
    val room = punch.coerceIn(0, Score.MAX_AMPLITUDE)
    return (room * (100 - distance.coerceIn(0, 100)) / 100)
}
