// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.interaction.canvas.fx

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Retained quarter circles on a square grid, mirrored at draw time. */
internal object EffectRings {
    private val radii = intArrayOf(3, 4, 5, 6, 8, 10, 13, 16, 20, 25, 32, 40, 50, 64)
    private const val STAGES = 5
    private val paths = Array(STAGES) { phase -> Array(radii.size) { quarter(radii[it], phase) } }

    fun warmUp() {
        // The small-ring fallback is used before any viewport-dependent sizes are drawn.
        paths[0][0].getBounds()
    }

    fun draw(canvas: Canvas, x: Float, y: Float, radius: Float, stage: Int, scale: Float, palette: ColorFilter, alternate: ColorFilter, alpha: Float) {
        val phase = stage.coerceIn(0, STAGES - 1)
        val radiusPx = (radius * scale).roundToInt()
        val cell = PixelSheet.step(scale)
        if (radiusPx < cell * 2) return
        val index = match(radiusPx.toFloat() / cell)
        val unit = radiusPx / (scale * radii[index])
        val centerX = (x * scale).roundToInt() / scale
        val centerY = (y * scale).roundToInt() / scale
        val tint = alpha * (1f - phase * 0.11f)
        for (quadrant in 0..3) {
            canvas.save()
            canvas.translate(centerX, centerY)
            canvas.scale(if (quadrant == 1 || quadrant == 2) -unit else unit, if (quadrant >= 2) -unit else unit)
            canvas.drawPath(paths[phase][index], EffectPaint.fill(if (quadrant and 1 == 0) palette else alternate, tint))
            canvas.restore()
        }
    }

    private fun match(ideal: Float): Int {
        for (i in 1 until radii.size) {
            if (ideal < (radii[i - 1] + radii[i]) * 0.5f) return i - 1
        }
        return radii.lastIndex
    }

    private fun quarter(radius: Int, stage: Int): Path {
        val width = if (stage < 2) 2 else 1
        val inner = (radius - width).coerceAtLeast(0)
        val path = Path()
        for (y in 0 until radius) {
            if (stage == 3 && y % 5 >= 3 || stage == 4 && y % 4 != 0) continue
            val end = edge(radius, y)
            val start = edge(inner, y).coerceAtMost(end - 1)
            if (end > start) {
                path.addRect(Rect(start.toFloat(), y.toFloat(), end.toFloat(), y + 1f))
            }
        }
        return path
    }

    private fun edge(radius: Int, y: Int): Int =
        sqrt((radius * radius - y * y).coerceAtLeast(0).toFloat()).roundToInt()
}
