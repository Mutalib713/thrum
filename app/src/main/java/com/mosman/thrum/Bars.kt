package com.mosman.thrum

import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The rhythm on the player and the mini player, in the style the user picked
 * ([BarStyle]; Pills by default). Mutalib chose these 14 on 2026-11-28 from
 * the sketch in scratch/bars-options.html; each draw function below is that
 * sketch's, ported, with the same numbers.
 *
 * What moves is the VIBRATION, not the sound: every shape is read from the
 * haptic score at the playback position ([BarMath]), so something moves only
 * while the motor runs. Paused, it holds still. With Android's "Remove
 * animations" on, it draws the moment and doesn't animate.
 *
 * The position from [Player] arrives twice a second; between those, the bars
 * count forward on the frame clock so they move smoothly, and only the canvas
 * redraws, not the screen around it.
 */
@Composable
fun Bars(
    style: BarStyle,
    score: Score?,
    positionMs: Long,
    playing: Boolean,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    description: String? = null,
) {
    val math = remember(score) { BarMath(score?.amplitudes ?: emptyList(), score?.stepMs ?: 20) }
    val palette = LocalThrumPalette.current
    val colours = remember(palette) {
        BarColours(palette.accent, palette.ink, palette.inkSoft, palette.ink2, palette.rule, palette.line, palette.surface, palette.surface2, palette.field)
    }
    val time = rememberSmoothMs(positionMs, playing && motionAllowed())
    Canvas(modifier.semantics { if (description != null) contentDescription = description }) {
        val t = time.value
        val d = BarDraw(this, math, colours, compact)
        when (style) {
            BarStyle.TAPE -> d.tape(t)
            BarStyle.PILLS -> d.pills(t)
            BarStyle.DOTS -> d.dots(t)
            BarStyle.BLOCKS -> d.blocks(t)
            BarStyle.RISE -> d.rise(t)
            BarStyle.PULSE -> d.pulse(t)
            BarStyle.MIRROR -> d.mirror(t)
            BarStyle.METER -> d.meter(t)
            BarStyle.MOON -> d.moon(t)
            BarStyle.LISTEN -> d.listen(t)
            BarStyle.CUPS -> d.cups(t)
            BarStyle.HEART -> d.heart(t)
            BarStyle.GLOW -> d.glow(t)
            BarStyle.ALBUM -> d.album(t)
        }
    }
}

/** Android's "Remove animations": with the animator scale at 0, the bars draw still. */
@Composable
fun motionAllowed(): Boolean {
    val ctx = LocalContext.current
    return remember { Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f }
}

/**
 * The playback position, counted forward on the frame clock while playing.
 * A new position from the player re-anchors it; one a little behind the
 * count (the player's own reading lags) is ignored, so the bars never jump back.
 */
@Composable
private fun rememberSmoothMs(positionMs: Long, moving: Boolean): State<Long> {
    val state = remember { mutableLongStateOf(positionMs) }
    LaunchedEffect(positionMs, moving) {
        val ahead = state.longValue - positionMs
        val anchor = if (moving && ahead in 1..SMOOTH_SLACK_MS) state.longValue else positionMs
        state.longValue = anchor
        if (!moving) return@LaunchedEffect
        val start = withFrameMillis { it }
        while (true) withFrameMillis { frame -> state.longValue = anchor + (frame - start) }
    }
    return state
}

private const val SMOOTH_SLACK_MS = 400L

private class BarColours(
    val accent: Color,
    val ink: Color,
    val inkSoft: Color,
    val ink2: Color,
    val rule: Color,
    val line: Color,
    val surface: Color,
    val surface2: Color,
    val field: Color,
)

private class BarDraw(val s: DrawScope, val m: BarMath, val c: BarColours, val compact: Boolean) {
    private val w = s.size.width
    private val h = s.size.height
    private fun dp(v: Float) = with(s) { v.dp.toPx() }
    private fun Color.a(alpha: Float) = copy(alpha = (this.alpha * alpha).coerceIn(0f, 1f))

