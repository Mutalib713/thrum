package com.mosman.thrum

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Screens 20, 21, 22: Settings, About Thrum, and Phone check.
 */
@Composable
fun SettingsTab(
    onOpenMusic: () -> Unit,
    onOpenHome: () -> Unit,
    onOpenTune: (() -> Unit)? = null,
) {
    val ctx = LocalContext.current
    val store = remember { Store(ctx) }
    val db = remember { LibraryDb.get(ctx) }
    val scope = rememberCoroutineScope()
    var showAbout by remember { mutableStateOf(false) }
    var showPhoneCheck by remember { mutableStateOf(false) }

    if (showAbout) {
        AboutScreen(onClose = { showAbout = false })
        return
    }
    if (showPhoneCheck) {
        PhoneCheckScreen(onClose = { showPhoneCheck = false })
        return
    }

    val callAccess by produceState(initialValue = false) {
        while (true) {
            value = NotificationManagerCompatHelper.isEnabled(ctx)
            delay(POLL_MS)
        }
    }
    val musicPermission = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }
    val musicAccess by produceState(initialValue = false) {
        while (true) {
            value = ContextCompat.checkSelfPermission(ctx, musicPermission) ==
                PackageManager.PERMISSION_GRANTED
            delay(POLL_MS)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF131312))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        // Title
        Text(
            "Settings",
            fontSize = 31.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFD8C513),
            modifier = Modifier.padding(top = 10.dp, bottom = 16.dp),
        )

        // Sect: Calls
        SettingsSectionTitle("Calls")
        ThrumCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                SettingsItem(
                    name = "Song for calls",
                    value = store.armedScore?.sourceName ?: "AIZO",
                    onClick = onOpenHome,
                )
                SettingsDivider()
                SettingsItem(
                    name = "Call access",
                    value = if (callAccess) "On" else "Off",
                    onClick = {
                        if (!callAccess) {
                            runCatching {
                                ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                            }
                        }
                    },
                )
                SettingsDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Also when the ringer is on",
                        fontSize = 14.5.sp,
                        color = Color(0xFFF5F5F0),
                    )
                    ThrumSwitch(
                        checked = store.fireInRingMode,
                        onCheckedChange = { store.fireInRingMode = it },
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        // Sect: Music
        SettingsSectionTitle("Music")
        ThrumCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                SettingsItem(
                    name = "Music access",
                    value = if (musicAccess) "On" else "Off",
                    onClick = {
                        if (!musicAccess) {
                            runCatching {
                                ctx.startActivity(
                                    Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        android.net.Uri.fromParts("package", ctx.packageName, null),
                                    ),
                                )
                            }
                        }
                    },
                )
                SettingsDivider()
                SettingsItem(
                    name = "Scan for new music",
                    sub = if (store.lastScanAtMs > 0) {
                        "Last scanned " + SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(store.lastScanAtMs))
                    } else {
                        "Last scanned today"
                    },
                    onClick = onOpenMusic,
                )
                SettingsDivider()
                SettingsItem(
                    name = "Make haptics",
                    value = if (store.hapticsMode == HapticsWorker.MODE_BACKGROUND) "In the background" else "As played",
                    onClick = {
                        val switchingToBg = store.hapticsMode != HapticsWorker.MODE_BACKGROUND
                        store.hapticsMode = if (switchingToBg) HapticsWorker.MODE_BACKGROUND else HapticsWorker.MODE_AS_PLAYED
                        store.hapticsDone = 0
                        if (switchingToBg) {
                            scope.launch {
                                val all = withContext(Dispatchers.IO) { db.dao().allTrackUris() }
                                HapticsWorker.enqueue(ctx, all)
                            }
                        } else {
                            // Nothing walks the list in "as I play them" mode.
                            store.editQueue(emptySet()) { HapticQueue() }
                        }
                    },
                )
                SettingsDivider()
                SettingsItem(
                    name = "Online catalog",
                    value = "On",
                    onClick = onOpenMusic,
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        // Sect: Haptics
        SettingsSectionTitle("Haptics")
        ThrumCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                SettingsItem(
                    name = "Default feel",
                    value = when (Tuning.matching(store.body)) {
                        Tuning.CRISP -> "Crisp"
                        Tuning.FULL -> "Full"
                        Tuning.STRONG -> "Strong"
                        else -> "Crisp"
                    },
                    onClick = { onOpenTune?.invoke() ?: onOpenHome() },
                )
                SettingsDivider()
                SettingsItem(
                    name = "Clean up low-quality songs",
                    value = "Ask first",
                    onClick = onOpenHome,
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        // Sect: This phone and Thrum
        SettingsSectionTitle("This phone and Thrum")
        ThrumCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                SettingsItem(
                    name = "Phone check",
                    onClick = { showPhoneCheck = true },
                )
                SettingsDivider()
                SettingsItem(
                    name = "About Thrum",
                    onClick = { showAbout = true },
                )
            }
        }

        Spacer(Modifier.height(90.dp)) // Bottom nav padding
    }
}

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = Color(0xFF8E8E86),
        letterSpacing = 0.5.sp,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        color = Color(0xFF2A2A26),
        thickness = 1.dp,
    )
}

@Composable
private fun SettingsItem(
    name: String,
    sub: String? = null,
    value: String? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                name,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFFF5F5F0),
            )
            if (sub != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    sub,
                    fontSize = 12.5.sp,
                    color = Color(0xFF8E8E86),
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (value != null) {
                Text(
                    value,
                    fontSize = 14.sp,
                    color = Color(0xFF8E8E86),
                )
            }
            ThrumIcon.Chev(tint = Color(0xFF8E8E86), size = 16.dp)
        }
    }
}

