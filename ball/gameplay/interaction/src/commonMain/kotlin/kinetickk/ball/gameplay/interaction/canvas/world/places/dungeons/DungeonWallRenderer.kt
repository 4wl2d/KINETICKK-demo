// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.interaction.canvas.world.places.dungeons

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import kinetickk.ball.content.api.StructureRoom
import kinetickk.ball.content.api.StructureTemplate
import kinetickk.ball.content.api.WallAxis
import kinetickk.ball.content.api.rotateQuarterX
import kinetickk.ball.content.api.rotateQuarterY
import kinetickk.ball.gameplay.nucleus.render.DungeonProjection
import kinetickk.ball.gameplay.nucleus.render.StructureProjection
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkEase
import kinetickk.foundation.design.KkFillKind
import kinetickk.foundation.design.KkRolePalette
import kinetickk.foundation.design.KkStrokeWidth
import kinetickk.foundation.design.kkFillBrush
import kinetickk.foundation.design.kkFillTilePx
import kinetickk.foundation.design.kkMix
import kinetickk.foundation.design.kkStroke
import kinetickk.foundation.design.positiveModulo
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * A dungeon's walls with depth: every slab casts a shadow down and right onto the floor, shows its lit top and
 * a darker face along its lower edge (both inside the collision outline, so nothing drawn reaches past a wall), keeps
 * a bone rim with a light top edge and a seam along its crest. Sconces burn at the rooms' corners and wall middles in
 * the room's mood. Doors: an open doorway is a lit threshold (volt for the player's next way); a sealed one slams its
 * two panels shut from the jambs when its room seals, flashes, then holds a crackling threat barrier while the room's
 * garrison lives; a locked one is hatched bone. Allocation-free: shapes are the cached slab paths and per-wall rects.
 */

/** The slab's face band (inside its outline) and its shadow's offset (world units). */
private const val FACE_DEPTH = 14f
private const val SHADOW_X = 11f
private const val SHADOW_Y = 18f

/** A sconce sits this far inside a wall line; its pool spans this far. */
private const val SCONCE_INSET = 34f
private const val SCONCE_POOL = 140f

/** Doors slam shut over this long after the seal, then flash for [SLAM_FLASH_SECONDS]. */
private const val SLAM_SECONDS = 0.22f
private const val SLAM_FLASH_SECONDS = 0.35f

/**
 * The slabs of [template] turned by [rotation] at [center]: the union's shadow, then slab by slab, for the slabs in view,
 * the rim (its outer half shows), the face, the faint light edge along the top of the face and the lit top that covers
 * all but the face's lower band. Each draw covers one slab's bounds; the pieces come from [SlabPieces].
 */
internal fun DrawScope.drawDungeonSlabs(template: StructureTemplate, rotation: Int, center: Offset, accent: Color) {
    translate(center.x + SHADOW_X, center.y + SHADOW_Y) { drawPath(StructurePaths.walls(template, rotation), Kk.Ink, alpha = 0.5f) }
    val slabs = SlabPieces.of(template, rotation)
    val count = slabs.count
    translate(center.x, center.y) {
        // One pass per layer, so a layer's draws follow each other and a slab's face covers its neighbours' inner rims.
        val rim = kkMix(Kk.Bone, accent, 0.3f)
        for (slab in 0 until count) if (slabs.inView(slab, center, size)) drawPath(slabs.outline(slab), rim, style = kkStroke(KkStrokeWidth.Icon))
        val face = kkMix(Kk.Ink1, accent, 0.10f)
        for (slab in 0 until count) if (slabs.inView(slab, center, size)) drawPath(slabs.outline(slab), face)
        for (slab in 0 until count) {
            if (slabs.hasEdge(slab) && slabs.inView(slab, center, size)) drawPath(slabs.edge(slab), Kk.Bone, alpha = 0.16f)
        }
        val top = kkMix(Kk.Ink3, accent, 0.06f)
        for (slab in 0 until count) if (slabs.hasTop(slab) && slabs.inView(slab, center, size)) drawPath(slabs.top(slab), top)
    }
}

