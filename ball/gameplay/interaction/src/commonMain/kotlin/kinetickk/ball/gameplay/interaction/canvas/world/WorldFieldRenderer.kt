// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.interaction.canvas.world

import kinetickk.ball.gameplay.interaction.fx.PieceMotion
import kinetickk.foundation.design.*

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.gameplay.interaction.fx.PixelFxClock
import kinetickk.ball.gameplay.interaction.fx.PixelStrip
import kinetickk.ball.gameplay.nucleus.render.CombatFieldProjection
import kinetickk.ball.gameplay.nucleus.render.CombatFieldSource
import kinetickk.ball.gameplay.nucleus.render.CombatFieldVisual
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kinetickk.ball.gameplay.interaction.canvas.*
import kinetickk.ball.gameplay.interaction.canvas.fx.*
import kinetickk.ball.gameplay.interaction.canvas.look.*

private const val TAU = CanvasMath.TAU
private const val QUARTER = (PI / 2.0).toFloat()

private val THERMAL = StatusElement.THERMAL.ordinal
private val CRYO = StatusElement.CRYO.ordinal
private val ION = StatusElement.ION.ordinal
private val GRAVITIC = StatusElement.GRAVITIC.ordinal
private val ENTROPY = StatusElement.ENTROPY.ordinal
private val SONIC = StatusElement.SONIC.ordinal
private val VOID = StatusElement.VOID.ordinal

/** A field grows from 0.6 of its radius over this long (Pull). */
private const val FIELD_SPAWN_SECONDS = 0.15f

/** A field fades over its last quarter second, in [FADE_STEPS] steps of pips going out. */
private const val FIELD_FADE_SECONDS = 0.25f
private const val FADE_STEPS = 3

/** Fusion collapses over its last quarter second and flashes over its last 0.08 s; an armed Null Well pops over 0.12 s. */
private const val FUSION_COLLAPSE_SECONDS = 0.25f
private const val FUSION_FLASH_SECONDS = 0.08f
private const val NULL_POP_SECONDS = 0.12f

/** The Fusion flash and the Null Well's pop sweep outward on the ticks of this rate (their flash strip's). */
private const val SWEEP_FPS = 24

/** A rim takes one pip per this many units of its length, [MIN_PIPS] to [MAX_PIPS]. */
private const val PIP_SPACING = 44f
private const val MIN_PIPS = 12
private const val MAX_PIPS = 24

/** The Time Bubble's rim is a clock face of twelve hours. */
private const val CLOCK_HOURS = 12

/** Null Well's notch places and the shots that arm it. */
private const val NULL_NOTCHES = 20
private const val NULL_ARMED_AT = 5

/** Pieces drawn in from a rim, Plasma's sparks and the most marks scattered over an area. */
private const val FIELD_MOTES = 6
private const val PLASMA_SPARKS = 5
private const val PLASMA_BLINK_SECONDS = 0.5f
private const val MAX_AREA_MARKS = 9

/** Frost Line: its edges lie this far either side, a hexagon every [FROST_HEX_SPACING] units; grows and thaws over these. */
private const val FROST_HALF_WIDTH = 24f
private const val FROST_HEX_SPACING = 32f
private const val FROST_GROW_SECONDS = 0.2f
private const val FROST_THAW_SECONDS = 0.5f
private const val MAX_FROST_HEXES = 24

/** The hue a layer of a field takes: the field's first, its second, or both (a duo strip). */
private const val HUE_A = 0
private const val HUE_B = 1
private const val HUE_DUO = 2

/**
 * One field kind's look. The rim is [pip] (with a [pipDiagonal] twin when the pip is a dash laid along the rim) looping
 * its first [loop] frames, each pip [march] frames behind its clockwise neighbour; the strip's last frame is a spent pip.
 * A [timer] rim lights the remaining share. [core] loops at the centre and [mote] pieces are drawn in from the rim.
 */
