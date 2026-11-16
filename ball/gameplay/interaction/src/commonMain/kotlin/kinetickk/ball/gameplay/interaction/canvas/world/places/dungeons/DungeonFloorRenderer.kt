// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.interaction.canvas.world.places.dungeons

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import kinetickk.ball.content.api.StructureRoom
import kinetickk.ball.content.api.StructureTemplate
import kinetickk.ball.content.api.rotateQuarterX
import kinetickk.ball.content.api.rotateQuarterY
import kinetickk.ball.gameplay.interaction.canvas.*
import kinetickk.ball.gameplay.interaction.canvas.fx.*
import kinetickk.ball.gameplay.nucleus.render.DungeonProjection
import kinetickk.ball.gameplay.nucleus.render.StructureProjection
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkRolePalette
import kinetickk.foundation.design.kkMix
import kinetickk.foundation.design.kkStroke
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/*
 * A dungeon's floor beyond its plate: the template's floor (deck plates, ice lattice, resonance rings, roots, grates,
 * sanctum tiles) in neutral lines with its accent inlay, the dark band where floor meets wall, a light pool in every
 * room that is not dormant, tinted by its mood (threat while sealed, warm bone once cleared) and the
 * runes along the walls in three groups that pulse out of step, lit for good once their room is cleared. Shapes come
 * from the content template, cached per template and rotation room by room in DungeonFloorPaths, and every room is
 * drawn only while it meets the view, in lines, circles, turned squares and small paths (a stroke or fill spanning the
 * whole structure would need a software mask of the whole screen); nothing allocates per frame once they are built.
 */

/** Ambient occlusion where floor meets wall: an ink stroke on the room outlines (half under the slabs). */
private const val OCCLUSION = 84f

/** The floor's detail lines and its inlay (the foundry's channels are wider: 26 and 3) are this wide (world units). */
private const val DETAIL_STROKE = 1.5f
private const val INLAY_STROKE = 2f

/** The room light pool spans this share of the room's shorter side. */
private const val ROOM_POOL_SHARE = 0.62f

/** Runes sit this far inside a wall line, this far apart, and are this large (world units). */
internal const val RUNE_INSET = 50f
internal const val RUNE_SPACING = 150f
internal const val RUNE_SIZE = 7f

/** Runes pulse in this many groups, a group's phase a third of a turn from the next. */
private const val RUNE_GROUPS = 3

private const val SQRT_TWO = 1.4142135f
private const val SQRT_HALF_TURN = 0.70710677f

/**
 * The template's floor detail, room lights and wall runes of [structure] centred at [center], over its floor plate and
 * under its walls: the cleared rooms' runes burn steady, the sealed room's pulse in threat.
 */