    private fun pill(x: Float, y: Float, width: Float, height: Float, colour: Color) {
        val r = min(width, height) / 2
        s.drawRoundRect(colour, Offset(x, y), Size(width, height), CornerRadius(r, r))
    }

    /** A soft glow: a radial fade from [colour] at [strength] to nothing at [radius]. */
    private fun glow(centre: Offset, radius: Float, colour: Color, strength: Float) {
        if (strength <= 0f || radius <= 0f) return
        s.drawCircle(Brush.radialGradient(listOf(colour.a(strength), Color.Transparent), centre, radius), radius, centre)
    }

    // ---- A to O: bars and lines ------------------------------------------------

    /** A, Tape: time slides past a centre line; now in yellow, what's coming faint on the right. */
    fun tape(t: Long) {
        val n = if (compact) 11 else 41
        val slice = if (compact) 120L else 60L
        val mid = (n - 1) / 2
        val gap = dp(if (compact) 2.5f else 3f)
        val bw = (w - dp(if (compact) 4f else 28f) - gap * (n - 1)) / n
        val x0 = (w - (bw * n + gap * (n - 1))) / 2
        val cy = h / 2
        val maxH = h * (if (compact) 0.9f else 0.78f)
        val minH = dp(if (compact) 3f else 4f)
        if (!compact) s.drawRect(c.rule, Offset(dp(14f), cy - 0.5f), Size(w - dp(28f), 1f))
        for (j in 0 until n) {
            val v = m.peak(t + (j - mid) * slice, slice)
            val bh = minH + v * (maxH - minH)
            val colour = when {
                j == mid -> c.accent
                j > mid -> c.ink.a(0.32f)
                else -> c.ink.a(0.55f + 0.45f * j / mid)
            }
            pill(x0 + j * (bw + gap), cy - bh / 2, bw, bh, colour)
        }
    }

    /** B, Pills: thick pills that swell together on every beat, the middle one first. */
    fun pills(t: Long) {
        val scales = if (compact) floatArrayOf(0.65f, 1f, 0.65f) else floatArrayOf(0.5f, 0.78f, 1f, 0.78f, 0.5f)
        val n = scales.size
        val mid = (n - 1) / 2
        val bw = dp(if (compact) 9f else 26f)
        val gap = dp(if (compact) 6f else 16f)
        val x0 = (w - (bw * n + gap * (n - 1))) / 2
        val cy = h / 2
        val maxH = h * (if (compact) 0.92f else 0.8f)
        for (k in 0 until n) {
            val v = m.env(t - abs(k - mid) * 45L)
            val bh = bw + v * scales[k] * (maxH - bw)
            pill(x0 + k * (bw + gap), cy - bh / 2, bw, bh, if (k == mid) c.accent else c.ink)
        }
    }

    /** C, Dots: dots at rest; a beat stretches the middle one into a pill and ripples outward. */
    fun dots(t: Long) {
        val n = if (compact) 5 else 7
        val mid = (n - 1) / 2
        val d = dp(if (compact) 7f else 12f)
        val gap = dp(if (compact) 6f else 16f)
        val x0 = (w - (d * n + gap * (n - 1))) / 2
        val cy = h / 2
        val maxH = h * (if (compact) 0.9f else 0.62f)
        for (k in 0 until n) {
            val off = abs(k - mid)
            val v = m.env(t - off * 70L)
            val bh = d + v * (1 - off * 0.12f) * (maxH - d)
            val colour = if (k == mid) c.accent else if (v > 0.25f) c.ink else c.ink2
            pill(x0 + k * (d + gap), cy - bh / 2, d, bh, colour)
        }
    }

