package com.mosman.thrum

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.os.VibrationEffect
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Developer tools: the two tests from the old diagnostics page worth keeping,
 * reachable from Home in **test builds only** (`BuildConfig.DEBUG`).
 *
 * Cut down on 2026-10-06 (Mutalib agreed, PLAN.md). The rest of the old page
 * was either on Home and in Settings already (the capability verdict, call
 * access, the ringer, the ring-mode switch), replaced by the real app (the
 * demo patterns, reading a file), or harmful: a "Loop while ringing" switch,
 * shown nowhere else, that could quietly stop calls repeating. The call log
 * moved to Settings → Phone check as "Your last calls", where anyone can see
 * it, and these tools no longer write to it — so test vibrations never show
 * up there looking like calls.
 *
 * The words are English only and not in strings.xml on purpose: a release
 * build never shows this screen.
 */
@Composable
fun DeveloperToolsScreen(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var probe by remember { mutableStateOf<Job?>(null) }
    var timing by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf(emptyList<String>()) }
    DisposableEffect(Unit) {
        onDispose {
            probe?.cancel()
            Haptics.stop(ctx)
        }
    }

    ThrumPage {
        ThrumTopBar(title = "Developer tools", onBack = onClose)
        Text(
            "Test builds only. Two checks to run on a new phone.",
            style = ThrumType.body,
            color = ThrumInk2,
            modifier = Modifier.padding(bottom = Space.S4, start = Space.S1, end = Space.S1),
        )

        // Table test: how hard can this phone hit? Task 8's question, still
        // the first thing to ask of a new phone.
        Overline("Table test", modifier = Modifier.padding(bottom = Space.S2, start = 2.dp))
        ThrumCard(padding = PaddingValues(18.dp)) {
            Text(
                "Put the phone flat on a hard table, not touching it, then try each one. " +
                    "Flat max is the strongest steady buzz Thrum's own path can make. " +
                    "THUD and CLICK are Android's built-in effects, which Thrum doesn't use. " +
                    "The Thrum tap is the shape Thrum does use. If Flat max moves the table " +
                    "and the Thrum tap doesn't, the fix is the shape of the beats.",
                style = ThrumType.body,
                color = ThrumInkSoft,
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(Space.S2),
                modifier = Modifier.padding(top = 14.dp),
            ) {
                ToolButton("Flat max buzz (1.5 s)") { Haptics.play(ctx, Demo.flatMax()) }
                ToolButton("THUD (built-in effect)") {
                    Haptics.playPrimitives(ctx, VibrationEffect.Composition.PRIMITIVE_THUD to 1f)
                }
                ToolButton("6 × CLICK (built-in effect)") {
                    Haptics.playPrimitives(ctx, *Array(6) { VibrationEffect.Composition.PRIMITIVE_CLICK to 1f })
                }
                ToolButton("Thrum tap") { Haptics.play(ctx, Demo.thrumTap()) }
                ToolButton("Stop") { Haptics.stop(ctx) }
            }
        }

        // R8: how many steps fit in one vibration on this phone. The app
        // cannot tell which arrive — an oversized one fails silently between
        // processes — so the answer is read from the system's own record.
        Overline("Step-limit probe", modifier = Modifier.padding(top = Space.S5, bottom = Space.S2, start = 2.dp))
        ThrumCard(padding = PaddingValues(18.dp)) {
            Text(
                "Sends longer and longer vibrations, from ${R8_LADDER.first()} to ${R8_LADDER.last()} steps. " +
                    "This screen can't see which ones arrive, so read the answer afterwards " +
                    "with adb shell dumpsys vibrator_manager. Haptics.MAX_STEPS is ${Haptics.MAX_STEPS}.",
                style = ThrumType.body,
                color = ThrumInkSoft,
            )
            SecondaryButton(
                text = if (probe != null) "Probing…" else "Run the probe (about 15 s)",
                small = true,
                enabled = probe == null,
                onClick = {
                    probe = scope.launch {
                        for (size in R8_LADDER) {
                            // 1 ms steps: the parcel's size depends on how many
                            // steps there are, not how long they last, so this
                            // asks the real question in a second, not minutes.
                            Haptics.play(ctx, Score(1, List(size) { 120 }, "probe-$size"), enforceLimit = false)
                            delay(1200)
                            Haptics.stop(ctx)
                            delay(400)
                        }
                        probe = null
                    }
                },
                modifier = Modifier.padding(top = 14.dp),
            )
        }

        // Instant Feel test (2026-10-07, approved by Mutalib): how long the
        // first 45 seconds of a song take to read, how long the whole song
        // takes, and the slowest stretch against real time. Measuring only;
        // the app's behaviour doesn't change.
        Overline("Instant Feel test", modifier = Modifier.padding(top = Space.S5, bottom = Space.S2, start = 2.dp))
        ThrumCard(padding = PaddingValues(18.dp)) {
            Text(
                "Reads up to three songs from your library, a short, a middle and a long one, the way a first play does. " +
                    "It times the first 45 seconds (the wait before an instant start), the whole song, and the slowest " +
                    "stretch compared with real time. Results also go to logcat under ThrumSpike.",
                style = ThrumType.body,
                color = ThrumInkSoft,
            )
            SecondaryButton(
                text = if (timing) "Measuring…" else "Measure",
                small = true,
                enabled = !timing,
                onClick = {
                    timing = true
                    results = emptyList()
                    scope.launch {
                        results = InstantFeelTest.run(ctx) { line -> results = results + line }
                        timing = false
                    }
                },
                modifier = Modifier.padding(top = 14.dp),
            )
            results.forEach { line ->
                Text(line, style = ThrumType.meta, color = ThrumInk, modifier = Modifier.padding(top = Space.S2))
            }
        }
    }
}

