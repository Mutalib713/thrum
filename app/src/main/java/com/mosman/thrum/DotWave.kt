package com.mosman.thrum

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * The onboarding's background: a sheet of small yellow dots seen in
 * perspective, low on the left, climbing a steep face in the middle to a raised
 * shelf on the right, under a black sky. Mutalib picked this look (direction C,
 * 2026-11-28) from a Pinterest picture whose owner is unknown, so it is drawn
 * here instead, and Thrum owns it.
 *
 * The shape is the sketch's (scratch/opening-c.html), in fractions of the
 * canvas, so it fits any screen. About 4,700 dots.
 *
 * **Fast on purpose.** Drawn dot by dot every frame, the opening made 8 to 16%
 * of its frames late on the Pixel (measured 2026-11-28: about 9 ms working out
 * positions and 15 to 40 ms drawing round dots, against an 8 ms budget). So the
 * wave is still (no ripple), its positions are worked out once per size, and it
 * is painted once into a picture that each frame simply shows.
 */
object DotWave {
    const val ROWS = 56
    const val COLS = 84
    const val COUNT = ROWS * COLS

    /** Where every dot sits for a canvas of [w] x [h]: one x, y pair per dot, row by row. */
    fun positions(w: Float, h: Float, out: FloatArray = FloatArray(COUNT * 2)): FloatArray {
        var n = 0
        for (j in 0 until ROWS) {
            val v = j / (ROWS - 1f)
            val base = h * (0.606f + 0.326f * v.pow(1.1f))
            val stepH = h * (0.265f * (1f - v).pow(1.4f) + 0.03f)
            val centre = 0.50f + 0.06f * v
            for (i in 0 until COLS) {
                val u = i / (COLS - 1f)
                out[n++] = w / 2f + (u - 0.5f) * w * (1f + 0.25f * v)
                out[n++] = base - stepH * rise(u, centre)
            }
        }
        return out
    }

    /** How bright each dot is, 0 to 1: brightest on the steep face, fading at the near edge. */
    val brightness: FloatArray by lazy {
        FloatArray(COUNT).also { a ->
            for (j in 0 until ROWS) {
                val v = j / (ROWS - 1f)
                val near = if (v > 0.72f) (1f - (v - 0.72f) / 0.28f).coerceAtLeast(0f) else 1f
                for (i in 0 until COLS) {
                    val r = rise(i / (COLS - 1f), 0.50f + 0.06f * v)
                    val face = r * (1f - r) * 4f
                    a[j * COLS + i] = (0.5f + 0.45f * (1f - v * 0.5f)) * (0.5f + 0.5f * face) * near
                }
            }
        }
    }

    /** The dot's radius for its row, from the canvas width: far rows small, near rows bigger. */
    fun radius(row: Int, w: Float): Float = (0.85f + 0.45f * row / (ROWS - 1f)) * w / 300f

    private fun rise(u: Float, centre: Float) = 1f / (1f + exp(-(u - centre) / 0.055f))

    /** Paints the whole wave, round dots at their own brightness, onto [canvas]. Once per picture. */
    private fun paint(canvas: android.graphics.Canvas, w: Float, h: Float, colour: Color) {
        val xy = positions(w, h)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        val base = colour.toArgb()
        val r = android.graphics.Color.red(base)
        val g = android.graphics.Color.green(base)
        val b = android.graphics.Color.blue(base)
        for (n in 0 until COUNT) {
            val bright = brightness[n]
            if (bright <= 0.01f) continue
            paint.color = android.graphics.Color.argb((bright * 255).toInt().coerceIn(0, 255), r, g, b)
            canvas.drawCircle(xy[n * 2], xy[n * 2 + 1], radius(n / COLS, w), paint)
        }
    }

    /** The wave as a picture of [size], for drawing in one call. */
    fun picture(size: Size, colour: Color): ImageBitmap {
        val bitmap = android.graphics.Bitmap.createBitmap(
            size.width.toInt().coerceAtLeast(1),
            size.height.toInt().coerceAtLeast(1),
            android.graphics.Bitmap.Config.ARGB_8888,
        )
        paint(android.graphics.Canvas(bitmap), size.width, size.height, colour)
        return bitmap.asImageBitmap()
    }
}

/**
 * The wave's picture, shared: the opening paints it in the background during
 * the splash, and the burst and the welcome page both use that one copy.
 * Painting it on the main thread cost a 44 ms stall mid-burst.
 */
object WavePictures {
    private var key: Triple<Int, Int, Int>? = null
    private var picture: ImageBitmap? = null

    @Synchronized
    fun get(w: Float, h: Float, colour: Color): ImageBitmap {
        val k = Triple(w.roundToInt(), h.roundToInt(), colour.toArgb())
        picture?.let { if (k == key) return it }
        return DotWave.picture(Size(k.first.toFloat(), k.second.toFloat()), colour).also {
            key = k
            picture = it
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
        size = Size(size.width, height),
    )
}

/** The wave's colour for the current theme (on light, the darker yellow that reads on white), and whether the theme is dark. */
@Composable
fun dotWaveColour(): Pair<Color, Boolean> {
    val palette = LocalThrumPalette.current
    val dark = palette.field.luminance() < 0.5f
    return (if (dark) palette.accent else palette.accentInk) to dark
}

/**
 * The wave as a background. [alpha] lets the opening hide it while its own
 * dots are still flying in. The picture is painted once per size and theme.
 */
@Composable
fun DotWaveBackground(modifier: Modifier = Modifier, alpha: Float = 1f) {
    val (colour, dark) = dotWaveColour()
    Box(
        modifier.drawWithCache {
            val picture = WavePictures.get(size.width, size.height, colour)
            onDrawBehind {
                if (alpha > 0f) {
                    // On the light theme there is no black sky.
                    if (dark) drawWaveSky(alpha)
                    drawImage(picture, alpha = alpha)
                }
            }
        },
    )
}

/**
 * Reusable point batches: a fixed array per batch, filled each frame and drawn
 * with one call each, so a frame allocates nothing. Drawing happens on the main
 * thread only.
 */
class DotBatches(count: Int, capacity: Int) {
    private val points = Array(count) { FloatArray(capacity * 2) }
    private val used = IntArray(count)
    private val paint = android.graphics.Paint().apply {
        // Square, not round: these dots are tiny and moving, and squares cost the
        // graphics chip a fraction of what smoothed circles do.
        strokeCap = android.graphics.Paint.Cap.SQUARE
    }

    fun clear() = used.fill(0)

    fun add(batch: Int, x: Float, y: Float) {
        val i = used[batch]
        val arr = points[batch]
        if (i + 1 >= arr.size) return
        arr[i] = x
        arr[i + 1] = y
        used[batch] = i + 2
    }

    fun draw(canvas: android.graphics.Canvas, batch: Int, colour: Color, width: Float) {
        val n = used[batch]
        if (n == 0) return
        paint.color = colour.toArgb()
        paint.strokeWidth = width
        canvas.drawPoints(points[batch], 0, n, paint)
    }
}
