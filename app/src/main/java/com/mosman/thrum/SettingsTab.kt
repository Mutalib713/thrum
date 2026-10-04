package com.mosman.thrum

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Settings: the choices from every feature, in one place. Task 26, screen 20.
 *
 * The proof this task asks for — "every setting changes what it says it
 * changes, read back from storage" — is why each row reads its value live
 * from [Store] or the system rather than from a cached copy, and why the
 * two permission rows poll: the user leaves for system settings, flips the
 * grant, and comes back to a screen that already knows.
 *
 * One honest omission: "Clean up low-quality songs" (screen 20's last row)
 * is not here, because clean-up is Task 29 — a row whose tap did nothing
 * would be a lie with a tap target, and this file does not draw those.
 */
@Composable
fun SettingsTab(onOpenMusic: () -> Unit, onOpenHome: () -> Unit) {
    val ctx = LocalContext.current
    val store = remember { Store(ctx) }
    val db = remember { LibraryDb.get(ctx) }
    val scope = rememberCoroutineScope()
    var showAbout by remember { mutableStateOf(false) }
    var showPhoneCheck by remember { mutableStateOf(false) }

    if (showAbout) {
        AboutScreen { showAbout = false }
        return
    }
    if (showPhoneCheck) {
        PhoneCheckScreen { showPhoneCheck = false }
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
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.S5, vertical = Space.S6),
        verticalArrangement = Arrangement.spacedBy(Space.S2),
    ) {
        Text(
            stringResource(R.string.tab_settings),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(bottom = Space.S2)
                .semantics { heading() },
        )

        SectionLabel(stringResource(R.string.settings_calls_section))
        ValueRow(
            title = stringResource(R.string.settings_song_for_calls),
            value = store.armedScore?.sourceName ?: stringResource(R.string.settings_not_set),
        )
        // Off is the state that needs an exit; on needs nothing. The row acts
        // only when it has somewhere to send the user (§9: the app explains
        // and sends; it never flips a permission itself).
        ActionValueRow(
            title = stringResource(R.string.settings_call_access),
            value = stringResource(if (callAccess) R.string.settings_on else R.string.settings_off),
            act = !callAccess,
        ) {
            runCatching { ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        }
        SwitchRow(
            title = stringResource(R.string.ring_mode_label),
            help = stringResource(R.string.ring_mode_help),
            checked = store.fireInRingMode,
        ) {
            store.fireInRingMode = it
        }

        SectionLabel(stringResource(R.string.settings_music_section))
        ActionValueRow(
            title = stringResource(R.string.settings_music_access),
            value = stringResource(if (musicAccess) R.string.settings_on else R.string.settings_off),
            act = !musicAccess,
        ) {
            runCatching {
                ctx.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        android.net.Uri.fromParts("package", ctx.packageName, null),
                    ),
                )
            }
        }
        ActionValueRow(
            title = stringResource(R.string.settings_scan_new),
            value = if (store.lastScanAtMs > 0) {
                stringResource(
                    R.string.settings_last_scanned,
                    SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(store.lastScanAtMs)),
                )
            } else {
                stringResource(R.string.settings_never_scanned)
            },
            act = true,
        ) {
            // The scan lives in the Music tab; Settings sends the user there
            // rather than growing a second scan path.
            onOpenMusic()
        }
        ActionValueRow(
            title = stringResource(R.string.settings_make_haptics),
            value = stringResource(
                if (store.hapticsMode == HapticsWorker.MODE_BACKGROUND) {
                    R.string.settings_mode_background
                } else {
                    R.string.settings_mode_as_played
                },
            ),
            act = true,
        ) {
            val switchingToBackground =
                store.hapticsMode != HapticsWorker.MODE_BACKGROUND
            store.hapticsMode =
                if (switchingToBackground) HapticsWorker.MODE_BACKGROUND else HapticsWorker.MODE_AS_PLAYED
            if (switchingToBackground) {
                // Everything without a haptic joins the walk now.
                scope.launch {
                    val made = withContext(Dispatchers.IO) { db.dao().madeTrackUris() }.toSet()
                    val queued = HapticQueue(emptyList(), made)
                        .enqueued(withContext(Dispatchers.IO) { db.dao().allTrackUris() })
                    store.hapticQueuePending = queued.pending
                    store.hapticsDone = 0
                    store.hapticsTotal = queued.pending.size
                    HapticsWorker.ensureEnqueued(ctx)
                }
            } else {
                store.hapticQueuePending = emptyList()
                store.hapticsDone = 0
                store.hapticsTotal = 0
            }
        }

        SectionLabel(stringResource(R.string.settings_haptics_section))
        ActionValueRow(
            title = stringResource(R.string.settings_default_feel),
            value = stringResource(
                when (Tuning.matching(store.body)) {
                    Tuning.CRISP -> R.string.tune_preset_crisp
                    Tuning.FULL -> R.string.tune_preset_full
                    Tuning.STRONG -> R.string.tune_preset_strong
                    else -> R.string.settings_custom
                },
            ),
            act = true,
        ) {
            // The dials live in Home's tune section in this build.
            onOpenHome()
        }

        SectionLabel(stringResource(R.string.settings_phone_section))
        ActionValueRow(stringResource(R.string.phonecheck_title), "›", act = true) {
            showPhoneCheck = true
        }
        ActionValueRow(stringResource(R.string.about_title), "›", act = true) {
            showAbout = true
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(top = Space.S4)
            .semantics { heading() },
    )
}