private class FieldLook(
    val pip: PixelStrip,
    val pipDiagonal: PixelStrip?,
    val loop: Int,
    val march: Int,
    val pipHue: Int,
    val timer: Boolean,
    val core: PixelStrip?,
    val coreHue: Int,
    val mote: PixelStrip? = null,
    val moteHue: Int = HUE_A,
)

/** The looks by [CombatFieldVisual] ordinal. Frost Line draws its own band; its entry only names its hexagon. */
private val FieldLooks: Array<FieldLook> = Array(CombatFieldVisual.entries.size) { ordinal ->
    when (CombatFieldVisual.entries[ordinal]) {
        CombatFieldVisual.FUSION -> FieldLook(PixelStrip.FIELD_FUSION_PIP, null, 4, 1, HUE_B, true, PixelStrip.FIELD_FUSION_CORE, HUE_DUO,
            PixelStrip.FIELD_FUSION_MOTE, HUE_B)
        CombatFieldVisual.ROT_WELL -> FieldLook(PixelStrip.FIELD_ROT_PIP, null, 2, 1, HUE_A, true, PixelStrip.FIELD_ROT_CORE, HUE_B,
            PixelStrip.FIELD_ROT_MOTE, HUE_B)
        CombatFieldVisual.DARK_STAR -> FieldLook(PixelStrip.FIELD_DARKSTAR_PIP, PixelStrip.FIELD_DARKSTAR_PIP_D, 4, 1, HUE_A, false,
            PixelStrip.FIELD_DARKSTAR_CORE, HUE_A, PixelStrip.FIELD_DARKSTAR_MOTE, HUE_A)

        CombatFieldVisual.NULL_WELL -> FieldLook(PixelStrip.FIELD_NULL_PIP, null, 8, 1, HUE_B, true, PixelStrip.FIELD_NULL_CORE, HUE_A)
        CombatFieldVisual.PLASMA -> FieldLook(PixelStrip.FIELD_PLASMA_PIP, null, 3, 1, HUE_B, true, PixelStrip.FIELD_PLASMA_CORE, HUE_DUO)
        CombatFieldVisual.HUSH -> FieldLook(PixelStrip.FIELD_HUSH_PIP, null, 4, 1, HUE_B, true, PixelStrip.FIELD_HUSH_CORE, HUE_A)
        CombatFieldVisual.TIME_BUBBLE -> FieldLook(PixelStrip.FIELD_TIME_PIP, null, 4, 0, HUE_A, true, PixelStrip.FIELD_TIME_CORE, HUE_A)
        CombatFieldVisual.ATTRACTOR -> FieldLook(PixelStrip.FIELD_ATTRACTOR_PIP, PixelStrip.FIELD_ATTRACTOR_PIP_D, 4, 1, HUE_B, false,
            PixelStrip.FIELD_ATTRACTOR_CORE, HUE_DUO)
        CombatFieldVisual.FROST_LINE -> FieldLook(PixelStrip.FIELD_FROST_HEX, null, 4, 1, HUE_A, false, null, HUE_A)
    }
}

/** Draw-thread scratch for the field being drawn: its two hues. */
private object FieldHues {
    var a: Color = Color.White
    var b: Color = Color.White
}

/** Field hues: the reaction's two elements, else the field's element twice, else the kind's own pair. */
private fun fieldHues(field: CombatFieldProjection, roles: KkRolePalette) {
    val reaction = ReactionId.entries.getOrNull(field.reaction)
    when {
        reaction != null -> {
            FieldHues.a = roles.element(reaction.first.ordinal)
            FieldHues.b = roles.element(reaction.second.ordinal)
        }
        field.element in 0 until StatusElement.entries.size -> {
            FieldHues.a = roles.element(field.element)
            FieldHues.b = FieldHues.a
        }
        else -> {
            val pair = kindElements(field.kind)
            FieldHues.a = roles.element(pair / 8)
            FieldHues.b = roles.element(pair % 8)
        }
    }
}

