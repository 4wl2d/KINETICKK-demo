// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.interaction.canvas.world.places.dungeons

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import kinetickk.ball.content.api.STRUCTURE_OUTSIDE
import kinetickk.ball.content.api.StructureRoom
import kinetickk.ball.content.api.StructureTemplate
import kinetickk.ball.content.api.StructureTemplateId
import kinetickk.ball.content.api.roomAt
import kinetickk.ball.content.api.rotateQuarterX
import kinetickk.ball.content.api.rotateQuarterY
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.nucleus.render.StructureProjection
import kinetickk.ball.gameplay.nucleus.render.StructureRenderProjection
import kinetickk.ball.gameplay.nucleus.render.TrialProjection
import kinetickk.ball.gameplay.nucleus.render.WallKind
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkRolePalette
import kinetickk.foundation.design.d
import kinetickk.foundation.design.drawKkIcon
import kinetickk.foundation.design.isOnScreen
import kinetickk.foundation.design.kkMix
import kinetickk.foundation.design.kkShear
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kinetickk.ball.gameplay.interaction.canvas.*
import kinetickk.ball.gameplay.interaction.canvas.hud.*
import kinetickk.ball.gameplay.interaction.canvas.look.*
import kinetickk.ball.gameplay.interaction.canvas.world.*
import kinetickk.ball.gameplay.interaction.canvas.world.places.*

/*
 * Structures and dungeons in the world layer: room floor plates (their detail, lights and runes in DungeonFloorRenderer),
 * wall slabs with depth, doors and sconces (DungeonWallRenderer), the sealed room's brackets (RoomBracketRenderer), the
 * final room's plates and the scrim outside the dungeon the Core is inside; their entrances as edge markers and on the
 * minimap; on the HUD the dungeon trial panel's label, rules, rewards and sealed-room cell, and the vignette with the
 * sealed room's alarm. Ink dominates, bone reads as solid, HATCH as locked, threat only for danger (a live garrison's
 * barrier, brackets and alarm, the fight's telegraphs, the threshold of a structure still to fight) and volt only for the
 * player's next way; no arrow, numeral or text marks a structure. Room and wall shapes come from the run's content
 * template, cached per template and rotation in StructurePaths, StructureRects and SlabPieces (never from projection
 * floats, so a rebase keeps them); positions, doors and progress come from the projection. The renderer culls room
 * rectangles and slab paths to the view. The occlusion band and translucent shadow keep their union paths, and the
 * scrim uses a path when more than four cuts are visible. Allocation-free per frame once the shapes are cached.
 */

/** A structure is drawn while its footprint, its thickest slab and this margin meet the view (world units). */
private const val VIEW_MARGIN = 40f

/** The thickest slab content accepts (a pillar's half thickness is at most this). */
private const val SLAB_REACH = 80f

/** Neutral floor for content without an elemental identity. */
private val FloorColor = kkMix(Kk.Ink, Kk.Ink1, 0.6f)

/** Elemental floors stay dark enough for enemies, projectiles and door states to read above them. */
internal fun structureFloorColor(template: StructureTemplate, roles: KkRolePalette): Color =
    template.element?.let { kkMix(FloorColor, roles.element(it.ordinal), 0.075f) } ?: FloorColor

internal fun structureAccent(template: StructureTemplate, roles: KkRolePalette): Color =
    template.element?.let { roles.element(it.ordinal) } ?: Kk.Bone

/** The scrim's ink at full depth, and the depth inside the footprint at which it is full (world units). */
private const val SCRIM_ALPHA = 0.42f
private const val INTERIOR_FADE = 240f

/** The Doodle's planet around its one room (the radius of its ring of chords). */
internal const val DOODLE_PLANET_RADIUS = 672f

/** An edge marker stands in for an entrance this far off the view at least (world units), like the Totem chest's. */
private const val ENTRANCE_MARK_MARGIN = 60f

/** No room: the Core is not inside the structure, or the structure is not the dungeon. */
private const val NO_ROOM = -2