internal fun DrawScope.drawDungeonFloor(
    template: StructureTemplate,
    structure: StructureProjection,
    dungeon: DungeonProjection?,
    center: Offset,
    time: Float,
    roles: KkRolePalette,
) {
    val rotation = structure.rotation
    val style = template.id.dungeonStyle
    val accent = structureAccent(template, roles)
    val pieces = DungeonFloorPaths.of(template, rotation)
    // The detail lines lie on the opaque plate alone, so they are drawn opaque in the colour their alpha gives over it:
    // where two cross, the crossing keeps that colour instead of blending twice.
    val detail = kkMix(structureFloorColor(template, roles), kkMix(Kk.Ink4, accent, 0.22f), 0.55f)
    val grate = style.floor == DungeonFloor.GRATE
    val glow = 0.07f + 0.03f * sin(time * 1.3f)
    val hot = kkMix(accent, Kk.Bone, 0.35f)
    for (room in template.rooms.indices) {
        if (!pieces.inView(room, center, size)) continue
        pieces.forEachDetail(room) { ax, ay, bx, by ->
            drawLine(detail, Offset(center.x + ax, center.y + ay), Offset(center.x + bx, center.y + by), DETAIL_STROKE)
        }
        if (grate) {
            // The furnace channels glow: a wide soft stroke under a hot thin one.
            pieces.forEachInlayLine(room) { ax, ay, bx, by ->
                drawLine(accent, Offset(center.x + ax, center.y + ay), Offset(center.x + bx, center.y + by), 26f, alpha = glow)
            }
            pieces.forEachInlayLine(room) { ax, ay, bx, by ->
                drawLine(hot, Offset(center.x + ax, center.y + ay), Offset(center.x + bx, center.y + by), 3f, alpha = 0.42f)
            }
        } else {
            pieces.forEachInlayLine(room) { ax, ay, bx, by ->
                drawLine(accent, Offset(center.x + ax, center.y + ay), Offset(center.x + bx, center.y + by), INLAY_STROKE, alpha = 0.20f)
            }
            pieces.forEachInlayCircle(room) { x, y, radius ->
                drawCircle(accent, radius, Offset(center.x + x, center.y + y), alpha = 0.20f, style = kkStroke(INLAY_STROKE))
            }
            if (pieces.hasInlayPaths(room)) translate(center.x, center.y) {
                pieces.forEachInlayPath(room) { drawPath(it, accent, alpha = 0.20f, style = kkStroke(INLAY_STROKE)) }
            }
        }
    }
    translate(center.x, center.y) {
        drawPath(StructurePaths.floor(template, rotation), Kk.Ink, alpha = 0.36f, style = kkStroke(OCCLUSION))
    }
    for (room in template.rooms.indices) {
        val spec = template.rooms[room]
        val mood = roomMood(structure, dungeon, room)
        val light = roomLight(mood, accent, roles)
        val breath = lightBreath(style, mood, time, room * 0.37f)
        // A dormant room is lit by its sconces alone; its own pool would only add fill under them.
        if (mood == RoomMood.DORMANT) continue
        val peak = when (mood) {
            RoomMood.SEALED -> 0.07f + 0.06f * breath
            RoomMood.GUARDIAN -> 0.12f + 0.08f * breath
            else -> 0.09f
        }
        val shorter = min(spec.maxX - spec.minX, spec.maxY - spec.minY)
        // Light stays inside its room: the walls hold it.
        clipRect(center.x + roomLeft(spec, rotation), center.y + roomTop(spec, rotation), center.x + roomRight(spec, rotation),
            center.y + roomBottom(spec, rotation)) {
            drawLightPool(roomCenter(center, spec, rotation), shorter * ROOM_POOL_SHARE, light, peak)
            if (mood == RoomMood.SEALED || mood == RoomMood.GUARDIAN) drawRoomAlarm(spec, center, rotation, light, breath)
        }
    }
    drawRunes(template, structure, dungeon, center, time, roles, accent)
}

/** A sealed room's alarm: its walls' feet glow in threat on the alarm's beat (drawn inside its room's clip). */
private fun DrawScope.drawRoomAlarm(spec: StructureRoom, center: Offset, rotation: Int, light: Color, beat: Float) {
    val left = center.x + roomLeft(spec, rotation)
    val top = center.y + roomTop(spec, rotation)
    val size = Size(center.x + roomRight(spec, rotation) - left, center.y + roomBottom(spec, rotation) - top)
    drawRect(light, Offset(left, top), size, alpha = 0.10f + 0.14f * beat, style = kkStroke(ALARM))
}

/** The alarm's glow stroke on a sealed room's outline (world units; half of it reaches into the room). */
private const val ALARM = 96f

/** The three rune groups: each room's runes in its mood, a group's phase a third of a turn from the next. */
private fun DrawScope.drawRunes(
    template: StructureTemplate,
    structure: StructureProjection,
    dungeon: DungeonProjection?,
    center: Offset,
    time: Float,
    roles: KkRolePalette,
    accent: Color,
) {
    val rotation = structure.rotation
    // Every rune in view in the element's hue, its group's pulse; a sealed or cleared room's runes burn over them. A
    // diamond is a square rotated 45°. The layer turns once and each rune is an axis-aligned square in it.
    val wall = template.wallHalfThickness
    val side = RUNE_SIZE * SQRT_TWO
    val first = 0.10f + 0.12f * (0.5f + 0.5f * sin(time * 1.1f))
    val second = 0.10f + 0.12f * (0.5f + 0.5f * sin(time * 1.1f + 2.1f))
    val third = 0.10f + 0.12f * (0.5f + 0.5f * sin(time * 1.1f + 4.2f))
    rotate(45f, Offset.Zero) {
        for (room in template.rooms.indices) {
            val spec = template.rooms[room]
            if (!roomInView(spec, rotation, center, RUNE_SIZE)) continue
            forEachRune(spec, wall) { x, y, index ->
                val px = center.x + rotateQuarterX(x, y, rotation)
                val py = center.y + rotateQuarterY(x, y, rotation)
                if (px < -RUNE_SIZE || py < -RUNE_SIZE || px > size.width + RUNE_SIZE || py > size.height + RUNE_SIZE) return@forEachRune
                val alpha = when (index % RUNE_GROUPS) {
                    0 -> first
                    1 -> second
                    else -> third
                }
                // The rune's centre in the turned layer.
                val tx = (px + py) * SQRT_HALF_TURN
                val ty = (py - px) * SQRT_HALF_TURN
                drawRect(accent, Offset(tx - side * 0.5f, ty - side * 0.5f), Size(side, side), alpha = alpha)
            }
        }
    }
    for (room in template.rooms.indices) {
        val mood = roomMood(structure, dungeon, room)
        if (mood == RoomMood.DORMANT) continue
        val spec = template.rooms[room]
        if (!roomInView(spec, rotation, center, RUNE_SIZE)) continue
        val color = roomLight(mood, accent, roles)
        val alpha = if (mood == RoomMood.CLEARED) 0.62f else 0.35f + 0.45f * lightBreath(template.id.dungeonStyle, mood, time, 0f)
        drawRoomRunes(spec, template.wallHalfThickness, center, rotation, color, alpha)
    }
}