/** The crest seam of a solid piece from [a] to [b] (a pillar's cap ring), lifted onto the lit top. */
internal fun DrawScope.drawSlabCrest(a: Offset, b: Offset, halfThickness: Float, accent: Color) {
    val color = kkMix(Kk.Ink4, accent, 0.18f)
    val lift = FACE_DEPTH * 0.5f
    if (a.x == b.x && a.y == b.y) {
        drawCircle(color, halfThickness * 0.45f, Offset(a.x, a.y - lift), alpha = 0.8f, style = kkStroke(KkStrokeWidth.Icon))
    } else {
        drawLine(color, Offset(a.x, a.y - lift), Offset(b.x, b.y - lift), (halfThickness * 0.3f).coerceAtLeast(2f), alpha = 0.8f)
    }
}

/**
 * The sconces of every room of [structure]: the inner corners and the middles of sides with no door at their middle,
 * each a pool on the floor and a bright nub, breathing out of step in the room's mood.
 */
internal fun DrawScope.drawDungeonSconces(
    template: StructureTemplate,
    structure: StructureProjection,
    dungeon: DungeonProjection?,
    center: Offset,
    time: Float,
    roles: KkRolePalette,
) {
    val accent = structureAccent(template, roles)
    val style = template.id.dungeonStyle
    val rotation = structure.rotation
    val inset = template.wallHalfThickness + SCONCE_INSET
    for (room in template.rooms.indices) {
        val spec = template.rooms[room]
        val left = center.x + roomLeft(spec, rotation)
        val top = center.y + roomTop(spec, rotation)
        val right = center.x + roomRight(spec, rotation)
        val bottom = center.y + roomBottom(spec, rotation)
        if (right < -SCONCE_POOL || bottom < -SCONCE_POOL || left > size.width + SCONCE_POOL || top > size.height + SCONCE_POOL) continue
        val mood = roomMood(structure, dungeon, room)
        val light = roomLight(mood, accent, roles)
        // A sconce lights its own room only: the walls hold its pool.
        clipRect(left, top, right, bottom) {
            for (slot in 0 until SCONCE_SLOTS) {
                if (!sconceAt(template, spec, slot, inset)) continue
                val at = Offset(center.x + rotateQuarterX(sconceX, sconceY, rotation), center.y + rotateQuarterY(sconceX, sconceY, rotation))
                val breath = lightBreath(style, mood, time, (room * 8 + slot) * 0.13f)
                drawLightPool(at, SCONCE_POOL, light, 0.07f + 0.08f * breath)
                drawCircle(light, 9f, at, alpha = 0.25f + 0.25f * breath, style = kkStroke(KkStrokeWidth.Line2))
                drawCircle(kkMix(light, Kk.Bone, 0.5f), 4.5f, at, alpha = 0.7f + 0.3f * breath)
            }
        }
    }
}

private const val SCONCE_SLOTS = 8

/** The last sconce point [sconceAt] found (template space); draw-thread scratch. */
private var sconceX = 0f
private var sconceY = 0f

/** The sconce point of slot [slot] of [room], written to [sconceX]/[sconceY]; false when the slot holds none. */
private fun sconceAt(template: StructureTemplate, room: StructureRoom, slot: Int, inset: Float): Boolean {
    val left = room.minX + inset
    val right = room.maxX - inset
    val top = room.minY + inset
    val bottom = room.maxY - inset
    val midX = (room.minX + room.maxX) * 0.5f
    val midY = (room.minY + room.maxY) * 0.5f
    when (slot) {
        0 -> { sconceX = left; sconceY = top }
        1 -> { sconceX = right; sconceY = top }
        2 -> { sconceX = right; sconceY = bottom }
        3 -> { sconceX = left; sconceY = bottom }
        else -> {
            // A side middle only on a long side, and never in a doorway.
            val side = slot - 4
            val horizontal = side % 2 == 0
            val length = if (horizontal) room.maxX - room.minX else room.maxY - room.minY
            if (length < MIDDLE_SCONCE_SIDE) return false
            val line = when (side) {
                0 -> room.minY
                1 -> room.maxX
                2 -> room.maxY
                else -> room.minX
            }
            val along = if (horizontal) midX else midY
            for (index in template.doors.indices) {
                val door = template.doors[index]
                val axis = if (horizontal) WallAxis.HORIZONTAL else WallAxis.VERTICAL
                if (door.axis == axis && door.line == line && abs(door.center - along) < door.width * 0.5f + SCONCE_INSET) return false
            }
            when (side) {
                0 -> { sconceX = midX; sconceY = top }
                1 -> { sconceX = right; sconceY = midY }
                2 -> { sconceX = midX; sconceY = bottom }
                else -> { sconceX = left; sconceY = midY }
            }
        }
    }
    return true
}

