package com.mosman.thrum

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics

/**
 * The app's frame. Task 18.
 *
 * The order is fixed and deliberate: the phone check runs inside onboarding's
 * splash before anything else; a first-time user walks the seven screens; and
 * only then does the tab shell appear, Home first, because calls come first.
 *
 * The three tabs that are not built yet carry one honest line each rather than
 * a pretence of a screen. A stub that mimicked a feature would be this
 * project's oldest lie — a surface reporting an intention — with a tab bar on
 * it.
 */
@Composable
fun ThrumRoot(onDiagnostics: (() -> Unit)? = null) {
    val ctx = LocalContext.current
    val store = remember { Store(ctx) }
    var onboarded by remember { mutableStateOf(store.onboarded) }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (onboarded) {
            Tabs(onDiagnostics)
        } else {
            OnboardingFlow(onDone = {
                store.onboarded = true
                onboarded = true
            })
        }
    }
}

private enum class Tab { HOME, HAPTICS, MUSIC, SETTINGS }

@Composable
private fun Tabs(onDiagnostics: (() -> Unit)?) {
    var tab by remember { mutableStateOf(Tab.HOME) }
    var showExport by remember { mutableStateOf(false) }

    if (Player.open) {
        // The player screen overlays the tabs; closing it keeps the song
        // playing, which is what the mini player above the tab bar is for.
        // Tune routes to Home, where the tune section lives in this build.
        PlayerScreen(onTune = { Player.open = false; tab = Tab.HOME })
        return
    }
    if (showExport) {
        ExportScreen(onClose = { showExport = false })
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Each tab's content manages its own insets — Home is the existing
        // calls screen, which already pads for the system bars.
        Box(modifier = Modifier.weight(1f)) {
            when (tab) {
                Tab.HOME -> ThrumApp(
                    onDiagnostics = onDiagnostics,
                    onOpenMusic = { tab = Tab.MUSIC },
                    onOpenCreate = { tab = Tab.HAPTICS },
                )
                Tab.HAPTICS -> MyHapticsTab(onGoToMusic = { tab = Tab.MUSIC })
                Tab.MUSIC -> MusicTab(onExport = { showExport = true })
                Tab.SETTINGS -> SettingsTab(onOpenMusic = { tab = Tab.MUSIC }, onOpenHome = { tab = Tab.HOME })
            }
        }
        if (Player.now != null) MiniPlayer()
        TabBar(
            current = tab,
            onSelect = { tab = it },
            labels = listOf(
                stringResource(R.string.tab_home),
                stringResource(R.string.tab_haptics),
                stringResource(R.string.tab_music),
                stringResource(R.string.tab_settings),
            ),
        )
    }
}

/**
 * Text-only tabs, on purpose. The design forbids an icon set ("no stock
 * imagery, no illustration, no icon set" — `docs/design/figma-handoff.md` §9),
 * and four words need no legend: Home, My Haptics, Music, Settings.
 *
 * Selection is announced to screen readers through `selected`, because colour
 * is never the only signal.
 */
@Composable
private fun TabBar(current: Tab, onSelect: (Tab) -> Unit, labels: List<String>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        Tab.entries.forEachIndexed { index, tab ->
            val isSelected = tab == current
            Text(
                labels[index],
                style = MaterialTheme.typography.labelLarge,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier
                    .heightIn(min = Touch.min)
                    .clickable { onSelect(tab) }
                    .padding(horizontal = Space.S3, vertical = Space.S3)
                    .semantics { selected = isSelected },
            )
        }
    }
}

@Composable
private fun Stub(title: String, body: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = Space.S5, vertical = Space.S6),
        verticalArrangement = Arrangement.spacedBy(Space.S4),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
