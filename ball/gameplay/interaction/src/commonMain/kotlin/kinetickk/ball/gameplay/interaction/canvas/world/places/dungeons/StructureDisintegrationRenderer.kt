// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.interaction.canvas.world.places.dungeons

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import kinetickk.ball.content.api.StructureTemplate
import kinetickk.ball.content.api.rotateQuarterX
import kinetickk.ball.content.api.rotateQuarterY
import kinetickk.ball.gameplay.interaction.canvas.*
import kinetickk.ball.gameplay.interaction.canvas.fx.*
import kinetickk.ball.gameplay.interaction.fx.PixelFxClock
import kinetickk.ball.gameplay.interaction.fx.PixelStrip
import kinetickk.ball.gameplay.interaction.fx.StructureDisintegrationProjection
import kinetickk.ball.gameplay.interaction.fx.VisualFxProjection
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.foundation.design.*
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.sqrt

/*
 * A spent structure shattering over its disintegration (the FX reducer's StructureDisintegrationProjection, never read
 * back): its floor plates fade level by level on the debris strip's ticks, its slabs crack with pixel crack flashes
 * running along them, then every solid wall breaks into up to four pieces of pixel rubble that tumble outward from the
 * centre over puffs of dust. Floors and slabs come from the cached StructurePaths and SlabPieces; the cracks and the
 * rubble are shapes placed from the template walls with an integer hash, so a frame allocates nothing and builds
 * no path. Rubble takes FxPool.RUBBLE operations and stops for the frame when it refuses; the fading floor and the
 * cracks always draw. The simulation's shockwave here is an accent that draws no ring, since the rubble is the look; the
 * burst comes from the simulation's own cue.
 */

/** The slabs crack for this share of the disintegration; shards start at [SHARD_START]. */
private const val CRACK_END = 0.18f
private const val SHARD_START = 0.10f

/** The farthest a shard drifts (world units), used to keep a shattering structure drawn while any shard is in view. */
private const val SHARD_MAX_DRIFT = 240f
private const val SLAB_REACH = 80f
private const val VIEW_MARGIN = 40f

/** A wall is cut into one shard per this many units, at most [MAX_SHARDS_PER_WALL], with an 8 % gap between them. */
private const val SHARD_LENGTH = 120f
private const val MAX_SHARDS_PER_WALL = 4
private const val SHARD_GAP = 0.08f

/** A crack flash runs along a slab every this many units; each starts up to [CRACK_STAGGER_TICKS] debris ticks late. */
private const val CRACK_SPACING = 52f
private const val CRACK_STAGGER_TICKS = 2

/** The floor's fixed fade levels over the first four fifths of the life; it is gone for the last fifth. */
private val FloorFade = floatArrayOf(1f, 0.6f, 0.32f, 0.12f)
private const val FLOOR_STEPS = 5

/** Draws every live disintegration in view, after the standing structures' walls and before the obstacles. */
internal fun DrawScope.drawStructureDisintegrations(
    engine: GameplayRenderModel,
    visualFx: VisualFxProjection,
    shakeX: Float,
    shakeY: Float,
    roles: KkRolePalette,
) {
    val disintegrations = visualFx.disintegrations
    if (disintegrations.isEmpty()) return
    for (index in disintegrations.indices) {
        val disintegration = disintegrations[index]
        val template = engine.content.structures.template(disintegration.template)
        val center = world(engine, disintegration.x, disintegration.y, shakeX, shakeY)
        if (!isOnScreen(center, template.circumradius + SLAB_REACH + SHARD_MAX_DRIFT + VIEW_MARGIN)) continue
        val progress = (1f - engine.fxLife(disintegration.life, disintegration.maxLife) / disintegration.maxLife).coerceIn(0f, 1f)
        val accent = structureAccent(template, roles)
        // Seconds since the structure broke, held to the debris strip's ticks: the floor, the cracks and the rubble step together.
        val age = PixelFxClock.stepped(progress * disintegration.maxLife, PixelStrip.DEBRIS.fps)
        drawFadingFloor(template, disintegration.rotation, center, age / disintegration.maxLife, roles, accent)
        if (progress < CRACK_END) drawCrackingSlabs(template, disintegration, center, age, accent, engine.worldScale)
        if (progress >= SHARD_START) drawShards(template, disintegration, center, progress, accent, engine.worldScale)
    }
}

