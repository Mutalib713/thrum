package com.mosman.thrum

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Bars: pick how the rhythm moves across the app (Mutalib, 2026-11-28: from
 * Settings, with a shortcut on the player). Every style plays live here, at
 * close to the player's own size, so it's judged as it will really look.
 *
 * A tap asks first (Mutalib: "there should be a prompt that asks the user if
 * they want to change"); "Use" changes it everywhere at once through
 * [BarChoice]: the player, the mini player, Home's ringtone card and the song
 * lists.
 *
 * The previews play the song that's playing, else the song calls vibrate to,
 * else Thrum's built-in sample rhythm, and the screen says when it's the
 * sample, so it's never mistaken for the user's own song.
 */
@Composable
fun BarsScreen(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val store = remember { Store(ctx) }
    val chosen = rememberBarStyle()
    var asking by remember { mutableStateOf<BarStyle?>(null) }
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
        BarStyle.entries.forEach { style ->
            val selected = style == chosen
            ThrumCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Space.S3)
                    .semantics {
                        this.selected = selected
                        role = Role.RadioButton
                    },
                borderColor = if (selected) ThrumAccent else ThrumRule,
                padding = PaddingValues(Space.S3),
                onClick = { if (!selected) asking = style },
            ) {
                Bars(
                    style = style,
                    score = score,
                    positionMs = loopMs,
                    playing = true,
                    modifier = Modifier.fillMaxWidth().height(PREVIEW_HEIGHT),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = Space.S2),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(style.nameRes()), style = ThrumType.row, color = ThrumInk, modifier = Modifier.weight(1f))
                    if (selected) {
                        Text(stringResource(R.string.bars_in_use), style = ThrumType.meta, color = ThrumAccentInk)
                        ThrumIcon("check", tint = ThrumAccentInk, size = 18.dp, modifier = Modifier.padding(start = Space.S1))
                    }
                }
            }
        }
    }

    asking?.let { style ->
        val name = stringResource(style.nameRes())
        ThrumBottomSheet(onDismiss = { asking = null }) {
            Text(
                stringResource(R.string.bars_confirm_title, name),
                style = ThrumType.heading,
                color = ThrumInk,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().semantics { heading() },
            )
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = Space.S4)) {
                Bars(style = style, score = score, positionMs = loopMs, playing = true, modifier = Modifier.fillMaxWidth().height(PREVIEW_HEIGHT))
            }
            Text(
                stringResource(R.string.bars_confirm_body, name),
                style = ThrumType.lead,
                color = ThrumInkSoft,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = Space.S4),
            )
            PrimaryButton(
                text = stringResource(R.string.bars_confirm_use, name),
                onClick = {
                    BarChoice.choose(ctx, style)
                    asking = null
                },
                modifier = Modifier.fillMaxWidth(),
            )
            ThrumTextButton(
                text = stringResource(R.string.bars_confirm_keep),
                onClick = { asking = null },
                color = ThrumInk2,
                modifier = Modifier.fillMaxWidth().padding(top = Space.S2),
            )
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

/** Close to the player's own (180 dp), so a style is judged at the size it plays. */
private val PREVIEW_HEIGHT = 160.dp