/** Sides shorter than this keep their corners' sconces only. */
private const val MIDDLE_SCONCE_SIDE = 900f

/**
 * An open doorway from [a] to [b]: a threshold light spilling both ways and two dashes across its gap, at 20–42 % and
 * 58–80 % of it, in [color] (volt for the player's next way).
 */
internal fun DrawScope.drawDungeonOpenDoor(a: Offset, b: Offset, color: Color, next: Boolean, time: Float) {
    val mid = Offset((a.x + b.x) * 0.5f, (a.y + b.y) * 0.5f)
    val span = abs(b.x - a.x) + abs(b.y - a.y)
    if (next) drawLightPool(mid, span * 0.55f, color, 0.10f + 0.05f * sin(time * 3f))
    val dx = b.x - a.x
    val dy = b.y - a.y
    drawLine(color, Offset(a.x + dx * 0.2f, a.y + dy * 0.2f), Offset(a.x + dx * 0.42f, a.y + dy * 0.42f), 3f)
    drawLine(color, Offset(a.x + dx * 0.58f, a.y + dy * 0.58f), Offset(a.x + dx * 0.8f, a.y + dy * 0.8f), 3f)
}

/**
 * A sealed doorway from [a] to [b]: the capsule's slab rect. Live (its room's garrison fights): the two panels slam in
 * from the jambs over [SLAM_SECONDS] from [sealedAt], flash, then a threat barrier crackles across it; locked: bone
 * hatch with a bone edge.
 */
internal fun DrawScope.drawDungeonSealedDoor(
    a: Offset,
    b: Offset,
    halfThickness: Float,
    live: Boolean,
    sealedAt: Float,
    time: Float,
    roles: KkRolePalette,
) {
    val left = min(a.x, b.x) - halfThickness
    val top = min(a.y, b.y) - halfThickness
    val width = abs(b.x - a.x) + halfThickness * 2f
    val height = abs(b.y - a.y) + halfThickness * 2f
    if (!live) {
        drawRect(Kk.Ink3, Offset(left, top), Size(width, height))
        drawRect(kkFillBrush(KkFillKind.HATCH, Kk.Bone.copy(alpha = 0.55f)), Offset(left, top), Size(width, height))
        drawRect(Kk.Bone.copy(alpha = 0.6f), Offset(left, top), Size(width, height), style = kkStroke(KkStrokeWidth.Icon))
        return
    }
    val threat = roles.threat
    val since = if (sealedAt >= 0f) time - sealedAt else Float.MAX_VALUE
    val closing = (since / SLAM_SECONDS).coerceIn(0f, 1f)
    val shut = KkEase.Snap.transform(closing)
    val horizontal = width >= height
    if (closing < 1f) {
        // Two panels sliding in from the jambs, the gap between them closing.
        val half = (if (horizontal) width else height) * 0.5f * shut
        for (panel in 0 until 2) {
            val x = if (horizontal && panel == 1) left + width - half else left
            val y = if (!horizontal && panel == 1) top + height - half else top
            val panelSize = if (horizontal) Size(half, height) else Size(width, half)
            drawRect(Kk.Ink3, Offset(x, y), panelSize)
            drawRect(kkFillBrush(KkFillKind.HATCH, Kk.Bone.copy(alpha = 0.4f)), Offset(x, y), panelSize)
            drawRect(threat, Offset(x, y), panelSize, style = kkStroke(KkStrokeWidth.Icon))
        }
        return
    }
    drawRect(Kk.Ink3, Offset(left, top), Size(width, height))
    drawRect(kkFillBrush(KkFillKind.HATCH, Kk.Bone.copy(alpha = 0.4f)), Offset(left, top), Size(width, height))
    drawBarrierStripes(left, top, width, height, threat, time)
    val pulse = 0.5f + 0.5f * sin(time * 6f)
    drawRect(threat, Offset(left - 5f, top - 5f), Size(width + 10f, height + 10f), alpha = 0.10f + 0.14f * pulse, style = kkStroke(10f))
    drawRect(threat, Offset(left, top), Size(width, height), style = kkStroke(KkStrokeWidth.Icon))
    // The barrier's seam flickers along the doorway.
    val flicker = 0.45f + 0.55f * sin(time * 31f + left * 0.01f).coerceAtLeast(0f)
    val seam = kkMix(threat, Kk.Bone, 0.55f)
    if (horizontal) drawLine(seam, Offset(left, top + height * 0.5f), Offset(left + width, top + height * 0.5f), 2f, alpha = flicker)
    else drawLine(seam, Offset(left + width * 0.5f, top), Offset(left + width * 0.5f, top + height), 2f, alpha = flicker)
    val flash = 1f - ((since - SLAM_SECONDS) / SLAM_FLASH_SECONDS).coerceIn(0f, 1f)
    if (flash > 0f) drawRect(kkMix(threat, Kk.Bone, 0.6f), Offset(left - 6f, top - 6f), Size(width + 12f, height + 12f), alpha = 0.7f * flash)
}