/** The floor plates (or the Doodle's planet) fading through the fixed levels of [FloorFade] as [progress] passes each fifth. */
private fun DrawScope.drawFadingFloor(
    template: StructureTemplate,
    rotation: Int,
    center: Offset,
    progress: Float,
    roles: KkRolePalette,
    accent: androidx.compose.ui.graphics.Color,
) {
    val step = (progress * FLOOR_STEPS).toInt()
    if (step >= FloorFade.size) return
    val fade = FloorFade[step]
    if (template.roomWalls) {
        translate(center.x, center.y) { drawPath(StructurePaths.floor(template, rotation), structureFloorColor(template, roles), alpha = fade) }
        return
    }
    val room = template.rooms[template.finalRoom]
    val localX = (room.minX + room.maxX) * 0.5f
    val localY = (room.minY + room.maxY) * 0.5f
    val planet = Offset(center.x + rotateQuarterX(localX, localY, rotation), center.y + rotateQuarterY(localX, localY, rotation))
    drawCircle(accent, DOODLE_PLANET_RADIUS, planet, alpha = 0.06f * fade)
}

/**
 * The cached slabs in ink, slab by slab for the slabs in view, with crack flashes running along each one: a cell every
 * [CRACK_SPACING] units of its wall, turned along it and mirrored by its hash, each starting up to
 * [CRACK_STAGGER_TICKS] ticks late, so the structure visibly cracks before it breaks. [age] is held to the debris
 * strip's ticks (the crack strip's own rate).
 */
private fun DrawScope.drawCrackingSlabs(
    template: StructureTemplate,
    disintegration: StructureDisintegrationProjection,
    center: Offset,
    age: Float,
    accent: androidx.compose.ui.graphics.Color,
    scale: Float,
) {
    val rotation = disintegration.rotation
    val slabs = SlabPieces.of(template, rotation)
    translate(center.x, center.y) {
        for (slab in 0 until slabs.count) if (slabs.inView(slab, center, size)) drawPath(slabs.outline(slab), Kk.Ink3)
    }
    val strip = PixelStrip.RUIN_CRACK
    val palette = PixelPalettes.of(kkMix(Kk.Bone, accent, 0.5f))
    val walls = template.walls
    var slab = 0
    for (wallIndex in walls.indices) {
        val wall = walls[wallIndex]
        if (wall.isDoor) continue
        if (!slabs.inView(slab++, center, size)) continue
        val ax = rotateQuarterX(wall.ax, wall.ay, rotation)
        val ay = rotateQuarterY(wall.ax, wall.ay, rotation)
        val bx = rotateQuarterX(wall.bx, wall.by, rotation)
        val by = rotateQuarterY(wall.bx, wall.by, rotation)
        val cracks = max(1, (sqrt((bx - ax) * (bx - ax) + (by - ay) * (by - ay)) / CRACK_SPACING).toInt())
        val turns = if (abs(by - ay) > abs(bx - ax)) 1 else 0
        for (crack in 0 until cracks) {
            val hash = fxHash(disintegration.seed, wallIndex * 61 + crack)
            val frame = PixelFxClock.frame(strip, age - (hash * (CRACK_STAGGER_TICKS + 1)).toInt() / strip.fps.toFloat())
            if (frame < 0 || !FxDrawBudget.take(FxPool.RUBBLE, 1, signature = true)) continue
            val along = (crack + 0.5f) / cracks
            drawPixelFrame(strip, frame, center.x + ax + (bx - ax) * along, center.y + ay + (by - ay) * along, scale, palette,
                flipX = hash > 0.5f, flipY = (hash * 8f).toInt() and 1 == 1, turns = turns)
        }
    }
}