/** Room floor plates of the structures in view, under the obstacles: cleared rooms lighter, the Doodle's planet. */
internal fun DrawScope.drawStructureFloors(engine: GameplayRenderModel, shakeX: Float, shakeY: Float, roles: KkRolePalette) {
    val structures = engine.structures.structures
    if (structures.isEmpty()) return
    for (index in structures.indices) {
        val structure = structures[index]
        val template = engine.content.structures.template(structure.template)
        val center = world(engine, structure.x, structure.y, shakeX, shakeY)
        if (!structureInView(center, structure)) continue
        val rotation = structure.rotation
        val rooms = template.rooms
        val accent = structureAccent(template, roles)
        if (template.id == StructureTemplateId.ARCHITECTS_DOODLE) {
            val room = rooms[template.finalRoom]
            drawCircle(accent.copy(alpha = 0.06f), DOODLE_PLANET_RADIUS, roomCenter(center, room, rotation))
            template.element?.let { drawKkIcon(elementIcon(it.ordinal), roomCenter(center, room, rotation), 180f, accent, 0.18f) }
        }
        // Rooms without walls (the Doodle's) have no plate: the planet is their floor.
        if (!template.roomWalls) continue
        // The plate is opaque, so its rects may overlap: the seams between rooms are covered twice and never show.
        val plate = StructureRects.of(template, rotation)
        val floor = structureFloorColor(template, roles)
        for (rect in 0 until plate.count) {
            if (!plate.inView(rect, center, size, 0f)) continue
            drawRect(floor, Offset(center.x + plate.left(rect), center.y + plate.top(rect)), plate.size(rect))
        }
        drawDungeonFloor(template, structure, engine.structures.dungeon, center, engine.renderElapsed, roles)
        val cleared = structure.clearedRooms
        for (room in rooms.indices) {
            val spec = rooms[room]
            // A shape as well as a hue identifies the element, including the monochrome palette.
            template.element?.let {
                drawKkIcon(elementIcon(it.ordinal), roomCenter(center, spec, rotation), 104f, accent, 0.16f)
            }
            if (cleared and (1 shl room) == 0) continue
            val left = center.x + roomLeft(spec, rotation)
            val top = center.y + roomTop(spec, rotation)
            drawRect(Kk.Bone.copy(alpha = 0.025f), Offset(left, top),
                Size(center.x + roomRight(spec, rotation) - left, center.y + roomBottom(spec, rotation) - top))
        }
    }
}

/**
 * Wall slabs and doors of the structures in view, right after their floors: each slab a bone-rimmed ink capsule with
 * a darker spine, open doors as two bone dashes (volt for the player's next way), sealed doors as hatched slabs edged
 * in threat while their room's garrison is alive, the sealed room's threat brackets and the final room's
 * plates until it is cleared (a filled key block with charge pips when a Totem chest waits; none for a secret template).
 */