private fun huePalette(hue: Int): ColorFilter = when (hue) {
    HUE_A -> PixelPalettes.of(FieldHues.a)
    HUE_B -> PixelPalettes.of(FieldHues.b)
    else -> PixelPalettes.duo(FieldHues.a, FieldHues.b)
}

/** A field kind's own two elements packed as `first · 8 + second` (no pair object per frame). */
private fun kindElements(kind: CombatFieldVisual): Int = when (kind) {
    CombatFieldVisual.FUSION -> THERMAL * 8 + GRAVITIC
    CombatFieldVisual.ROT_WELL -> GRAVITIC * 8 + ENTROPY
    CombatFieldVisual.DARK_STAR -> GRAVITIC * 8 + GRAVITIC
    CombatFieldVisual.NULL_WELL -> GRAVITIC * 8 + VOID
    CombatFieldVisual.PLASMA -> THERMAL * 8 + ION
    CombatFieldVisual.HUSH -> SONIC * 8 + VOID
    CombatFieldVisual.TIME_BUBBLE -> CRYO * 8 + VOID
    CombatFieldVisual.ATTRACTOR -> ION * 8 + GRAVITIC
    CombatFieldVisual.FROST_LINE -> CRYO * 8 + CRYO
}

/**
 * Lingering combat fields from the render model (never from cues, so a dropped cue never hides one), as pixel art drawn
 * as ground decals under everything that moves. Each kind has its own rim of pips on the field's exact radius; a timer
 * rim lights the remaining share of the field's life clockwise from the top and draws the rest spent. Inside it the
 * kind's centre strip loops and some kinds draw pieces in from the rim or scatter marks over the area; those motion
 * layers go first at the Reduced and Minimal settings, in a crowd and when the field pool runs out, while rims and
 * readouts (Rot Well's growth, Null Well's eaten shots, the Core inside Plasma or a Time Bubble,
 * a skill field's four ticks in the player's colour) always draw. A field grows from 0.6 of its radius over 0.15 s and
 * its pips go out in three steps over its last quarter second; everything moves on its rim strip's ticks.
 */
internal fun DrawScope.drawCombatFields(engine: GameplayRenderModel, shakeX: Float, shakeY: Float, roles: KkRolePalette) {
    val fields = engine.combatFields
    if (fields.isEmpty()) return
    val elapsed = engine.renderElapsed
    val scale = engine.worldScale
    for (index in fields.indices) {
        val field = fields[index]
        if (field.kind == CombatFieldVisual.FROST_LINE) {
            drawFrostLine(engine, field, shakeX, shakeY, roles, scale)
            continue
        }
        val look = FieldLooks[field.kind.ordinal]
        val total = max(0.001f, field.expiresAt - field.createdAt)
        // The field's age held to its rim's ticks: it grows, counts down and fades only on them.
        val clock = (elapsed - field.createdAt).coerceIn(0f, total)
        val age = PixelFxClock.stepped(clock, look.pip.fps)
        val left = total - age
        if (left <= 0f) continue
        val center = fieldCenter(engine, field, shakeX, shakeY)
        val grow = if (age < FIELD_SPAWN_SECONDS) 0.6f + 0.4f * KkEase.Pull.transform(steppedProgress(age, FIELD_SPAWN_SECONDS, look.pip.fps)) else 1f
        val radius = max(1f, field.radius * grow)
        if (!isOnScreen(center, radius + 24f)) continue
        fieldHues(field, roles)
        val seed = field.createdAt.toRawBits() xor (field.kind.ordinal * 977)
        val fade = if (left >= FIELD_FADE_SECONDS) 0 else min(FADE_STEPS, 1 + ((FIELD_FADE_SECONDS - left) / FIELD_FADE_SECONDS * FADE_STEPS).toInt())
        val inside = engine.coreState.inFieldMask and (1 shl field.kind.ordinal) != 0
        val scene = FieldScene.set(center, radius, field.radius, age, left, total - clock, total, elapsed, fade, seed, scale)
        if (field.kind == CombatFieldVisual.FUSION && left < FUSION_COLLAPSE_SECONDS) drawFusionEnd(scene, look)
        else drawRim(scene, look, field, inside)
        drawCore(scene, look, field.kind, left)
        drawFieldExtras(scene, look, field, inside)
        if (field.source == CombatFieldSource.SKILL && fade < 2) {
            // A skill's field is the player's zone: four ticks across its rim in the player's colour.
            FxDrawBudget.take(FxPool.FIELDS, 4, signature = true)
            val strip = PixelStrip.FIELD_SKILL_TICK
            val frame = PixelFxClock.loopFrame(strip, elapsed)
            for (tick in 0 until 4) {
                val angle = (45f + 90f * tick) / RAD_TO_DEG
                drawPixelFrame(strip, frame, center.x + cos(angle) * radius, center.y + sin(angle) * radius, scale, PixelPalettes.of(roles.you),
                    turns = tick)
            }
        }
    }
}