/**
 * The runes of one lit room drawn as single dots along its walls (no path: a room's runes are at most a few dozen
 * circles), so a cleared or sealed room's runes burn over the dormant groups.
 */
private fun DrawScope.drawRoomRunes(spec: StructureRoom, wall: Float, center: Offset, rotation: Int, color: Color, alpha: Float) {
    forEachRune(spec, wall) { x, y, _ ->
        drawCircle(color, RUNE_SIZE * 0.62f, Offset(center.x + rotateQuarterX(x, y, rotation), center.y + rotateQuarterY(x, y, rotation)), alpha)
    }
}

/**
 * Every rune point of [room] (template space): along each side [RUNE_INSET] inside the wall line, [RUNE_SPACING]
 * apart, starting half a spacing from a corner; [action] gets the point and the rune's index around the room.
 */
internal inline fun forEachRune(room: StructureRoom, wall: Float, action: (Float, Float, Int) -> Unit) {
    val inset = RUNE_INSET + wall
    val left = room.minX + inset
    val right = room.maxX - inset
    val top = room.minY + inset
    val bottom = room.maxY - inset
    var index = 0
    var x = left + RUNE_SPACING * 0.5f
    while (x < right) {
        action(x, top, index++)
        action(x, bottom, index++)
        x += RUNE_SPACING
    }
    var y = top + RUNE_SPACING * 0.5f
    while (y < bottom) {
        action(left, y, index++)
        action(right, y, index++)
        y += RUNE_SPACING
    }
}

/** A room's floor is drawn while its bounds and this margin (the furnace glow's half width, world units) meet the view. */
private const val ROOM_VIEW_MARGIN = 14f

/** Whether [room] turned by [rotation] of the structure centred at [center] (layer units) grown by [margin] meets the view. */
private fun DrawScope.roomInView(room: StructureRoom, rotation: Int, center: Offset, margin: Float): Boolean =
    center.x + roomRight(room, rotation) >= -margin && center.x + roomLeft(room, rotation) <= size.width + margin &&
        center.y + roomBottom(room, rotation) >= -margin && center.y + roomTop(room, rotation) <= size.height + margin

/** [FloorPieces] by template and rotation. */
internal val DungeonFloorPaths = StructureCache(::FloorPieces)

/**
 * A dungeon's floor shapes room by room in object-local units (origin at the structure centre), built from the content
 * template turned by its rotation: the template's neutral floor lines, and its accent inlay as lines, circles and small
 * paths. Inlay strokes that cross or join (a corner mark, a frost star, roots that meet, the reliquary's rune circle)
 * share a path, so they cover each other once as the template's one inlay stroke did; every other piece stands alone.
 */
internal class FloorPieces(template: StructureTemplate, rotation: Int) {
    private val rooms = template.rooms
    private val turn = rotation
    internal val detail: Array<FloatArray>
    internal val lines: Array<FloatArray>
    internal val circles: Array<FloatArray>
    internal val paths: Array<Array<Path>>