internal fun DrawScope.drawStructureWalls(engine: GameplayRenderModel, shakeX: Float, shakeY: Float, roles: KkRolePalette) {
    val projection = engine.structures
    val structures = projection.structures
    if (structures.isEmpty()) return
    val walls = projection.walls
    val dungeon = projection.dungeon
    for (index in structures.indices) {
        val structure = structures[index]
        val template = engine.content.structures.template(structure.template)
        val center = world(engine, structure.x, structure.y, shakeX, shakeY)
        if (!structureInView(center, structure)) continue
        val rotation = structure.rotation
        val accent = structureAccent(template, roles)
        drawDungeonSlabs(template, rotation, center, accent)
        val current = dungeon != null && dungeon.structureId == structure.id
        val coreRoom = if (current) {
            template.roomAt(engine.renderCoreX - structure.x, engine.renderCoreY - structure.y, rotation)
        } else {
            NO_ROOM
        }
        val engaged = structure.engagedRoom
        for (wallIndex in structure.wallStart until structure.wallEnd) {
            val wall = walls[wallIndex]
            val a = world(engine, wall.ax, wall.ay, shakeX, shakeY)
            val b = world(engine, wall.bx, wall.by, shakeX, shakeY)
            when (wall.kind) {
                WallKind.SOLID -> drawSlabCrest(a, b, wall.halfThickness, accent)
                WallKind.DOOR_OPEN -> {
                    val next = current && leadsOnward(template, wall.door, structure.clearedRooms, coreRoom)
                    val color = if (next) roles.you.copy(alpha = 0.8f) else kkMix(Kk.Bone, accent, 0.3f).copy(alpha = 0.35f)
                    drawDungeonOpenDoor(a, b, color, next, engine.renderElapsed)
                }
                WallKind.DOOR_SEALED -> {
                    val live = engaged >= 0 && doorTouches(template, wall.door, engaged)
                    drawDungeonSealedDoor(a, b, wall.halfThickness, live, dungeon?.sealedAt ?: -1f, engine.renderElapsed, roles)
                }
            }
        }
        if (template.dungeon) drawDungeonSconces(template, structure, dungeon, center, engine.renderElapsed, roles)
        if (engaged >= 0 && engaged < template.rooms.size) {
            drawRoomBrackets(center, template.rooms[engaged], rotation, engine.renderElapsed, dungeon?.sealedAt ?: -1f, engine.worldScale, roles)
        }
        if (!template.secret && structure.clearedRooms and (1 shl template.finalRoom) == 0) {
            // The Totem chest's own plates promise the reward: a key block and a pip per charge when one waits.
            val promise = roomCenter(center, template.rooms[template.finalRoom], rotation)
            val chest = structure.chestCharges > 0
            drawTotemPlates(promise, TOTEM_CHEST_SCALE, accent, keyBlock = chest, keyIcon = chest)
            drawTotemChargePips(promise, TOTEM_CHEST_SCALE, structure.chestCharges, accent)
        }
    }
}

/** The ink scrim outside the dungeon's rooms while the Core is in one of them, over the Core and under the singularity. */
internal fun DrawScope.drawStructureScrim(engine: GameplayRenderModel, shakeX: Float, shakeY: Float) {
    val amount = structureInteriorAmount(engine)
    if (amount <= 0.01f) return
    val structure = dungeonStructure(engine.structures) ?: return
    val template = engine.content.structures.template(structure.template)
    val center = world(engine, structure.x, structure.y, shakeX, shakeY)
    val rooms = StructureRects.of(template, structure.rotation)
    var cuts = 0
    for (rect in 0 until rooms.count) if (rooms.inView(rect, center, size, SCRIM_MARGIN)) cuts++
    if (cuts > MAX_SCRIM_CUTS) {
        translate(center.x, center.y) {
            drawPath(StructurePaths.scrim(template, structure.rotation), Kk.Ink, alpha = SCRIM_ALPHA * amount)
        }
        return
    }
    // The view in ink with the rooms in view cut out: rect clips stay analytic, where the even-odd scrim path would need a
    // mask of the whole screen.
    val canvas = drawContext.canvas
    canvas.save()
    for (rect in 0 until rooms.count) {
        if (!rooms.inView(rect, center, size, SCRIM_MARGIN)) continue
        val left = center.x + rooms.left(rect)
        val top = center.y + rooms.top(rect)
        canvas.clipRect(left, top, left + rooms.width(rect), top + rooms.height(rect), ClipOp.Difference)
    }
    drawRect(Kk.Ink, Offset(-SCRIM_MARGIN, -SCRIM_MARGIN), Size(size.width + SCRIM_MARGIN * 2f, size.height + SCRIM_MARGIN * 2f),
        alpha = SCRIM_ALPHA * amount)
    canvas.restore()
}

/** The scrim covers the view and this margin (world units); at most this many room rects are cut out of it by clips. */
private const val SCRIM_MARGIN = 8f
private const val MAX_SCRIM_CUTS = 4

/**
 * How far the drawn Core is inside the dungeon (0..1): 0 without a dungeon, for a secret template or while the Core is
 * in none of its rooms (no scrim, no vignette: a Core outside the walls in a notch of an L-shaped footprint is in open
 * space), else its depth inside the footprint over [INTERIOR_FADE], so the scrim fades in on the way in and out on the
 * way out without any presentation memory. Outer doors lie on the footprint's edge and rooms meet edge to edge, so the
 * room test never cuts the fade short on a way the Core can fly.
 */