/** Draw-thread scratch for the field being drawn (filled per field, so the helpers take no long parameter lists). */
private object FieldScene {
    var x = 0f
    var y = 0f
    var radius = 0f
    var fullRadius = 0f
    var age = 0f
    var left = 0f

    /** Seconds left on the run clock itself, for the flash and the pop that step faster than the rim. */
    var end = 0f
    var total = 0f
    var elapsed = 0f
    var fade = 0
    var seed = 0
    var scale = 1f

    fun set(center: Offset, radius: Float, fullRadius: Float, age: Float, left: Float, end: Float, total: Float, elapsed: Float, fade: Int,
        seed: Int, scale: Float): FieldScene {
        x = center.x
        y = center.y
        this.radius = radius
        this.fullRadius = fullRadius
        this.age = age
        this.left = left
        this.end = end
        this.total = total
        this.elapsed = elapsed
        this.fade = fade
        this.seed = seed
        this.scale = scale
        return this
    }
}

/** The rim pips a field of [fullRadius] takes. */
private fun rimPips(fullRadius: Float): Int = (fullRadius * TAU / PIP_SPACING).roundToInt().coerceIn(MIN_PIPS, MAX_PIPS)

/**
 * The rim: [FieldLook.pip] pips on the radius clockwise from the top. A timer lights the remaining share and draws the
 * rest spent, the last lit pip blinking once its share is half gone; the fade puts pips out in steps. Always drawn.
 */
private fun DrawScope.drawRim(scene: FieldScene, look: FieldLook, field: CombatFieldProjection, inside: Boolean) {
    val strip = look.pip
    val pips = if (field.kind == CombatFieldVisual.TIME_BUBBLE) CLOCK_HOURS else rimPips(scene.fullRadius)
    val first = when (field.kind) {
        CombatFieldVisual.ROT_WELL -> 2 * (field.charge01 * 3f).roundToInt().coerceIn(0, 2)
        CombatFieldVisual.PLASMA -> if (inside) look.loop else 0
        else -> 0
    }
    val palette = when {
        field.kind == CombatFieldVisual.TIME_BUBBLE && inside -> PixelPalettes.of(Kk.Bone)
        field.kind == CombatFieldVisual.PLASMA -> PixelPalettes.of(if (inside) FieldHues.a else FieldHues.b)
        else -> huePalette(look.pipHue)
    }
    val share = if (look.timer) (scene.left / scene.total).coerceIn(0f, 1f) * pips else pips.toFloat()
    val lit = ceil(share - 0.001f).toInt()
    // The last lit pip blinks on alternate ticks once half its share has run out.
    val gutter = lit - share >= 0.5f && PixelFxClock.tick(scene.age, strip.fps) % 2 == 1
    val spent = strip.frames - 1
    val drawn = when (scene.fade) {
        0, 1 -> pips
        2 -> (pips + 1) / 2
        else -> (pips + 3) / 4
    }
    FxDrawBudget.take(FxPool.FIELDS, drawn, signature = true)
    for (pip in 0 until pips) {
        var frame = if (pip < lit && !(gutter && pip == lit - 1)) {
            PixelFxClock.loopFrame(strip, scene.elapsed, first, first + look.loop, -pip * look.march)
        } else spent
        when (scene.fade) {
            1 -> if (pip % 2 == 1) frame = spent
            2 -> if (pip % 2 == 1) continue else frame = spent
            3 -> if (pip % 4 != 0) continue else frame = spent
        }
        val angle = -QUARTER + pip * TAU / pips
        drawRimPip(look, frame, scene.x + cos(angle) * scene.radius, scene.y + sin(angle) * scene.radius, angle, scene.scale, palette)
    }
}

