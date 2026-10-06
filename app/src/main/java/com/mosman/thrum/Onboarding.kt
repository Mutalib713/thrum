package com.mosman.thrum

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * First launch: seven screens, calls before music, the phone check before
 * everything. Task 18, drawn in `docs/design/screens` screens 1–7.
 *
 * - **Screen 1 is the capability check** (Sacred Rule 2). An incapable phone
 *   never sees screen 2: it sees the honest dead end, with no button on it.
 * - **Screen 3 lets the phone be felt before any permission** — the flat
 *   buzz on one side, the demo rhythm on the other, both exactly what they
 *   say they are. Until the built-in collection ships (Task 27), the Thrum
 *   side is the rhythm the app has demoed since Task 1, and is not dressed up
 *   as a Thrum Original "with sound": the first UI drawn here did that, and
 *   played the demo with no sound at all.
 * - **Call access is asked, never demanded.** "Maybe later" goes on, and Home
 *   keeps saying calls cannot work until it is granted.
 *
 * Nothing here is sample data: the first UI drawn on these screens named
 * Mutalib's own call song as the reader's, and drew a player with a play
 * button that did nothing.
 */
@Composable
fun OnboardingFlow(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val capability = remember { Haptics.capability(ctx) }
    var screen by remember { mutableIntStateOf(0) }

    // Back walks back through the screens, but never into the splash, which
    // would only bounce forward again a second later.
    BackHandler(enabled = screen in 2..6) { screen -= 1 }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ThrumField),
    ) {
        when {
            !capability.usable -> CapabilityDeadEnd(capability)
            screen == 0 -> Splash(onNext = { screen = 1 })
            screen == 1 -> Welcome(onSeeHow = { screen = 2 }, onSkip = onDone)
            screen == 2 -> SoundToTouch(onNext = { screen = 3 })
            screen == 3 -> Sources(onNext = { screen = 4 })
            screen == 4 -> FeelCalls(onNext = { screen = 5 })
            screen == 5 -> FeelMusic(onNext = { screen = 6 })
            else -> YouAreSet(onDone = onDone)
        }
    }
}

/** A first-launch screen: scrolls on a small phone, its button at the bottom. */
@Composable
private fun Step(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.S5, vertical = 28.dp),
        content = content,
    )
}

@Composable
private fun Statement(text: String, top: Int = 28) {
    Text(
        text,
        style = ThrumType.statement,
        color = ThrumInk,
        modifier = Modifier
            .padding(top = top.dp)
            .semantics { heading() },
    )
}

@Composable
private fun Lead(text: String) {
    Text(text, style = ThrumType.lead, color = ThrumInkSoft, modifier = Modifier.padding(top = Space.S3))
}

@Composable
private fun Splash(onNext: () -> Unit) {
    // The check has already run; this moment exists so the first thing the
    // app ever does is say what it is doing.
    LaunchedEffect(Unit) {
        delay(1200)
        onNext()
    }
    val accent = ThrumAccent
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.radialGradient(colors = listOf(accent.copy(alpha = 0.22f), Color.Transparent), radius = 800f))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = Space.S5, vertical = 28.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.weight(1f))
            PulseRibbon(score = remember { Demo.rhythm() }, height = 110.dp, barWidth = 2.dp, barGap = 2.dp)
            Text(
                stringResource(R.string.app_name).uppercase(),
                style = ThrumType.splash,
                color = ThrumAccentInk,
                modifier = Modifier
                    .padding(top = 28.dp)
                    .semantics { heading() },
            )
            Text(stringResource(R.string.onboarding_tagline), style = ThrumType.lead, color = ThrumInk, modifier = Modifier.padding(top = Space.S2))
            Spacer(Modifier.weight(1f))
            Text(stringResource(R.string.onboarding_checking), style = ThrumType.meta, color = ThrumInk2)
        }
    }
}