internal fun structureInteriorAmount(engine: GameplayRenderModel): Float {
    val dungeon = engine.structures.dungeon ?: return 0f
    if (!dungeon.panel) return 0f
    val structure = dungeonStructure(engine.structures) ?: return 0f
    val dx = engine.renderCoreX - structure.x
    val dy = engine.renderCoreY - structure.y
    val depth = min(structure.halfWidth - abs(dx), structure.halfHeight - abs(dy))
    if (depth <= 0f) return 0f
    val template = engine.content.structures.template(structure.template)
    if (template.roomAt(dx, dy, structure.rotation) == STRUCTURE_OUTSIDE) return 0f
    return (depth / INTERIOR_FADE).coerceIn(0f, 1f)
}

/**
 * Edge markers of structure entrances off screen, after the dimension markers: the two nearest to the Core, never the
 * dungeon the Core is in (the window announces at most four).
 */
internal fun DrawScope.collectStructureEdgeMarkers(engine: GameplayRenderModel, batch: EdgeMarkerBatch) {
    val projection = engine.structures
    val signals = projection.signals
    if (signals.isEmpty()) return
    val dungeon = projection.dungeon
    var nearest = -1
    var nearestDistance = Float.MAX_VALUE
    var second = -1
    var secondDistance = Float.MAX_VALUE
    for (index in signals.indices) {
        val signal = signals[index]
        if (dungeon != null && dungeon.structureId == signal.id) continue
        if (isOnScreen(world(engine, signal.x, signal.y, 0f, 0f), ENTRANCE_MARK_MARGIN)) continue
        val dx = signal.x - engine.coreX
        val dy = signal.y - engine.coreY
        val distance = sqrt(dx * dx + dy * dy)
        if (distance < nearestDistance) {
            second = nearest
            secondDistance = nearestDistance
            nearest = index
            nearestDistance = distance
        } else if (distance < secondDistance) {
            second = index
            secondDistance = distance
        }
    }
    val scale = engine.worldScale
    if (nearest >= 0) {
        val target = world(engine, signals[nearest].x, signals[nearest].y, 0f, 0f)
        batch.add(target.x * scale, target.y * scale, nearestDistance, EdgeMarkerIcon.STRUCTURE)
    }
    if (second >= 0) {
        val target = world(engine, signals[second].x, signals[second].y, 0f, 0f)
        batch.add(target.x * scale, target.y * scale, secondDistance, EdgeMarkerIcon.STRUCTURE)
    }
}

/**
 * Walls, sealed doors and entrances on the minimap, inside its clip, after the obstacles: walls as faint bone lines,
 * sealed doors firmer (threat while their room's garrison is alive), open doors left out, and each entrance a hollow
 * bone square pinned to the rim when beyond the range.
 */
internal fun DrawScope.drawMinimapStructures(
    engine: GameplayRenderModel,
    centerX: Float,
    centerY: Float,
    half: Float,
    scale: Float,
    dot: Float,
    roles: KkRolePalette,
) {
    val projection = engine.structures
    val structures = projection.structures
    val signals = projection.signals
    if (structures.isEmpty() && signals.isEmpty()) return
    val coreX = engine.renderCoreX
    val coreY = engine.renderCoreY
    val walls = projection.walls
    val wallWidth = max(1f, dot * 0.6f)
    val doorWidth = max(1.5f, dot * 0.9f)
    for (index in structures.indices) {
        val structure = structures[index]
        val template = engine.content.structures.template(structure.template)
        val accent = structureAccent(template, roles)
        val engaged = structure.engagedRoom
        for (wallIndex in structure.wallStart until structure.wallEnd) {
            val wall = walls[wallIndex]
            if (wall.kind == WallKind.DOOR_OPEN) continue
            val ax = centerX + (wall.ax - coreX) * scale
            val ay = centerY + (wall.ay - coreY) * scale
            val bx = centerX + (wall.bx - coreX) * scale
            val by = centerY + (wall.by - coreY) * scale
            // Clipped by the map's clip; only a wall wholly to one side of the square is skipped.
            if (max(ax, bx) < centerX - half || min(ax, bx) > centerX + half || max(ay, by) < centerY - half ||
                min(ay, by) > centerY + half
            ) {
                continue
            }
            if (wall.kind == WallKind.SOLID) {
                drawLine(kkMix(Kk.Bone, accent, 0.3f).copy(alpha = 0.45f), Offset(ax, ay), Offset(bx, by), wallWidth)
            } else {
                val live = engaged >= 0 && doorTouches(template, wall.door, engaged)
                drawLine(if (live) roles.threat.copy(alpha = 0.9f) else Kk.Bone.copy(alpha = 0.7f), Offset(ax, ay), Offset(bx, by), doorWidth)
            }
        }
    }
    val mark = dot * 2.4f
    for (index in signals.indices) {
        val signal = signals[index]
        val accent = structureAccent(engine.content.structures.template(signal.template), roles)
        drawPinnedMark(centerX, centerY, half, (signal.x - coreX) * scale, (signal.y - coreY) * scale, mark, accent, filled = false)
    }
}