/** The barrier's threat stripes over the door rect, scrolling one tile per loop of the stripes fill. */
private fun DrawScope.drawBarrierStripes(left: Float, top: Float, width: Float, height: Float, threat: Color, time: Float) {
    val kind = KkFillKind.STRIPES
    val brush = kkFillBrush(kind, threat.copy(alpha = 0.28f))
    val shift = positiveModulo(time / kind.loopSeconds, 1f) * kkFillTilePx(kind)
    clipRect(left, top, left + width, top + height) {
        translate(shift, 0f) { drawRect(brush, Offset(left - shift, top), Size(width, height)) }
    }
}

// ---------------------------------------------------------------------------------------------
// Cached shapes.

/** How far the scrim path reaches around the dungeon (world units): past any view. */
private const val SCRIM_REACH = 40_000f

/** Corners of a slab outline ([slabOutline]). */
internal const val SLAB_OUTLINE_CORNERS = 10

private const val SQRT_HALF = 0.70710677f

/**
 * Writes the outline of the capsule ([ax], [ay]) → ([bx], [by]) with [halfThickness] into [out] as
 * [SLAB_OUTLINE_CORNERS] (x, y) pairs: both sides, then half-octagon caps whose corners lie on the cap circles, so every
 * corner is [halfThickness] from the segment and every edge stays inside the collision capsule by at most
 * 0.076 × [halfThickness]. A zero-length capsule (a pillar) is an octagon.
 */
internal fun slabOutline(ax: Float, ay: Float, bx: Float, by: Float, halfThickness: Float, out: FloatArray) {
    var ux = bx - ax
    var uy = by - ay
    val length = sqrt(ux * ux + uy * uy)
    if (length > 1e-4f) {
        ux /= length
        uy /= length
    } else {
        ux = 1f
        uy = 0f
    }
    val nx = -uy
    val ny = ux
    val h = halfThickness
    val d = halfThickness * SQRT_HALF
    out[0] = ax + nx * h
    out[1] = ay + ny * h
    out[2] = bx + nx * h
    out[3] = by + ny * h
    out[4] = bx + (ux + nx) * d
    out[5] = by + (uy + ny) * d
    out[6] = bx + ux * h
    out[7] = by + uy * h
    out[8] = bx + (ux - nx) * d
    out[9] = by + (uy - ny) * d
    out[10] = bx - nx * h
    out[11] = by - ny * h
    out[12] = ax - nx * h
    out[13] = ay - ny * h
    out[14] = ax - (ux + nx) * d
    out[15] = ay - (uy + ny) * d
    out[16] = ax - ux * h
    out[17] = ay - uy * h
    out[18] = ax - (ux - nx) * d
    out[19] = ay - (uy - ny) * d
}

/**
 * Structure shapes in object-local units (origin at the structure centre), built from the content template turned by
 * its rotation: the floor (room rects), the solid slabs and the scrim (a rect past any view with the rooms cut out,
 * even-odd). Keyed by template, rotation and kind; a bounded round robin (a window holds at most nine structures, three
 * kinds each) whose evicted paths are reset and reused. Draw-thread confined.
 */
