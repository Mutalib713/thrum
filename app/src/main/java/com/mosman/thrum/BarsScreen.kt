package com.mosman.thrum

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Bars: pick how the rhythm moves on the player and the mini player
 * (Mutalib, 2026-11-28: from Settings, with a shortcut on the player). Every
 * style plays live here, so the choice is made by watching, not by name.
 *
 * The previews play the song that's playing, else the song calls vibrate to,
 * else Thrum's built-in sample rhythm, and the screen says when it's the
 * sample, so it's never mistaken for the user's own song.
 */
@Composable
fun BarsScreen(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val store = remember { Store(ctx) }
    var chosen by remember { mutableStateOf(store.barStyle) }
    BackHandler(onBack = onClose)

    val nowScore = Player.haptic?.score
    val callScore = remember { store.armedScore }
    val sample = remember { Demo.rhythm() }
    val score = nowScore ?: callScore ?: sample
    val isSample = nowScore == null && callScore == null

    // The previews' own clock, looping the rhythm so they never run out.
    val startMs = remember { if (nowScore != null) Player.now?.positionMs ?: 0L else 0L }
    val loopMs by produceState(startMs, score) {
        val duration = (score.amplitudes.size.toLong() * score.stepMs).coerceAtLeast(1L)
        while (true) {
            delay(CLOCK_MS)
            value = (value + CLOCK_MS) % duration
        }
    }

    ThrumPage {
        ThrumTopBar(title = stringResource(R.string.bars_title), onBack = onClose)
        Text(
            stringResource(if (isSample) R.string.bars_sub_sample else R.string.bars_sub),
            style = ThrumType.body,
            color = ThrumInk2,
            modifier = Modifier.padding(bottom = Space.S4, start = Space.S1, end = Space.S1),
        )
        BarStyle.entries.chunked(2).forEach { pair ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = Space.S3),
                horizontalArrangement = Arrangement.spacedBy(Space.S3),
            ) {
                pair.forEach { style ->
                    val selected = style == chosen
                    val name = stringResource(style.nameRes())
                    ThrumCard(
                        modifier = Modifier
                            .weight(1f)
                            .semantics {
                                this.selected = selected
                                role = Role.RadioButton
                            },
                        borderColor = if (selected) ThrumAccent else ThrumRule,
                        padding = PaddingValues(Space.S3),
                        onClick = {
                            chosen = style
                            store.barStyle = style
                        },
                    ) {
                        Box(modifier = Modifier.fillMaxWidth().height(110.dp)) {
                            Bars(
                                style = style,
                                score = score,
                                positionMs = loopMs,
                                playing = true,
                                modifier = Modifier.fillMaxWidth().height(110.dp),
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = Space.S2),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(name, style = ThrumType.row, color = ThrumInk, modifier = Modifier.weight(1f))
                            if (selected) ThrumIcon("check", tint = ThrumAccentInk, size = 18.dp)
                        }
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** Each style's name, as the picker, Settings and the player say it. */
@StringRes
fun BarStyle.nameRes(): Int = when (this) {
    BarStyle.TAPE -> R.string.bar_tape
    BarStyle.PILLS -> R.string.bar_pills
    BarStyle.DOTS -> R.string.bar_dots
    BarStyle.BLOCKS -> R.string.bar_blocks
    BarStyle.RISE -> R.string.bar_rise
    BarStyle.PULSE -> R.string.bar_pulse
    BarStyle.MIRROR -> R.string.bar_mirror
    BarStyle.METER -> R.string.bar_meter
    BarStyle.MOON -> R.string.bar_moon
    BarStyle.LISTEN -> R.string.bar_listen
    BarStyle.CUPS -> R.string.bar_cups
    BarStyle.HEART -> R.string.bar_heart
    BarStyle.GLOW -> R.string.bar_glow
    BarStyle.ALBUM -> R.string.bar_album
}

private const val CLOCK_MS = 250L