    /** D, Blocks: a segmented meter, a tenth of a second per column, newest right, with falling caps. */
    fun blocks(t: Long) {
        val cols = if (compact) 6 else 14
        val rows = if (compact) 4 else 9
        val slice = 100L
        val gapX = dp(if (compact) 3f else 6f)
        val gapY = dp(if (compact) 2f else 4f)
        val pad = dp(if (compact) 2f else 22f)
        val bw = (w - pad * 2 - gapX * (cols - 1)) / cols
        val bh = (h - pad * 2 - gapY * (rows - 1) - if (compact) 0f else dp(8f)) / rows
        for (col in 0 until cols) {
            val at = t - (cols - 1 - col) * slice
            val v = m.peak(at, slice)
            val lit = (v * rows).toInt().let { if (v * rows - it >= 0.5f) it + 1 else it }
            var cap = 0f
            var k = 0L
            while (k <= 600) { cap = max(cap, m.amp(at - k) * (1 - k / 600f)); k += 20 }
            val capRow = max(lit, (cap * rows + 0.5f).toInt())
            val x = pad + col * (bw + gapX)
            for (r in 0 until rows) {
                val y = h - pad - (r + 1) * bh - r * gapY
                val colour = if (r < lit) (if (r == lit - 1) c.accent else c.ink) else c.line.a(0.55f)
                s.drawRect(colour, Offset(x, y), Size(bw, bh))
            }
            if (capRow in 1 until rows) {
                val y = h - pad - (capRow + 1) * bh - capRow * gapY
                s.drawRect(c.accent, Offset(x, y + bh - dp(3f)), Size(bw, dp(3f)))
            }
        }
    }

    /** F, Rise: bars on a floor; a beat rises in the middle and spreads outward as it ages. */
    fun rise(t: Long) {
        val n = if (compact) 9 else 21
        val mid = (n - 1) / 2
        val slice = if (compact) 70L else 50L
        val gap = dp(if (compact) 3f else 5f)
        val pad = dp(if (compact) 2f else 22f)
        val bw = (w - pad * 2 - gap * (n - 1)) / n
        val floor = h - dp(if (compact) 3f else 24f)
        val maxH = floor - dp(if (compact) 3f else 18f)
        val minH = dp(if (compact) 3f else 5f)
        if (!compact) s.drawRect(c.rule, Offset(pad, floor + dp(6f)), Size(w - pad * 2, 1f))
        val r = min(bw / 2, dp(4f))
        for (k in 0 until n) {
            val off = abs(k - mid)
            val v = m.peak(t - off * slice, slice)
            val bh = minH + v * (1 - off / (n * 0.9f)) * (maxH - minH)
            val x = pad + k * (bw + gap)
            val path = Path().apply {
                addRoundRect(RoundRect(x, floor - bh, x + bw, floor, CornerRadius(r), CornerRadius(r), CornerRadius.Zero, CornerRadius.Zero))
            }
            s.drawPath(path, if (k == mid) c.accent else c.ink)
        }
    }

    /** G, Pulse: a heartbeat trace drawn from the right edge, a spike for every beat. */
    fun pulse(t: Long) {
        val pad = dp(if (compact) 2f else 18f)
        val span = if (compact) 1600f else 2400f
        val cy = h / 2
        val amp = h * (if (compact) 0.44f else 0.38f)
        if (!compact) s.drawRect(c.rule, Offset(pad, cy - 0.5f), Size(w - pad * 2, 1f))
        val path = Path()
        var lastY = cy
        var x = pad
        val stepX = dp(if (compact) 1f else 2f)
        while (x <= w - pad) {
            val at = t - ((w - pad - x) / (w - pad * 2) * span).toLong()
            val v = m.amp(at)
            val odd = Math.floorMod(at / 20, 2L) == 1L
            lastY = cy - v * amp * (if (odd) -0.55f else 1f)
            if (x == pad) path.moveTo(x, lastY) else path.lineTo(x, lastY)
            x += stepX
        }
        val brush = Brush.horizontalGradient(0f to c.ink.a(0f), 0.6f to c.ink.a(0.85f), 1f to c.accent, startX = pad, endX = w - pad)
        s.drawPath(path, brush, style = Stroke(width = dp(if (compact) 1.6f else 2.4f)))
        val head = Offset(w - pad, lastY)
        glow(head, dp(if (compact) 6f else 14f), c.accent, 0.5f)
        s.drawCircle(c.accent, dp(if (compact) 2.5f else 4.5f), head)
    }

