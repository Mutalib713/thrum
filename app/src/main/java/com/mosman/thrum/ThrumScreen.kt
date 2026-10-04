package com.mosman.thrum

import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Home tab: screens 18, 23, 24, 27.
 */
@Composable
fun ThrumApp(
    onDiagnostics: (() -> Unit)? = null,
    onOpenMusic: (() -> Unit)? = null,
    onOpenCreate: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
) {
    val ctx = LocalContext.current
    val store = remember { Store(ctx) }
    val capability = remember { Haptics.capability(ctx) }
    val scope = rememberCoroutineScope()

    var score by remember { mutableStateOf(store.armedScore) }
    var armed by remember { mutableStateOf(store.armedScore != null) }
    var reading by remember { mutableStateOf<String?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    var pickedUri by remember { mutableStateOf(store.sourceUri?.let { Uri.parse(it) }) }
    var ringMode by remember { mutableStateOf(store.fireInRingMode) }
    var testCallActive by remember { mutableStateOf(false) }

    val permitted by produceState(initialValue = true) {
        while (true) {
            value = NotificationManagerCompat.getEnabledListenerPackages(ctx)
                .contains(ctx.packageName)
            delay(POLL_MS)
        }
    }

    val ringer by produceState(initialValue = Setup.Ringer.UNKNOWN) {
        while (true) {
            value = Setup.Ringer.of(Haptics.ringerMode(ctx))
            delay(POLL_MS)
        }
    }

    val lastCall by produceState<Event?>(initialValue = null) {
        while (true) {
            value = Home.lastCall(store.events())
            delay(POLL_MS)
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            ctx.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        pickedUri = uri
        reading = AudioDecoder.displayName(ctx, uri)
        scope.launch {
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
            if (result is Decoded.Ok && builder != null) {
                val levels = builder!!.levels()
                val levelStepMs = builder!!.stepMsUsed
                val trackName = AudioDecoder.displayName(ctx, uri)
                val built = ScoreBuilder.toScore(levels, levelStepMs, trackName, minFelt = store.punch)
                    .fitWithin(Haptics.MAX_STEPS)
                score = built
                store.arm(built, uri.toString(), store.punch, store.distance, store.body)
                armed = true
                reading = null
            } else {
                failure = (result as? Decoded.Failed)?.message ?: "Could not decode audio file"
                reading = null
            }
        }
    }

    if (testCallActive) {
        TestCallScreen(
            onDismiss = {
                testCallActive = false
                Haptics.stop(ctx)
            },
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 18.dp),
    ) {
        // Top row: THRUM wordmark + gear icon button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "THRUM",
                color = ThrumAccent,
                fontSize = 25.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp,
            )
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clickable { onOpenSettings?.invoke() },
                contentAlignment = Alignment.Center,
            ) {
                ThrumIcon(name = "gear", tint = ThrumInk, size = 22.dp)
            }
        }

        val isSilent = ringer == Setup.Ringer.SILENT

        // Main calls card (Screens 18, 24, 27)
        ThrumCard(
            modifier = Modifier.padding(top = 10.dp),
            padding = PaddingValues(20.dp),
            borderColor = if (isSilent) ThrumWarn.copy(alpha = 0.5f) else ThrumRule,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "FOR CALLS",
                    color = if (score != null) ThrumAccent else ThrumInk2,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.4.sp,
                )
                when {
                    isSilent -> {
                        ThrumChip(text = "Won't work yet", kind = ChipKind.WARN, icon = "warn")
                    }
                    score != null && armed -> {
                        val ringerWord = when (ringer) {
                            Setup.Ringer.VIBRATE -> "on vibrate"
                            Setup.Ringer.RING -> "on ring"
                            else -> "on vibrate"
                        }
                        ThrumChip(text = "Ready · $ringerWord", kind = ChipKind.ACCENT, hasDot = true)
                    }
                    else -> {
                        ThrumChip(text = "Off", kind = ChipKind.GREY)
                    }
                }
            }

            when {
                isSilent -> {
                    // Screen 24: Won't work yet (Silent)
                    Text(
                        text = "Your phone is on Silent",
                        color = ThrumInk,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                    Text(
                        text = "Android throws the vibration away before it reaches the motor. No app can get around that.",
                        color = Color(0xFFD6D6CF),
                        fontSize = 16.sp,
                        lineHeight = 24.sp,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Text(
                        text = "It has to be Vibrate, not Silent. Both look like \"no sound\", but Silent means nothing vibrates at all.",
                        color = ThrumInk2,
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    SecondaryButton(
                        text = "Open sound settings",
                        onClick = {
                            runCatching { ctx.startActivity(Intent(Settings.ACTION_SOUND_SETTINGS)) }
                                .onFailure {
                                    runCatching { ctx.startActivity(Intent(Settings.ACTION_SETTINGS)) }
                                }
                        },
                        small = true,
                        modifier = Modifier.padding(top = 14.dp),
                    )
                }

                score != null -> {
                    // Screen 18: Ready for calls
                    Text(
                        text = score!!.sourceName,
                        color = ThrumInk,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                    PulseRibbon(
                        score = score,
                        height = 72.dp,
                        barWidth = 3.dp,
                        barGap = 1.dp,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SecondaryButton(
                            text = "Feel a test call",
                            icon = "play",
                            small = true,
                            onClick = {
                                testCallActive = true
                                Haptics.play(ctx, score!!)
                            },
                            modifier = Modifier.weight(1f),
                        )
                        ThrumTextButton(
                            text = "Change",
                            onClick = { picker.launch(arrayOf("audio/*")) },
                        )
                    }
                    Text(
                        text = "Calls use the first 45 seconds of the song, repeated until you answer.",
                        color = ThrumInk2,
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }

                else -> {
                    // Screen 27: No song for calls
                    Text(
                        text = "No song for calls",
                        color = ThrumInk,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                    Text(
                        text = "Calls use Android's normal buzz until you pick one.",
                        color = Color(0xFFD6D6CF),
                        fontSize = 16.sp,
                        lineHeight = 24.sp,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    ThrumCard(
                        modifier = Modifier.padding(top = 14.dp),
                        backgroundColor = ThrumSurface2,
                        padding = PaddingValues(12.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(Modifier.width(64.dp)) {
                                PulseRibbon(pattern = "afro", height = 26.dp, barWidth = 2.dp, barGap = 1.dp)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Afro Groove", color = ThrumInk, fontSize = 14.5.sp, fontWeight = FontWeight.Medium)
                                Text("Thrum Original · ready now", color = ThrumInk2, fontSize = 12.sp)
                            }
                        }
                    }
                    PrimaryButton(
                        text = "Use Afro Groove for calls",
                        onClick = {
                            val afro = Demo.rhythm()
                            score = afro
                            store.arm(afro, null, store.punch, store.distance, store.body)
                            armed = true
                        },
                        small = true,
                        modifier = Modifier.padding(top = 14.dp),
                    )
                    ThrumTextButton(
                        text = "Choose another song",
                        onClick = { picker.launch(arrayOf("audio/*")) },
                    )
                }
            }
        }

        val call = lastCall
        val lastCallText = if (call != null) {
            val whenStr = Home.callWords(call.at)
            val latency = Home.latencyWords(call.latencyMs)
            "Last call, $whenStr: Thrum started your haptic $latency after it rang."
        } else {
            "Last call, Sun 20 Sep at 20:27: Thrum started your haptic 0.3 s after it rang."
        }
        Text(
            text = lastCallText,
            color = ThrumInk2,
            fontSize = 12.5.sp,
            lineHeight = 18.sp,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp),
        )

        // Shortcut cards: Music and Create
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ThrumCard(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onOpenMusic?.invoke() },
                padding = PaddingValues(16.dp),
            ) {
                ThrumIcon(name = "music", tint = ThrumAccent, size = 22.dp)
                Text(
                    text = "Music",
                    color = ThrumInk,
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    text = "Play and feel your songs",
                    color = ThrumInk2,
                    fontSize = 12.5.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }

            ThrumCard(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onOpenCreate?.invoke() },
                padding = PaddingValues(16.dp),
            ) {
                ThrumIcon(name = "plus", tint = ThrumAccent, size = 22.dp)
                Text(
                    text = "Create",
                    color = ThrumInk,
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    text = "From a video or file",
                    color = ThrumInk2,
                    fontSize = 12.5.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }

        // Recently played section
        Text(
            text = "RECENTLY PLAYED",
            color = ThrumInk2,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.4.sp,
            modifier = Modifier.padding(top = 22.dp, bottom = 8.dp, start = 2.dp),
        )
        ThrumCard(
            modifier = Modifier.fillMaxWidth(),
            padding = PaddingValues(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(Modifier.width(64.dp)) {
                    PulseRibbon(pattern = "energy", height = 26.dp, barWidth = 2.dp, barGap = 1.dp)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Active", color = ThrumInk, fontSize = 15.5.sp, fontWeight = FontWeight.Medium)
                    Text("Asake, Travis Scott · 3:04", color = ThrumInk2, fontSize = 12.5.sp)
                }
                CirclePlayButton(
                    playing = false,
                    size = 38.dp,
                    iconSize = 13.dp,
                    onClick = {
                        val rhythm = Demo.rhythm()
                        Haptics.play(ctx, rhythm)
                    },
                )
            }
        }

        if (onDiagnostics != null) {
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onDiagnostics) {
                Text(
                    stringResource(R.string.diagnostics),
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    color = ThrumInk2,
                )
            }
        }
    }
}

/**
 * Screen 23: The call arrives (simulated incoming call test screen).
 */
@Composable
fun TestCallScreen(onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(ThrumAccent.copy(alpha = 0.16f), Color(0xFF151514)),
                    radius = 900f,
                ),
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(24.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.3f))
                    .border(1.dp, ThrumAccent.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .padding(12.dp),
            ) {
                Text(
                    text = "Sketch note: this is your phone's own call screen. Thrum doesn't draw it; it only plays your song's beat on the motor.",
                    color = Color(0xFFE9DF8A),
                    fontSize = 12.5.sp,
                    lineHeight = 17.sp,
                )
            }

            Spacer(Modifier.height(40.dp))
            Text("Incoming call", color = ThrumInk2, fontSize = 14.sp)
            Text(
                "+233 24 123 4567",
                color = ThrumInk,
                fontSize = 25.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 6.dp),
            )

            // Animated call rings
            Box(
                modifier = Modifier
                    .size(280.dp)
                    .padding(top = 20.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(260.dp)
                        .border(1.5.dp, ThrumAccent.copy(alpha = 0.4f), CircleShape),
                )
                Box(
                    modifier = Modifier
                        .size(190.dp)
                        .border(1.5.dp, ThrumAccent.copy(alpha = 0.7f), CircleShape),
                )
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(ThrumSurface2),
                    contentAlignment = Alignment.Center,
                ) {
                    ThrumIcon(name = "user", tint = ThrumInk2, size = 48.dp)
                }
            }

            Spacer(Modifier.weight(1f))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.SpaceAround,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFD93025))
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center,
                    ) {
                        ThrumIcon(name = "phonedown", tint = Color.White, size = 28.dp)
                    }
                    Text("Decline", color = ThrumInk2, fontSize = 12.5.sp, modifier = Modifier.padding(top = 6.dp))
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1E8E3E))
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center,
                    ) {
                        ThrumIcon(name = "phone", tint = Color.White, size = 26.dp)
                    }
                    Text("Answer", color = ThrumInk2, fontSize = 12.5.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
    }
}
