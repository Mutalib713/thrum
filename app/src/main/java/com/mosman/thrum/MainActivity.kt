package com.mosman.thrum

import android.content.Intent
import android.os.Bundle
import android.os.VibrationEffect
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Task 2's interface. Still a probe, not the product.
 *
 * The event log is the important part. There is no cable on this machine, so
 * `adb logcat` is unavailable and this screen is the only way to see what the
 * listener actually did during a real call. Task 2's results table gets filled
 * in by reading it.
 *
 * The real single-screen UI is Task 7. Don't grow this file into it.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var diagnostics by remember { mutableStateOf(false) }
            ThrumTheme {
                if (diagnostics) {
                    // Back returns to the app rather than closing it.
                    BackHandler { diagnostics = false }
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        ProbeScreen()
                    }
                } else {
                    // Task 18's frame: phone check, seven first-launch screens,
                    // then the tabs with Home carrying the calls screen. The
                    // probe stays reachable from Home in debug builds — Task
                    // 10's soak needs its event log, and the R8 ladder has to
                    // run again on every new phone. It is not product surface.
                    ThrumRoot(onDiagnostics = if (BuildConfig.DEBUG) ({ diagnostics = true }) else null)
                }
            }
        }
    }
}

@Composable
private fun ProbeScreen() {
    val ctx = LocalContext.current
    val store = remember { Store(ctx) }
    val capability = remember { Haptics.capability(ctx) }
    val scores = remember { Demo.all() }

    var fireInRingMode by remember { mutableStateOf(store.fireInRingMode) }
    var loopWhileRinging by remember { mutableStateOf(store.loopWhileRinging) }
    var logRefresh by remember { mutableStateOf(0) }

    // Task 3. Decoding runs off the main thread — a three-minute track takes
    // long enough that doing it here would freeze the screen and, on a slow
    // file, trip Android's "app isn't responding" dialog.
    val scope = rememberCoroutineScope()
    var decoding by remember { mutableStateOf(false) }
    var decoded by remember { mutableStateOf<Decoded?>(null) }
    var score by remember { mutableStateOf<Score?>(null) }
    var probing by remember { mutableStateOf(false) }
    var pickedUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var levels by remember { mutableStateOf<Levels?>(null) }
    var levelStepMs by remember { mutableStateOf(Demo.STEP_MS) }
    var strength by remember { mutableStateOf(ScoreBuilder.MIN_FELT.toFloat()) }
    var playingTogether by remember { mutableStateOf(false) }

    // Task 5: the audio and the vibration at once, because the only way to judge
    // whether a rhythm matches a track is to feel it against the track. Held
    // across recompositions and released with the screen — a leaked MediaPlayer
    // keeps playing after the app is gone.
    val player = remember { mutableStateOf<android.media.MediaPlayer?>(null) }
    DisposableEffect(Unit) {
        onDispose {
            player.value?.runCatching { release() }
            player.value = null
            Haptics.stop(ctx)
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            decoded = null
            score = null
            pickedUri = uri
            decoding = true
            scope.launch {
                // Task 4. The analyser consumes the decoder's chunks as they
                // arrive and keeps one number per 20 ms, so a four-minute track
                // costs about 12,000 integers instead of the 24 MB it came from.
                var builder: ScoreBuilder? = null
                val result = withContext(Dispatchers.IO) {
                    AudioDecoder.decode(
                        ctx,
                        uri,
                        onFormat = { rate, _ ->
                            builder = ScoreBuilder(rate, name = AudioDecoder.displayName(ctx, uri))
                        },
                        onMono = { samples, count -> builder?.feed(samples, count) },
                    )
                }
                // Fitted immediately, not at playback. A score that cannot be
                // played is not a score, and the place to find that out is here,
                // where the step count is still on screen — not silently at the
                // moment a call comes in. R8.
                // Keep the analysed levels, not just the score. Changing the
                // strength then costs nothing, where re-decoding costs seven
                // seconds — and tuning by feel is many small adjustments.
                levels = if (result is Decoded.Ok) builder?.levels() else null
                levelStepMs = builder?.stepMsUsed ?: Demo.STEP_MS
                val trackName = AudioDecoder.displayName(ctx, uri)
                score = levels?.let {
                    ScoreBuilder.toScore(it, levelStepMs, trackName, minFelt = strength.toInt())
                        .fitWithin(Haptics.MAX_STEPS)
                }
                decoded = result
                // Dump the whole score where adb can reach it. The event log
                // carries a summary, and a summary cannot tell "a few long
                // smears" apart from "sparse hits" — the two need opposite
                // fixes. Tuning by asking Mutalib to re-feel a track after every
                // guess would take all day; this way the guessing happens on the
                // PC against the real numbers.
                //
                // Debug aid, and now actually confined to debug builds. It used to
                // write on every decode in release too, which cost a disk write per
                // analysis for a file nothing in the shipped app ever reads. The
                // probe is pulled with `adb` on a dev build; `BuildConfig.DEBUG` is
                // available because `buildFeatures { buildConfig = true }`.
                if (BuildConfig.DEBUG) {
                    score?.let { built ->
                        runCatching { ctx.filesDir.resolve("last-score.txt").writeText(built.encode()) }
                    }
                }
                store.addEvent(
                    Event(
                        at = System.currentTimeMillis(),
                        kind = Event.Kind.DECODED,
                        ringer = Haptics.ringerMode(ctx),
                        latencyMs = if (result is Decoded.Ok) result.elapsedMs else 0,
                        note = summarise(result) + (score?.let { "  ||  " + summarise(it) } ?: ""),
                    ),
                )
                logRefresh++
                decoding = false
            }
        }
    }

    // Polled rather than observed. The ringer mode gets changed with the hardware
    // keys mid-test and the listener writes events from another callback, so a
    // stale screen would make the results table wrong in a way nobody notices.
    val ringer by produceState(initialValue = Haptics.ringerMode(ctx)) {
        while (true) {
            value = Haptics.ringerMode(ctx)
            delay(1000)
        }
    }
    val listenerOn by produceState(initialValue = false) {
        while (true) {
            value = NotificationManagerCompat.getEnabledListenerPackages(ctx)
                .contains(ctx.packageName)
            delay(1000)
        }
    }
    val events by produceState(initialValue = emptyList<Event>(), logRefresh) {
        while (true) {
            value = store.events().reversed()
            delay(1000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Thrum", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Test build. Task 2: does a real incoming call vibrate to a rhythm?",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        StatusCard(
            good = capability.usable,
            title = if (capability.usable) "This phone can do it" else "This phone cannot do it",
            body = if (capability.usable) {
                "The motor can change strength, which is what a rhythm needs."
            } else {
                "The motor has one strength, so it can only buzz. Nothing Thrum " +
                    "does would be felt on this phone."
            },
            detail = "vibrator ${capability.hasVibrator} · " +
                "strength control ${capability.amplitudeControl} · " +
                "sharp effects ${capability.richPrimitives}",
        )

        StatusCard(
            good = listenerOn,
            title = if (listenerOn) "Watching for calls" else "Not watching for calls",
            body = if (listenerOn) {
                "Thrum can see the incoming-call notification, which is how it " +
                    "knows to start."
            } else {
                "Thrum needs permission to see notifications. That is how it " +
                    "notices a call without asking to monitor your phone calls."
            },
        )
        if (!listenerOn) {
            Button(
                onClick = {
                    ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Open notification access settings")
            }
        }

        Text("Ringer is on: $ringer", style = MaterialTheme.typography.titleSmall)

        SwitchRow(
            label = "Loop while ringing",
            help = "Repeat the rhythm until the call is answered or stops.",
            checked = loopWhileRinging,
        ) {
            loopWhileRinging = it
            store.loopWhileRinging = it
        }
        SwitchRow(
            label = "Also fire in ring mode",
            help = "Off by default: with sound on, the system already plays a " +
                "stock ringtone's own vibration, so both at once feels wrong. " +
                "Turn on only to fill in the ring-mode row of the test.",
            checked = fireInRingMode,
        ) {
            fireInRingMode = it
            store.fireInRingMode = it
        }

        HorizontalDivider()
        Text("Feel the patterns", style = MaterialTheme.typography.titleMedium)
        scores.forEach { score ->
            OutlinedButton(
                onClick = { Haptics.play(ctx, score) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("${score.sourceName}  ·  ${score.durationMs / 1000}s")
            }
        }
        OutlinedButton(
            onClick = { Haptics.stop(ctx) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Stop")
        }

        HorizontalDivider()
        Text("Table test — how hard can it hit?", style = MaterialTheme.typography.titleMedium)
        Text(
            "Put the phone flat on a hard table, not touching it, then tap each one. " +
                "Flat max is the strongest steady buzz the amplitude path can make; THUD " +
                "and CLICK are haptic primitives Thrum does not use today; the Thrum tap " +
                "is the decaying shape it does use. If Flat max moves the table but the " +
                "Thrum tap does not, the fix is score shape. If nothing moves the table, " +
                "matching Pixel's buzz needs primitives.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = { Haptics.play(ctx, Demo.flatMax()) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Flat max buzz (1.5s)")
        }
        OutlinedButton(
            onClick = {
                Haptics.playPrimitives(
                    ctx,
                    VibrationEffect.Composition.PRIMITIVE_THUD to 1f,
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("THUD punch (primitive)")
        }
        OutlinedButton(
            onClick = {
                Haptics.playPrimitives(
                    ctx,
                    *Array(6) { VibrationEffect.Composition.PRIMITIVE_CLICK to 1f },
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("6× CLICK (primitive)")
        }
        OutlinedButton(
            onClick = { Haptics.play(ctx, Demo.thrumTap()) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Thrum-style tap")
        }

        HorizontalDivider()
        Text("Read an audio file", style = MaterialTheme.typography.titleMedium)
        Text(
            "Task 3. Turns a file into plain numbers — the step before it can " +
                "become a rhythm. Nothing is played and nothing is saved.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = { picker.launch(arrayOf("audio/*")) },
            enabled = !decoding,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (decoding) "Reading…" else "Pick an audio file")
        }
        when (val result = decoded) {
            null -> Unit
            is Decoded.Failed -> StatusCard(
                good = false,
                title = "Couldn't read that file",
                body = result.message,
            )
            is Decoded.Ok -> StatusCard(
                good = result.durationAgrees && !result.isSilent,
                title = result.name,
                body = "${result.sampleRate} Hz · ${channelWord(result.channels)} · " +
                    clock(result.decodedMs) +
                    if (result.isSilent) "\nThis file is completely silent." else "",
                detail = "${result.mime} · ${result.frames} frames · peak ${result.peakPercent}% · " +
                    "read in ${result.elapsedMs} ms\n" +
                    if (result.containerMs <= 0) {
                        "the file doesn't state its own length, so there's nothing to check against"
                    } else if (result.durationAgrees) {
                        "matches the length the file claims (${clock(result.containerMs)})"
                    } else {
                        "DISAGREES with the length the file claims (${clock(result.containerMs)}) " +
                            "— the decode stopped early"
                    },
            )
        }

        levels?.let {
            Text(
                "Strength floor: ${strength.toInt()}  " +
                    "(the weakest a hit is allowed to be, out of 255)",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Higher hits harder but flattens loud and quiet beats together. " +
                    "Rebuilds instantly — no need to pick the file again.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = strength,
                onValueChange = { v ->
                    strength = v
                    score = ScoreBuilder
                        .toScore(it, levelStepMs, "tuned", minFelt = v.toInt())
                        .fitWithin(Haptics.MAX_STEPS)
                },
                valueRange = 100f..250f,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        score?.let { built ->
            StatusCard(
                good = !built.isSilent(),
                title = "Rhythm: ${built.pulseCount()} hits",
                body = if (built.isSilent()) {
                    "This file produced no rhythm at all — nothing loud enough to feel."
                } else {
                    "${built.amplitudes.size} steps of ${built.stepMs}ms · " +
                        "strongest ${built.amplitudes.max()}/255"
                },
                detail = "R8: playing this asks the vibrator for ${built.amplitudes.size} steps " +
                    "in one effect. If it refuses, the reason lands in the log below.",
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = {
                        val failure = Haptics.play(ctx, built)
                        store.addEvent(
                            Event(
                                at = System.currentTimeMillis(),
                                kind = if (failure == null) Event.Kind.FIRED else Event.Kind.SKIPPED,
                                ringer = Haptics.ringerMode(ctx),
                                latencyMs = 0,
                                note = failure ?: "played score · ${summarise(built)}",
                            ),
                        )
                        logRefresh++
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Feel this rhythm")
                }
                Button(
                    onClick = {
                        val uri = pickedUri ?: return@Button
                        player.value?.runCatching { release() }
                        Haptics.stop(ctx)
                        playingTogether = true

                        val mp = android.media.MediaPlayer()
                        player.value = mp
                        mp.setAudioAttributes(
                            android.media.AudioAttributes.Builder()
                                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build(),
                        )
                        mp.setOnCompletionListener {
                            Haptics.stop(ctx)
                            playingTogether = false
                        }
                        mp.setOnPreparedListener { ready ->
                            ready.start()
                            scope.launch {
                                // Start the vibration when sound actually leaves
                                // the speaker, not when start() returns. Asking a
                                // player to play and assuming it has is the same
                                // mistake as assuming a vibration happened —
                                // getCurrentPosition only advances once audio is
                                // genuinely running.
                                var position = 0
                                val gaveUpAt = System.currentTimeMillis() + 2000
                                while (position == 0 && System.currentTimeMillis() < gaveUpAt) {
                                    position = runCatching { ready.currentPosition }.getOrDefault(0)
                                    delay(2)
                                }
                                val aligned = built.from(position.toLong())
                                val failure = Haptics.play(ctx, aligned)
                                store.addEvent(
                                    Event(
                                        at = System.currentTimeMillis(),
                                        kind = if (failure == null) Event.Kind.FIRED else Event.Kind.SKIPPED,
                                        ringer = Haptics.ringerMode(ctx),
                                        latencyMs = position.toLong(),
                                        note = failure
                                            ?: "with audio · skipped ${position}ms to match the speaker · " +
                                            "${aligned.amplitudes.size} steps",
                                    ),
                                )
                                logRefresh++
                            }
                        }
                        val failed = runCatching {
                            mp.setDataSource(ctx, uri)
                            mp.prepareAsync()
                        }.exceptionOrNull()
                        if (failed != null) {
                            playingTogether = false
                            store.addEvent(
                                Event(
                                    at = System.currentTimeMillis(),
                                    kind = Event.Kind.SKIPPED,
                                    ringer = Haptics.ringerMode(ctx),
                                    latencyMs = 0,
                                    note = "couldn't play the audio: ${failed.javaClass.simpleName}",
                                ),
                            )
                            logRefresh++
                        }
                    },
                    enabled = pickedUri != null,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (playingTogether) "Playing…" else "Play with song")
                }
                OutlinedButton(
                    onClick = {
                        Haptics.stop(ctx)
                        player.value?.runCatching { if (isPlaying) stop() }
                        player.value?.runCatching { release() }
                        player.value = null
                        playingTogether = false
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Stop")
                }
            }
        }

        HorizontalDivider()
        Text("R8: how many steps fit?", style = MaterialTheme.typography.titleMedium)
        Text(
            "Sends progressively longer vibrations. The app cannot tell which " +
                "ones arrive — a too-long one fails silently between processes — " +
                "so the answer is read afterwards from the system's own record.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = {
                probing = true
                scope.launch {
                    for (size in R8_LADDER) {
                        // 1ms steps: the parcel's size depends on how many steps
                        // there are, not how long they last, so this asks the
                        // real question in a second rather than in minutes.
                        val probe = Score(1, List(size) { 120 }, "probe-$size")
                        val failure = Haptics.play(ctx, probe, enforceLimit = false)
                        store.addEvent(
                            Event(
                                at = System.currentTimeMillis(),
                                kind = Event.Kind.SKIPPED,
                                ringer = Haptics.ringerMode(ctx),
                                latencyMs = size.toLong(),
                                note = failure ?: "R8 probe: sent $size steps",
                            ),
                        )
                        delay(1200)
                        Haptics.stop(ctx)
                        delay(400)
                    }
                    probing = false
                    logRefresh++
                }
            },
            enabled = !probing,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (probing) "Probing…" else "Run the step-limit probe (~15s)")
        }

        HorizontalDivider()
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("What happened", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = {
                store.clearEvents()
                logRefresh++
            }) {
                Text("Clear")
            }
        }

        if (events.isEmpty()) {
            Text(
                "Nothing yet. Set your ringer, then get someone to call you.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Start,
            )
        } else {
            events.forEach { EventRow(it) }
        }
    }
}

/**
 * One line per decode for the event log, so Task 3's results table can be
 * pulled off the phone instead of read aloud from the screen.
 */
private fun summarise(result: Decoded): String = when (result) {
    is Decoded.Failed ->
        "FAILED" + (if (result.mime.isEmpty()) "" else " (${result.mime})") + " — ${result.message}"
    is Decoded.Ok -> buildString {
        append(result.name).append(" · ").append(result.mime)
        append(" · ").append(result.sampleRate).append("Hz")
        append(" · ").append(channelWord(result.channels))
        append(" · ").append(clock(result.decodedMs))
        append(" · ").append(result.frames).append(" frames")
        append(" · peak ").append(result.peakPercent).append("%")
        append(
            when {
                result.containerMs <= 0 -> " · no length claimed"
                result.durationAgrees -> " · matches ${clock(result.containerMs)}"
                else -> " · DISAGREES, file claims ${clock(result.containerMs)}"
            },
        )
    }
}

/**
 * Step counts for the R8 probe, coarse to fine around where the wall is
 * expected. A 3:58 track is 11,922 steps and failed; 500 is a 10-second
 * ringtone, which must work or the product does not exist.
 */
private val R8_LADDER = listOf(8000, 9000, 9500, 10000, 10500, 11000, 11500, 12000)

/** One line describing a built score, for the event log. */
private fun summarise(score: Score): String =
    "${score.pulseCount()} hits · ${score.amplitudes.size} steps × ${score.stepMs}ms · " +
        "${clock(score.durationMs)} · strongest ${score.amplitudes.maxOrNull() ?: 0}/255"

/** `3:01`, so the length can be compared against a music player at a glance. */
private fun clock(ms: Long): String {
    val total = ms / 1000
    return "%d:%02d".format(total / 60, total % 60)
}

private fun channelWord(channels: Int): String = when (channels) {
    1 -> "mono"
    2 -> "stereo"
    else -> "$channels channels"
}

@Composable
private fun StatusCard(good: Boolean, title: String, body: String, detail: String? = null) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (good) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.errorContainer
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium)
            if (detail != null) {
                Text(detail, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun SwitchRow(
    label: String,
    help: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.fillMaxWidth(0.78f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                help,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun EventRow(event: Event) {
    val time = remember(event.at) {
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(event.at))
    }
    val colour = when (event.kind) {
        Event.Kind.FIRED -> MaterialTheme.colorScheme.primary
        Event.Kind.CAPPED -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "$time  ${event.kind.name.lowercase()}  ·  ringer ${event.ringer}" +
                if (event.kind == Event.Kind.FIRED) "  ·  ${event.latencyMs}ms late" else "",
            style = MaterialTheme.typography.bodyMedium,
            color = colour,
        )
        if (event.note.isNotEmpty()) {
            Text(
                event.note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