/**
 * Screen 21: About Thrum.
 */
@Composable
private fun AboutScreen(onClose: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF131312))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 22.dp, vertical = 12.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        // Topbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.CenterStart,
            ) {
                ThrumIcon.Back(tint = Color(0xFFF5F5F0), size = 20.dp)
            }
            Text(
                "About Thrum",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFF5F5F0),
            )
        }

        Spacer(Modifier.height(16.dp))

        // Wordmark
        Text(
            "THRUM",
            fontSize = 31.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFFF5F5F0),
            letterSpacing = 2.5.sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Hear it. Feel it.",
            fontSize = 15.sp,
            color = Color(0xFFF5F5F0),
        )

        Spacer(Modifier.height(28.dp))

        SettingsSectionTitle("Why Thrum")
        Text(
            "I wanted my phone to do more than play sound. I wanted to feel the music too.",
            fontSize = 14.sp,
            color = Color(0xFFF5F5F0),
            lineHeight = 20.sp,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "I'd seen iPhones sync vibration with music, and I wanted that on Android, made from the songs already on my phone. So I built Thrum.",
            fontSize = 14.sp,
            color = Color(0xFFF5F5F0),
            lineHeight = 20.sp,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Mutalib Osman",
            fontSize = 12.5.sp,
            color = Color(0xFF8E8E86),
        )

        Spacer(Modifier.height(24.dp))

        SettingsSectionTitle("Thrum Originals")
        Text(
            "Made for Thrum by .",
            fontSize = 14.sp,
            color = Color(0xFFF5F5F0),
        )

        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(30.dp))

        // Version & Privacy card
        ThrumCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Version", fontSize = 14.5.sp, color = Color(0xFFF5F5F0))
                    Text(BuildConfig.VERSION_NAME, fontSize = 14.sp, color = Color(0xFF8E8E86))
                }
                SettingsDivider()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    Text(
                        "No internet permission",
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFF5F5F0),
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Your songs never leave this phone.",
                        fontSize = 12.5.sp,
                        color = Color(0xFF8E8E86),
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))
    }
}

/**
 * Screen 22: Phone check.
 */
@Composable
private fun PhoneCheckScreen(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val capability = remember { Haptics.capability(ctx) }
    val scope = rememberCoroutineScope()
    var ratePlaying by remember { mutableStateOf(0) }

    val callAccess by produceState(initialValue = false) {
        while (true) {
            value = NotificationManagerCompatHelper.isEnabled(ctx)
            delay(POLL_MS)
        }
    }
    val musicPermission = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }
    val musicAccess by produceState(initialValue = false) {
        while (true) {
            value = ContextCompat.checkSelfPermission(ctx, musicPermission) ==
                PackageManager.PERMISSION_GRANTED
            delay(POLL_MS)
        }
    }

    fun runRateTest() {
        scope.launch {
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF131312))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 22.dp, vertical = 12.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        // Topbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.CenterStart,
            ) {
                ThrumIcon.Back(tint = Color(0xFFF5F5F0), size = 20.dp)
            }
            Text(
                "Phone check",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFF5F5F0),
            )
        }

        Spacer(Modifier.height(8.dp))

        // Statement
        Text(
            if (capability.usable) "Your phone can do it" else "Your phone can't do this",
            fontSize = 31.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFD8C513),
            lineHeight = 36.sp,
        )

        Spacer(Modifier.height(20.dp))

        // Status Card list
        ThrumCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                CheckItemRow(
                    name = "Vibration motor",
                    value = if (capability.hasVibrator) "Yes" else "None",
                    checked = capability.hasVibrator,
                )
                SettingsDivider()
                CheckItemRow(
                    name = "Strength control",
                    sub = "The part rhythm needs",
                    value = if (capability.amplitudeControl) "Yes" else "No",
                    checked = capability.amplitudeControl,
                )
                SettingsDivider()
                CheckItemRow(
                    name = "Call access",
                    value = if (callAccess) "On" else "Off",
                    checked = callAccess,
                )
                SettingsDivider()
                CheckItemRow(
                    name = "Music access",
                    value = if (musicAccess) "On" else "Off",
                    checked = musicAccess,
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // Sect: How fast can it tap?
        SettingsSectionTitle("How fast can it tap?")
        ThrumCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    "Separate taps up to 8 a second",
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFF5F5F0),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Measured on this phone. Faster than that, the taps blur into one buzz.",
                    fontSize = 12.5.sp,
                    color = Color(0xFF8E8E86),
                    lineHeight = 17.sp,
                )

                if (ratePlaying > 0) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "$ratePlaying / sec",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFD8C513),
                    )
                }

                Spacer(Modifier.height(14.dp))
                SecondaryButton(
                    text = "Run the tap test again",
                    onClick = { runRateTest() },
                )
            }
        }

        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun CheckItemRow(
    name: String,
    sub: String? = null,
    value: String,
    checked: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f),
        ) {
            ThrumIcon.Check(
                tint = if (checked) Color(0xFFD8C513) else Color(0xFF8E8E86),
                size = 18.dp,
            )
            Column {
                Text(
                    name,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFF5F5F0),
                )
                if (sub != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        sub,
                        fontSize = 12.5.sp,
                        color = Color(0xFF8E8E86),
                    )
                }
            }
        }
        Text(
            value,
            fontSize = 14.sp,
            color = Color(0xFF8E8E86),
        )
    }
}

private val RATE_LADDER = listOf(2, 3, 4, 6, 8, 12)
private const val RATE_GAP_MS = 900L

private object NotificationManagerCompatHelper {
    fun isEnabled(ctx: android.content.Context): Boolean =
        androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(ctx)
            .contains(ctx.packageName)
}
