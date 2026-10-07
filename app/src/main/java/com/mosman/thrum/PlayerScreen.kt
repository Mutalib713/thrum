package com.mosman.thrum

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * The player, screen 14, and each song's page (PROFILE §4 item 6): tune it,
 * use it for calls, export it. Screen 13 is its "making the haptic" moment.
 *
 * Everything on it is this song's own: its rhythm, its hits and stillness,
 * the decoder's own sentence when it cannot be read. The first UI drawn on
 * this screen filled the gaps with the design's sample numbers ("164 hits ·
 * still 42%"), told every song "Sound quality: low", and offered a clean-up
 * whose "Use cleaned up" only closed the sheet. Clean up is Task 29; until it
 * is built, it is not drawn.
 *
 * The screen is an overlay on the tabs, not a destination: closing it keeps
 * the song playing, which is what the mini player is for.
 */
@Composable
fun PlayerScreen(onTune: (TuneTarget) -> Unit, onExport: () -> Unit) {
    val ctx = LocalContext.current
    val now = Player.now
    val actions = rememberRowActions(onTune)

    BackHandler { Player.open = false }

    // Null for a moment while a song starts — the screen opens on the tap,
    // and the song's details land a frame later. Drawing nothing for that
    // frame is right; closing here (tried on 2026-10-04) shut the player on
    // every tap. Back still works, because it is handled above.
    if (now == null) return
    val haptic = Player.haptic
    // Asked again and again: the ringtone question can end either way, and
    // the user can change the ringtone in Android's settings at any time.
    val isCallSong by rememberPolled(Player.isCallSong(ctx)) { Player.isCallSong(it) }
    val isRingtone by rememberPolled(false) { Player.isCallSong(it) && Ringtone.isTheSong(it) }

    ThrumPage {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButtonBox(icon = "back", label = stringResource(R.string.player_close), onClick = { Player.open = false })
            if (haptic != null && !now.preparing) {
                IconButtonBox(icon = "export", label = stringResource(R.string.export_title), onClick = onExport)
            }
        }

        Text(
            now.track.name,
            style = ThrumType.heading,
            color = ThrumInk,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.semantics { heading() },
        )
        Row(
            modifier = Modifier.padding(top = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.S2),
        ) {
            Text(
                listOfNotNull(now.track.artist.ifEmpty { null }, MyHaptics.kindLabel(now.track.kind)).joinToString(" · "),
                style = ThrumType.body,
                color = ThrumInk2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (isCallSong) ThrumChip(text = stringResource(R.string.haptics_calls_badge), hasDot = true)
        }

        when {
            // Screen 13: the one wait the app asks for, in its own words. A
            // flat line, not a made-up rhythm: there is none yet.
            now.preparing -> {
                ThrumCard(modifier = Modifier.padding(top = 18.dp), padding = PaddingValues(Space.S4)) {
                    PulseRibbon(score = null, height = 180.dp, barWidth = 2.dp, barGap = 1.dp)
                }
                Row(
                    modifier = Modifier.padding(top = 22.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.S3),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.5.dp, color = ThrumAccentInk)
                    Column {
                        Text(stringResource(R.string.player_making_title), style = ThrumType.lead, color = ThrumInk)
                        Text(stringResource(R.string.player_making_body), style = ThrumType.meta, color = ThrumInk2)
                    }
                }
            }

            haptic != null -> {
                ThrumCard(modifier = Modifier.padding(top = 14.dp), padding = PaddingValues(Space.S4)) {
                    PulseRibbon(
                        score = haptic.score,
                        progress = if (now.durationMs > 0) now.positionMs.toFloat() / now.durationMs else -1f,
                        height = 180.dp,
                        barWidth = 2.dp,
                        barGap = 1.dp,
                    )
                }

                // Where the song is, what its haptic carries, how long it is.
                val hits = remember(haptic) { haptic.score.pulseCount() }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Space.S2),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(clockOf(now.positionMs), style = ThrumType.body, color = ThrumInk2)
                    Text(pluralStringResource(R.plurals.player_hits, hits, hits), style = ThrumType.body, color = ThrumInk2)
                    Text(clockOf(now.durationMs), style = ThrumType.body, color = ThrumInk2)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButtonBox(
                        icon = "prev",
                        label = stringResource(R.string.player_previous),
                        onClick = { Player.previous(ctx) },
                        enabled = Player.canStep(-1),
                        iconSize = 24.dp,
                    )
                    Spacer(Modifier.width(28.dp))
                    BigPlayButton(playing = now.playing, onClick = { Player.togglePause(ctx) })
                    Spacer(Modifier.width(28.dp))
                    IconButtonBox(
                        icon = "next",
                        label = stringResource(R.string.player_next),
                        onClick = { Player.next(ctx) },
                        enabled = Player.canStep(+1),
                        iconSize = 24.dp,
                    )
                }

                // Real mid-play: the drive restarts from the true position.
                // Read from the player, so a refused switch (an imported
                // haptic has no song) never leaves the wrong side lit.
                ThrumSegmentedControl(
                    options = listOf(stringResource(R.string.player_hear_feel), stringResource(R.string.player_feel_only)),
                    selectedIndex = if (now.hearAndFeel) 0 else 1,
                    onSelect = { Player.setHearAndFeel(ctx, it == 0) },
                    modifier = Modifier.padding(top = 20.dp),
                )

                Spacer(Modifier.height(28.dp))

                if (isCallSong) {
                    Text(
                        stringResource(if (isRingtone) R.string.player_using_calls else R.string.player_using_calls_vibration),
                        style = ThrumType.body,
                        color = ThrumInk2,
                        modifier = Modifier.padding(bottom = Space.S3),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Space.S2),
                ) {
                    SecondaryButton(
                        text = stringResource(R.string.player_tune),
                        icon = "tune",
                        onClick = { onTune(TuneTarget.Song(now.track, haptic)) },
                        modifier = Modifier.weight(1f),
                    )
                    if (!isRingtone) {
                        PrimaryButton(
                            text = stringResource(R.string.player_use_calls),
                            icon = "bell",
                            onClick = { actions.setAsRingtone(now.track) },
                            modifier = Modifier.weight(1.4f),
                        )
                    }
                }
            }
        }

        // The decoder's or the player's own sentence, whenever something
        // refused. The first UI collected these and never showed them.
        now.error?.let { message ->
            Text(message, style = ThrumType.lead, color = ThrumWarn, modifier = Modifier.padding(top = Space.S4))
        }
        RowActionsHost(actions)
    }
}

