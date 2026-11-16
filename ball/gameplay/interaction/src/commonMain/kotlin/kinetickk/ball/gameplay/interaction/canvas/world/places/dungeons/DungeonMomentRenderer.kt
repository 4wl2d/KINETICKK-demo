// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.interaction.canvas.world.places.dungeons

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import kinetickk.ball.content.api.WallAxis
import kinetickk.ball.content.api.rotateQuarterX
import kinetickk.ball.content.api.rotateQuarterY
import kinetickk.ball.gameplay.interaction.canvas.world.places.ROOM_LINK_TILES
import kinetickk.ball.gameplay.interaction.canvas.world.places.forEachPerimeterCell
import kinetickk.ball.gameplay.interaction.canvas.world.places.cellUnits
import kinetickk.ball.gameplay.interaction.fx.DungeonMomentProjection
import kinetickk.ball.gameplay.interaction.fx.PieceMotion
import kinetickk.ball.gameplay.interaction.fx.PixelFxClock
import kinetickk.ball.gameplay.interaction.fx.PixelStrip
import kinetickk.ball.gameplay.interaction.fx.VisualFxProjection
import kinetickk.ball.gameplay.nucleus.protocol.DungeonMomentKind
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkRolePalette
import kinetickk.foundation.design.isOnScreen
import kinetickk.foundation.design.kkMix
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kinetickk.ball.gameplay.interaction.canvas.*
import kinetickk.ball.gameplay.interaction.canvas.fx.*

/*
 * Dungeon moments over the fight (after the enemies and projectiles, under the Core), each its own pixel animation:
 * a seal slams a lock shut at the room's centre while bars drop round its walls from the doors and a threat halftone
 * drains off the floor; a wave tears a breach open over the room while its walls crackle; the guardian's entrance
 * pulls a ring of light and a ring of shards in onto it; a room's clear bursts light from its centre, sweeps an outline
 * out to its walls and ignites its runes round the room, and the whole structure's clear also throws two volleys of
 * rays and burns longer. Every part steps on the moment's 16 FPS ticks. Moments are signature work of FxPool.DUNGEON
 * (few and short); their places, kinds and lives come from the FX projection, the doors from the held structure.
 */

/** Draws every dungeon moment in view. */
internal fun DrawScope.drawDungeonMoments(engine: GameplayRenderModel, visualFx: VisualFxProjection, shakeX: Float, shakeY: Float, roles: KkRolePalette) {
    val moments = visualFx.dungeonMoments
    if (moments.isEmpty()) return
    for (index in moments.indices) {
        val moment = moments[index]
        val center = world(engine, moment.x, moment.y, shakeX, shakeY)
        if (!isOnScreen(center, hypot(moment.halfWidth, moment.halfHeight) * 1.4f)) continue
        val accent = structureAccent(engine.content.structures.template(moment.template), roles)
        // A moment plays in whole ticks of one frame rate: its strips, cells and runes change on the same ticks.
        val tick = PixelFxClock.tick(moment.maxLife - engine.fxLife(moment.life, moment.maxLife), MOMENT_FPS)
        when (moment.kind) {
            DungeonMomentKind.SEALED -> drawSeal(engine, moment, center, tick, PixelPalettes.of(roles.threat))
            DungeonMomentKind.WAVE -> drawWaveLanding(engine, moment, center, tick, PixelPalettes.of(roles.threat))
            DungeonMomentKind.GUARDIAN ->
                drawGuardianEntrance(engine, moment, world(engine, moment.focusX, moment.focusY, shakeX, shakeY), tick, kkMix(roles.threat, accent, 0.35f))
            DungeonMomentKind.ROOM_CLEARED, DungeonMomentKind.STRUCTURE_CLEARED ->
                drawClear(engine, moment, center, tick, PixelPalettes.of(kkMix(Kk.Bone, accent, 0.3f)), moment.kind == DungeonMomentKind.STRUCTURE_CLEARED)
        }
    }
}

/** Dungeon moments step at the frame rate of the rune glint, so a rune plays its strip exactly on the moment's ticks. */
internal val MOMENT_FPS = PixelStrip.GLINT.fps

/** The frame of [strip] [ticks] moment ticks after it started (−1 before it starts and after it ends). */
private fun momentFrame(strip: PixelStrip, ticks: Int): Int = PixelFxClock.frame(strip, (ticks + 0.5f) / MOMENT_FPS)

/**
 * The seal's threat halftone: its level on each of the first ticks (solid is 4), gone after them. It never rises past
 * a quarter of the room's texels: a sealed room can fill the screen, and nothing flashes the whole screen.
 */
private val SealFlood = intArrayOf(1, 1, 0, 0, 0)

