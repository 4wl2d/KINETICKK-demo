// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.interaction.canvas.world.places.dungeons

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import kinetickk.ball.content.api.StructureRoom
import kinetickk.ball.gameplay.interaction.canvas.CanvasMath
import kinetickk.ball.gameplay.interaction.canvas.fx.FxDrawBudget
import kinetickk.ball.gameplay.interaction.canvas.fx.FxPool
import kinetickk.ball.gameplay.interaction.canvas.fx.PixelPalettes
import kinetickk.ball.gameplay.interaction.canvas.fx.PixelSheet
import kinetickk.ball.gameplay.interaction.canvas.fx.drawPixelFrame
import kinetickk.ball.gameplay.interaction.canvas.fx.drawPixelLink
import kinetickk.ball.gameplay.interaction.canvas.world.places.holdLoopFrame
import kinetickk.ball.gameplay.interaction.fx.PixelFxClock
import kinetickk.ball.gameplay.interaction.fx.PixelStrip
import kinetickk.foundation.design.KkRolePalette
import kotlin.math.cos

/*
 * The sealed room's corner brackets as pixel cells: in each corner the corner cell (drawn
 * for the top left and mirrored for the others), each arm carried on by link tiles and capped at its exact reach by an
 * end cell. The three strips share one loop of a second on the same ticks: the corner locks, a current runs out along
 * the arms, the arm ends tick and the bracket dims. The HUD's sealed-room cell swells on the same loop and its alarm
 * at the same tempo ([roomBracketBeat]).
 */

/** The sealed room's corner brackets: arm length and inset from the room corner (world units). */
internal const val BRACKET_ARM = 90f
internal const val BRACKET_INSET = 36f

/** The corner cell's corner point lies this many texels in from its top-left corner (`BRACKET_CORNER` in fx_places.py). */
private const val CORNER_TEXELS = 5

/** An arm's link tiles start this many texels from the corner, a texel under the corner cell's own arm. */
private const val LINK_START_TEXELS = 5

/** A bracket past the view by more than this is not drawn (units): its arm's reach and half a corner cell. */
private const val BRACKET_VIEW_MARGIN = BRACKET_ARM + 16f

/**
 * Four threat brackets in the corners of the sealed room, [BRACKET_INSET] in from them with arms [BRACKET_ARM] long,
 * looping on their strips' ticks from the seal at [sealedAt] (from the run's start when it is unknown), so the first
 * lock comes with the seal. Signature work of the dungeon fight's pool.
 */
internal fun DrawScope.drawRoomBrackets(
    center: Offset,
    room: StructureRoom,
    rotation: Int,
    time: Float,
    sealedAt: Float,
    scale: Float,
    roles: KkRolePalette,
) {
    val left = center.x + roomLeft(room, rotation) + BRACKET_INSET
    val top = center.y + roomTop(room, rotation) + BRACKET_INSET
    val right = center.x + roomRight(room, rotation) - BRACKET_INSET
    val bottom = center.y + roomBottom(room, rotation) - BRACKET_INSET
    val frame = holdLoopFrame(PixelStrip.PLACE_BRACKET, roomBracketAge(time, sealedAt))
    val palette = PixelPalettes.of(roles.threat)
    var ops = 0
    for (corner in 0 until 4) {
        val east = corner and 1 != 0
        val south = corner and 2 != 0
        ops += drawRoomBracket(if (east) right else left, if (south) bottom else top, east, south, frame, scale, palette)
    }
    if (ops > 0) FxDrawBudget.take(FxPool.DUNGEON, ops, signature = true)
}

/** Seconds into the brackets' loop at [time]: since the seal at [sealedAt], or since the run's start when it is unknown. */
internal fun roomBracketAge(time: Float, sealedAt: Float): Float = if (sealedAt >= 0f) time - sealedAt else time

/**
 * The brackets' beat at [age] seconds into their loop ([roomBracketAge]): 1 on the corner's lock, 0 half a loop later
 * as the bracket dims, a cosine between, so a pulse driven by it peaks with every lock.
 */
internal fun roomBracketBeat(age: Float): Float = 0.5f + 0.5f * cos(CanvasMath.TAU * age / PixelFxClock.seconds(PixelStrip.PLACE_BRACKET))

/**
 * One bracket at [frame] whose corner point is ([x], [y]), its arms running in toward the room (west from an [east]
 * corner, north from a [south] one): the arms' link tiles, an end cell on each arm's end and the corner cell over
 * them. Returns its draw operations; a bracket wholly out of view draws nothing.
 */
private fun DrawScope.drawRoomBracket(x: Float, y: Float, east: Boolean, south: Boolean, frame: Int, scale: Float, palette: ColorFilter): Int {
    val sx = if (east) -1f else 1f
    val sy = if (south) -1f else 1f
    val nearX = x - sx * (BRACKET_VIEW_MARGIN - BRACKET_ARM)
    val farX = x + sx * BRACKET_VIEW_MARGIN
    val nearY = y - sy * (BRACKET_VIEW_MARGIN - BRACKET_ARM)
    val farY = y + sy * BRACKET_VIEW_MARGIN
    if (maxOf(nearX, farX) < 0f || minOf(nearX, farX) > size.width || maxOf(nearY, farY) < 0f || minOf(nearY, farY) > size.height) return 0
    val unit = PixelSheet.step(scale) / scale
    val start = LINK_START_TEXELS * unit
    val link = PixelStrip.LINK_PLACE_BRACKET
    var ops = drawPixelLink(link, frame, x + sx * start, y, x + sx * BRACKET_ARM, y, scale, palette)
    ops += drawPixelLink(link, frame, x, y + sy * start, x, y + sy * BRACKET_ARM, scale, palette)
    // The end cell is drawn for an arm running east; quarter turns face it along the others.
    val end = PixelStrip.PLACE_BRACKET_END
    drawPixelFrame(end, frame, x + sx * BRACKET_ARM, y, scale, palette, turns = if (east) 2 else 0)
    drawPixelFrame(end, frame, x, y + sy * BRACKET_ARM, scale, palette, turns = if (south) 3 else 1)
    val corner = PixelStrip.PLACE_BRACKET
    val offset = (corner.w / 2 - CORNER_TEXELS) * unit
    drawPixelFrame(corner, frame, x + sx * offset, y + sy * offset, scale, palette, flipX = east, flipY = south)
    return ops + 3
}
