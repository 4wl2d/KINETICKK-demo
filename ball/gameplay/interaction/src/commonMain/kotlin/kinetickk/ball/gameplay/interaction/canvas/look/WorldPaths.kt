// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.interaction.canvas.look

import androidx.compose.ui.graphics.Path

/**
 * World-layer caches. Everything drawn per frame reuses these: paths are built once per shape and
 * size and drawn under translate/rotate transforms, strokes and gradient brushes are created once,
 * and changing numbers resolve to cached strings. All of it is draw-thread confined.
 */
internal object WorldPaths {
    /** Silhouette paths plus room for the Architect's frames, plates and fragments. */
    private const val CAPACITY = 160
    private val kinds = IntArray(CAPACITY) { -1 }
    private val sizes = FloatArray(CAPACITY)
    private val paths = arrayOfNulls<Path>(CAPACITY)
    private var next = 0

    private const val ARCHITECT_OUTER = 100
    private const val ARCHITECT_INNER = 101
    private const val PLATE = 102

    /** [shape] centered at the origin with the 24-grid half extent (10 units) mapped to [radius]. */
    fun silhouette(shape: EnemySilhouette, radius: Float): Path = get(shape.ordinal, radius)

    /** The Architect's outer triangle (`M50 4 96 84H4Z` on a 100 grid) with half width [radius]. */
    fun architectTriangle(radius: Float): Path = get(ARCHITECT_OUTER, radius)

    /** The Architect's inner bone triangle (`M50 30 74 72H26Z`). */
    fun architectInnerTriangle(radius: Float): Path = get(ARCHITECT_INNER, radius)

    /** A −12° sheared plate [width] wide and 0.7 × [width] tall centered at the origin. */
    fun shearedPlate(width: Float): Path = get(PLATE, width)

    private fun get(kind: Int, size: Float): Path {
        for (index in 0 until CAPACITY) {
            if (kinds[index] == kind && sizes[index] == size) return paths[index]!!
        }
        val path = paths[next]?.also { it.rewind() } ?: Path()
        build(path, kind, size)
        kinds[next] = kind
        sizes[next] = size
        paths[next] = path
        next = (next + 1) % CAPACITY
        return path
    }

    private fun build(path: Path, kind: Int, size: Float) {
        when (kind) {
            ARCHITECT_OUTER -> polygon(path, ArchitectOuter, 50f, size / 46f)
            ARCHITECT_INNER -> polygon(path, ArchitectInner, 50f, size / 46f)
            PLATE -> {
                val halfWidth = size * 0.5f
                val halfHeight = size * 0.35f
                val lean = halfHeight * 0.2126f
                path.moveTo(-halfWidth + lean, -halfHeight)
                path.lineTo(halfWidth + lean, -halfHeight)
                path.lineTo(halfWidth - lean, halfHeight)
                path.lineTo(-halfWidth - lean, halfHeight)
                path.close()
            }
            else -> {
                val shape = EnemySilhouette.entries[kind]
                for (contour in shape.contours) polygon(path, contour, 12f, size / 10f)
            }
        }
    }

    private fun polygon(path: Path, points: FloatArray, center: Float, scale: Float) {
        path.moveTo((points[0] - center) * scale, (points[1] - center) * scale)
        var index = 2
        while (index < points.size) {
            path.lineTo((points[index] - center) * scale, (points[index + 1] - center) * scale)
            index += 2
        }
        path.close()
    }

    private val ArchitectOuter = floatArrayOf(50f, 4f, 96f, 84f, 4f, 84f)
    private val ArchitectInner = floatArrayOf(50f, 30f, 74f, 72f, 26f, 72f)
}