/** The farthest bar from a door slams this many ticks after the nearest. */
private const val BAR_SWEEP_TICKS = 5

/**
 * The seal: the threat halftone floods the room and drains off it level by level, the bars slam round the walls one
 * after another out from the doors, and the lock slams shut at the room's centre.
 */
private fun DrawScope.drawSeal(engine: GameplayRenderModel, moment: DungeonMomentProjection, center: Offset, tick: Int, threat: ColorFilter) {
    val scale = engine.worldScale
    if (tick < SealFlood.size) {
        val pieces = drawPixelFill(center.x - moment.halfWidth, center.y - moment.halfHeight, center.x + moment.halfWidth,
            center.y + moment.halfHeight, SealFlood[tick], scale, threat)
        FxDrawBudget.take(FxPool.DUNGEON, pieces, signature = true)
    }
    val doors = MomentDoors.find(engine, moment)
    val perimeter = 4f * (moment.halfWidth + moment.halfHeight)
    // A bar's delay grows with its walk along the walls from the nearest door, up to half the gap between two doors.
    val pace = max(1f, perimeter / (2f * doors) / BAR_SWEEP_TICKS)
    val strip = PixelStrip.MOMENT_SEAL_BAR
    forEachPerimeterCell(center.x, center.y, moment.halfWidth, moment.halfHeight, cellUnits(strip, scale)) { x, y, turns, along ->
        val frame = momentFrame(strip, tick - (MomentDoors.distance(along, perimeter) / pace).toInt())
        if (frame >= 0 && isOnScreen(Offset(x, y), strip.w * PIXEL_FX_TEXEL) && FxDrawBudget.take(FxPool.DUNGEON, 1, signature = true)) {
            drawPixelFrame(strip, frame, x, y, scale, threat, turns = turns)
        }
    }
    FxDrawBudget.take(FxPool.DUNGEON, 1, signature = true)
    drawPixelFrame(PixelStrip.MOMENT_SEAL_LOCK, momentFrame(PixelStrip.MOMENT_SEAL_LOCK, tick), center.x, center.y, scale, threat)
}

/** Ticks after which a second flash runs along every other wall cell of a wave's landing. */
private const val EDGE_SECOND_FLASH = 6

/** A wave landing: a breach tears open at the room's centre while its walls crackle, cell by cell out of step. */
private fun DrawScope.drawWaveLanding(engine: GameplayRenderModel, moment: DungeonMomentProjection, center: Offset, tick: Int, threat: ColorFilter) {
    val scale = engine.worldScale
    val strip = PixelStrip.MOMENT_WAVE_EDGE
    var cell = 0
    forEachPerimeterCell(center.x, center.y, moment.halfWidth, moment.halfHeight, cellUnits(strip, scale)) { x, y, turns, _ ->
        val hash = fxHash(moment.seed, cell++)
        // Each cell crackles up to two ticks late, and every other one flashes again.
        var frame = momentFrame(strip, tick - (hash * 3f).toInt())
        if (frame < 0 && hash < 0.5f) frame = momentFrame(strip, tick - EDGE_SECOND_FLASH - (hash * 6f).toInt())
        if (frame >= 0 && isOnScreen(Offset(x, y), strip.w * PIXEL_FX_TEXEL) && FxDrawBudget.take(FxPool.DUNGEON, 1, signature = true)) {
            drawPixelFrame(strip, frame, x, y, scale, threat, turns = turns)
        }
    }
    FxDrawBudget.take(FxPool.DUNGEON, 1, signature = true)
    drawPixelFrame(PixelStrip.MOMENT_WAVE_BREACH, momentFrame(PixelStrip.MOMENT_WAVE_BREACH, tick), center.x, center.y, scale, threat)
}

/** The guardian's shards start this far out (world units) and land on it as its light crowns it. */
private const val GUARDIAN_PULL = 380f

/** The guardian's entrance: a ring of light closes onto it and crowns it, as a ring of shards is pulled in from afar. */
private fun DrawScope.drawGuardianEntrance(
    engine: GameplayRenderModel,
    moment: DungeonMomentProjection,
    at: Offset,
    tick: Int,
    color: androidx.compose.ui.graphics.Color,
) {
    val palette = PixelPalettes.of(color)
    val scale = engine.worldScale
    val count = pieceCount(GUARDIAN_PULL)
    drawPixelPieces(PixelStrip.MOMENT_GUARDIAN_SHARD, PixelStrip.MOMENT_GUARDIAN_SHARD_D, PieceMotion.IN, at.x, at.y, GUARDIAN_PULL,
        (tick + 0.5f) / MOMENT_FPS, moment.seed, count, scale, palette, pool = FxPool.DUNGEON)
    FxDrawBudget.take(FxPool.DUNGEON, 1, signature = true)
    drawPixelFrame(PixelStrip.MOMENT_GUARDIAN_GATHER, momentFrame(PixelStrip.MOMENT_GUARDIAN_GATHER, tick), at.x, at.y, scale, palette)
}

