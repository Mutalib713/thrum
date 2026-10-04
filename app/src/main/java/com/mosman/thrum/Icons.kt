package com.mosman.thrum

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Thrum's vector icon system.
 *
 * Drawn directly from `docs/design/screens/thrum-screens.html` icon paths.
 * Every icon is authored in a 24x24 coordinate box with a 1.8 stroke width.
 * Strictly vector iconography only — zero emoji.
 */
object ThrumIcon {

    @Composable
    operator fun invoke(
        name: String,
        modifier: Modifier = Modifier,
        tint: Color = Color.Unspecified,
        size: Dp = 22.dp,
    ) {
        ThrumIconImpl(name, modifier, tint, size)
    }
}

@Composable
private fun ThrumIconImpl(
    name: String,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
    size: Dp = 22.dp,
) {
    Canvas(modifier = modifier.size(size)) {
        val scale = this.size.width / 24f
        val stroke = Stroke(
            width = 1.8f * scale,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )
        drawIcon(name, tint, scale, stroke)
    }
}

private fun DrawScope.drawIcon(name: String, tint: Color, s: Float, stroke: Stroke) {
    fun p(x: Float, y: Float) = Offset(x * s, y * s)

    when (name) {
        "home" -> {
            val path = Path().apply {
                moveTo(3.5f * s, 10.5f * s)
                lineTo(12f * s, 3.5f * s)
                lineTo(20.5f * s, 10.5f * s)
                lineTo(20.5f * s, 20f * s)
                lineTo(15f * s, 20f * s)
                lineTo(15f * s, 14f * s)
                lineTo(9f * s, 14f * s)
                lineTo(9f * s, 20f * s)
                lineTo(3.5f * s, 20f * s)
                close()
            }
            drawPath(path, color = tint, style = stroke)
        }

        "library" -> {
            val p1 = Path().apply {
                moveTo(12f * s, 3.5f * s)
                lineTo(20.5f * s, 8.1f * s)
                lineTo(12f * s, 12.7f * s)
                lineTo(3.5f * s, 8.1f * s)
                close()
            }
            val p2 = Path().apply {
                moveTo(3.5f * s, 12.4f * s)
                lineTo(12f * s, 17f * s)
                lineTo(20.5f * s, 12.4f * s)
            }
            val p3 = Path().apply {
                moveTo(3.5f * s, 16.4f * s)
                lineTo(12f * s, 21f * s)
                lineTo(20.5f * s, 16.4f * s)
            }
            drawPath(p1, color = tint, style = stroke)
            drawPath(p2, color = tint, style = stroke)
            drawPath(p3, color = tint, style = stroke)
        }

        "music" -> {
            val stem = Path().apply {
                moveTo(9f * s, 18f * s)
                lineTo(9f * s, 5f * s)
                lineTo(20f * s, 3f * s)
                lineTo(20f * s, 16f * s)
            }
            drawPath(stem, color = tint, style = stroke)
            drawCircle(tint, radius = 3f * s, center = p(6f, 18f), style = stroke)
            drawCircle(tint, radius = 3f * s, center = p(17f, 16f), style = stroke)
        }

        "gear" -> {
            drawCircle(tint, radius = 3.2f * s, center = p(12f, 12f), style = stroke)
            val path = Path().apply {
                moveTo(19.4f * s, 13.5f * s)
                lineTo(21f * s, 14.7f * s); lineTo(19.2f * s, 17.8f * s); lineTo(17.3f * s, 17.1f * s)
                lineTo(15.4f * s, 18.2f * s); lineTo(15.1f * s, 20.2f * s); lineTo(11.5f * s, 20.2f * s)
                lineTo(11.2f * s, 18.2f * s); lineTo(9.3f * s, 17.1f * s); lineTo(7.4f * s, 17.8f * s)
                lineTo(5.6f * s, 14.7f * s); lineTo(7.2f * s, 13.5f * s); lineTo(7.2f * s, 10.5f * s)
                lineTo(5.6f * s, 9.3f * s); lineTo(7.4f * s, 6.2f * s); lineTo(9.3f * s, 6.9f * s)
                lineTo(11.2f * s, 5.8f * s); lineTo(11.5f * s, 3.8f * s); lineTo(15.1f * s, 3.8f * s)
                lineTo(15.4f * s, 5.8f * s); lineTo(17.3f * s, 6.9f * s); lineTo(19.2f * s, 6.2f * s)
                lineTo(21f * s, 9.3f * s); lineTo(19.4f * s, 10.5f * s)
                close()
            }
            drawPath(path, color = tint, style = stroke)
        }

        "plus" -> {
            drawLine(tint, p(12f, 5f), p(12f, 19f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            drawLine(tint, p(5f, 12f), p(19f, 12f), strokeWidth = stroke.width, cap = StrokeCap.Round)
        }

        "video" -> {
            drawRoundRect(
                color = tint,
                topLeft = p(3f, 5f),
                size = Size(18f * s, 14f * s),
                cornerRadius = CornerRadius(2.5f * s, 2.5f * s),
                style = stroke,
            )
            val tri = Path().apply {
                moveTo(10f * s, 9.2f * s)
                lineTo(15f * s, 12f * s)
                lineTo(10f * s, 14.8f * s)
                close()
            }
            drawPath(tri, color = tint, style = Fill)
        }

        "file" -> {
            val path = Path().apply {
                moveTo(14f * s, 3f * s)
                lineTo(7f * s, 3f * s)
                lineTo(5f * s, 5f * s)
                lineTo(5f * s, 19f * s)
                lineTo(7f * s, 21f * s)
                lineTo(17f * s, 21f * s)
                lineTo(19f * s, 19f * s)
                lineTo(19f * s, 8f * s)
                close()
            }
            drawPath(path, color = tint, style = stroke)
            drawLine(tint, p(14f, 3f), p(14f, 8f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            drawLine(tint, p(14f, 8f), p(19f, 8f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            drawLine(tint, p(9f, 13f), p(15f, 13f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            drawLine(tint, p(9f, 17f), p(13f, 17f), strokeWidth = stroke.width, cap = StrokeCap.Round)
        }

        "phone" -> {
            val path = Path().apply {
                moveTo(21f * s, 16.4f * s)
                lineTo(21f * s, 19.2f * s)
                cubicTo(19.8f * s, 21.1f * s, 14.7f * s, 21.1f * s, 10.7f * s, 18.2f * s)
                cubicTo(6.7f * s, 15.3f * s, 4.3f * s, 11.2f * s, 2.1f * s, 6.1f * s)
                cubicTo(2.1f * s, 4.2f * s, 4f * s, 2.1f * s, 5.9f * s, 2.1f * s)
                lineTo(8.7f * s, 2.1f * s)
                lineTo(9.6f * s, 6.3f * s)
                lineTo(7.8f * s, 9.5f * s)
                cubicTo(9.5f * s, 12.5f * s, 11.5f * s, 14.5f * s, 14.5f * s, 16.2f * s)
                lineTo(17.7f * s, 14.4f * s)
                close()
            }
            drawPath(path, color = tint, style = stroke)
        }

        "phonedown" -> {
            val path = Path().apply {
                moveTo(3f * s, 14.5f * s)
                cubicTo(7.9f * s, 10.1f * s, 16.1f * s, 10.1f * s, 21f * s, 14.5f * s)
                lineTo(18.8f * s, 17.2f * s)
                lineTo(15.4f * s, 15.9f * s)
                lineTo(15f * s, 13.2f * s)
                cubicTo(13f * s, 12.8f * s, 11f * s, 12.8f * s, 9f * s, 13.2f * s)
                lineTo(8.6f * s, 15.9f * s)
                lineTo(5.2f * s, 17.2f * s)
                close()
            }
            drawPath(path, color = tint, style = stroke)
        }

        "export" -> {
            val arrow = Path().apply {
                moveTo(12f * s, 3f * s)
                lineTo(12f * s, 15f * s)
                moveTo(7.5f * s, 7.5f * s)
                lineTo(12f * s, 3f * s)
                lineTo(16.5f * s, 7.5f * s)
            }
            val tray = Path().apply {
                moveTo(5f * s, 13.5f * s)
                lineTo(5f * s, 19f * s)
                lineTo(7f * s, 21f * s)
                lineTo(17f * s, 21f * s)
                lineTo(19f * s, 19f * s)
                lineTo(19f * s, 13.5f * s)
            }
            drawPath(arrow, color = tint, style = stroke)
            drawPath(tray, color = tint, style = stroke)
        }

        "trash" -> {
            drawLine(tint, p(4f, 7f), p(20f, 7f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            val lid = Path().apply {
                moveTo(9.5f * s, 7f * s)
                lineTo(9.5f * s, 4.5f * s)
                lineTo(14.5f * s, 4.5f * s)
                lineTo(14.5f * s, 7f * s)
            }
            val can = Path().apply {
                moveTo(6.5f * s, 7f * s)
                lineTo(7.5f * s, 20f * s)
                lineTo(16.5f * s, 20f * s)
                lineTo(17.5f * s, 7f * s)
            }
            drawPath(lid, color = tint, style = stroke)
            drawPath(can, color = tint, style = stroke)
        }

        "tune" -> {
            drawLine(tint, p(4f, 6f), p(13f, 6f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            drawLine(tint, p(17f, 6f), p(20f, 6f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            drawLine(tint, p(4f, 12f), p(7f, 12f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            drawLine(tint, p(11f, 12f), p(20f, 12f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            drawLine(tint, p(4f, 18f), p(15f, 18f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            drawLine(tint, p(19f, 18f), p(20f, 18f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            drawCircle(tint, radius = 2f * s, center = p(15f, 6f), style = stroke)
            drawCircle(tint, radius = 2f * s, center = p(9f, 12f), style = stroke)
            drawCircle(tint, radius = 2f * s, center = p(17f, 18f), style = stroke)
        }

        "vib" -> {
            drawRoundRect(
                color = tint,
                topLeft = p(8f, 3f),
                size = Size(8f * s, 18f * s),
                cornerRadius = CornerRadius(2f * s, 2f * s),
                style = stroke,
            )
            drawLine(tint, p(4.5f, 8.5f), p(4.5f, 15.5f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            drawLine(tint, p(19.5f, 8.5f), p(19.5f, 15.5f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            drawLine(tint, p(1.5f, 10.5f), p(1.5f, 13.5f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            drawLine(tint, p(22.5f, 10.5f), p(22.5f, 13.5f), strokeWidth = stroke.width, cap = StrokeCap.Round)
        }

        "warn" -> {
            val tri = Path().apply {
                moveTo(12f * s, 3.5f * s)
                lineTo(21.5f * s, 20f * s)
                lineTo(2.5f * s, 20f * s)
                close()
            }
            drawPath(tri, color = tint, style = stroke)
            drawLine(tint, p(12f, 10f), p(12f, 14.5f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            drawLine(tint, p(12f, 17.2f), p(12f, 17.5f), strokeWidth = stroke.width, cap = StrokeCap.Round)
        }

        "user" -> {
            drawCircle(tint, radius = 4f * s, center = p(12f, 8.5f), style = stroke)
            val shoulders = Path().apply {
                moveTo(4f * s, 21f * s)
                cubicTo(5.2f * s, 17f * s, 8.3f * s, 15f * s, 12f * s, 15f * s)
                cubicTo(15.7f * s, 15f * s, 18.8f * s, 17f * s, 20f * s, 21f * s)
            }
            drawPath(shoulders, color = tint, style = stroke)
        }

        "play" -> {
            val tri = Path().apply {
                moveTo(7f * s, 4.5f * s)
                lineTo(20f * s, 12f * s)
                lineTo(7f * s, 19.5f * s)
                close()
            }
            drawPath(tri, color = tint, style = Fill)
        }

        "pause" -> {
            drawRoundRect(
                color = tint,
                topLeft = p(6f, 4.5f),
                size = Size(4.2f * s, 15f * s),
                cornerRadius = CornerRadius(1f * s, 1f * s),
                style = Fill,
            )
            drawRoundRect(
                color = tint,
                topLeft = p(13.8f, 4.5f),
                size = Size(4.2f * s, 15f * s),
                cornerRadius = CornerRadius(1f * s, 1f * s),
                style = Fill,
            )
        }

        "prev" -> {
            val tri = Path().apply {
                moveTo(18f * s, 5.5f * s)
                lineTo(18f * s, 18.5f * s)
                lineTo(8.5f * s, 12f * s)
                close()
            }
            drawPath(tri, color = tint, style = Fill)
            drawLine(tint, p(6f, 5.5f), p(6f, 18.5f), strokeWidth = stroke.width, cap = StrokeCap.Round)
        }

        "next" -> {
            val tri = Path().apply {
                moveTo(6f * s, 5.5f * s)
                lineTo(6f * s, 18.5f * s)
                lineTo(15.5f * s, 12f * s)
                close()
            }
            drawPath(tri, color = tint, style = Fill)
            drawLine(tint, p(18f, 5.5f), p(18f, 18.5f), strokeWidth = stroke.width, cap = StrokeCap.Round)
        }

        "check" -> {
            val path = Path().apply {
                moveTo(5f * s, 12.5f * s)
                lineTo(9.5f * s, 17f * s)
                lineTo(19f * s, 7.5f * s)
            }
            drawPath(path, color = tint, style = stroke)
        }

        "chev" -> {
            val path = Path().apply {
                moveTo(9f * s, 5f * s)
                lineTo(16f * s, 12f * s)
                lineTo(9f * s, 19f * s)
            }
            drawPath(path, color = tint, style = stroke)
        }

        "back" -> {
            val path = Path().apply {
                moveTo(15f * s, 5f * s)
                lineTo(8f * s, 12f * s)
                lineTo(15f * s, 19f * s)
            }
            drawPath(path, color = tint, style = stroke)
        }

        "x" -> {
            drawLine(tint, p(6f, 6f), p(18f, 18f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            drawLine(tint, p(18f, 6f), p(6f, 18f), strokeWidth = stroke.width, cap = StrokeCap.Round)
        }

        "globe" -> {
            drawCircle(tint, radius = 8.5f * s, center = p(12f, 12f), style = stroke)
            drawLine(tint, p(3.5f, 12f), p(20.5f, 12f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            val arc1 = Path().apply {
                moveTo(12f * s, 3.5f * s)
                cubicTo(14.6f * s, 6.1f * s, 15.6f * s, 9.1f * s, 15.6f * s, 12f * s)
                cubicTo(15.6f * s, 14.9f * s, 14.6f * s, 17.9f * s, 12f * s, 20.5f * s)
            }
            val arc2 = Path().apply {
                moveTo(12f * s, 3.5f * s)
                cubicTo(9.4f * s, 6.1f * s, 8.4f * s, 9.1f * s, 8.4f * s, 12f * s)
                cubicTo(8.4f * s, 14.9f * s, 9.4f * s, 17.9f * s, 12f * s, 20.5f * s)
            }
            drawPath(arc1, color = tint, style = stroke)
            drawPath(arc2, color = tint, style = stroke)
        }

        "spark" -> {
            val s1 = Path().apply {
                moveTo(12f * s, 3.5f * s)
                lineTo(13.9f * s, 8.9f * s); lineTo(19.5f * s, 10.5f * s); lineTo(13.9f * s, 12.1f * s)
                lineTo(12f * s, 17.5f * s)
                lineTo(10.1f * s, 12.1f * s); lineTo(4.5f * s, 10.5f * s); lineTo(10.1f * s, 8.9f * s)
                close()
            }
            val s2 = Path().apply {
                moveTo(18.5f * s, 16.5f * s)
                lineTo(19.3f * s, 18.5f * s); lineTo(21.3f * s, 19.3f * s); lineTo(19.3f * s, 20.1f * s)
                lineTo(18.5f * s, 22.1f * s)
                lineTo(17.7f * s, 20.1f * s); lineTo(15.7f * s, 19.3f * s); lineTo(17.7f * s, 18.5f * s)
                close()
            }
            drawPath(s1, color = tint, style = stroke)
            drawPath(s2, color = tint, style = stroke)
        }

        "search" -> {
            drawCircle(tint, radius = 6.5f * s, center = p(11f, 11f), style = stroke)
            drawLine(tint, p(16f, 16f), p(20.5f, 20.5f), strokeWidth = stroke.width, cap = StrokeCap.Round)
        }
    }
}