    /** N, Mirror: a dense mirrored waveform, the last two and a half seconds, now at the right edge. */
    fun mirror(t: Long) {
        val pad = dp(if (compact) 2f else 14f)
        val span = if (compact) 1600f else 2500f
        val cy = h / 2
        val amp = h * (if (compact) 0.45f else 0.4f)
        val dx = dp(if (compact) 1f else 2f)
        val tops = ArrayList<Pair<Float, Float>>()
        var x = pad
        val slice = max(20L, (span / (w - pad * 2) * dx).toLong())
        while (x <= w - pad) {
            val at = t - ((w - pad - x) / (w - pad * 2) * span).toLong()
            tops += x to max(0.6f, m.peak(at, slice) * amp)
            x += dx
        }
        val path = Path().apply {
            moveTo(pad, cy)
            for ((px, a) in tops) lineTo(px, cy - a)
            for (i in tops.indices.reversed()) lineTo(tops[i].first, cy + tops[i].second)
            close()
        }
        s.drawPath(path, Brush.horizontalGradient(0f to c.ink.a(0.08f), 0.85f to c.ink.a(0.85f), 1f to c.accent, startX = pad, endX = w - pad))
    }

    /** O, Meter: one level bar with a tick that marks the last strong hit and falls back. */
    fun meter(t: Long) {
        val pad = dp(if (compact) 3f else 26f)
        val cy = h / 2
        val th = dp(if (compact) 8f else 18f)
        val full = w - pad * 2
        val e = m.env(t)
        var tick = 0f
        var k = 0L
        while (k <= 1200) { tick = max(tick, m.amp(t - k) * (1 - k / 1200f)); k += 20 }
        if (!compact) for (i in 0..20) {
            s.drawRect(c.ink2, Offset(pad + i * full / 20, cy + th / 2 + dp(10f)), Size(1f, dp(if (i % 5 == 0) 8f else 4f)))
        }
        s.drawRoundRect(c.line.a(0.6f), Offset(pad, cy - th / 2), Size(full, th), CornerRadius(th / 2))
        if (e > 0.01f) {
            val brush = Brush.horizontalGradient(0f to c.ink, 0.75f to c.ink, 1f to c.accent, startX = pad, endX = pad + full)
            s.drawRoundRect(brush, Offset(pad, cy - th / 2), Size(max(th, e * full), th), CornerRadius(th / 2))
        }
        if (tick > 0.02f) {
            val over = dp(if (compact) 3f else 8f)
            s.drawRect(c.accent, Offset(pad + tick * full - dp(1.5f), cy - th / 2 - over), Size(dp(3f), th + over * 2))
        }
    }

    // ---- P: the moon in headphones ---------------------------------------------