@Composable
private fun ValueRow(title: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Touch.min)
            .padding(vertical = Space.S1),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.padding(start = Space.S4),
        )
    }
}

@Composable
private fun ActionValueRow(title: String, value: String, act: Boolean, onAct: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Touch.min)
            .then(if (act) Modifier.clickable(onClick = onAct) else Modifier)
            .padding(vertical = Space.S1),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = if (act) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SwitchRow(title: String, help: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Touch.min)
            .padding(vertical = Space.S1),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(0.76f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                help,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/**
 * About Thrum, screen 21. The founder story is the agreed text from the
 * final screens, verbatim.
 */
@Composable
private fun AboutScreen(onClose: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.S5, vertical = Space.S6),
        verticalArrangement = Arrangement.spacedBy(Space.S4),
    ) {
        TextButton(onClick = onClose, modifier = Modifier.heightIn(min = Touch.min)) {
            Text(stringResource(R.string.player_close))
        }
        Text(
            stringResource(R.string.about_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            stringResource(R.string.onboarding_tagline),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            stringResource(R.string.about_why),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            stringResource(R.string.about_story_1),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            stringResource(R.string.about_story_2),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            stringResource(R.string.about_founder),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(R.string.about_originals),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            stringResource(R.string.about_originals_note),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(R.string.about_no_internet),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            stringResource(R.string.about_no_internet_help),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Phone check, screen 22 — what this phone can do, including how fast its
 * motor can tap. The tap test moved here from Home: it measures the
 * instrument, not the product.
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
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.S5, vertical = Space.S6),
        verticalArrangement = Arrangement.spacedBy(Space.S4),
    ) {
        TextButton(onClick = onClose, modifier = Modifier.heightIn(min = Touch.min)) {
            Text(stringResource(R.string.player_close))
        }
        Text(
            stringResource(R.string.phonecheck_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            stringResource(
                if (capability.usable) R.string.phonecheck_good_title else R.string.phonecheck_bad_title,
            ),
            style = MaterialTheme.typography.titleLarge,
            color = if (capability.usable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
        ValueRow(
            stringResource(R.string.phonecheck_motor),
            stringResource(if (capability.hasVibrator) R.string.phonecheck_yes else R.string.phonecheck_none),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(stringResource(R.string.phonecheck_amplitude), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.phonecheck_amplitude_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                stringResource(if (capability.amplitudeControl) R.string.phonecheck_yes else R.string.phonecheck_no),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ValueRow(
            stringResource(R.string.settings_call_access),
            stringResource(if (callAccess) R.string.settings_on else R.string.settings_off),
        )
        ValueRow(
            stringResource(R.string.settings_music_access),
            stringResource(if (musicAccess) R.string.settings_on else R.string.settings_off),
        )

        Text(
            stringResource(R.string.rate_title),
            style = MaterialTheme.typography.titleMedium,
        )
        if (ratePlaying > 0) {
            Text(
                stringResource(R.string.rate_playing, ratePlaying),
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            stringResource(R.string.rate_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = { runRateTest() }, modifier = Modifier.heightIn(min = Touch.min)) {
            Text(stringResource(R.string.rate_run))
        }
    }
}

/** Same reason as everywhere else in the app: uptime, not wall clock. */
private val RATE_LADDER = listOf(2, 3, 4, 6, 8, 12)
private const val RATE_GAP_MS = 900L
private const val POLL_MS = 800L

/** The listener check, named so no call site imports the compat class directly. */
private object NotificationManagerCompatHelper {
    fun isEnabled(ctx: android.content.Context): Boolean =
        androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(ctx)
            .contains(ctx.packageName)
}
