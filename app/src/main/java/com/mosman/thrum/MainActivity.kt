package com.mosman.thrum

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
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
import kotlinx.coroutines.delay

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
    val store = remember { Store(ctx) }
    val capability = remember { Haptics.capability(ctx) }
    val scores = remember { Demo.all() }

    var fireInRingMode by remember { mutableStateOf(store.fireInRingMode) }
    var loopWhileRinging by remember { mutableStateOf(store.loopWhileRinging) }
    var logRefresh by remember { mutableStateOf(0) }

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