// ---------------------------------------------------------------------------------------------
// HUD: the dungeon's trial panel pieces and the vignette.

/** The vignette is clear out to this share of the corner radius and this ink alpha at the screen's corners. */
private const val VIGNETTE_CLEAR = 0.55f
private const val VIGNETTE_ALPHA = 0.5f

/** The sealed room's meter cell pulses between these alphas on the brackets' loop (a cleared cell is solid). */
private const val SEGMENT_PULSE_MIN = 0.2f
private const val SEGMENT_PULSE_SPAN = 0.35f

/**
 * The sealed room's cell of the dungeon panel's meter ([meter], drawn with the trial's segments, the cleared rooms
 * filled from the left): while a room is sealed the first empty cell pulses in volt on the brackets' loop, brightest
 * as their corners lock ([roomBracketBeat]). A sealed room always counts (only a garrisoned room seals), so that cell
 * is its own.
 */
internal fun DrawScope.drawSealedRoomSegment(engine: GameplayRenderModel, trial: TrialProjection, meter: Rect, roles: KkRolePalette) {
    val dungeon = engine.structures.dungeon ?: return
    val segments = trial.segments
    val index = trial.done.roundToInt()
    if (dungeon.engagedRoom < 0 || segments <= 0 || index < 0 || index >= segments) return
    val pitch = meter.width / segments
    val gap = d(3f).coerceAtMost(pitch * 0.5f)
    val alpha = SEGMENT_PULSE_MIN + SEGMENT_PULSE_SPAN * roomBracketBeat(roomBracketAge(engine.renderElapsed, dungeon.sealedAt))
    withTransform({ kkShear(meter.center.y) }) {
        drawRect(roles.you, Offset(meter.left + index * pitch, meter.top), Size(pitch - gap, meter.height), alpha)
    }
}

/**
 * The HUD vignette while the Core is inside a dungeon: ink closing in from the screen's edges (clear out to
 * [VIGNETTE_CLEAR] of the corner radius, [VIGNETTE_ALPHA] at the corners), as deep as the world's scrim, so both fade in
 * on the way in and out on the way out, and while a room is sealed its threat alarm over it. None for a secret template
 * or outside the dungeon's rooms.
 */
internal fun DrawScope.drawDungeonVignette(engine: GameplayRenderModel, roles: KkRolePalette, time: Float) {
    val amount = structureInteriorAmount(engine)
    if (amount <= 0.01f) return
    drawScreenVignette(HudBrush.DUNGEON, VIGNETTE_CLEAR, Kk.Ink.copy(alpha = VIGNETTE_ALPHA), amount)
    // A sealed room's alarm closes in from the edges at the brackets' tempo, harder while the room's guardian lives. It
    // counts on the HUD's presentation clock ([time]), not the run's, so it keeps their rate but not their phase.
    val dungeon = engine.structures.dungeon ?: return
    if (dungeon.engagedRoom < 0) return
    val edge = roles.threat.copy(alpha = if (dungeon.guardianId >= 0) ALARM_GUARDIAN_ALPHA else ALARM_ALPHA)
    drawScreenVignette(HudBrush.DUNGEON_ALARM, ALARM_CLEAR, edge, amount * (0.45f + 0.55f * roomBracketBeat(time)))
}