    init {
        val builder = FloorBuilder(rotation)
        val style = template.id.dungeonStyle
        detail = Array(rooms.size) { room ->
            buildDetail(builder, rooms[room], style)
            builder.takeLines()
        }
        lines = Array(rooms.size) { FloatArray(0) }
        circles = Array(rooms.size) { FloatArray(0) }
        paths = Array(rooms.size) { emptyArray() }
        for (room in rooms.indices) {
            buildInlay(builder, rooms[room], template, room)
            lines[room] = builder.takeLines()
            circles[room] = builder.takeCircles()
            paths[room] = builder.takePieces()
        }
    }

    fun hasInlayPaths(room: Int): Boolean = paths[room].isNotEmpty()

    /** Whether [room] of the structure centred at [center] (layer units) meets the view of [view]. */
    fun inView(room: Int, center: Offset, view: Size): Boolean {
        val spec = rooms[room]
        return center.x + roomRight(spec, turn) >= -ROOM_VIEW_MARGIN && center.x + roomLeft(spec, turn) <= view.width + ROOM_VIEW_MARGIN &&
            center.y + roomBottom(spec, turn) >= -ROOM_VIEW_MARGIN && center.y + roomTop(spec, turn) <= view.height + ROOM_VIEW_MARGIN
    }

    inline fun forEachDetail(room: Int, action: (Float, Float, Float, Float) -> Unit) = forEachLine(detail[room], action)

    inline fun forEachInlayLine(room: Int, action: (Float, Float, Float, Float) -> Unit) = forEachLine(lines[room], action)

    inline fun forEachInlayCircle(room: Int, action: (Float, Float, Float) -> Unit) {
        val values = circles[room]
        var index = 0
        while (index < values.size) {
            action(values[index], values[index + 1], values[index + 2])
            index += 3
        }
    }

    inline fun forEachInlayPath(room: Int, action: (Path) -> Unit) {
        val pieces = paths[room]
        for (index in pieces.indices) action(pieces[index])
    }

    inline fun forEachLine(values: FloatArray, action: (Float, Float, Float, Float) -> Unit) {
        var index = 0
        while (index < values.size) {
            action(values[index], values[index + 1], values[index + 2], values[index + 3])
            index += 4
        }
    }

    /** The template's neutral floor lines in [room]: plates, a lattice, spokes, a tile grid or grates. */
    private fun buildDetail(path: FloorBuilder, room: StructureRoom, style: DungeonStyle) {
        val tile = style.tile
        when (style.floor) {
            DungeonFloor.ICE -> lattice(path, room, tile)
            DungeonFloor.RINGS -> {
                val cx = (room.minX + room.maxX) * 0.5f
                val cy = (room.minY + room.maxY) * 0.5f
                val reach = min(room.maxX - room.minX, room.maxY - room.minY) * 0.46f
                for (spoke in 0 until 12) {
                    val angle = spoke * (CanvasMath.TAU / 12f)
                    path.line(cx + cos(angle) * tile * 0.6f, cy + sin(angle) * tile * 0.6f, cx + cos(angle) * reach, cy + sin(angle) * reach)
                }
            }
            DungeonFloor.GRATE -> {
                val alongX = room.maxX - room.minX >= room.maxY - room.minY
                if (alongX) stripes(room.minY, room.maxY, tile) { y -> path.line(room.minX, y, room.maxX, y) }
                else stripes(room.minX, room.maxX, tile) { x -> path.line(x, room.minY, x, room.maxY) }
            }
            DungeonFloor.DECK, DungeonFloor.ROOTS, DungeonFloor.SANCTUM -> {
                stripes(room.minX, room.maxX, tile) { x -> path.line(x, room.minY, x, room.maxY) }
                stripes(room.minY, room.maxY, tile) { y -> path.line(room.minX, y, room.maxX, y) }
            }
        }
    }

