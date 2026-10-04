package com.mosman.thrum

import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow

/**
 * The player screen, drawn in screen 14. It is also each song's page — though
 * in this build only "Use for calls" lives here yet; Tune is Task 24 and
 * Export is Task 25, and a control that did nothing would be a lie with a tap
 * target.
 *
 * The screen is an overlay on the tabs, not a destination: closing it keeps
 * the song playing, which is what the mini player above the tab bar is for.
 */
@Composable
fun PlayerScreen(onTune: (() -> Unit)? = null) {
    val ctx = LocalContext.current
    val now = Player.now ?: return
    val haptic = Player.haptic

    BackHandler { Player.open = false }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.S5, vertical = Space.S6),
        verticalArrangement = Arrangement.spacedBy(Space.S4),
    ) {
        TextButton(onClick = { Player.open = false }, modifier = Modifier.heightIn(min = Touch.min)) {
            Text(stringResource(R.string.player_close))
        }

        Text(
            now.track.name,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.semantics { heading() },
        )
        if (now.track.artist.isNotEmpty()) {
            Text(
                stringResource(R.string.player_artist_source, now.track.artist),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        when {
            // Screen 13. The one wait the app asks for, said in its own words.
            now.preparing -> {
                Text(
                    stringResource(R.string.player_making_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    stringResource(R.string.player_making_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                CircularProgressIndicator(
                    modifier = Modifier.size(Space.S5),
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            else -> {
                PulseRibbon(
                    score = haptic?.score,
                    progress = if (now.durationMs > 0) {
                        now.positionMs.toFloat() / now.durationMs
                    } else {
                        -1f
                    },
                    barColour = MaterialTheme.colorScheme.primary,
                    silentColour = MaterialTheme.colorScheme.outline,
                    playedColour = MaterialTheme.colorScheme.onBackground,
                )

                // The design's stats line: where the song is, how many hits
                // its haptic carries, and how much of it is still — the
                // number that separates a rhythm from a buzz.
                haptic?.let { ready ->
                    val still = remember(ready) {
                        ready.score.amplitudes.count { it == 0 } * 100 /
                            ready.score.amplitudes.size.coerceAtLeast(1)
                    }
                    Text(
                        stringResource(
                            R.string.player_stats,
                            clockOf(now.positionMs),
                            ready.score.pulseCount(),
                            still,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    clockOf(now.durationMs),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = { Player.previous(ctx) },
                        enabled = Player.canStep(-1),
                        modifier = Modifier.heightIn(min = Touch.min),
                    ) {
                        Text(stringResource(R.string.player_previous))
                    }
                    TextButton(
                        onClick = { Player.togglePause(ctx) },
                        modifier = Modifier.heightIn(min = Touch.min),
                    ) {
                        Text(
                            stringResource(if (now.playing) R.string.player_pause else R.string.player_play),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    TextButton(
                        onClick = { Player.next(ctx) },
                        enabled = Player.canStep(+1),
                        modifier = Modifier.heightIn(min = Touch.min),
                    ) {
                        Text(stringResource(R.string.player_next))
                    }
                }

                // Hear and feel · Feel only. The switch is real mid-play:
                // the drive restarts from the true position either way.
                Row(horizontalArrangement = Arrangement.spacedBy(Space.S2)) {
                    FilterChip(
                        selected = now.hearAndFeel,
                        onClick = { Player.setHearAndFeel(ctx, true) },
                        label = { Text(stringResource(R.string.player_hear_feel)) },
                    )
                    FilterChip(
                        selected = !now.hearAndFeel,
                        onClick = { Player.setHearAndFeel(ctx, false) },
                        label = { Text(stringResource(R.string.player_feel_only)) },
                    )
                }

                var confirmed by remember(now.track.sourceUri) { mutableStateOf(false) }
                if (haptic != null) {
                    if (confirmed) {
                        Text(
                            stringResource(R.string.player_using_calls),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        // The design's two side-by-side actions. Tune routes to
                        // the tune section, where the dials live in this build.
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.S2)) {
                            if (onTune != null) {
                                Secondary(stringResource(R.string.tune_title), Modifier.weight(1f)) { onTune() }
                            }
                            Secondary(
                                stringResource(R.string.player_use_calls),
                                Modifier.weight(1f),
                            ) {
                                Player.useForCalls(ctx)
                                confirmed = true
                            }
                        }
                    }
                }
            }
        }

        now.error?.let { message ->
            Text(
                message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/**
 * The small player that rides above the tab bar while a song plays. Tapping
 * it opens the full screen; the song keeps playing either way.
 */
@Composable
fun MiniPlayer() {
    val ctx = LocalContext.current
    val now = Player.now ?: return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { Player.open = true }
            .padding(horizontal = Space.S4, vertical = Space.S1)
            .heightIn(min = Touch.min),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.S2),
    ) {
        Text(
            now.track.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        TextButton(
            onClick = { Player.togglePause(ctx) },
            modifier = Modifier.heightIn(min = Touch.min),
        ) {
            Text(stringResource(if (now.playing) R.string.player_pause else R.string.player_play))
        }
    }
}