    /**
     * P, Moon: proportions measured off the pin in Mutalib's 27 Nov recording,
     * in moon radii (R); drawn fresh, not traced. The earcup glows with the
     * motor, each beat sends a sound arc out, strong beats release a note, and
     * the moon nods up to about 3 degrees.
     */
    fun moon(t: Long) {
        val r = if (compact) h * 0.34f else min(w, h) * 0.3f
        val cx = if (compact) w * 0.42f else w * 0.49f
        val cy = if (compact) h * 0.56f else h * 0.58f
        val e = m.env(t)
        if (!compact) {
            listOf(0.08f to 0.12f, 0.9f to 0.1f, 0.82f to 0.86f, 0.12f to 0.8f, 0.62f to 0.08f, 0.95f to 0.5f, 0.04f to 0.45f, 0.35f to 0.93f)
                .forEachIndexed { i, (x, y) -> s.drawCircle(c.ink.a(0.45f), dp(if (i % 3 == 0) 1.6f else 1f), Offset(x * w, y * h)) }
        }
        s.withTransform({ rotate(-e * 3.15f, Offset(cx, cy)) }) {
            // Headband: lands on the moon's upper-left edge, over the top, down to the slider.
            val band = Path().apply {
                moveTo(cx - 0.52f * r, cy - 0.92f * r)
                cubicTo(cx - 0.5f * r, cy - 1.8f * r, cx + 0.8f * r, cy - 1.92f * r, cx + 0.86f * r, cy - 0.95f * r)
            }
            drawPath(band, c.ink2, style = Stroke(r * 0.19f, cap = StrokeCap.Round))
            val shine = Path().apply {
                moveTo(cx - 0.45f * r, cy - 1.0f * r)
                cubicTo(cx - 0.43f * r, cy - 1.7f * r, cx + 0.72f * r, cy - 1.82f * r, cx + 0.79f * r, cy - 1.02f * r)
            }
            drawPath(shine, c.ink.a(0.55f), style = Stroke(r * 0.04f, cap = StrokeCap.Round))
            drawRoundRect(c.ink2, Offset(cx - 0.68f * r, cy - 0.98f * r), Size(0.3f * r, 0.24f * r), CornerRadius(0.06f * r))
            drawRoundRect(c.ink2, Offset(cx + 0.72f * r, cy - 1.02f * r), Size(0.3f * r, 0.34f * r), CornerRadius(0.06f * r))
            // The moon: a halo, lit from the upper left, darker towards the right.
            glow(Offset(cx, cy), r * 1.3f, c.ink, if (compact) 0.25f else 0.35f)
            drawCircle(Brush.radialGradient(0f to c.ink, 0.7f to c.inkSoft, 1f to c.ink2, center = Offset(cx - 0.35f * r, cy - 0.35f * r), radius = r * 1.35f), r, Offset(cx, cy))
            drawCircle(Brush.horizontalGradient(0.45f to c.field.a(0f), 1f to c.field.a(0.38f), startX = cx - r, endX = cx + r), r, Offset(cx, cy))
            if (!compact) {
                listOf(Triple(0.12f, -0.5f, 0.17f), Triple(-0.22f, 0.5f, 0.13f), Triple(0.3f, 0.32f, 0.1f), Triple(-0.55f, 0.2f, 0.07f), Triple(-0.3f, -0.62f, 0.06f))
                    .forEach { (x, y, cr) -> drawCircle(c.ink2.a(0.35f), cr * r, Offset(cx + x * r, cy + y * r)) }
                // Face on the left half: two closed eyes and a small smile.
                val face = Stroke(r * 0.05f, cap = StrokeCap.Round)
                listOf(-0.55f to -0.12f, -0.06f to -0.2f).forEach { (x, y) ->
                    drawArc(c.field, 27f, 126f, false, Offset(cx + (x - 0.09f) * r, cy + (y - 0.09f) * r), Size(0.18f * r, 0.18f * r), style = face)
                }
                drawArc(c.field, 36f, 108f, false, Offset(cx - 0.38f * r, cy + 0.0f * r), Size(0.16f * r, 0.16f * r), style = face)
                // Sound arcs: each beat sends one out of the earcup, growing and fading over 0.7 s.
                for (st in m.onsets(t - 700, t)) {
                    val age = (t - st) / 700f
                    val ex = cx + 0.8f * r
                    val ey = cy + 0.04f * r
                    val rx = (0.66f + age * 0.45f) * r
                    val ry = (0.7f + age * 0.45f) * r
                    rotate(-8.6f, Offset(ex, ey)) {
                        drawArc(c.ink.a(0.85f * (1 - age)), -31.5f, 63f, false, Offset(ex - rx, ey - ry), Size(rx * 2, ry * 2), style = Stroke(r * 0.03f))
                    }
                }
            }
            // Earcup: dark outer ring, the inner disc that glows with the motor, the speaker.
            rotate(-8.6f, Offset(cx + 0.76f * r, cy + 0.02f * r)) {
                val o = Offset(cx + 0.76f * r, cy + 0.02f * r)
                drawOval(c.surface2, Offset(o.x - 0.6f * r, o.y - 0.63f * r), Size(1.2f * r, 1.26f * r))
                drawOval(c.line, Offset(o.x - 0.6f * r, o.y - 0.63f * r), Size(1.2f * r, 1.26f * r), style = Stroke(r * 0.03f))
            }
            val disc = Offset(cx + 0.9f * r, cy + 0.07f * r)
            glow(disc, r * (0.55f + e * 0.6f), c.accent, 0.25f + e * 0.55f)
            rotate(-8.6f, disc) {
                drawOval(c.accent.a(0.45f + 0.55f * e), Offset(disc.x - 0.33f * r, disc.y - 0.52f * r), Size(0.66f * r, 1.04f * r))
                val sp = Offset(cx + 0.95f * r, cy + 0.07f * r)
                drawOval(c.surface, Offset(sp.x - 0.15f * r, sp.y - 0.22f * r), Size(0.3f * r, 0.44f * r))
                drawOval(c.line, Offset(sp.x - 0.15f * r, sp.y - 0.22f * r), Size(0.3f * r, 0.44f * r), style = Stroke(r * 0.03f))
            }
        }
        if (compact) return
        // Notes: a strong beat with no other strong beat in the 0.6 s before it.
        val life = 2400L
        for (st in m.onsets(t - life, t)) {
            if (m.amp(st) < 0.8f || m.onsets(st - 580, st - 20).any { m.amp(it) >= 0.8f }) continue
            val age = (t - st).toFloat() / life
            val lane = BarMath.lane(st, 20)
            val x = cx + (-0.92f - age * 0.25f - lane * 0.2f) * r
            val y = cy + (-1.0f - age * 0.4f - lane * 0.16f) * r
            note(x, y, r * 0.42f, lane == 1, c.ink.a(min(1f, age * 8) * min(1f, (1 - age) / 0.4f)))
        }
    }

