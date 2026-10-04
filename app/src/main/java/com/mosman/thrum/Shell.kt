package com.mosman.thrum

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The app's frame. Task 18, drawn across all final screens.
 *
 * Bottom tabs in exact order: Home, My Haptics, Music, Settings.
 * Fixed 80dp bar in #1A1A18 with 1px #3A3A36 top border.
 */
@Composable
fun ThrumRoot(onDiagnostics: (() -> Unit)? = null) {
    val ctx = LocalContext.current
    val store = remember { Store(ctx) }
    var onboarded by remember { mutableStateOf(store.onboarded) }

    // A walk left unfinished resumes when the app opens. Before the
    // 2026-10-04 fix the walk stopped after one song, so a phone updated
    // from that build can be holding a list nothing is working through.
    // Safe to ask every launch: a walk already running is left alone.
    LaunchedEffect(Unit) {
        if (store.hapticQueuePending.isNotEmpty()) HapticsWorker.ensureEnqueued(ctx)
    }

    Surface(modifier = Modifier.fillMaxSize(), color = ThrumField) {
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

enum class NavTab(val icon: String, val labelRes: Int) {
    HOME("home", R.string.tab_home),
    HAPTICS("library", R.string.tab_haptics),
    MUSIC("music", R.string.tab_music),
    SETTINGS("gear", R.string.tab_settings),
}

@Composable
private fun Tabs(onDiagnostics: (() -> Unit)?) {
    var tab by remember { mutableStateOf(NavTab.HOME) }
    var showExport by remember { mutableStateOf(false) }
    var showTuning by remember { mutableStateOf(false) }

    if (Player.open) {
        PlayerScreen(
            onTune = {
                Player.open = false
                showTuning = true
            },
            onExport = {
                Player.open = false
                showExport = true
            },
        )
        return
    }
    if (showTuning) {
        TuneScreen(onClose = { showTuning = false })
        return
    }
    if (showExport) {
        ExportScreen(onClose = { showExport = false })
        return
    }

    Column(modifier = Modifier.fillMaxSize().background(ThrumField)) {
        Box(modifier = Modifier.weight(1f)) {
            when (tab) {
                NavTab.HOME -> ThrumApp(
                    onDiagnostics = onDiagnostics,
                    onOpenMusic = { tab = NavTab.MUSIC },
                    onOpenCreate = { tab = NavTab.HAPTICS },
                    onOpenSettings = { tab = NavTab.SETTINGS },
                )
                NavTab.HAPTICS -> MyHapticsTab(
                    onGoToMusic = { tab = NavTab.MUSIC },
                    onExport = { showExport = true },
                )
                NavTab.MUSIC -> MusicTab(
                    onExport = { showExport = true },
                )
                NavTab.SETTINGS -> SettingsTab(
                    onOpenMusic = { tab = NavTab.MUSIC },
                    onOpenHome = { tab = NavTab.HOME },
                    onOpenTune = { showTuning = true },
                )
            }

            if (Player.now != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    MiniPlayer()
                }
            }
        }

        ThrumNavBar(
            current = tab,
            onSelect = { tab = it },
        )
    }
}

@Composable
fun ThrumNavBar(
    current: NavTab,
    onSelect: (NavTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF1A1A18))
            .border(width = 1.dp, color = ThrumRule)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .height(72.dp)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NavTab.entries.forEach { tabItem ->
            val isSelected = tabItem == current
            val color = if (isSelected) ThrumAccent else ThrumInk2

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelect(tabItem) }
                    .padding(vertical = 4.dp)
                    .semantics { selected = isSelected },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                ThrumIcon(
                    name = tabItem.icon,
                    tint = color,
                    size = 22.dp,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(tabItem.labelRes),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = color,
                    maxLines = 1,
                )
            }
        }
    }
}
