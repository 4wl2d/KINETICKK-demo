// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.interaction.canvas.world.places

import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import kinetickk.ball.gameplay.interaction.canvas.CanvasMath
import kinetickk.ball.gameplay.interaction.canvas.fx.PixelSheet
import kinetickk.ball.gameplay.interaction.canvas.fx.drawPixelFill
import kinetickk.ball.gameplay.interaction.canvas.fx.drawPixelFrame
import kinetickk.ball.gameplay.interaction.fx.PixelFxClock
import kinetickk.ball.gameplay.interaction.fx.PixelStrip
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * The cells the places' effects are laid out with: pips on a circle of exact radius (a rim, a timer, a landing ring),
 * a halftone disc built from axis-aligned fill rows (an area that fills or drains in steps), and the cells round a
 * room's walls. All of them place shapes at exact positions, so the gameplay geometry they show stays exact.
 */

/** Rows of a halftone disc are a whole number of turns of the 4 × 4 dither tall, so every row shows it whole. */
private const val DISC_ROW_TEXELS = 4

/** Tallest row of a halftone disc, in texels: one fill band, so a row is one piece for each band width it spans. */
private const val DISC_ROW_MAX_TEXELS = 16

/** A disc takes at most this many rows while its rows can grow; a larger one takes rows a whole band tall. */
internal const val MAX_DISC_ROWS = 32

/**
 * A pip ring keeps at least this many pips, however small, and at most [MAX_PIPS], however large. The largest thermal
 * vent's plume edge (150 units round at a pip every 9.8 units) takes 96. One ring of the default tuning needs more: a
 * mini-boss guardian's breach ring (DungeonFightRenderer) starts at 1.8 × 3.4 of the guardian's radius, about 282 units
 * for the Brood Queen and 269 for Warden Prime, which at a pip every 14 units would take about 126 and 121 pips. Its 96
 * pips stand up to about 18 units apart at the start of the telegraph and are back to their spacing once the ring has
 * closed to about 215 units, about a third of the way through.
 */
private const val MIN_PIPS = 8
internal const val MAX_PIPS = 96

/**
 * Tiles a link across a room from wall to wall may need: the largest room's side (2,200 units, the Vault's galleries)
 * at the smallest texel a density can give (just over 4/3 of a unit, at a density just under 0.75).
 */
internal const val ROOM_LINK_TILES = 104

/** The frame of [strip] looping with its own holds at [age] seconds (its frames change only on its ticks). */
internal fun holdLoopFrame(strip: PixelStrip, age: Float): Int {
    val tick = PixelFxClock.tick(age.coerceAtLeast(0f), strip.fps) % PixelFxClock.ticks(strip)
    return PixelFxClock.frame(strip, (tick + 0.5f) / strip.fps)
}

/** World units a cell of [strip] covers at [scale]: cells this far apart abut on the texel grid. */
internal fun cellUnits(strip: PixelStrip, scale: Float): Float = strip.w * PixelSheet.step(scale) / scale

/**
 * Pips of [strip] on the circle of [radius] round ([x], [y]), about [spacing] units apart (farther on a ring that would
 * need more than [MAX_PIPS]), starting at [turn] radians. With [dashes] the ring is cut into that many dashes, each
 * covering [dashShare] of its arc. [frameOf] gives a pip's frame by its index (−1 skips it). Returns the pips drawn
 * (their draw operations). Allocation-free.
 */
internal inline fun DrawScope.drawPipRing(
    strip: PixelStrip,
    x: Float,
    y: Float,
    radius: Float,
    spacing: Float,
    scale: Float,
    palette: ColorFilter,
    turn: Float = 0f,
    dashes: Int = 0,
    dashShare: Float = 0.5f,
    frameOf: (Int) -> Int,
): Int {
    if (radius <= 0f) return 0
    val count = (CanvasMath.TAU * radius / spacing).roundToInt().coerceIn(MIN_PIPS, MAX_PIPS)
    var drawn = 0
    for (pip in 0 until count) {
        val share = pip.toFloat() / count
        if (dashes > 0 && (share * dashes) % 1f >= dashShare) continue
        val frame = frameOf(pip)
        if (frame < 0) continue
        val angle = turn + share * CanvasMath.TAU
        drawPixelFrame(strip, frame, x + cos(angle) * radius, y + sin(angle) * radius, scale, palette)
        drawn++
    }
    return drawn
}

/**
 * A disc of [radius] round ([x], [y]) in the halftone [level] (0 sparse to 4 solid), built from axis-aligned fill rows
 * on one texel grid, so its edge steps like pixel art and every row's dither lines up with the next. A large disc takes
 * taller rows (up to a fill band) so it draws at most [MAX_DISC_ROWS] pieces while it can. A disc drawn over another of
 * a lower level at the same centre shows the higher level where they overlap. Returns its draw operations.
 */
internal fun DrawScope.drawPixelDisc(x: Float, y: Float, radius: Float, level: Int, scale: Float, palette: ColorFilter): Int {
    // Its sides step a dither turn at a time whatever its rows' height.
    val column = DISC_ROW_TEXELS * PixelSheet.step(scale) / scale
    var turns = 1
    while (turns < DISC_ROW_MAX_TEXELS / DISC_ROW_TEXELS && 2f * radius / (turns * column) > MAX_DISC_ROWS) turns++
    val row = turns * column
    val rows = (radius / row).roundToInt()
    var pieces = 0
    for (index in -rows until rows) {
        val middle = (index + 0.5f) * row
        val half = sqrt(max(0f, radius * radius - middle * middle))
        val columns = (half / column).roundToInt()
        if (columns <= 0) continue
        pieces += drawPixelFill(x - columns * column, y + index * row, x + columns * column, y + (index + 1) * row, level, scale, palette)
    }
    return pieces
}

/**
 * Calls [action] for every cell round the walls of the room [halfWidth] × [halfHeight] centred at ([x], [y]): cells
 * [cell] units apart, centred on each wall, with the quarter turns that face a cell's top outward (0 on the top wall,
 * then clockwise) and the cell's place along the walls, clockwise from the top left corner (units).
 */
internal inline fun forEachPerimeterCell(
    x: Float,
    y: Float,
    halfWidth: Float,
    halfHeight: Float,
    cell: Float,
    action: (cellX: Float, cellY: Float, turns: Int, along: Float) -> Unit,
) {
    val width = halfWidth * 2f
    val height = halfHeight * 2f
    for (side in 0 until 4) {
        val length = if (side % 2 == 0) width else height
        val count = (length / cell).toInt()
        if (count <= 0) continue
        val start = (length - count * cell) * 0.5f + cell * 0.5f
        val before = when (side) {
            0 -> 0f
            1 -> width
            2 -> width + height
            else -> 2f * width + height
        }
        for (index in 0 until count) {
            val s = start + index * cell
            when (side) {
                0 -> action(x - halfWidth + s, y - halfHeight, 0, before + s)
                1 -> action(x + halfWidth, y - halfHeight + s, 1, before + s)
                2 -> action(x + halfWidth - s, y + halfHeight, 2, before + s)
                else -> action(x - halfWidth, y + halfHeight - s, 3, before + s)
            }
        }
    }
}
