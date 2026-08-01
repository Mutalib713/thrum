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
        pickedUri = uri
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
                    // Trim before fitting. Trimming keeps 20 ms steps; fitting
                    // would have halved them to 40 ms across the whole track,
                    // which is the chunkiness Mutalib felt. fitWithin stays as
                    // the backstop for anything the trim does not catch.
                    val built = builder?.build()
                        ?.firstSeconds(RINGTONE_SECONDS)
                        ?.fitWithin(Haptics.MAX_STEPS)
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