/** The alarm vignette is clear out to this share of the corner radius, this threat alpha at the corners (guardian: more). */
private const val ALARM_CLEAR = 0.62f
private const val ALARM_ALPHA = 0.3f
private const val ALARM_GUARDIAN_ALPHA = 0.45f

// ---------------------------------------------------------------------------------------------
// Pieces.

/** Whether the footprint of [structure] centred at [center] (layer units), with slabs and margin, meets the view. */
private fun DrawScope.structureInView(center: Offset, structure: StructureProjection): Boolean {
    val reachX = structure.halfWidth + SLAB_REACH + VIEW_MARGIN
    val reachY = structure.halfHeight + SLAB_REACH + VIEW_MARGIN
    return center.x + reachX >= 0f && center.x - reachX <= size.width && center.y + reachY >= 0f && center.y - reachY <= size.height
}

/** The windowed structure the dungeon holds, or null. */
private fun dungeonStructure(projection: StructureRenderProjection): StructureProjection? {
    val dungeon = projection.dungeon ?: return null
    val structures = projection.structures
    for (index in structures.indices) if (structures[index].id == dungeon.structureId) return structures[index]
    return null
}

/** Whether door [door] of [template] opens onto [room]. */
private fun doorTouches(template: StructureTemplate, door: Int, room: Int): Boolean {
    if (door < 0 || door >= template.doors.size) return false
    val spec = template.doors[door]
    return spec.roomA == room || spec.roomB == room
}

/**
 * Whether open door [door] is the player's next way: it joins a room reached (cleared, or the one the Core is in;
 * the outside while the Core is in no room) to a room that still counts (a garrison or the final room) and is not clear.
 */
private fun leadsOnward(template: StructureTemplate, door: Int, cleared: Int, coreRoom: Int): Boolean {
    if (door < 0 || door >= template.doors.size) return false
    val spec = template.doors[door]
    val counted = template.roomsToClearMask
    return (reached(spec.roomA, cleared, coreRoom) && pending(spec.roomB, cleared, counted)) ||
        (reached(spec.roomB, cleared, coreRoom) && pending(spec.roomA, cleared, counted))
}

private fun reached(room: Int, cleared: Int, coreRoom: Int): Boolean =
    if (room == STRUCTURE_OUTSIDE) coreRoom == STRUCTURE_OUTSIDE else room == coreRoom || cleared and (1 shl room) != 0

private fun pending(room: Int, cleared: Int, counted: Int): Boolean =
    room != STRUCTURE_OUTSIDE && counted and (1 shl room) != 0 && cleared and (1 shl room) == 0

/** The centre of [room] turned by [rotation], as a point from the structure centre [center]. */
internal fun roomCenter(center: Offset, room: StructureRoom, rotation: Int): Offset {
    val x = (room.minX + room.maxX) * 0.5f
    val y = (room.minY + room.maxY) * 0.5f
    return Offset(center.x + rotateQuarterX(x, y, rotation), center.y + rotateQuarterY(x, y, rotation))
}

// The bounds of a room turned by a quarter rotation (quarter turns keep rooms axis-aligned).
internal fun roomLeft(room: StructureRoom, rotation: Int): Float = when (rotation and 3) {
    0 -> room.minX
    1 -> -room.maxY
    2 -> -room.maxX
    else -> room.minY
}

internal fun roomTop(room: StructureRoom, rotation: Int): Float = when (rotation and 3) {
    0 -> room.minY
    1 -> room.minX
    2 -> -room.maxY
    else -> -room.maxX
}

internal fun roomRight(room: StructureRoom, rotation: Int): Float = when (rotation and 3) {
    0 -> room.maxX
    1 -> -room.minY
    2 -> -room.minX
    else -> room.maxY
}

internal fun roomBottom(room: StructureRoom, rotation: Int): Float = when (rotation and 3) {
    0 -> room.maxY
    1 -> room.maxX
    2 -> -room.minY
    else -> -room.minX
}