private fun DrawScope.drawRimPip(look: FieldLook, frame: Int, x: Float, y: Float, angle: Float, scale: Float, palette: ColorFilter) {
    val diagonal = look.pipDiagonal
    if (diagonal != null) drawPixelAimed(look.pip, diagonal, frame, x, y, angle + QUARTER, scale, palette)
    else drawPixelFrame(look.pip, frame, x, y, scale, palette)
}

/**
 * Fusion's end in place of its rim: the pips collapse onto the centre over its last quarter second on the rim's ticks,
 * then a bone flash throws them back out past the rim in its last 0.08 s, in even steps of its own.
 */
private fun DrawScope.drawFusionEnd(scene: FieldScene, look: FieldLook) {
    val pips = rimPips(scene.fullRadius)
    FxDrawBudget.take(FxPool.FIELDS, pips + 1, signature = true)
    val flash = scene.end < FUSION_FLASH_SECONDS
    val reach = max(1f, scene.radius * if (flash) 0.1f + 1.1f * fieldSweep(scene.end, FUSION_FLASH_SECONDS) else
        1f - 0.9f * (1f - scene.left / FUSION_COLLAPSE_SECONDS))
    val palette = if (flash) PixelPalettes.of(Kk.Bone) else huePalette(look.pipHue)
    for (pip in 0 until pips) {
        val angle = -QUARTER + pip * TAU / pips
        val frame = if (flash) 0 else PixelFxClock.loopFrame(look.pip, scene.elapsed, 0, look.loop, -pip)
        drawRimPip(look, frame, scene.x + cos(angle) * reach, scene.y + sin(angle) * reach, angle, scene.scale, palette)
    }
    if (flash) {
        val strip = PixelStrip.FIELD_FUSION_FLASH
        val frame = PixelFxClock.frame(strip, FUSION_FLASH_SECONDS - scene.end).coerceIn(0, strip.frames - 1)
        drawPixelFrame(strip, frame, scene.x, scene.y, scene.scale, palette)
    }
}

/** The kind's centre strip: a motion layer, gone in the second fade step (Fusion's burns through its collapse) and under its flash. */
private fun DrawScope.drawCore(scene: FieldScene, look: FieldLook, kind: CombatFieldVisual, left: Float) {
    val strip = look.core ?: return
    val fusion = kind == CombatFieldVisual.FUSION
    if ((scene.fade >= 2 && !fusion) || (fusion && scene.end < FUSION_FLASH_SECONDS) || !fieldMotion(1)) return
    val frame = PixelFxClock.loopFrame(strip, scene.elapsed)
    // Fusion's star burns bone white as it collapses.
    val palette = if (kind == CombatFieldVisual.FUSION && left < FUSION_COLLAPSE_SECONDS) PixelPalettes.duo(Kk.Bone, FieldHues.b) else huePalette(look.coreHue)
    drawPixelFrame(strip, frame, scene.x, scene.y, scene.scale, palette)
}