/** A clear's outline reaches the walls after this many ticks, flashes there for [OUTLINE_FLASH_TICKS], then cools. */
private const val OUTLINE_SWEEP_TICKS = 7
private const val OUTLINE_FLASH_TICKS = 2

/** The finale's second volley of rays leaves this many ticks after the first. */
private const val FINALE_VOLLEY_TICKS = 8

/**
 * A clear: light bursts from the room's centre, an outline sweeps out to its walls and lands there, and its runes ignite
 * one after another round the room; the structure's finale throws two volleys of rays out to the walls and its outline
 * burns longer (its moment lives longer).
 */
private fun DrawScope.drawClear(
    engine: GameplayRenderModel,
    moment: DungeonMomentProjection,
    center: Offset,
    tick: Int,
    light: ColorFilter,
    finale: Boolean,
) {
    val scale = engine.worldScale
    drawClearOutline(moment, center, tick, light, scale)
    if (finale) {
        val reach = max(moment.halfWidth, moment.halfHeight) * 1.1f
        val count = pieceCount(reach)
        for (volley in 0 until 2) {
            drawPixelPieces(PixelStrip.MOMENT_FINALE_RAY, PixelStrip.MOMENT_FINALE_RAY_D, PieceMotion.OUT, center.x, center.y, reach,
                (tick - volley * FINALE_VOLLEY_TICKS + 0.5f) / MOMENT_FPS, moment.seed + volley * 97, count, scale, light, pool = FxPool.DUNGEON)
        }
    }
    FxDrawBudget.take(FxPool.DUNGEON, 1, signature = true)
    drawPixelFrame(PixelStrip.MOMENT_CLEAR_BURST, momentFrame(PixelStrip.MOMENT_CLEAR_BURST, tick), center.x, center.y, scale, light)
    drawIgnitingRunes(engine, moment, center, tick, light)
}

/**
 * The clear's outline: a rect of tiled outline segments walked clockwise (so each segment's outside faces out),
 * stepping out from the centre to the walls, flashing as it lands, then cooling into dots over the rest of the life.
 */
private fun DrawScope.drawClearOutline(moment: DungeonMomentProjection, center: Offset, tick: Int, light: ColorFilter, scale: Float) {
    val strip = PixelStrip.LINK_PLACE_CLEAR
    // It slows as it nears the walls: a quadratic ease out over the sweep's ticks.
    val sweep = 1f - ((tick + 1f) / OUTLINE_SWEEP_TICKS).coerceAtMost(1f)
    val reach = 1f - sweep * sweep
    val landed = tick - OUTLINE_SWEEP_TICKS + 1
    val ticks = (moment.maxLife * MOMENT_FPS).toInt()
    val frame = when {
        landed < 0 -> tick and 1
        landed < OUTLINE_FLASH_TICKS -> 2
        // The last three frames spread over what is left of the life.
        else -> 3 + (3 * (landed - OUTLINE_FLASH_TICKS) / max(1, ticks - OUTLINE_SWEEP_TICKS - OUTLINE_FLASH_TICKS)).coerceAtMost(2)
    }
    val left = center.x - moment.halfWidth * reach
    val top = center.y - moment.halfHeight * reach
    val right = center.x + moment.halfWidth * reach
    val bottom = center.y + moment.halfHeight * reach
    var tiles = drawPixelLink(strip, frame, left, top, right, top, scale, light, maxTiles = ROOM_LINK_TILES)
    tiles += drawPixelLink(strip, frame, right, top, right, bottom, scale, light, maxTiles = ROOM_LINK_TILES)
    tiles += drawPixelLink(strip, frame, right, bottom, left, bottom, scale, light, maxTiles = ROOM_LINK_TILES)
    tiles += drawPixelLink(strip, frame, left, bottom, left, top, scale, light, maxTiles = ROOM_LINK_TILES)
    FxDrawBudget.take(FxPool.DUNGEON, tiles, signature = true)
}

/** The ignition sweeps the whole room in this share of a clear's life (1 / RUNE_SWEEP). */
private const val RUNE_SWEEP = 1.6f