/**
 * A structure's rooms as few rects as cover them exactly (object-local units, turned by the rotation): rooms joined
 * while two share a whole side, then a thin rect over each stretch of side two of them still share, so every point
 * between two rooms lies inside one rect. The floor plate fills them, the scrim cuts them out.
 */
internal class RoomRects(template: StructureTemplate, rotation: Int) {
    private val rects: FloatArray
    val count: Int

    init {
        val joined = ArrayList<FloatArray>()
        for (room in template.rooms) joined += floatArrayOf(room.minX, room.minY, room.maxX, room.maxY)
        var merged = true
        while (merged) {
            merged = false
            search@ for (a in joined.indices) for (b in a + 1 until joined.size) {
                val union = joinedSides(joined[a], joined[b]) ?: continue
                joined[a] = union
                joined.removeAt(b)
                merged = true
                break@search
            }
        }
        val seams = ArrayList<FloatArray>()
        for (a in joined.indices) for (b in joined.indices) if (a != b) sharedSide(joined[a], joined[b])?.let { seams += it }
        val all = joined + seams
        count = all.size
        rects = FloatArray(count * 4)
        for (index in all.indices) {
            val rect = all[index]
            val x0 = rotateQuarterX(rect[0], rect[1], rotation)
            val y0 = rotateQuarterY(rect[0], rect[1], rotation)
            val x1 = rotateQuarterX(rect[2], rect[3], rotation)
            val y1 = rotateQuarterY(rect[2], rect[3], rotation)
            rects[index * 4] = min(x0, x1)
            rects[index * 4 + 1] = min(y0, y1)
            rects[index * 4 + 2] = max(x0, x1)
            rects[index * 4 + 3] = max(y0, y1)
        }
    }

    fun left(rect: Int): Float = rects[rect * 4]

    fun top(rect: Int): Float = rects[rect * 4 + 1]

    fun width(rect: Int): Float = rects[rect * 4 + 2] - rects[rect * 4]

    fun height(rect: Int): Float = rects[rect * 4 + 3] - rects[rect * 4 + 1]

    fun size(rect: Int): Size = Size(width(rect), height(rect))

    /** Whether [rect] of the structure centred at [center] (layer units) meets the view of [view] grown by [margin]. */
    fun inView(rect: Int, center: Offset, view: Size, margin: Float): Boolean {
        val base = rect * 4
        return center.x + rects[base + 2] >= -margin && center.x + rects[base] <= view.width + margin &&
            center.y + rects[base + 3] >= -margin && center.y + rects[base + 1] <= view.height + margin
    }

    /** The rect [a] and [b] make when they share a whole side, else null. */
    private fun joinedSides(a: FloatArray, b: FloatArray): FloatArray? {
        val rows = a[1] == b[1] && a[3] == b[3] && (a[2] == b[0] || b[2] == a[0])
        val columns = a[0] == b[0] && a[2] == b[2] && (a[3] == b[1] || b[3] == a[1])
        if (!rows && !columns) return null
        return floatArrayOf(min(a[0], b[0]), min(a[1], b[1]), max(a[2], b[2]), max(a[3], b[3]))
    }

    /** A thin rect over the stretch of [a]'s right or bottom side that [b]'s left or top side shares, else null. */
    private fun sharedSide(a: FloatArray, b: FloatArray): FloatArray? {
        if (a[2] == b[0]) {
            val low = max(a[1], b[1])
            val high = min(a[3], b[3])
            if (high > low) return floatArrayOf(a[2] - SEAM_COVER, low, a[2] + SEAM_COVER, high)
        }
        if (a[3] == b[1]) {
            val low = max(a[0], b[0])
            val high = min(a[2], b[2])
            if (high > low) return floatArrayOf(low, a[3] - SEAM_COVER, high, a[3] + SEAM_COVER)
        }
        return null
    }

    private companion object {
        /** A shared side's rect reaches this far into both rooms (world units): past the antialiased edge at any scale. */
        const val SEAM_COVER = 2f
    }
}

/** [RoomRects] by template and rotation. */
internal val StructureRects = StructureCache(::RoomRects)
