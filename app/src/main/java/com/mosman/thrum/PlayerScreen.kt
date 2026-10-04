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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Player screens: 13 (making), 14 (player), 32 (low quality warning), 33 (clean up sheet).
 */
@Composable
fun PlayerScreen(
    onTune: (() -> Unit)? = null,
    onExport: (() -> Unit)? = null,
) {
    val ctx = LocalContext.current
    val now = Player.now ?: return
    val haptic = Player.haptic

    var showCleanUpSheet by remember { mutableStateOf(false) }
    var cleanUpSegment by remember { mutableStateOf(1) } // 0: Original, 1: Cleaned up
    var modeSegment by remember { mutableStateOf(if (now.hearAndFeel) 0 else 1) }

    BackHandler { Player.open = false }

    Box(modifier = Modifier.fillMaxSize().background(ThrumField)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 18.dp),
        ) {
            // Topbar: back button + export button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clickable { Player.open = false },
                    contentAlignment = Alignment.CenterStart,
                ) {
                    ThrumIcon(name = "back", tint = ThrumInk, size = 22.dp)
                }

                if (!now.preparing && onExport != null) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clickable { onExport() },
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        ThrumIcon(name = "export", tint = ThrumInk, size = 22.dp)
                    }
                }
            }

            Text(
                text = now.track.name,
                color = ThrumInk,
                fontSize = 25.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            val artistText = if (now.track.artist.isNotEmpty()) "${now.track.artist} · Music" else "Music"
            Text(
                text = artistText,
                color = ThrumInk2,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 2.dp),
            )

            when {
                // Screen 13: Making its haptic
                now.preparing -> {
                    ThrumCard(
                        modifier = Modifier.padding(top = 18.dp),
                        padding = PaddingValues(16.dp),
                    ) {
                        PulseRibbon(
                            pattern = "afro",
                            height = 180.dp,
                            barWidth = 2.dp,
                            barGap = 1.dp,
                            fill = 0.4f,
                        )
                    }

                    Row(
                        modifier = Modifier.padding(top = 22.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.5.dp,
                            color = ThrumAccent,
                        )
                        Column {
                            Text("Making its haptic", color = ThrumInk, fontSize = 16.sp)
                            Text("About 8 seconds, the first time only.", color = ThrumInk2, fontSize = 12.5.sp)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(ThrumRule),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.4f)
                                    .height(6.dp)
                                    .background(ThrumAccent),
                            )
                        }
                        Text("40%", color = ThrumInk2, fontSize = 13.5.sp)
                    }
                }

                // Screen 14 & 32: Player
                else -> {
                    // Screen 32 clean-up banner option
                    ThrumCard(
                        modifier = Modifier.padding(top = 12.dp),
                        padding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            ThrumIcon(name = "spark", tint = ThrumAccent, size = 20.dp)
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Sound quality: low", color = ThrumInk, fontSize = 14.5.sp, fontWeight = FontWeight.Medium)
                                Text("This copy sounds squashed. Make it clearer?", color = ThrumInk2, fontSize = 12.sp)
                            }
                            ThrumTextButton(
                                text = "Clean it up",
                                onClick = { showCleanUpSheet = true },
                            )
                        }
                    }

                    // Main waveform card
                    ThrumCard(
                        modifier = Modifier.padding(top = 14.dp),
                        padding = PaddingValues(16.dp),
                    ) {
                        val progress = if (now.durationMs > 0) {
                            now.positionMs.toFloat() / now.durationMs
                        } else {
                            0.096f
                        }
                        PulseRibbon(
                            score = haptic?.score,
                            pattern = if (haptic == null) "afro" else null,
                            height = 180.dp,
                            barWidth = 2.dp,
                            barGap = 1.dp,
                            progress = progress,
                        )
                    }

                    // Stats row: current time, hits + still %, duration
                    val hits = haptic?.score?.pulseCount() ?: 164
                    val still = haptic?.let { ready ->
                        ready.score.amplitudes.count { it == 0 } * 100 /
                            ready.score.amplitudes.size.coerceAtLeast(1)
                    } ?: 42
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(clockOf(now.positionMs), color = ThrumInk2, fontSize = 14.sp)
                        Text("$hits hits · still $still%", color = ThrumInk2, fontSize = 14.sp)
                        Text(clockOf(now.durationMs), color = ThrumInk2, fontSize = 14.sp)
                    }

                    // Transport controls: Previous, Big Play, Next
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clickable { Player.previous(ctx) },
                            contentAlignment = Alignment.Center,
                        ) {
                            ThrumIcon(name = "prev", tint = ThrumInk, size = 24.dp)
                        }
                        Spacer(Modifier.width(28.dp))
                        BigPlayButton(
                            playing = now.playing,
                            onClick = { Player.togglePause(ctx) },
                        )
                        Spacer(Modifier.width(28.dp))
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clickable { Player.next(ctx) },
                            contentAlignment = Alignment.Center,
                        ) {
                            ThrumIcon(name = "next", tint = ThrumInk, size = 24.dp)
                        }
                    }

                    // Segmented control: Hear and feel | Feel only
                    ThrumSegmentedControl(
                        options = listOf("Hear and feel", "Feel only"),
                        selectedIndex = modeSegment,
                        onSelect = {
                            modeSegment = it
                            Player.setHearAndFeel(ctx, it == 0)
                        },
                        modifier = Modifier.padding(top = 20.dp),
                    )

                    Spacer(Modifier.height(28.dp))

                    // Bottom actions: Tune & Use for calls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SecondaryButton(
                            text = "Tune",
                            icon = "tune",
                            onClick = { onTune?.invoke() },
                            modifier = Modifier.weight(1f),
                        )
                        PrimaryButton(
                            text = "Use for calls",
                            icon = "phone",
                            onClick = { Player.useForCalls(ctx) },
                            modifier = Modifier.weight(1.4f),
                        )
                    }
                }
            }
        }

        // Screen 33: Clean up bottom sheet modal
        if (showCleanUpSheet) {
            ThrumBottomSheet(
                onDismiss = { showCleanUpSheet = false },
            ) {
                Text("Make it sound clearer?", color = ThrumInk, fontSize = 25.sp, fontWeight = FontWeight.Medium)
                Text(
                    "Thrum cleans up the squashed sound on this phone. Nothing is uploaded.",
                    color = Color(0xFFD6D6CF),
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(ThrumRule),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.4f)
                                .height(6.dp)
                                .background(ThrumAccent),
                        )
                    }
                    Text("40%", color = ThrumInk2, fontSize = 13.5.sp)
                }

                Text(
                    text = "LISTEN TO BOTH",
                    color = ThrumInk2,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.4.sp,
                    modifier = Modifier.padding(top = 18.dp, bottom = 8.dp),
                )

                ThrumSegmentedControl(
                    options = listOf("Original", "Cleaned up"),
                    selectedIndex = cleanUpSegment,
                    onSelect = { cleanUpSegment = it },
                )

                Text(
                    text = "The vibration stays almost the same either way. We measured it: squashed copies of AIZO kept about 90% of their beats in place.",
                    color = ThrumInk2,
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(top = 12.dp),
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ThrumTextButton(
                        text = "Keep original",
                        onClick = { showCleanUpSheet = false },
                        color = ThrumInk2,
                        modifier = Modifier.weight(1f),
                    )
                    PrimaryButton(
                        text = "Use cleaned up",
                        onClick = { showCleanUpSheet = false },
                        modifier = Modifier.weight(1.3f),
                    )
                }
            }
        }
    }
}

/**
 * The mini player that rides above the bottom nav bar.
 */
@Composable
fun MiniPlayer() {
    val ctx = LocalContext.current
    val now = Player.now ?: return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(ThrumSurface2)
            .border(1.dp, ThrumLine, RoundedCornerShape(16.dp))
            .clickable { Player.open = true }
            .padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.width(60.dp)) {
            val progress = if (now.durationMs > 0) now.positionMs.toFloat() / now.durationMs else 0.1f
            PulseRibbon(pattern = "afro", height = 26.dp, barWidth = 2.dp, barGap = 1.dp, progress = progress)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(now.track.name, color = ThrumInk, fontSize = 14.5.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Hear and feel · ${clockOf(now.positionMs)}", color = ThrumInk2, fontSize = 12.sp)
        }
        Box(
            modifier = Modifier
                .size(48.dp)
                .clickable { Player.togglePause(ctx) },
            contentAlignment = Alignment.Center,
        ) {
            ThrumIcon(
                name = if (now.playing) "pause" else "play",
                tint = ThrumInk,
                size = 20.dp,
            )
        }
    }
}
