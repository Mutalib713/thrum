package com.mosman.thrum

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * The one activity. Everything the user sees is [ThrumRoot]; a test build can
 * also open [DeveloperToolsScreen] from Home.
 *
 * This file was Task 2's probe screen, which grew into a diagnostics page
 * with the call log on it. On 2026-10-06 the log moved to Settings → Phone
 * check and the page shrank to the two developer tests (DeveloperTools.kt).
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openPlayerIfAsked(intent)
        enableEdgeToEdge()
        setContent {
            var devTools by remember { mutableStateOf(false) }
            ThrumTheme {
                if (devTools) {
                    // Back returns to the app rather than closing it.
                    BackHandler { devTools = false }
                    DeveloperToolsScreen(onClose = { devTools = false })
                } else {
                    // Task 18's frame: phone check, seven first-launch screens,
                    // then the tabs with Home carrying the calls screen. The
                    // developer tools stay reachable from Home in test builds —
                    // the step-limit probe has to run again on every new
                    // phone. They are not product surface.
                    ThrumRoot(onDeveloperTools = if (BuildConfig.DEBUG) ({ devTools = true }) else null)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openPlayerIfAsked(intent)
    }

    /** A tap on the playing notification opens Thrum on the song, not on whatever tab it was left. */
    private fun openPlayerIfAsked(intent: Intent?) {
        if (intent?.getBooleanExtra(Player.EXTRA_OPEN_PLAYER, false) == true && Player.now != null) {
            Player.open = true
        }
    }
}