/**
 * The solid walls as pixel-art rubble: each wall is cut into pieces that leave as tumbling chunks, drifting outward
 * along the direction from the centre to their midpoint and sliding along the wall, over a puff of dust where the
 * piece stood. Chunks move and tumble on the debris strip's ticks, not between them. Stops at the first piece
 * [FxPool.RUBBLE] refuses.
 */
private fun DrawScope.drawShards(
    template: StructureTemplate,
    disintegration: StructureDisintegrationProjection,
    center: Offset,
    progress: Float,
    accent: androidx.compose.ui.graphics.Color,
    scale: Float,
) {
    val strip = PixelStrip.DEBRIS
    // Seconds since the walls cracked, held to the debris strip's ticks.
    val cracked = PixelFxClock.stepped(progress * disintegration.maxLife, strip.fps) - SHARD_START * disintegration.maxLife
    if (cracked < 0f) return
    val q = (cracked / ((1f - SHARD_START) * disintegration.maxLife)).coerceIn(0f, 1f)
    val ease = 1f - (1f - q) * (1f - q)
    val chunk = PixelPalettes.of(kkMix(Kk.Bone, accent, 0.3f))
    val dust = PixelPalettes.of(Kk.Mute2)
    val rotation = disintegration.rotation
    val walls = template.walls
    for (wallIndex in walls.indices) {
        val wall = walls[wallIndex]
        if (wall.isDoor) continue
        val ax = rotateQuarterX(wall.ax, wall.ay, rotation)
        val ay = rotateQuarterY(wall.ax, wall.ay, rotation)
        val bx = rotateQuarterX(wall.bx, wall.by, rotation)
        val by = rotateQuarterY(wall.bx, wall.by, rotation)
        val length = sqrt((bx - ax) * (bx - ax) + (by - ay) * (by - ay))
        val pieces = ceil(length / SHARD_LENGTH).toInt().coerceIn(1, MAX_SHARDS_PER_WALL)
        // A pillar (zero length) keeps the x axis as its direction.
        val ux = if (length > 0.001f) (bx - ax) / length else 1f
        val uy = if (length > 0.001f) (by - ay) / length else 0f
        for (piece in 0 until pieces) {
            if (!FxDrawBudget.take(FxPool.RUBBLE, 1, signature = false)) return
            val seed = disintegration.seed xor (wallIndex * 31 + piece)
            val start = (piece + SHARD_GAP * 0.5f) / pieces
            val end = (piece + 1 - SHARD_GAP * 0.5f) / pieces
            val midX = ax + (bx - ax) * (start + end) * 0.5f
            val midY = ay + (by - ay) * (start + end) * 0.5f
            val distance = sqrt(midX * midX + midY * midY)
            val dirX = if (distance > 1f) midX / distance else -uy
            val dirY = if (distance > 1f) midY / distance else ux
            val drift = ease * (70f + 160f * fxHash(seed, 1))
            val slide = ease * (fxHash(seed, 2) - 0.5f) * 60f
            val puff = PixelFxClock.frame(PixelStrip.DUST, cracked - 0.1f * fxHash(seed, 3))
            if (puff >= 0 && FxDrawBudget.take(FxPool.RUBBLE, 1, signature = false)) {
                drawPixelFrame(PixelStrip.DUST, puff, center.x + midX, center.y + midY, scale, dust)
            }
            val frame = if (q > 0.86f) 7 else if (q > 0.72f) 6 else PixelFxClock.loopFrame(strip, cracked, 0, 6, seed and 7)
            drawPixelFrame(strip, frame, center.x + midX + dirX * drift + ux * slide, center.y + midY + dirY * drift + uy * slide, scale, chunk,
                flipX = seed and 8 != 0)
        }
    }
}