internal object StructurePaths {
    private const val CAPACITY = 32
    private const val FLOOR = 0
    private const val WALLS = 1
    private const val SCRIM = 2
    private val keys = IntArray(CAPACITY) { -1 }
    private val sources = arrayOfNulls<StructureTemplate>(CAPACITY)
    private val paths = arrayOfNulls<Path>(CAPACITY)
    private val corners = FloatArray(SLAB_OUTLINE_CORNERS * 2)
    private var next = 0

    /** Paths built so far: a warm frame builds none. */
    var builds = 0
        private set

    fun floor(template: StructureTemplate, rotation: Int): Path = get(template, rotation, FLOOR)

    fun walls(template: StructureTemplate, rotation: Int): Path = get(template, rotation, WALLS)

    fun scrim(template: StructureTemplate, rotation: Int): Path = get(template, rotation, SCRIM)

    private fun get(template: StructureTemplate, rotation: Int, kind: Int): Path {
        val key = (template.id.ordinal shl 8) or ((rotation and 3) shl 4) or kind
        for (index in 0 until CAPACITY) {
            if (keys[index] == key && sources[index] === template) return paths[index]!!
        }
        val path = paths[next]?.also { it.reset() } ?: Path()
        when (kind) {
            FLOOR -> buildRooms(path, template, rotation)
            WALLS -> buildSlabs(path, template, rotation)
            else -> {
                path.addRect(Rect(-SCRIM_REACH, -SCRIM_REACH, SCRIM_REACH, SCRIM_REACH))
                buildRooms(path, template, rotation)
            }
        }
        path.fillType = if (kind == SCRIM) PathFillType.EvenOdd else PathFillType.NonZero
        keys[next] = key
        sources[next] = template
        paths[next] = path
        next = (next + 1) % CAPACITY
        builds++
        return path
    }

    private fun buildRooms(path: Path, template: StructureTemplate, rotation: Int) {
        val rooms = template.rooms
        for (index in rooms.indices) {
            val room = rooms[index]
            path.addRect(Rect(roomLeft(room, rotation), roomTop(room, rotation), roomRight(room, rotation), roomBottom(room, rotation)))
        }
    }

    private fun buildSlabs(path: Path, template: StructureTemplate, rotation: Int) {
        val walls = template.walls
        for (index in walls.indices) {
            val wall = walls[index]
            if (wall.isDoor) continue
            slabOutline(
                rotateQuarterX(wall.ax, wall.ay, rotation), rotateQuarterY(wall.ax, wall.ay, rotation),
                rotateQuarterX(wall.bx, wall.by, rotation), rotateQuarterY(wall.bx, wall.by, rotation),
                wall.halfThickness, corners,
            )
            path.moveTo(corners[0], corners[1])
            for (corner in 1 until SLAB_OUTLINE_CORNERS) path.lineTo(corners[corner * 2], corners[corner * 2 + 1])
            path.close()
        }
    }
}

/** A slab is drawn while its outline and this margin (its rim, world units) meet the view. */
private const val SLAB_VIEW_MARGIN = 2f

/** The face's light edge spans this far below the lit top: the outer half of the icon stroke the edge was drawn with. */
private const val EDGE_REACH = KkStrokeWidth.Icon * 0.5f

/**
 * The solid slabs of a template turned by a rotation, one by one in object-local units: each slab's outline, its lit
 * top (the part of the slab the slab union moved up by [FACE_DEPTH] covers) and the light edge of its face (the part of
 * the slab, and of no earlier slab, within [EDGE_REACH] below that moved union). Tops and edges are cut from the whole
 * union, so neighbouring slabs join as one wall, and the edges never overlap, so their light never doubles.
 */
internal class SlabSet(template: StructureTemplate, rotation: Int) {
    val count: Int
    private val bounds: FloatArray
    private val outlines: Array<Path>
    private val tops: Array<Path>
    private val edges: Array<Path>
    private val drawn: BooleanArray

