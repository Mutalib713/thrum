package com.mosman.thrum

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

/**
 * The onboarding's background: a sheet of small yellow dots seen in
 * perspective, low on the left, climbing a steep face in the middle to a raised
 * shelf on the right, under a black sky. Mutalib picked this look (direction C,
 * 2026-11-28) from a Pinterest picture whose owner is unknown, so it is drawn
 * here instead: Thrum owns it, and it can move.
 *
 * The shape is the sketch's (scratch/opening-c.html), in fractions of the
 * canvas, so it fits any screen. About 4,700 dots: drawn in batches of the same
 * size and brightness, never one call per dot.
 */
object DotWave {
    const val ROWS = 56
    const val COLS = 84

    /** Where every dot sits for a canvas of [w] x [h] at ripple [phase]. One float pair per dot, row by row. */
    fun positions(w: Float, h: Float, phase: Float, out: FloatArray = FloatArray(ROWS * COLS * 2)): FloatArray {
        var n = 0
        for (j in 0 until ROWS) {
            val v = j / (ROWS - 1f)
            val base = h * (0.606f + 0.326f * v.pow(1.1f))
            val stepH = h * (0.265f * (1f - v).pow(1.4f) + 0.03f)
            val centre = 0.50f + 0.06f * v
            for (i in 0 until COLS) {
                val u = i / (COLS - 1f)
                val x = w / 2f + (u - 0.5f) * w * (1f + 0.25f * v)
                val rise = rise(u, centre)
                val ripple = 0.018f * h * sin(u * 7f + v * 5f + phase) * (1f - v)
                out[n++] = x
                out[n++] = base - stepH * rise - ripple
            }
        }
        return out
    }

    /** How bright each dot is, 0 to 1: brightest on the steep face, fading at the near edge. */
    val brightness: FloatArray by lazy {
        FloatArray(ROWS * COLS).also { a ->
            for (j in 0 until ROWS) {
                val v = j / (ROWS - 1f)
                val near = if (v > 0.72f) (1f - (v - 0.72f) / 0.28f).coerceAtLeast(0f) else 1f
                for (i in 0 until COLS) {
                    val u = i / (COLS - 1f)
                    val r = rise(u, 0.50f + 0.06f * v)
                    val face = r * (1f - r) * 4f
                    a[j * COLS + i] = (0.5f + 0.45f * (1f - v * 0.5f)) * (0.5f + 0.5f * face) * near
                }
            }
        }
    }

    /** The dot's radius as a share of the canvas width: far rows small, near rows bigger. */
    fun radius(row: Int, w: Float): Float = (0.85f + 0.45f * row / (ROWS - 1f)) * w / 300f

    private fun rise(u: Float, centre: Float) = 1f / (1f + exp(-(u - centre) / 0.055f))

    /** The ripple's phase for a moment, shared so two drawings of the wave line up exactly. */
    fun phaseAt(uptimeMs: Long): Float = (uptimeMs % 600_000L) * 0.0006f

    const val BUCKETS = 8
}

/**
 * Draws the wave's dots from [xy] (see [DotWave.positions]), [alpha] times
 * their own brightness, skipping any dot [hidden] says is elsewhere (the
 * opening's dots still in flight).
 */
fun DrawScope.drawDotWave(xy: FloatArray, colour: Color, alpha: Float = 1f, hidden: ((Int) -> Boolean)? = null) {
    if (alpha <= 0f) return
    val w = size.width
    val bright = DotWave.brightness
    // Batches: rows grouped by size, dots by brightness level.
    val bands = 7
    val rowsPerBand = (DotWave.ROWS + bands - 1) / bands
    val lists = Array(bands * DotWave.BUCKETS) { ArrayList<Offset>(200) }
    for (j in 0 until DotWave.ROWS) {
        val band = j / rowsPerBand
        for (i in 0 until DotWave.COLS) {
            val n = j * DotWave.COLS + i
            if (hidden != null && hidden(n)) continue
            val b = bright[n]
            if (b <= 0.01f) continue
            val level = (b * DotWave.BUCKETS).toInt().coerceIn(0, DotWave.BUCKETS - 1)
            lists[band * DotWave.BUCKETS + level].add(Offset(xy[n * 2], xy[n * 2 + 1]))
        }
    }
    for (band in 0 until bands) {
        val r = DotWave.radius((band * rowsPerBand + rowsPerBand / 2).coerceAtMost(DotWave.ROWS - 1), w)
        for (level in 0 until DotWave.BUCKETS) {
            val points = lists[band * DotWave.BUCKETS + level]
            if (points.isEmpty()) continue
            val a = ((level + 0.5f) / DotWave.BUCKETS * alpha).coerceIn(0f, 1f)
            drawPoints(points, PointMode.Points, colour.copy(alpha = a), strokeWidth = r * 2f, cap = StrokeCap.Round)
        }
    }
}

/** The black sky above the wave, fading out where the dots begin to thin. */
fun DrawScope.drawWaveSky(alpha: Float = 1f, height: Float = size.height) {
    if (alpha <= 0f) return
    drawRect(
        Brush.verticalGradient(
            0f to Color.Black.copy(alpha = alpha),
            0.70f to Color.Black.copy(alpha = alpha),
            1f to Color.Transparent,
            endY = height,
        ),
        size = androidx.compose.ui.geometry.Size(size.width, height),
    )
}

/**
 * The wave as a background, rippling slowly while motion is allowed. [alpha]
 * lets the opening hide it while its own dots are still flying in.
 */
@Composable
fun DotWaveBackground(modifier: Modifier = Modifier, alpha: Float = 1f) {
    val moving = motionAllowed()
    var now by remember { mutableLongStateOf(SystemClock.uptimeMillis()) }
    LaunchedEffect(moving) {
        if (!moving) return@LaunchedEffect
        while (true) withFrameMillis { now = SystemClock.uptimeMillis() }
    }
    val palette = LocalThrumPalette.current
    val dark = palette.field.luminance() < 0.5f
    // On the light theme there is no black sky, and the dots take the darker yellow that reads on white.
    val colour = if (dark) palette.accent else palette.accentInk
    val buffer = remember { FloatArray(DotWave.ROWS * DotWave.COLS * 2) }
    Canvas(modifier) {
        if (dark) drawWaveSky(alpha)
        val phase = if (moving) DotWave.phaseAt(now) else 0f
        drawDotWave(DotWave.positions(size.width, size.height, phase, buffer), colour, alpha)
    }
}
