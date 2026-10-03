package com.mosman.thrum

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.delay

/**
 * First launch: seven screens, calls before music, the phone check before
 * everything. Task 18, drawn in `docs/design/screens` screens 1–7.
 *
 * The flow carries the project's oldest rules with it:
 *
 * - **Screen 1 is the capability check** (Sacred Rule 2). An incapable phone
 *   never sees screen 2 — it sees the same honest dead end the app has always
 *   drawn, with no button on it.
 * - **Screen 3 lets the phone be felt before any permission** — the demo
 *   rhythm on one side, the flat buzz on the other. Until the built-in
 *   collection ships (Task 27), the Thrum side plays the stand-in rhythm the
 *   app has demoed since Task 1; the plan records that deliberately.
 * - **Call access is asked, never demanded** — "Maybe later" goes on to the
 *   app, and the Home screen polls for the grant the way it always has.
 *
 * Copy is verbatim from the screens doc except the two places this build does
 * not have the thing a line described: the compare note names only what is
 * here, and screen 7 does not promise a Thrum Original to start from.
 */
@Composable
fun OnboardingFlow(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val capability = remember { Haptics.capability(ctx) }
    var screen by remember { mutableStateOf(0) }

    BackHandler(enabled = screen in 1..6) { screen -= 1 }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when {
            // The dead end. No way forward is drawn, because there is none:
            // a motor that cannot vary strength turns every rhythm back into
            // the flat buzz the phone already makes.
            !capability.usable -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Space.S5, vertical = Space.S6),
                verticalArrangement = Arrangement.spacedBy(Space.S4),
            ) {
                Text(
                    stringResource(R.string.onboarding_tagline),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.semantics { heading() },
                )
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

            // The splash. The check has already run — this moment exists so
            // the first thing the app ever does is say what it is doing.
            screen == 0 -> Splash(onChecked = { screen = 1 })

            else -> OnboardingScreen(screen = screen, onDone = onDone, onBack = { screen -= 1 }, onNext = { screen += 1 })
        }
    }
}