/** Each kind's readouts and pieces beyond its rim and centre. */
private fun DrawScope.drawFieldExtras(scene: FieldScene, look: FieldLook, field: CombatFieldProjection, inside: Boolean) {
    val mote = look.mote
    if (mote != null && scene.fade == 0 && satellitesOn()) {
        // Pieces drawn in from the rim, one inflow per play of their strip, the set turned on each.
        val period = PixelFxClock.seconds(mote)
        val cycle = (scene.elapsed / period).toInt()
        drawPixelPieces(mote, null, PieceMotion.IN, scene.x, scene.y, scene.radius, scene.elapsed - cycle * period, scene.seed + cycle * 7,
            FIELD_MOTES, scene.scale, huePalette(look.moteHue), signatures = 0, pool = FxPool.FIELDS)
    }
    when (field.kind) {
        CombatFieldVisual.ROT_WELL -> drawAreaMarks(scene, PixelStrip.FIELD_ROT_SPECK, PixelPalettes.of(FieldHues.b), 22f)
        CombatFieldVisual.NULL_WELL -> drawNullNotches(scene, field)
        CombatFieldVisual.PLASMA -> drawPlasmaSparks(scene)
        CombatFieldVisual.HUSH -> drawHushTicks(scene)
        CombatFieldVisual.TIME_BUBBLE -> drawClockHand(scene, if (inside) Kk.Bone else FieldHues.a)
        else -> Unit
    }
}

/** Marks of [strip] scattered over the area, one per [step] units of radius: the ground the field has turned. */
private fun DrawScope.drawAreaMarks(scene: FieldScene, strip: PixelStrip, palette: ColorFilter, step: Float) {
    val count = (scene.radius / step).toInt().coerceIn(4, MAX_AREA_MARKS)
    if (scene.fade > 0 || !fieldMotion(count)) return
    for (mark in 0 until count) {
        val angle = fxHash(scene.seed, 2 * mark + 40) * TAU
        val reach = scene.radius * (0.3f + 0.55f * fxHash(scene.seed, 2 * mark + 41))
        drawPixelFrame(strip, PixelFxClock.loopFrame(strip, scene.elapsed, phase = mark * 3), scene.x + cos(angle) * reach,
            scene.y + sin(angle) * reach, scene.scale, palette)
    }
}

/**
 * Null Well's eaten shots: twenty places on a ring at 0.45 of the radius, a notch on each eaten one and a socket on
 * every other empty one. From five eaten the notches arm and turn bone; an armed well pops them out past its rim over
 * its last 0.12 s in even steps of its own, the last always past the rim.
 */
private fun DrawScope.drawNullNotches(scene: FieldScene, field: CombatFieldProjection) {
    val eaten = (field.charge01 * NULL_NOTCHES).roundToInt().coerceIn(0, NULL_NOTCHES)
    val armed = eaten >= NULL_ARMED_AT
    val strip = PixelStrip.FIELD_NULL_NOTCH
    val pop = armed && scene.end < NULL_POP_SECONDS
    if (scene.fade >= 2 && !pop) return
    val reach = scene.radius * if (pop) 0.45f + 0.75f * fieldSweep(scene.end, NULL_POP_SECONDS) else 0.45f
    FxDrawBudget.take(FxPool.FIELDS, if (pop) NULL_NOTCHES else eaten + (NULL_NOTCHES - eaten + 1) / 2, signature = true)
    val bone = PixelPalettes.of(Kk.Bone)
    val socket = PixelPalettes.of(FieldHues.b)
    for (notch in 0 until NULL_NOTCHES) {
        val full = notch < eaten || pop
        if (!full && notch % 2 == 1) continue
        val frame = when {
            !full -> 0
            armed -> PixelFxClock.loopFrame(strip, scene.elapsed, 3, 5, notch)
            else -> PixelFxClock.loopFrame(strip, scene.elapsed, 1, 3, notch)
        }
        val angle = -QUARTER + notch * TAU / NULL_NOTCHES
        drawPixelFrame(strip, frame, scene.x + cos(angle) * reach, scene.y + sin(angle) * reach, scene.scale,
            if (full && armed) bone else socket)
    }
}

