// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.foundation.design

import androidx.compose.ui.graphics.Path
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Core silhouettes in character order, shared by the world, Home, Hangar, Codex and HUD.
 * Orb, Prism and Shard use the circle, square and triangle respectively.
 */
object KkFormGeometry {
    const val COUNT: Int = 3

    const val CIRCLE: Int = 0
    const val SQUARE: Int = 1
    const val TRIANGLE: Int = 2

    /** Writes form [index] centred at ([cx], [cy]) into [path]. */
    fun build(path: Path, index: Int, cx: Float, cy: Float, radius: Float) {
        require(index in 0 until COUNT) { "form index $index is outside 0..${COUNT - 1}" }
        path.rewind()
        val u = radius / 42f
        when (index) {
            CIRCLE -> circle(path, cx, cy, radius)
            SQUARE -> polygon(path, cx, cy, 28f * u * SQRT2, 4, PI.toFloat() / 4f)
            TRIANGLE -> {
                path.moveTo(cx, cy - 40f * u)
                path.lineTo(cx + 42f * u, cy + 34f * u)
                path.lineTo(cx - 42f * u, cy + 34f * u)
                path.close()
            }
        }
    }

    /** A circle of four cubic arcs, clockwise from the top. */
    private fun circle(path: Path, cx: Float, cy: Float, radius: Float) {
        val k = radius * KAPPA
        path.moveTo(cx, cy - radius)
        path.cubicTo(cx + k, cy - radius, cx + radius, cy - k, cx + radius, cy)
        path.cubicTo(cx + radius, cy + k, cx + k, cy + radius, cx, cy + radius)
        path.cubicTo(cx - k, cy + radius, cx - radius, cy + k, cx - radius, cy)
        path.cubicTo(cx - radius, cy - k, cx - k, cy - radius, cx, cy - radius)
        path.close()
    }

    /** A regular polygon of [sides] with circumradius [radius]; the first vertex sits at [start] radians. */
    private fun polygon(path: Path, cx: Float, cy: Float, radius: Float, sides: Int, start: Float) {
        for (vertex in 0 until sides) {
            val angle = start + vertex * TAU / sides
            val x = cx + cos(angle) * radius
            val y = cy + sin(angle) * radius
            if (vertex == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
    }

    private const val KAPPA: Float = 0.5522848f
    private const val SQRT2: Float = 1.4142135f
    private const val TAU: Float = (PI * 2.0).toFloat()
}