@Composable
private fun ToolButton(text: String, onClick: () -> Unit) {
    SecondaryButton(text = text, small = true, onClick = onClick, modifier = Modifier.fillMaxWidth())
}

/**
 * Step counts for the probe, coarse to fine around where the wall was found:
 * this Pixel dropped anything over about 10,500 steps. A 3:58 track is 11,922.
 */
private val R8_LADDER = listOf(8000, 9000, 9500, 10000, 10500, 11000, 11500, 12000)

/**
 * The numbers behind Instant Feel: can Thrum start a song after reading only
 * its first 45 seconds, and then stay ahead of the music? It reads each song
 * exactly as [HapticMaker.analyse] does (the same decoder feeding the same
 * analyser) and notes the clock as it goes.
 */
private object InstantFeelTest {

    suspend fun run(ctx: Context, onLine: (String) -> Unit): List<String> {
        val dao = LibraryDb.get(ctx).dao()
        val songs = withContext(Dispatchers.IO) {
            dao.allTrackUris().mapNotNull { dao.trackFor(it)?.toTrack() }
                .filter { it.kind == TrackKind.MUSIC && it.readable && it.durationMs > 0 }
                .sortedBy { it.durationMs }
        }
        if (songs.isEmpty()) return listOf("No songs in the library yet. Scan for music first.").also { it.forEach(onLine) }
        val picks = listOf(songs.first(), songs[songs.size / 2], songs.last()).distinct()
        val lines = mutableListOf<String>()
        for (song in picks) {
            val line = measure(ctx, song)
            Log.i(TAG, line)
            lines += line
            onLine(line)
        }
        return lines
    }

    private suspend fun measure(ctx: Context, song: Track): String = withContext(Dispatchers.Default) {
        val start = SystemClock.elapsedRealtime()
        var builder: ScoreBuilder? = null
        var rate = 0
        var frames = 0L
        var first45 = -1L
        var markAt = start
        var markFrames = 0L
        var slowest = Double.MAX_VALUE
        val decoded = AudioDecoder.decode(
            ctx,
            Uri.parse(song.sourceUri),
            onFormat = { r, _ ->
                rate = r
                builder = ScoreBuilder(r, name = song.name)
            },
            onMono = { samples, count ->
                builder?.feed(samples, count)
                frames += count
                if (rate > 0) {
                    if (first45 < 0 && frames >= FIRST_SECONDS * rate) first45 = SystemClock.elapsedRealtime() - start
                    // Speed over each 5 s of audio: how many seconds of song per second of work.
                    if (frames - markFrames >= STRETCH_SECONDS * rate) {
                        val now = SystemClock.elapsedRealtime()
                        val ratio = ((frames - markFrames).toDouble() / rate) / ((now - markAt).coerceAtLeast(1) / 1000.0)
                        slowest = minOf(slowest, ratio)
                        markAt = now
                        markFrames = frames
                    }
                }
            },
        )
        val readMs = SystemClock.elapsedRealtime() - start
        val built = builder
        if (decoded is Decoded.Failed || built == null || rate == 0) return@withContext "${song.name}: couldn't read it"
        val t0 = SystemClock.elapsedRealtime()
        val levels = built.levels()
        ScoreBuilder.wholeScore(levels, built.stepMsUsed, song.name, Tuning.NORMAL_PUNCH, Tuning.EXTRA_TAPS_ON, ScoreBuilder.BODY_MS)
        val buildMs = SystemClock.elapsedRealtime() - t0
        val audioSec = frames.toDouble() / rate
        "%s (%.0f s): first 45 s in %d ms, whole song %d ms + %d ms to build, %.1f× real time, slowest stretch %.1f×".format(
            song.name.take(40), audioSec, if (first45 < 0) readMs else first45, readMs, buildMs,
            audioSec / (readMs / 1000.0), if (slowest == Double.MAX_VALUE) audioSec / (readMs / 1000.0) else slowest,
        )
    }

    private const val TAG = "ThrumSpike"
    private const val FIRST_SECONDS = 45L
    private const val STRETCH_SECONDS = 5L
}
