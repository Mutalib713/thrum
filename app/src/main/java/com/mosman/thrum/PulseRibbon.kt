package com.mosman.thrum

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The score, drawn. Thrum's signature visual element.
 *
 * Configurable to draw:
 * - Real live score waveforms
 * - Mirrored raw audio waveforms
 * - Preset pattern ribbons (buzz, afro, heart, pulse, energy)
 * - Mini ribbons in list rows
 * - Progress playheads
 */
@Composable
fun PulseRibbon(
    score: Score? = null,
    amplitudes: List<Int>? = null,
    pattern: String? = null,
    limit: Int? = null,
    progress: Float = -1f,
    fill: Float = 1f,
    mirror: Boolean = false,
    glow: Boolean = false,
    height: Dp = Ribbon.height,
    barWidth: Dp = Ribbon.barWidth,
    barGap: Dp = Ribbon.barGap,
    modifier: Modifier = Modifier,
    barColour: Color = Color(0xFFD8C513),
    silentColour: Color = Color(0xFF3A3A36),
    playedColour: Color = Color(0xFFEFEFEA),
) {
    val rawAmps = remember(score, amplitudes, pattern, limit) {
        val base = when {
            amplitudes != null -> amplitudes
            score != null -> score.amplitudes
            pattern != null -> ribbonPattern(pattern)
            else -> emptyList()
        }
        if (limit != null && limit < base.size) base.subList(0, limit) else base
    }

    val label = when {
        score != null -> "The rhythm: ${score.pulseCount()} hits over ${score.durationMs / 1000} seconds"
        pattern != null -> "Rhythm pattern $pattern"
        else -> "Waveform ribbon"
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .semantics { contentDescription = label },
    ) {
        val barPx = barWidth.toPx()
        val gapPx = barGap.toPx()
        val slot = barPx + gapPx
        val columns = (size.width / slot).toInt().coerceAtLeast(1)
        val h = size.height

        if (glow) {
            drawIntoCanvas { canvas ->
                val nativePaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.parseColor("#D8C513")
                    setShadowLayer(24f, 0f, 0f, android.graphics.Color.parseColor("#D8C513"))
                }
            }
        }

        if (rawAmps.isEmpty()) {
            val y = if (mirror) (h - 2f) / 2f else h - 2f
            for (c in 0 until columns) {
                drawRect(
                    color = silentColour,
                    topLeft = Offset(c * slot, y),
                    size = Size(barPx, 2f),
                )
            }
            return@Canvas
        }

        val peakValues = peaks(rawAmps, columns)
        val playedUpTo = if (progress < 0f) -1 else (progress * columns).toInt()

        for (c in 0 until columns) {
            val p = peakValues.getOrElse(c) { 0 }
            val drawn = if (c.toFloat() / columns <= fill) p else 0
            val maxH = if (mirror) h * 0.9f else h
            val bh = ((maxH * drawn) / 255f).coerceAtLeast(2f)
            val topY = if (mirror) (h - bh) / 2f else h - bh

            val color = when {
                mirror -> Color(0xFF6E6E68)
                drawn == 0 -> silentColour
                progress >= 0f && c <= playedUpTo -> playedColour
                else -> barColour
            }

            drawRect(
                color = color,
                topLeft = Offset(c * slot, topY),
                size = Size(barPx, bh),
            )
        }

        if (progress in 0f..1f) {
            val x = (progress * columns * slot).coerceIn(0f, size.width - 1.5f)
            drawRect(
                color = playedColour,
                topLeft = Offset(x, 0f),
                size = Size(1.5.dp.toPx(), h),
            )
        }
    }
}

/**
 * Downsamples amplitudes into [n] columns taking the peak value in each bucket.
 */
private fun peaks(amps: List<Int>, n: Int): List<Int> {
    if (amps.isEmpty() || n <= 0) return emptyList()
    val out = ArrayList<Int>(n)
    val per = amps.size.toFloat() / n
    for (c in 0 until n) {
        val start = (c * per).toInt().coerceIn(0, amps.size - 1)
        val end = ((c + 1) * per).toInt().coerceIn(start + 1, amps.size)
        var p = 0
        for (i in start until end) {
            if (amps[i] > p) p = amps[i]
        }
        out.add(p)
    }
    return out
}

/**
 * Designed shapes from `thrum-screens.html` for stand-ins and Thrum Originals.
 */
fun ribbonPattern(name: String): List<Int> {
    val a = ArrayList<Int>()
    fun push(v: Int, n: Int) {
        for (i in 0 until n) a.add(v)
    }

    when (name) {
        "buzz" -> {
            for (k in 0 until 3) {
                push(255, 50)
                push(0, 50)
            }
        }
        "heart" -> {
            for (k in 0 until 12) {
                push(255, 5)
                push(0, 5)
                push(190, 4)
                push(0, 36)
            }
        }
        "pulse" -> {
            for (k in 0 until 24) {
                push(255, 6)
                push(0, 19)
            }
        }
        "energy" -> {
            for (k in 0 until 30) {
                push(255, 4)
                push(0, 6)
                push(if (k % 2 == 1) 230 else 160, 3)
                push(0, 7)
            }
        }
        "afro" -> {
            val kick = setOf(0, 3, 6, 10)
            val snare = setOf(4, 12)
            for (bar in 0 until 4) {
                for (s in 0 until 16) {
                    when {
                        kick.contains(s) -> {
                            push(255, 5)
                            push(0, 3)
                        }
                        snare.contains(s) -> {
                            push(220, 4)
                            push(0, 4)
                        }
                        s % 2 == 0 -> {
                            push(150, 2)
                            push(0, 6)
                        }
                        else -> push(0, 8)
                    }
                }
            }
        }
        else -> {
            for (i in 0 until 300) a.add(0)
        }
    }
    return a
}