    /** A music note as shapes (no font glyph): one note, or two joined by a beam. */
    private fun note(x: Float, y: Float, size: Float, beamed: Boolean, colour: Color) {
        val hw = size * 0.42f
        val hh = size * 0.3f
        val stem = size * 0.08f
        fun head(hx: Float) {
            s.drawOval(colour, Offset(hx - hw / 2, y - hh / 2), Size(hw, hh))
            s.drawRect(colour, Offset(hx + hw * 0.5f - stem, y - size), Size(stem, size))
        }
        if (beamed) {
            head(x - size * 0.3f); head(x + size * 0.3f)
            s.drawRect(colour, Offset(x - size * 0.3f + hw * 0.5f - stem, y - size), Size(size * 0.6f + stem, stem * 1.6f))
        } else {
            head(x)
            val flag = Path().apply {
                moveTo(x + hw * 0.5f, y - size)
                quadraticBezierTo(x + hw * 0.5f + size * 0.35f, y - size * 0.7f, x + hw * 0.5f + size * 0.15f, y - size * 0.45f)
            }
            s.drawPath(flag, colour, style = Stroke(stem, cap = StrokeCap.Round))
        }
    }

    // ---- Q to W: headphones -------------------------------------------------

    /**
     * Headphones, shared by Q to W. [r] is the headband's radius; the cups hang at
     * ±r, proportions from the "listen" pin (cup 0.82 r tall, 0.34 r wide).
     */
    private fun headphones(cx: Float, cy: Float, r: Float, outline: Boolean = false, cupGlow: Float = 0f) {
        s.drawArc(c.ink, 180f, 180f, false, Offset(cx - r, cy - r), Size(r * 2, r * 2), style = Stroke(r * (if (outline) 0.07f else 0.16f), cap = StrokeCap.Round))
        val cw = r * 0.34f
        val ch = r * 0.82f
        for (side in intArrayOf(-1, 1)) {
            val x = cx + side * r - cw / 2
            val y = cy - r * 0.06f
            if (outline) {
                s.drawRoundRect(c.ink, Offset(x, y), Size(cw, ch), CornerRadius(cw * 0.45f), style = Stroke(r * 0.07f))
            } else {
                glow(Offset(x + cw / 2, y + ch / 2), r * 0.6f, c.accent, cupGlow * 0.6f)
                s.drawRoundRect(c.ink, Offset(x, y), Size(cw, ch), CornerRadius(cw * 0.45f))
                s.drawRoundRect(c.ink2.a(0.6f), Offset(x + if (side < 0) cw * 0.55f else 0f, y + ch * 0.15f), Size(cw * 0.45f, ch * 0.7f), CornerRadius(cw * 0.2f))
            }
        }
    }