@Composable
private fun Splash(onChecked: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(900)
        onChecked()
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = Space.S5),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            stringResource(R.string.onboarding_tagline),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.heightIn(min = Space.S5))
        Text(
            stringResource(R.string.onboarding_checking),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun OnboardingScreen(screen: Int, onDone: () -> Unit, onBack: () -> Unit, onNext: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = Space.S5)
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.weight(1f))
        when (screen) {
            1 -> Welcome(onNext)
            2 -> Touch(onNext)
            3 -> Sources(onNext)
            4 -> Calls(onNext)
            5 -> MusicPlay(onNext)
            6 -> Done(onDone)
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun Welcome(onNext: () -> Unit) {
    Heading(stringResource(R.string.onboarding_tagline))
    Text(
        stringResource(R.string.onboarding_welcome_body),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onBackground,
    )
    Text(
        stringResource(R.string.onboarding_welcome_capable),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Buttons(primary = stringResource(R.string.onboarding_see_how), onPrimary = onNext)
    SecondaryRow(stringResource(R.string.onboarding_skip), onNext)
}

@Composable
private fun Touch(onNext: () -> Unit) {
    Heading(stringResource(R.string.onboarding_touch_title))
    Text(
        stringResource(R.string.onboarding_touch_body),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onBackground,
    )
    val demo = remember { Demo.rhythm() }
    PulseRibbon(
        score = demo,
        progress = -1f,
        barColour = MaterialTheme.colorScheme.primary,
        silentColour = MaterialTheme.colorScheme.outline,
        playedColour = MaterialTheme.colorScheme.primary,
    )
    val ctx = LocalContext.current
    Row(horizontalArrangement = Arrangement.spacedBy(Space.S2), modifier = Modifier.fillMaxWidth()) {
        Secondary(stringResource(R.string.onboarding_feel_android), Modifier.weight(1f)) {
            Haptics.play(ctx, Demo.systemBuzz())
        }
        Secondary(stringResource(R.string.onboarding_feel_thrum), Modifier.weight(1f)) {
            Haptics.play(ctx, demo)
        }
    }
    Text(
        stringResource(R.string.onboarding_feel_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Buttons(primary = stringResource(R.string.onboarding_next), onPrimary = onNext)
}

@Composable
private fun Sources(onNext: () -> Unit) {
    Heading(stringResource(R.string.onboarding_sources_title))
    Text(
        stringResource(R.string.onboarding_sources_body),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onBackground,
    )
    SourceRow(stringResource(R.string.onboarding_calls_row), stringResource(R.string.onboarding_calls_row_help))
    SourceRow(stringResource(R.string.onboarding_music_row), stringResource(R.string.onboarding_music_row_help))
    SourceRow(stringResource(R.string.onboarding_videos_row), stringResource(R.string.onboarding_videos_row_help))
    Buttons(primary = stringResource(R.string.onboarding_next), onPrimary = onNext)
}

@Composable
private fun SourceRow(title: String, help: String) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.S1)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            help,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Calls(onNext: () -> Unit) {
    val ctx = LocalContext.current
    // Polled, for the same reason Home polls it: the user leaves for system
    // settings, grants, and comes back — a screen still asking for what was
    // just granted is how an app looks broken.
    val permitted by produceState(initialValue = false) {
        while (true) {
            value = NotificationManagerCompat.getEnabledListenerPackages(ctx)
                .contains(ctx.packageName)
            delay(POLL_MS)
        }
    }
    Heading(stringResource(R.string.onboarding_calls_title))
    Text(
        stringResource(R.string.onboarding_calls_body),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onBackground,
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Space.S4),
        verticalArrangement = Arrangement.spacedBy(Space.S1),
    ) {
        Text(
            stringResource(R.string.onboarding_calls_example_head),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            stringResource(R.string.onboarding_calls_example_line),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Text(
        stringResource(R.string.onboarding_calls_permission),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (permitted) {
        Buttons(primary = stringResource(R.string.onboarding_next), onPrimary = onNext)
    } else {
        Buttons(primary = stringResource(R.string.onboarding_calls_action), onPrimary = {
            runCatching { ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        })
        SecondaryRow(stringResource(R.string.onboarding_calls_later), onNext)
    }
}

@Composable
private fun MusicPlay(onNext: () -> Unit) {
    Heading(stringResource(R.string.onboarding_music_title))
    Text(
        stringResource(R.string.onboarding_music_body),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onBackground,
    )
    // The flat line is the honest illustration: the player this screen
    // promises does not exist in this build yet, and the ribbon's empty state
    // is exactly what the design drew for it.
    PulseRibbon(
        score = null,
        progress = -1f,
        barColour = MaterialTheme.colorScheme.primary,
        silentColour = MaterialTheme.colorScheme.outline,
        playedColour = MaterialTheme.colorScheme.primary,
    )
    Text(
        stringResource(R.string.onboarding_music_privacy),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Buttons(primary = stringResource(R.string.onboarding_next), onPrimary = onNext)
}

@Composable
private fun Done(onDone: () -> Unit) {
    Text(
        stringResource(R.string.onboarding_done_hint),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Heading(stringResource(R.string.onboarding_done_title))
    Text(
        stringResource(R.string.onboarding_done_body),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onBackground,
    )
    // Both buttons finish onboarding; the pick itself happens on Home, whose
    // empty state exists for exactly this. One day the button will arm a
    // Thrum Original here — the collection has not shipped yet.
    Buttons(primary = stringResource(R.string.onboarding_done_action), onPrimary = onDone)
    SecondaryRow(stringResource(R.string.onboarding_done_look), onDone)
}

@Composable
private fun Heading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(bottom = Space.S4)
            .semantics { heading() },
    )
}

@Composable
private fun Buttons(primary: String, onPrimary: () -> Unit) {
    Button(
        onClick = onPrimary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Space.S6)
            .heightIn(min = Touch.min),
    ) {
        Text(primary, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
}

@Composable
private fun SecondaryRow(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Space.S2)
            .heightIn(min = Touch.min),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

private const val POLL_MS = 800L
