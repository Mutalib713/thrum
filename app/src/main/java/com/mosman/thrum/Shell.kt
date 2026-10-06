package com.mosman.thrum

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * The app's frame. Task 18, drawn across all final screens.
 *
 * The order is fixed and deliberate: the phone check runs inside first
 * launch's splash before anything else; a first-time user walks the seven
 * screens; and only then does the tab bar appear, Home first, because calls
 * come first. Tabs in exact order: Home, My Haptics, Music, Settings.
 */
@Composable
fun ThrumRoot(onDeveloperTools: (() -> Unit)? = null) {
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
            Tabs(onDeveloperTools)
        } else {
            OnboardingFlow(onDone = {
                store.onboarded = true
                onboarded = true
            })
        }
    }
}

/**
 * What Export opens on: the song playing first (from its page), every haptic
 * ([uris] null), or exactly the rows chosen in a ⋮ menu or a selection.
 */
data class ExportRequest(val thisSongFirst: Boolean, val uris: List<String>? = null)

enum class NavTab(val icon: String, val labelRes: Int) {
    HOME("home", R.string.tab_home),
    HAPTICS("library", R.string.tab_haptics),
    MUSIC("music", R.string.tab_music),
    SETTINGS("gear", R.string.tab_settings),
}

@Composable
private fun Tabs(onDeveloperTools: (() -> Unit)?) {
    var tab by rememberSaveable { mutableStateOf(NavTab.HOME) }
    // Null when closed; otherwise what it opened on.
    var export by remember { mutableStateOf<ExportRequest?>(null) }
    var tuning by remember { mutableStateOf<TuneTarget?>(null) }
    // Home's Create card: My Haptics opens with its "make a haptic" choice up.
    var makeRequested by remember { mutableStateOf(false) }

    // Screens over the tabs, nearest first. Tune and Export open over the
    // player too, so closing them returns to the song, not to a tab.
    // Each one handles the phone's Back button itself; before, only the
    // player did, and Back from any other closed the whole app.
    tuning?.let { target ->
        TuneScreen(target = target, onClose = { tuning = null })
        return
    }
    export?.let { request ->
        ExportScreen(thisSongFirst = request.thisSongFirst, uris = request.uris, onClose = { export = null })
        return
    }
    if (Player.open) {
        PlayerScreen(
            onTune = { target -> tuning = target },
            onExport = { export = ExportRequest(thisSongFirst = true) },
        )
        return
    }

    // Back on another tab goes Home first, the way Android apps do, rather
    // than closing the app from the middle of it.
    BackHandler(enabled = tab != NavTab.HOME) { tab = NavTab.HOME }

    Column(modifier = Modifier.fillMaxSize().background(ThrumField)) {
        Box(modifier = Modifier.weight(1f)) {
            when (tab) {
                NavTab.HOME -> ThrumApp(
                    onDeveloperTools = onDeveloperTools,
                    onOpenMusic = { tab = NavTab.MUSIC },
                    onOpenCreate = {
                        makeRequested = true
                        tab = NavTab.HAPTICS
                    },
                    onOpenSettings = { tab = NavTab.SETTINGS },
                )
                NavTab.HAPTICS -> MyHapticsTab(
                    onExport = { uris -> export = ExportRequest(thisSongFirst = false, uris = uris) },
                    onTune = { target -> tuning = target },
                    makeRequested = makeRequested,
                    onMakeShown = { makeRequested = false },
                )
                NavTab.MUSIC -> MusicTab(
                    onExport = { uris -> export = ExportRequest(thisSongFirst = false, uris = uris) },
                    onTune = { target -> tuning = target },
                )
                NavTab.SETTINGS -> SettingsTab(
                    onOpenMusic = { tab = NavTab.MUSIC },
                    onOpenHome = { tab = NavTab.HOME },
                    onOpenTune = { tuning = TuneTarget.Calls },
                )
            }

            if (Player.now != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = Space.S3, vertical = 10.dp),
                ) {
                    MiniPlayer()
                }
            }
        }

        ThrumNavBar(current = tab, onSelect = { tab = it })
    }
}

@Composable
fun ThrumNavBar(
    current: NavTab,
    onSelect: (NavTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(ThrumTabBar)
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        // A rule along the top only; the first bar boxed itself on all four sides.
        HorizontalDivider(color = ThrumRule, thickness = 1.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .padding(horizontal = Space.S2, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavTab.entries.forEach { item ->
                val isSelected = item == current
                val color = if (isSelected) ThrumAccentInk else ThrumInk2
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(item) })
                        .padding(vertical = Space.S1),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    ThrumIcon(name = item.icon, tint = color, size = 22.dp)
                    Spacer(Modifier.height(Space.S1))
                    Text(stringResource(item.labelRes), style = ThrumType.tab, color = color, maxLines = 1)
                }
            }
        }
    }
}