/** Plasma's sparks: five blobs over the area flaring through their strip in the first half of every half second. */
private fun DrawScope.drawPlasmaSparks(scene: FieldScene) {
    val strip = PixelStrip.FIELD_PLASMA_SPARK
    val frame = PixelFxClock.frame(strip, scene.elapsed % PLASMA_BLINK_SECONDS)
    if (frame < 0 || scene.fade > 0 || !fieldMotion(PLASMA_SPARKS)) return
    for (spark in 0 until PLASMA_SPARKS) {
        val angle = fxHash(scene.seed, spark) * TAU
        val reach = scene.radius * (0.2f + 0.6f * fxHash(scene.seed, spark + 9))
        drawPixelFrame(strip, frame, scene.x + cos(angle) * reach, scene.y + sin(angle) * reach, scene.scale, PixelPalettes.of(FieldHues.a))
    }
}

/** Hush's eight ticks across its rim, each cut and sealed in turn, and the quiet marks over its area. */
private fun DrawScope.drawHushTicks(scene: FieldScene) {
    drawAreaMarks(scene, PixelStrip.FIELD_HUSH_MARK, PixelPalettes.of(FieldHues.a), 24f)
    if (scene.fade > 0 || !fieldMotion(8)) return
    val palette = PixelPalettes.of(FieldHues.a)
    for (tick in 0 until 8) {
        val angle = tick * TAU / 8f
        val frame = PixelFxClock.loopFrame(PixelStrip.FIELD_HUSH_TICK, scene.elapsed, phase = tick)
        drawPixelAimed(PixelStrip.FIELD_HUSH_TICK, PixelStrip.FIELD_HUSH_TICK_D, frame, scene.x + cos(angle) * scene.radius,
            scene.y + sin(angle) * scene.radius, angle + QUARTER, scene.scale, palette)
    }
}

/** The Time Bubble's hand: from the centre to 0.8 of the radius, one turn over the bubble's life, on the rim's ticks. */
private fun DrawScope.drawClockHand(scene: FieldScene, hue: Color) {
    if (scene.fade >= 2) return
    val strip = PixelStrip.LINK_TIME_HAND
    val angle = -QUARTER + TAU * (scene.age / scene.total)
    val reach = scene.radius * 0.8f
    val tiles = max(1, ceil(reach * scene.scale / (strip.w * PixelSheet.step(scene.scale))).toInt())
    if (!fieldMotion(tiles)) return
    drawPixelLink(strip, PixelFxClock.loopFrame(strip, scene.elapsed), scene.x, scene.y, scene.x + cos(angle) * reach, scene.y + sin(angle) * reach,
        scene.scale, PixelPalettes.of(hue))
}

/**
 * How far (0 to 1) a sweep over a field's last [window] seconds has gone with [left] seconds to go: it moves on the
 * ticks of [SWEEP_FPS] (the flash strip's rate), from its start on the first tick to its full reach on the last.
 */
internal fun fieldSweep(left: Float, window: Float): Float {
    val ticks = max(2, (window * SWEEP_FPS).roundToInt())
    return PixelFxClock.tick(window - left, SWEEP_FPS).coerceIn(0, ticks - 1) / (ticks - 1f)
}

/** Takes [ops] from the field budget for a motion layer: never at the Reduced or Minimal setting or in a crowd. */
private fun fieldMotion(ops: Int): Boolean = satellitesOn() && FxDrawBudget.take(FxPool.FIELDS, ops, signature = false)

/** Where a field sits: on the interpolated Core, on its anchor enemy, or at its own point. */
private fun DrawScope.fieldCenter(engine: GameplayRenderModel, field: CombatFieldProjection, shakeX: Float, shakeY: Float): Offset {
    if (field.followsCore) return world(engine, engine.renderCoreX, engine.renderCoreY, shakeX, shakeY)
    if (field.kind == CombatFieldVisual.ATTRACTOR && field.anchorId >= 0) {
        val enemies = engine.enemies
        for (index in enemies.indices) {
            val enemy = enemies[index]
            if (enemy.id != field.anchorId) continue
            return world(engine, engine.interpolated(enemy.previousX, enemy.x), engine.interpolated(enemy.previousY, enemy.y), shakeX, shakeY)
        }
    }
    return world(engine, field.x, field.y, shakeX, shakeY)
}

