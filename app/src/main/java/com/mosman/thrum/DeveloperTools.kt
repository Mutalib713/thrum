package com.mosman.thrum

import android.os.VibrationEffect
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