    /**
     * The accent inlay of room [index]: the station's corner deck markings, the vault's frost stars, the spire's
     * resonance rings, the labyrinth's roots, the foundry's two channels and the reliquary's rune circle.
     */
    private fun buildInlay(path: FloorBuilder, room: StructureRoom, template: StructureTemplate, index: Int) {
        val cx = (room.minX + room.maxX) * 0.5f
        val cy = (room.minY + room.maxY) * 0.5f
        val width = room.maxX - room.minX
        val height = room.maxY - room.minY
        val shorter = min(width, height)
        val seed = template.id.ordinal * 131 + index * 17
        when (template.id.dungeonStyle.floor) {
            DungeonFloor.DECK -> {
                val inset = template.wallHalfThickness + 46f
                val arm = 70f
                for (corner in 0 until 4) {
                    val x = if (corner % 2 == 0) room.minX + inset else room.maxX - inset
                    val y = if (corner < 2) room.minY + inset else room.maxY - inset
                    val sx = if (corner % 2 == 0) 1f else -1f
                    val sy = if (corner < 2) 1f else -1f
                    path.piece()
                    path.move(x, y + sy * arm)
                    path.lineTo(x, y)
                    path.lineTo(x + sx * arm, y)
                }
            }
            DungeonFloor.ICE -> for (star in 0 until 3) {
                val x = room.minX + width * (0.2f + 0.6f * fxHash(seed, star * 2))
                val y = room.minY + height * (0.2f + 0.6f * fxHash(seed, star * 2 + 1))
                val reach = 26f + 22f * fxHash(seed, 9 + star)
                path.piece()
                for (spoke in 0 until 3) {
                    val angle = spoke * (CanvasMath.TAU / 6f)
                    path.move(x - cos(angle) * reach, y - sin(angle) * reach)
                    path.lineTo(x + cos(angle) * reach, y + sin(angle) * reach)
                }
            }
            DungeonFloor.RINGS -> {
                var radius = template.id.dungeonStyle.tile
                while (radius < shorter * 0.47f) {
                    path.circle(cx, cy, radius)
                    radius += template.id.dungeonStyle.tile
                }
            }
            DungeonFloor.ROOTS -> for (root in 0 until 4) {
                // A root from a wall point toward the room's centre, bending as it goes.
                val side = root % 4
                var x = if (side == 1) room.maxX else if (side == 3) room.minX else room.minX + width * (0.25f + 0.5f * fxHash(seed, root))
                var y = if (side == 0) room.minY else if (side == 2) room.maxY else room.minY + height * (0.25f + 0.5f * fxHash(seed, root))
                var heading = kotlin.math.atan2(cy - y, cx - x)
                path.piece()
                path.move(x, y)
                for (segment in 0 until 5) {
                    heading += (fxHash(seed, 20 + root * 8 + segment) - 0.5f) * 1.4f
                    val length = 50f + 50f * fxHash(seed, 60 + root * 8 + segment)
                    x = (x + cos(heading) * length).coerceIn(room.minX, room.maxX)
                    y = (y + sin(heading) * length).coerceIn(room.minY, room.maxY)
                    path.lineTo(x, y)
                }
            }
            DungeonFloor.GRATE -> {
                val alongX = width >= height
                for (channel in 1..2) {
                    if (alongX) {
                        val y = room.minY + height * channel / 3f
                        path.line(room.minX, y, room.maxX, y)
                    } else {
                        val x = room.minX + width * channel / 3f
                        path.line(x, room.minY, x, room.maxY)
                    }
                }
            }
            DungeonFloor.SANCTUM -> {
                // The outer circle stands clear of the star; the inner circle holds the star's points.
                val radius = shorter * 0.28f
                path.circle(cx, cy, radius)
                path.piece()
                path.oval(cx, cy, radius * 0.72f)
                for (point in 0 until 8) {
                    val from = point * (CanvasMath.TAU / 8f)
                    val to = from + CanvasMath.TAU * 3f / 8f
                    path.move(cx + cos(from) * radius * 0.72f, cy + sin(from) * radius * 0.72f)
                    path.lineTo(cx + cos(to) * radius * 0.72f, cy + sin(to) * radius * 0.72f)
                }
            }
        }
    }

    /** Diagonal lines a [tile] apart both ways, cut to the room. */
    private fun lattice(path: FloorBuilder, room: StructureRoom, tile: Float) {
        val width = room.maxX - room.minX
        val height = room.maxY - room.minY
        var offset = tile * 0.5f - height
        while (offset < width) {
            // x - minX = offset + (y - minY): rising to the right; and its mirror.
            clipDiagonal(path, room, offset, rising = true)
            clipDiagonal(path, room, offset, rising = false)
            offset += tile
        }
    }