    init {
        val solid = template.walls.filter { !it.isDoor }
        count = solid.size
        bounds = FloatArray(count * 4)
        val corners = FloatArray(SLAB_OUTLINE_CORNERS * 2)
        val union = Path()
        val reach = Path()
        outlines = Array(count) { index ->
            val wall = solid[index]
            val ax = rotateQuarterX(wall.ax, wall.ay, rotation)
            val ay = rotateQuarterY(wall.ax, wall.ay, rotation)
            val bx = rotateQuarterX(wall.bx, wall.by, rotation)
            val by = rotateQuarterY(wall.bx, wall.by, rotation)
            slabOutline(ax, ay, bx, by, wall.halfThickness + EDGE_REACH, corners)
            addOutline(reach, corners, -FACE_DEPTH)
            slabOutline(ax, ay, bx, by, wall.halfThickness, corners)
            var left = corners[0]
            var top = corners[1]
            var right = corners[0]
            var bottom = corners[1]
            for (corner in 1 until SLAB_OUTLINE_CORNERS) {
                left = min(left, corners[corner * 2])
                right = max(right, corners[corner * 2])
                top = min(top, corners[corner * 2 + 1])
                bottom = max(bottom, corners[corner * 2 + 1])
            }
            bounds[index * 4] = left
            bounds[index * 4 + 1] = top
            bounds[index * 4 + 2] = right
            bounds[index * 4 + 3] = bottom
            Path().also { addOutline(it, corners, 0f) }.also { union.addPath(it) }
        }
        val raised = Path().apply { addPath(union, Offset(0f, -FACE_DEPTH)) }
        val earlier = Path()
        val scratch = Path()
        tops = Array(count) { index -> cut(outlines[index], raised, PathOperation.Intersect) }
        edges = Array(count) { index ->
            val own = cut(outlines[index], earlier, PathOperation.Difference)
            earlier.addPath(outlines[index])
            if (!scratch.op(own, reach, PathOperation.Intersect)) scratch.reset()
            cut(scratch, raised, PathOperation.Difference)
        }
        drawn = BooleanArray(count * 2) { if (it < count) !tops[it].isEmpty else !edges[it - count].isEmpty }
    }

    fun outline(slab: Int): Path = outlines[slab]

    fun top(slab: Int): Path = tops[slab]

    fun edge(slab: Int): Path = edges[slab]

    fun hasTop(slab: Int): Boolean = drawn[slab]

    fun hasEdge(slab: Int): Boolean = drawn[count + slab]

    /** Whether [slab] of the structure centred at [center] (layer units) meets the view of [view]. */
    fun inView(slab: Int, center: Offset, view: Size): Boolean {
        val base = slab * 4
        return center.x + bounds[base + 2] >= -SLAB_VIEW_MARGIN && center.x + bounds[base] <= view.width + SLAB_VIEW_MARGIN &&
            center.y + bounds[base + 3] >= -SLAB_VIEW_MARGIN && center.y + bounds[base + 1] <= view.height + SLAB_VIEW_MARGIN
    }

    private fun addOutline(path: Path, corners: FloatArray, dy: Float) {
        path.moveTo(corners[0], corners[1] + dy)
        for (corner in 1 until SLAB_OUTLINE_CORNERS) path.lineTo(corners[corner * 2], corners[corner * 2 + 1] + dy)
        path.close()
    }

    /** [a] combined with [b] by [operation] as a new path; empty when the operation fails. */
    private fun cut(a: Path, b: Path, operation: PathOperation): Path = Path().also { if (!it.op(a, b, operation)) it.reset() }
}

/** [SlabSet]s by template and rotation. */
internal val SlabPieces = StructureCache(::SlabSet)

/**
 * Values built from a template turned by a rotation, keyed by both: a bounded round robin (a window holds at most nine
 * structures), each value built once when its structure comes into view. Draw-thread confined.
 */
internal class StructureCache<T : Any>(private val build: (StructureTemplate, Int) -> T) {
    private val templates = arrayOfNulls<StructureTemplate>(CAPACITY)
    private val rotations = IntArray(CAPACITY)
    private val values = arrayOfNulls<Any>(CAPACITY)
    private var next = 0

    /** Values built so far: a warm frame builds none. */
    var builds = 0
        private set

    @Suppress("UNCHECKED_CAST")
    fun of(template: StructureTemplate, rotation: Int): T {
        val turn = rotation and 3
        for (index in 0 until CAPACITY) {
            if (templates[index] === template && rotations[index] == turn) return values[index] as T
        }
        val built = build(template, turn)
        templates[next] = template
        rotations[next] = turn
        values[next] = built
        next = (next + 1) % CAPACITY
        builds++
        return built
    }

    private companion object {
        const val CAPACITY = 12
    }
}