/**
 * Frost Line: an ice wall tiled along each edge 24 either side of the segment with its icicles leaning into the band,
 * an end bar aimed across each end and a hexagon of ice every 32 along the middle. It grows from the centre over 0.2 s
 * and thaws over its last 0.5 s, the walls and bars breaking up and the hexagons going out, all on the wall's ticks.
 */
private fun DrawScope.drawFrostLine(
    engine: GameplayRenderModel,
    field: CombatFieldProjection,
    shakeX: Float,
    shakeY: Float,
    roles: KkRolePalette,
    scale: Float,
) {
    val from = world(engine, field.x, field.y, shakeX, shakeY)
    val to = world(engine, field.x2, field.y2, shakeX, shakeY)
    val dx = to.x - from.x
    val dy = to.y - from.y
    val length = sqrt(dx * dx + dy * dy)
    if (length < 1f) return
    val edge = PixelStrip.LINK_FROST_EDGE
    val total = max(0.001f, field.expiresAt - field.createdAt)
    val age = PixelFxClock.stepped((engine.renderElapsed - field.createdAt).coerceIn(0f, total), edge.fps)
    val left = total - age
    if (left <= 0f) return
    val mid = Offset((from.x + to.x) * 0.5f, (from.y + to.y) * 0.5f)
    if (!isOnScreen(mid, length * 0.5f + 30f)) return
    val half = length * 0.5f * steppedProgress(age, FROST_GROW_SECONDS, edge.fps)
    // Thaw: 0 standing, 1 breaking up over the first 0.3 s of the last half second, 2 nearly gone.
    val thaw = if (left >= FROST_THAW_SECONDS) 0 else if (left >= FROST_THAW_SECONDS * 0.4f) 1 else 2
    val ux = dx / length
    val uy = dy / length
    val sx = mid.x - ux * half
    val sy = mid.y - uy * half
    val ex = mid.x + ux * half
    val ey = mid.y + uy * half
    val nx = -uy * FROST_HALF_WIDTH
    val ny = ux * FROST_HALF_WIDTH
    val palette = PixelPalettes.of(roles.element(field.element.takeIf { it in 0 until StatusElement.entries.size } ?: CRYO))
    val elapsed = engine.renderElapsed
    val wall = if (thaw == 0) PixelFxClock.loopFrame(edge, elapsed, 0, 4) else 3 + thaw
    val tiles = max(1, ceil(half * 2f * scale / (edge.w * PixelSheet.step(scale))).toInt())
    FxDrawBudget.take(FxPool.FIELDS, 2 * tiles + 2, signature = true)
    // Both walls run so their icicles face the band: one from the start, the other back from the end.
    drawPixelLink(edge, wall, sx + nx, sy + ny, ex + nx, ey + ny, scale, palette)
    drawPixelLink(edge, wall, ex - nx, ey - ny, sx - nx, sy - ny, scale, palette)
    val cap = PixelStrip.FIELD_FROST_CAP
    val bar = if (thaw == 0) PixelFxClock.loopFrame(cap, elapsed, 0, 3) else 2 + thaw
    val heading = atan2(uy, ux)
    drawPixelAimed(cap, PixelStrip.FIELD_FROST_CAP_D, bar, sx, sy, heading, scale, palette)
    drawPixelAimed(cap, PixelStrip.FIELD_FROST_CAP_D, bar, ex, ey, heading + PI.toFloat(), scale, palette)
    val hexes = min(MAX_FROST_HEXES, (half * 2f / FROST_HEX_SPACING).toInt())
    if (thaw == 2 || hexes == 0 || !fieldMotion(if (thaw == 1) (hexes + 1) / 2 else hexes)) return
    val hex = PixelStrip.FIELD_FROST_HEX
    for (index in 0 until hexes) {
        if (thaw == 1 && index % 2 == 1) continue
        val along = (index + 0.5f) * FROST_HEX_SPACING
        drawPixelFrame(hex, PixelFxClock.loopFrame(hex, elapsed, phase = index), sx + ux * along, sy + uy * along, scale, palette)
    }
}