    /** Notes that pop out beside the cups on strong beats, one side then the other. */
    private fun cupNotes(cx: Float, cy: Float, r: Float, t: Long, size: Float) {
        val life = 1300L
        for (st in m.onsets(t - life, t)) {
            if (m.amp(st) < 0.6f) continue
            val age = (t - st).toFloat() / life
            val side = if ((st / 20) % 2 == 1L) 1 else -1
            val lane = BarMath.lane(st, 20)
            val alpha = min(1f, age * 8) * min(1f, (1 - age) / 0.45f)
            note(cx + side * (r * 1.42f + age * r * 0.45f), cy - r * (0.05f + lane * 0.18f + age * 0.4f), size, lane == 1, c.ink.a(alpha))
        }
    }

    /** Q, Listen: chunky headphones, notes out of both cups, a wave strip right under them. */
    fun listen(t: Long) {
        val e = m.env(t)
        if (compact) { headphones(w / 2, h * 0.5f, h * 0.36f, cupGlow = e); return }
        val r = h * 0.2f
        val cx = w / 2
        val cy = h * 0.36f
        headphones(cx, cy, r, cupGlow = e * 0.6f)
        cupNotes(cx, cy + r * 0.3f, r, t, r * 0.42f)
        val n = 64
        val x0 = w * 0.14f
        val bw = (w * 0.72f) / n
        val by = cy + r * 2.15f
        val mid = n / 2
        for (k in 0 until n) {
            val v = m.peak(t + (k - mid) * 40L, 40)
            val bh = dp(2f) + v * h * 0.12f
            val colour = if (k == mid) c.accent else c.ink.a(if (k < mid) 0.9f else 0.45f)
            s.drawRect(colour, Offset(x0 + k * bw, by - bh / 2), Size(max(1f, bw * 0.55f), bh))
        }
    }

    /** R, Cups: bars bounce between the cups, a spark on the band flickers on strong hits. */
    fun cups(t: Long) {
        val r = if (compact) h * 0.36f else h * 0.22f
        val cx = w / 2
        val cy = if (compact) h * 0.48f else h * 0.42f
        headphones(cx, cy, r)
        val n = if (compact) 3 else 7
        val mid = (n - 1) / 2
        val bw = r * (if (compact) 0.16f else 0.09f)
        val gap = r * (if (compact) 0.1f else 0.05f)
        val x0 = cx - (n * bw + (n - 1) * gap) / 2
        val ccy = cy + r * 0.35f
        for (k in 0 until n) {
            val v = m.peak(t - abs(k - mid) * 60L, 60)
            val bh = bw + v * r * (0.75f - abs(k - mid) * 0.08f)
            pill(x0 + k * (bw + gap), ccy - bh / 2, bw, bh, if (k == mid) c.accent else c.ink)
        }
        if (compact) return
        var hit = 0f
        var k = 0L
        while (k <= 300) { if (m.amp(t - k) >= 0.8f) hit = max(hit, 1 - k / 300f); k += 20 }
        if (hit > 0f) {
            val sx = cx + r * 0.74f
            val sy = cy - r * 0.78f
            val sr = r * 0.16f * (0.5f + hit)
            glow(Offset(sx, sy), sr * 1.8f, c.accent, 0.5f * hit)
            val star = Path().apply {
                for (i in 0 until 8) {
                    val a = i * PI.toFloat() / 4
                    val rr = if (i % 2 == 1) sr * 0.3f else sr
                    if (i == 0) moveTo(sx + cos(a) * rr, sy + sin(a) * rr) else lineTo(sx + cos(a) * rr, sy + sin(a) * rr)
                }
                close()
            }
            s.drawPath(star, c.accent)
        }
        // Where the song is: a thin line under it, as on the pin.
        val py = h * 0.84f
        val px0 = w * 0.2f
        val full = w * 0.6f
        val prog = if (m.durationMs > 0) (t.toFloat() / m.durationMs).coerceIn(0f, 1f) else 0f
        s.drawRect(c.rule, Offset(px0, py), Size(full, dp(2f)))
        s.drawRect(c.ink, Offset(px0, py), Size(full * prog, dp(2f)))
        s.drawCircle(c.ink, dp(4f), Offset(px0 + full * prog, py + dp(1f)))
    }