    private fun clipDiagonal(path: FloorBuilder, room: StructureRoom, offset: Float, rising: Boolean) {
        val height = room.maxY - room.minY
        val width = room.maxX - room.minX
        val startY = maxOf(0f, -offset)
        val endY = minOf(height, width - offset)
        if (endY <= startY) return
        val x0 = room.minX + offset + startY
        val x1 = room.minX + offset + endY
        if (rising) path.line(x0, room.minY + startY, x1, room.minY + endY)
        else path.line(x0, room.maxY - startY, x1, room.maxY - endY)
    }

    private inline fun stripes(low: Float, high: Float, tile: Float, line: (Float) -> Unit) {
        var at = low + tile
        while (at < high - tile * 0.5f) {
            line(at)
            at += tile
        }
    }
}

/**
 * Collects one room's floor shapes in template space, every point turned by a quarter [rotation]: lone line segments,
 * lone circles, and pieces (polylines and circles that are stroked as one path). Pieces whose stroked bounds meet are
 * merged into one path when taken, so no two inlay paths of a room ever overlap.
 */
private class FloorBuilder(private val rotation: Int) {
    private var segments = FloatArray(64)
    private var segmentCount = 0
    private var rings = FloatArray(12)
    private var ringCount = 0
    private val pieces = ArrayList<Path>()
    private val pieceBounds = ArrayList<FloatArray>()
    private var path: Path? = null
    private var bounds = FloatArray(4)

    fun line(ax: Float, ay: Float, bx: Float, by: Float) {
        if (segmentCount + 4 > segments.size) segments = segments.copyOf(segments.size * 2)
        segments[segmentCount++] = rotateQuarterX(ax, ay, rotation)
        segments[segmentCount++] = rotateQuarterY(ax, ay, rotation)
        segments[segmentCount++] = rotateQuarterX(bx, by, rotation)
        segments[segmentCount++] = rotateQuarterY(bx, by, rotation)
    }

    fun circle(x: Float, y: Float, radius: Float) {
        if (ringCount + 3 > rings.size) rings = rings.copyOf(rings.size * 2)
        rings[ringCount++] = rotateQuarterX(x, y, rotation)
        rings[ringCount++] = rotateQuarterY(x, y, rotation)
        rings[ringCount++] = radius
    }

    /** Starts a new piece; [move], [lineTo] and [oval] add to it. */
    fun piece() {
        path = Path().also { pieces += it }
        bounds = floatArrayOf(Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE).also { pieceBounds += it }
    }

    fun move(x: Float, y: Float) = point(x, y, 0f) { px, py -> moveTo(px, py) }

    fun lineTo(x: Float, y: Float) = point(x, y, 0f) { px, py -> lineTo(px, py) }

    fun oval(x: Float, y: Float, radius: Float) =
        point(x, y, radius) { px, py -> addOval(Rect(px - radius, py - radius, px + radius, py + radius)) }

    private inline fun point(x: Float, y: Float, reach: Float, add: Path.(Float, Float) -> Unit) {
        val px = rotateQuarterX(x, y, rotation)
        val py = rotateQuarterY(x, y, rotation)
        checkNotNull(path).add(px, py)
        bounds[0] = min(bounds[0], px - reach)
        bounds[1] = min(bounds[1], py - reach)
        bounds[2] = maxOf(bounds[2], px + reach)
        bounds[3] = maxOf(bounds[3], py + reach)
    }

    fun takeLines(): FloatArray = segments.copyOf(segmentCount).also { segmentCount = 0 }

    fun takeCircles(): FloatArray = rings.copyOf(ringCount).also { ringCount = 0 }

    /** The room's pieces as paths, those whose bounds grown by a stroke meet merged into one. */
    fun takePieces(): Array<Path> {
        var merged = true
        while (merged) {
            merged = false
            search@ for (a in pieces.indices) for (b in a + 1 until pieces.size) {
                val first = pieceBounds[a]
                val second = pieceBounds[b]
                if (first[0] > second[2] + INLAY_STROKE || second[0] > first[2] + INLAY_STROKE ||
                    first[1] > second[3] + INLAY_STROKE || second[1] > first[3] + INLAY_STROKE
                ) {
                    continue
                }
                pieces[a].addPath(pieces[b])
                for (side in 0 until 2) first[side] = min(first[side], second[side])
                for (side in 2 until 4) first[side] = maxOf(first[side], second[side])
                pieces.removeAt(b)
                pieceBounds.removeAt(b)
                merged = true
                break@search
            }
        }
        return pieces.toTypedArray().also {
            pieces.clear()
            pieceBounds.clear()
            path = null
        }
    }
}
