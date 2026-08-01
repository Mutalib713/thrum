package com.mosman.thrum

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * The score, drawn. Thrum's signature move.
 *
 * Every other app that touches vibration asks the user to take it on faith.
 * This one shows the rhythm before it is felt — and the shape on screen is the
 * shape the motor will make, not an illustration of one. A user who can see
 * that the tall bars land on the drums can trust the thing without having to
 * hold the phone to their ear.
 *
 * Bars are square-cornered on purpose. A rounded bar reads as a decorative
 * chart; this is a machine part shown at its real proportions.
 *
 * Drawn as one `Canvas` rather than a row of composables: a four-minute track is
 * around 6,000 steps, and 6,000 layout nodes would drop frames on the phones
 * this app is aimed at. The ribbon downsamples to the pixels it actually has,
 * keeping the **loudest** step in each column — averaging would flatten the
 * transients, which are the whole point.
 *
 * @param progress 0f..1f, how far the playhead has travelled. Negative when
 *   nothing is playing, which draws no playhead at all.
 */
@Composable
fun PulseRibbon(
    score: Score?,
    progress: Float,
    modifier: Modifier = Modifier,
    barColour: Color,
    silentColour: Color,
    playedColour: Color,
) {
    val label = when {
        score == null -> "No rhythm yet"
        score.isSilent() -> "This track produced no rhythm"
        else -> "The rhythm: ${score.pulseCount()} hits over ${score.durationMs / 1000} seconds"
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(Ribbon.height)
            .semantics { contentDescription = label },
    ) {
        val barPx = Ribbon.barWidth.toPx()
        val gapPx = Ribbon.barGap.toPx()
        val slot = barPx + gapPx
        val columns = (size.width / slot).toInt().coerceAtLeast(1)
        val amplitudes = score?.amplitudes

        // A flat line is the honest empty state: it is exactly what the phone
        // gives the user today, and what this app exists to replace.
        if (amplitudes.isNullOrEmpty()) {
            val y = size.height - Ribbon.silentHeight.toPx()
            for (c in 0 until columns) {
                drawRect(
                    color = silentColour,
                    topLeft = Offset(c * slot, y),
                    size = Size(barPx, Ribbon.silentHeight.toPx()),
                )
            }
            return@Canvas
        }

        val perColumn = (amplitudes.size.toFloat() / columns).coerceAtLeast(1f)
        val playedUpTo = if (progress < 0f) -1 else (progress * columns).toInt()

        for (c in 0 until columns) {
            val from = (c * perColumn).toInt()
            val to = ((c + 1) * perColumn).toInt().coerceAtMost(amplitudes.size)
            if (from >= amplitudes.size) break

            // Loudest, not average: averaging a hit with the silence beside it
            // flattens the transient that makes a rhythm feel like one.
            var peak = 0
            for (i in from until maxOf(to, from + 1)) {
                if (i < amplitudes.size && amplitudes[i] > peak) peak = amplitudes[i]
            }

            val fraction = peak.toFloat() / Score.MAX_AMPLITUDE
            val barHeight = (size.height * fraction).coerceAtLeast(Ribbon.silentHeight.toPx())
            drawRect(
                color = when {
                    peak == 0 -> silentColour
                    c <= playedUpTo -> playedColour
                    else -> barColour
                },
                topLeft = Offset(c * slot, size.height - barHeight),
                size = Size(barPx, barHeight),
            )
        }

        // The playhead. A single hairline, because the bars are the content and
        // a heavy marker would compete with them.
        if (progress in 0f..1f) {
            val x = progress * columns * slot
            drawRect(
                color = playedColour,
                topLeft = Offset(x, 0f),
                size = Size(1.dp.toPx(), size.height),
            )
        }
    }
}