    /** S, Heart: a glowing heart between the cups beats with the motor. */
    fun heart(t: Long) {
        val e = m.env(t)
        val r = if (compact) h * 0.36f else h * 0.22f
        val cx = w / 2
        val cy = if (compact) h * 0.46f else h * 0.42f
        headphones(cx, cy, r)
        val k = r * 0.5f * (1 + e * 0.22f) / 16f
        val hx = cx
        val hy = cy + r * 0.32f
        val heart = Path().apply {
            moveTo(hx, hy + 6 * k)
            cubicTo(hx - 14 * k, hy - 4 * k, hx - 8 * k, hy - 16 * k, hx, hy - 8 * k)
            cubicTo(hx + 8 * k, hy - 16 * k, hx + 14 * k, hy - 4 * k, hx, hy + 6 * k)
            close()
        }
        val width = r * (if (compact) 0.09f else 0.07f) * (1 + e * 0.5f)
        s.drawPath(heart, c.accent.a(0.25f * e), style = Stroke(width * 3.5f, cap = StrokeCap.Round))
        s.drawPath(heart, c.accent, style = Stroke(width, cap = StrokeCap.Round))
        if (!compact) cupNotes(cx, cy + r * 0.3f, r, t, r * 0.36f)
    }

    /** T, Glow: thin outline headphones over a glowing line that stretches on each beat. */
    fun glow(t: Long) {
        val e = m.env(t)
        val r = if (compact) h * 0.32f else h * 0.22f
        val cx = w / 2
        val cy = if (compact) h * 0.4f else h * 0.36f
        headphones(cx, cy, r, outline = true)
        val len = r * (0.3f + 1.9f * e)
        val ly = cy + r * (if (compact) 1.05f else 1.45f)
        val thick = dp(if (compact) 1.5f else 2.5f)
        val brush = Brush.horizontalGradient(0f to c.ink.a(0f), 0.5f to c.ink, 1f to c.ink.a(0f), startX = cx - len, endX = cx + len)
        s.drawRect(Brush.horizontalGradient(0f to c.ink.a(0f), 0.5f to c.ink.a(0.25f + e * 0.25f), 1f to c.ink.a(0f), startX = cx - len, endX = cx + len),
            Offset(cx - len, ly - thick * 2), Size(len * 2, thick * 5))
        s.drawRect(brush, Offset(cx - len, ly - thick / 2), Size(len * 2, thick))
    }

    /** W, Album: headphones round an album square that bumps on each beat; a wave progress line below. */
    fun album(t: Long) {
        val e = m.env(t)
        val r = if (compact) h * 0.36f else h * 0.2f
        val cx = w / 2
        val cy = if (compact) h * 0.42f else h * 0.34f
        val sq = r * 1.05f * (1 + e * 0.08f)
        val scy = cy + r * 0.32f
        s.drawRoundRect(c.surface2, Offset(cx - sq / 2, scy - sq / 2), Size(sq, sq), CornerRadius(r * 0.08f))
        note(cx + sq * 0.02f, scy + sq * 0.2f, sq * 0.42f, true, if (e > 0.05f) c.accent else c.ink)
        headphones(cx, cy, r)
        if (compact) return
        val n = 70
        val x0 = w * 0.14f
        val bw = (w * 0.72f) / n
        val by = h * 0.84f
        val prog = if (m.durationMs > 0) (t.toFloat() / m.durationMs).coerceIn(0f, 1f) else 0f
        for (k in 0 until n) {
            val v = m.peak(t + ((k - n * prog) * 40).toLong(), 40)
            val bh = dp(1.5f) + v * h * 0.07f
            s.drawRect(if (k.toFloat() / n <= prog) c.ink else c.ink2.a(0.5f), Offset(x0 + k * bw, by - bh), Size(max(1f, bw * 0.55f), bh))
        }
    }
}
