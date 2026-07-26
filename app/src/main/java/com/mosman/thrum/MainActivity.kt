package com.mosman.thrum

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Task 1's whole interface. Not the product — a probe.
 *
 * It exists to answer two things with a hand rather than an argument: what this
 * phone's motor is capable of, and whether a varied-strength score actually
 * feels like rhythm next to the flat buzz Android plays today.
 *
 * The real single-screen UI is Task 7. Don't grow this file into it.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ThrumTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    ProbeScreen()
                }
            }
        }
    }
}

@Composable
private fun ProbeScreen() {
    val ctx = LocalContext.current
    val capability = remember { Haptics.capability(ctx) }
    val scores = remember { Demo.all() }
    val lastPlayed = remember { mutableStateOf("") }

    // Polled rather than observed: the ringer mode is changed with the hardware
    // keys mid-test, and a stale readout here would make the Task 2 results
    // table wrong in a way nobody would notice.
    val ringer by produceState(initialValue = Haptics.ringerMode(ctx)) {
        while (true) {
            value = Haptics.ringerMode(ctx)
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
            "A test build. Feel the difference between a rhythm and the buzz your " +
                "phone plays today.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (capability.usable) {
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
                Text(
                    if (capability.usable) {
                        "This phone can do it"
                    } else {
                        "This phone cannot do it"
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    if (capability.usable) {
                        "The motor can change strength, which is what a rhythm needs."
                    } else {
                        "The motor has one strength, so it can only buzz. Nothing " +
                            "Thrum does would be felt on this phone."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "vibrator: ${capability.hasVibrator}   " +
                        "strength control: ${capability.amplitudeControl}   " +
                        "sharp effects: ${capability.richPrimitives}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Text(
            "Ringer is on: $ringer",
            style = MaterialTheme.typography.titleSmall,
        )

        scores.forEach { score ->
            Button(
                onClick = {
                    Haptics.play(ctx, score)
                    lastPlayed.value = score.sourceName
                },
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

        if (lastPlayed.value.isNotEmpty()) {
            val score = scores.first { it.sourceName == lastPlayed.value }
            Text(
                "Last played: ${score.sourceName} — ${score.amplitudes.size} steps of " +
                    "${score.stepMs}ms, ${score.pulseCount()} pulses",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