/** The cleared room's runes lighting round the room as the moment runs (a rune flares, then holds lit). */
private fun DrawScope.drawIgnitingRunes(engine: GameplayRenderModel, moment: DungeonMomentProjection, center: Offset, tick: Int, light: ColorFilter) {
    val wall = engine.content.structures.template(moment.template).wallHalfThickness
    // The moment's room as a rect centred on the origin (already turned): its runes, placed at the moment's centre.
    val room = MomentRoom.of(moment.halfWidth, moment.halfHeight)
    // The ignition sweeps the room in 1 / RUNE_SWEEP of the moment. Each rune catches on a tick, plays the glint from
    // there and holds its last frame lit.
    val sweep = moment.maxLife / RUNE_SWEEP * MOMENT_FPS
    forEachRune(room, wall) { x, y, _ ->
        val since = tick - (runeOrder(x, y) * sweep).toInt()
        if (since < 0) return@forEachRune
        val caught = momentFrame(PixelStrip.GLINT, since)
        val frame = if (caught < 0) PixelStrip.GLINT.frames - 1 else caught
        FxDrawBudget.take(FxPool.DUNGEON, 1, signature = true)
        drawPixelFrame(PixelStrip.GLINT, frame, center.x + x, center.y + y, engine.worldScale, light)
    }
}

/** A rune's place in the ignition's sweep round the room (0..1, clockwise from the top). */
private fun runeOrder(x: Float, y: Float): Float {
    val angle = kotlin.math.atan2(x, -y)
    return ((angle / CanvasMath.TAU) + 1f) % 1f
}

/** A reusable axis-aligned room centred on the origin for the ignition's rune walk (draw-thread scratch). */
private object MomentRoom {
    private var room = kinetickk.ball.content.api.StructureRoom(-200f, -200f, 200f, 200f)

    fun of(halfWidth: Float, halfHeight: Float): kinetickk.ball.content.api.StructureRoom {
        val held = room
        if (held.maxX == halfWidth && held.maxY == halfHeight) return held
        return kinetickk.ball.content.api.StructureRoom(-halfWidth, -halfHeight, halfWidth, halfHeight).also { room = it }
    }
}

/** A door counts as on a moment's wall within this many units of the wall's line. */
private const val DOOR_TOLERANCE = 4f

/**
 * Places of the doors on a moment's room walls, clockwise from the top left corner (units), for the seal's bars:
 * the held structure's doors that lie on the room's walls, else the middles of its four walls. Draw-thread scratch.
 */
private object MomentDoors {
    private const val MAX_DOORS = 8
    private val along = FloatArray(MAX_DOORS)
    private var count = 0

    /** Finds the doors of [moment]'s room and returns how many there are (at least one). */
    fun find(engine: GameplayRenderModel, moment: DungeonMomentProjection): Int {
        count = 0
        val structure = heldStructure(engine)
        if (structure != null && structure.template == moment.template) {
            val doors = engine.content.structures.template(structure.template).doors
            for (index in doors.indices) {
                val door = doors[index]
                val vertical = door.axis == WallAxis.VERTICAL
                val localX = if (vertical) door.line else door.center
                val localY = if (vertical) door.center else door.line
                val place = place(structure.x + rotateQuarterX(localX, localY, structure.rotation) - moment.x,
                    structure.y + rotateQuarterY(localX, localY, structure.rotation) - moment.y, moment.halfWidth, moment.halfHeight)
                if (place >= 0f && count < MAX_DOORS) along[count++] = place
            }
        }
        if (count == 0) {
            val width = moment.halfWidth * 2f
            val height = moment.halfHeight * 2f
            along[0] = width * 0.5f
            along[1] = width + height * 0.5f
            along[2] = width * 1.5f + height
            along[3] = width * 2f + height * 1.5f
            count = 4
        }
        return count
    }

    /** The walk along the walls from [at] to the nearest door found last, round walls [perimeter] units long. */
    fun distance(at: Float, perimeter: Float): Float {
        var nearest = perimeter
        for (index in 0 until count) {
            val gap = abs(at - along[index])
            nearest = min(nearest, min(gap, perimeter - gap))
        }
        return nearest
    }

    /** The place along the walls of the point ([dx], [dy]) from the room's centre, or −1 when it is not on a wall. */
    private fun place(dx: Float, dy: Float, halfWidth: Float, halfHeight: Float): Float = when {
        abs(dy + halfHeight) < DOOR_TOLERANCE && abs(dx) <= halfWidth -> dx + halfWidth
        abs(dx - halfWidth) < DOOR_TOLERANCE && abs(dy) <= halfHeight -> 2f * halfWidth + dy + halfHeight
        abs(dy - halfHeight) < DOOR_TOLERANCE && abs(dx) <= halfWidth -> 3f * halfWidth + 2f * halfHeight - dx
        abs(dx + halfWidth) < DOOR_TOLERANCE && abs(dy) <= halfHeight -> 4f * halfWidth + 3f * halfHeight - dy
        else -> -1f
    }
}