@Composable
private fun Welcome(onSeeHow: () -> Unit, onSkip: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = Space.S5, vertical = 28.dp),
    ) {
        ThrumDots(count = 5, activeIndex = 0)
        Text(
            stringResource(R.string.app_name).uppercase(),
            style = ThrumType.wordmark,
            color = ThrumAccentInk,
            modifier = Modifier.padding(top = 40.dp),
        )
        Text(
            // Two lines, as the screens draw it.
            stringResource(R.string.onboarding_tagline).replace(". ", ".\n"),
            style = ThrumType.hero,
            color = ThrumInk,
            modifier = Modifier
                .padding(top = 14.dp)
                .semantics { heading() },
        )
        Text(stringResource(R.string.onboarding_welcome_body), style = ThrumType.lead, color = ThrumInkSoft, modifier = Modifier.padding(top = Space.S4))
        Row(
            modifier = Modifier.padding(top = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.S2),
        ) {
            ThrumIcon(name = "check", tint = ThrumAccentInk, size = 18.dp)
            Text(stringResource(R.string.onboarding_welcome_capable), style = ThrumType.meta, color = ThrumInk)
        }
        Spacer(Modifier.weight(1f))
        PrimaryButton(text = stringResource(R.string.onboarding_see_how), onClick = onSeeHow, modifier = Modifier.fillMaxWidth())
        ThrumTextButton(
            text = stringResource(R.string.onboarding_skip),
            onClick = onSkip,
            color = ThrumInk2,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Space.S2),
        )
    }
}

/** Screen 3. Which of the two patterns is playing: 0 none, 1 Android's, 2 Thrum's. */
@Composable
private fun SoundToTouch(onNext: () -> Unit) {
    val ctx = LocalContext.current
    val buzz = remember { Demo.systemBuzz() }
    val rhythm = remember { Demo.rhythm() }
    var playing by remember { mutableIntStateOf(0) }

    // A button shows "pause" only while its pattern is really playing:
    // reset when the pattern ends, and the motor stopped when the screen goes.
    LaunchedEffect(playing) {
        val length = when (playing) {
            1 -> buzz.durationMs
            2 -> rhythm.durationMs
            else -> return@LaunchedEffect
        }
        delay(length)
        playing = 0
    }
    DisposableEffect(Unit) { onDispose { Haptics.stop(ctx) } }

    fun toggle(which: Int, score: Score) {
        if (playing == which) {
            Haptics.stop(ctx)
            playing = 0
        } else {
            playing = if (Haptics.play(ctx, score) == null) which else 0
        }
    }

    Step {
        ThrumDots(count = 5, activeIndex = 1)
        Statement(stringResource(R.string.onboarding_touch_title))
        Lead(stringResource(R.string.onboarding_touch_body))

        // How it works: a sound, the beats in it, the motor.
        ThrumCard(modifier = Modifier.padding(top = 20.dp), padding = PaddingValues(Space.S4)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.S2),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    PulseRibbon(score = rhythm, height = 44.dp, barWidth = 2.dp, barGap = 1.dp, mirror = true)
                    Text(stringResource(R.string.onboarding_diagram_sound), style = ThrumType.meta, color = ThrumInk2, modifier = Modifier.padding(top = 6.dp))
                }
                ThrumIcon(name = "chev", tint = ThrumInk2, size = 16.dp)
                Column(modifier = Modifier.weight(1f)) {
                    PulseRibbon(score = rhythm, height = 44.dp, barWidth = 2.dp, barGap = 1.dp)
                    Text(stringResource(R.string.onboarding_diagram_beats), style = ThrumType.meta, color = ThrumInk2, modifier = Modifier.padding(top = 6.dp))
                }
                ThrumIcon(name = "chev", tint = ThrumInk2, size = 16.dp)
                Column(modifier = Modifier.width(56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    ThrumIcon(name = "vib", tint = ThrumAccentInk, size = 34.dp)
                    Text(stringResource(R.string.onboarding_diagram_phone), style = ThrumType.meta, color = ThrumInk2, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }

        ThrumCard(modifier = Modifier.padding(top = Space.S3), padding = PaddingValues(Space.S4)) {
            Overline(stringResource(R.string.onboarding_feel_title))
            FeelRow(
                label = stringResource(R.string.onboarding_feel_android),
                score = buzz,
                playing = playing == 1,
                onToggle = { toggle(1, buzz) },
            )
            FeelRow(
                label = stringResource(R.string.onboarding_feel_thrum),
                score = rhythm,
                playing = playing == 2,
                onToggle = { toggle(2, rhythm) },
            )
        }
        Text(
            stringResource(R.string.onboarding_feel_note),
            style = ThrumType.meta,
            color = ThrumInk2,
            modifier = Modifier.padding(top = 10.dp),
        )

        Spacer(Modifier.height(Space.S6))
        PrimaryButton(text = stringResource(R.string.onboarding_next), onClick = onNext, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun FeelRow(label: String, score: Score, playing: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Space.S3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.S3),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = ThrumType.meta, color = ThrumInk2)
            PulseRibbon(score = score, height = 24.dp, barWidth = 2.dp, barGap = 1.dp)
        }
        CirclePlayButton(playing = playing, onClick = onToggle)
    }
}