/**
 * The small player that rides above the tab bar while a song plays: back,
 * pause and next without opening the player (Mutalib, 2026-10-04). A tap
 * anywhere else opens it.
 */
@Composable
fun MiniPlayer() {
    val ctx = LocalContext.current
    val now = Player.now ?: return
    val haptic = Player.haptic
    val shape = RoundedCornerShape(Radius.large)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(shape)
            .background(ThrumSurface2)
            .border(1.dp, ThrumLine, shape)
            .clickable(role = Role.Button) { Player.open = true }
            .padding(start = 14.dp, end = Space.S1),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.S3),
    ) {
        Box(Modifier.width(44.dp)) {
            PulseRibbon(
                score = haptic?.score,
                progress = if (now.durationMs > 0) now.positionMs.toFloat() / now.durationMs else -1f,
                height = 26.dp,
                barWidth = 2.dp,
                barGap = 1.dp,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(now.track.name, style = ThrumType.row, color = ThrumInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                stringResource(if (now.hearAndFeel) R.string.player_hear_feel else R.string.player_feel_only) +
                    " · " + clockOf(now.positionMs),
                style = ThrumType.meta,
                color = ThrumInk2,
                maxLines = 1,
            )
        }
        // One group, no gaps: each button is already a 48 dp target.
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButtonBox(
                icon = "prev",
                label = stringResource(R.string.player_previous),
                onClick = { Player.previous(ctx) },
                enabled = Player.canStep(-1),
                iconSize = 18.dp,
            )
            IconButtonBox(
                icon = if (now.playing) "pause" else "play",
                label = stringResource(if (now.playing) R.string.player_pause else R.string.player_play),
                onClick = { Player.togglePause(ctx) },
                enabled = !now.preparing,
                iconSize = 20.dp,
            )
            IconButtonBox(
                icon = "next",
                label = stringResource(R.string.player_next),
                onClick = { Player.next(ctx) },
                enabled = Player.canStep(+1),
                iconSize = 18.dp,
            )
        }
    }
}