@Composable
private fun Sources(onNext: () -> Unit) {
    Step {
        ThrumDots(count = 5, activeIndex = 2)
        Statement(stringResource(R.string.onboarding_sources_title))
        Lead(stringResource(R.string.onboarding_sources_body))
        ThrumCard(modifier = Modifier.padding(top = 22.dp), padding = PaddingValues(0.dp)) {
            SourceRow("phone", stringResource(R.string.onboarding_calls_row), stringResource(R.string.onboarding_calls_row_help))
            HorizontalDivider(color = ThrumRule, thickness = 1.dp)
            SourceRow("music", stringResource(R.string.onboarding_music_row), stringResource(R.string.onboarding_music_row_help))
            HorizontalDivider(color = ThrumRule, thickness = 1.dp)
            SourceRow("video", stringResource(R.string.onboarding_videos_row), stringResource(R.string.onboarding_videos_row_help))
        }
        Spacer(Modifier.height(Space.S6))
        PrimaryButton(text = stringResource(R.string.onboarding_next), onClick = onNext, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun SourceRow(icon: String, title: String, subtitle: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.S4, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconCircle(icon = icon, size = 40.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = ThrumType.row, color = ThrumInk)
            Text(subtitle, style = ThrumType.meta, color = ThrumInk2, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun FeelCalls(onNext: () -> Unit) {
    val ctx = LocalContext.current
    // Polled: the user leaves for system settings, grants, and comes back to
    // a screen that already knows — and says Next instead of asking again.
    val permitted by rememberPolled(hasCallAccess(ctx)) { hasCallAccess(it) }
    val mayBeLocked = remember { callAccessMayBeLocked(ctx) }
    var asked by remember { mutableStateOf(Store(ctx).callAccessAsked) }
    Step {
        ThrumDots(count = 5, activeIndex = 3)
        ThrumCard(modifier = Modifier.padding(top = Space.S5), padding = PaddingValues(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Overline(stringResource(R.string.onboarding_calls_example_head))
                ThrumIcon(name = "phone", tint = ThrumAccentInk, size = 18.dp)
            }
            PulseRibbon(
                score = remember { Demo.rhythm() },
                height = 56.dp,
                barWidth = 3.dp,
                barGap = 1.dp,
                modifier = Modifier.padding(top = Space.S3),
            )
            Text(
                stringResource(R.string.onboarding_calls_example_line),
                style = ThrumType.meta,
                color = ThrumInk2,
                modifier = Modifier.padding(top = Space.S2),
            )
        }
        Statement(stringResource(R.string.onboarding_calls_title), top = 24)
        Lead(stringResource(R.string.onboarding_calls_body))
        Text(
            stringResource(R.string.onboarding_calls_permission),
            style = ThrumType.meta,
            color = ThrumInk2,
            modifier = Modifier.padding(top = Space.S3),
        )
        Spacer(Modifier.height(Space.S6))
        if (permitted) {
            PrimaryButton(text = stringResource(R.string.onboarding_next), onClick = onNext, modifier = Modifier.fillMaxWidth())
        } else {
            PrimaryButton(
                text = stringResource(R.string.onboarding_calls_action),
                onClick = {
                    asked = true
                    openCallAccess(ctx)
                },
                modifier = Modifier.fillMaxWidth(),
            )
            if (asked && mayBeLocked) {
                CallAccessLockedHelp(modifier = Modifier.padding(top = Space.S5))
            }
            ThrumTextButton(
                text = stringResource(R.string.onboarding_calls_later),
                onClick = onNext,
                color = ThrumInk2,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Space.S2),
            )
        }
    }
}

@Composable
private fun FeelMusic(onNext: () -> Unit) {
    Step {
        ThrumDots(count = 5, activeIndex = 4)
        // An illustration of a rhythm, not a pretend player: there is no
        // song here to play, so there is no play button.
        ThrumCard(modifier = Modifier.padding(top = Space.S5), padding = PaddingValues(Space.S4)) {
            PulseRibbon(score = remember { Demo.rhythm() }, height = 64.dp, barWidth = 2.dp, barGap = 1.dp)
        }
        Statement(stringResource(R.string.onboarding_music_title), top = 24)
        Lead(stringResource(R.string.onboarding_music_body))
        Text(
            stringResource(R.string.onboarding_music_privacy),
            style = ThrumType.meta,
            color = ThrumInk2,
            modifier = Modifier.padding(top = Space.S3),
        )
        Spacer(Modifier.height(Space.S6))
        PrimaryButton(text = stringResource(R.string.onboarding_next), onClick = onNext, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun YouAreSet(onDone: () -> Unit) {
    Step {
        // Empty, and it says so: this is where the first haptic will be.
        ThrumCard(
            modifier = Modifier.padding(top = 28.dp),
            backgroundColor = ThrumSurface,
            padding = PaddingValues(20.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 100.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.onboarding_done_hint),
                    style = ThrumType.body,
                    color = ThrumInk2,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Statement(stringResource(R.string.onboarding_done_title), top = 24)
        Lead(stringResource(R.string.onboarding_done_body))
        Spacer(Modifier.height(Space.S6))
        // Both finish first launch. The pick itself happens on Home, whose
        // empty calls card exists for exactly this.
        PrimaryButton(text = stringResource(R.string.onboarding_done_action), onClick = onDone, modifier = Modifier.fillMaxWidth())
        ThrumTextButton(
            text = stringResource(R.string.onboarding_done_look),
            onClick = onDone,
            color = ThrumInk2,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Space.S2),
        )
    }
}

/**
 * The dead end. No way forward is drawn, because there is none: a motor that
 * cannot vary strength turns every rhythm back into the flat buzz the phone
 * already makes. The first UI drew the design board's note about this — "No
 * button here, on purpose" — inside a button-shaped outline.
 */
@Composable
private fun CapabilityDeadEnd(capability: Haptics.Capability) {
    Step {
        Text(stringResource(R.string.app_name).uppercase(), style = ThrumType.wordmark, color = ThrumAccentInk)
        Text(
            stringResource(R.string.blocked_title),
            style = ThrumType.statement,
            color = ThrumWarn,
            modifier = Modifier
                .padding(top = 28.dp)
                .semantics { heading() },
        )
        Lead(stringResource(R.string.blocked_body))
        Lead(stringResource(R.string.blocked_safe))
        Text(
            stringResource(
                R.string.blocked_detail,
                stringResource(if (capability.hasVibrator) R.string.phonecheck_yes else R.string.phonecheck_none),
                stringResource(if (capability.amplitudeControl) R.string.phonecheck_yes else R.string.phonecheck_no),
            ),
            style = ThrumType.meta,
            color = ThrumInk2,
            modifier = Modifier.padding(top = Space.S3),
        )
    }
}
