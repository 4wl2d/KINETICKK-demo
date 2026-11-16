// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.interaction.canvas.world

import kinetickk.foundation.design.*

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.gameplay.interaction.fx.PixelFxClock
import kinetickk.ball.gameplay.interaction.fx.PixelStrip
import kinetickk.ball.gameplay.nucleus.render.CombatFieldVisual
import kinetickk.ball.gameplay.nucleus.render.EnemyControlBits
import kinetickk.ball.gameplay.nucleus.render.EnemyMarkVisual
import kinetickk.ball.gameplay.nucleus.render.EnemyProjection
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kinetickk.ball.gameplay.interaction.canvas.*
import kinetickk.ball.gameplay.interaction.canvas.fx.*

/*
 * State feedback round enemies and the Core as looping pixel cells: one strip per
 * state, placed where the vector glyph sat. A strip loops on the run clock with its own holds (heldLoopFrame); a
 * piece that moves round a ring changes place only on its strip's ticks. Rings of exact radius are pips set on the
 * circle and aimed outward; every pip, mark or segment takes one operation from its pool.
 */

private const val TAU = CanvasMath.TAU

private val THERMAL = StatusElement.THERMAL.ordinal
private val CRYO = StatusElement.CRYO.ordinal
private val ION = StatusElement.ION.ordinal
private val GRAVITIC = StatusElement.GRAVITIC.ordinal
private val ENTROPY = StatusElement.ENTROPY.ordinal
private val SONIC = StatusElement.SONIC.ordinal
private val VOID = StatusElement.VOID.ordinal

/** A Cracked mark is armed for Breakpoint while the Core brakes this close (world units). */
private const val CRACKED_ARMED_REACH = 320f

/** Clamps on each of the Void Anchor's two rings; the rings turn a quarter turn a second, the inner one clockwise. */
private const val ANCHOR_CLAMPS = 4
private const val ANCHOR_TURN = (PI / 2.0).toFloat()

/** A clamp's middle lies this far round from its quarter's start (the old 50 degree arcs' middle). */
private const val ANCHOR_CLAMP_MIDDLE = 25f * CanvasMath.DEG_TO_RAD

/** The strike bracket's corner lies four texels up and left of its cell's centre (world units). */
private const val STRIKE_CORNER = 8f

/** A mark's cell sits this far outside the status ring, so its art ends where the old glyph ended (units). */
private const val MARK_GAP = 11f

/** The silence mark's cell sits this far outside the status ring, up and to the left (units). */
private const val SILENCE_GAP = 10f

/** Blackfire frames: 0-5 an upright flame, 6-11 a leaning one (mirrored for the left), 12 the crowd dot. */
private const val BLACKFIRE_LEAN = 6
private const val BLACKFIRE_DOT = 12

/** A Blackfire flame's root lies this far below its cell's centre, and a leaning one's this far to the side (units). */
private const val FLAME_ROOT_Y = 8.8f
private const val FLAME_LEAN_X = 3.2f

/** Daze stars: frames 0-3 on the near half of their orbit, 4-5 on the far half; one turn every 0.8 s. */
private const val DAZE_NEAR_FRAMES = 4
private const val DAZE_ORBIT_SECONDS = 0.8f

/** A throw streak's cell centre lies this far behind its root (10.5 texels, units). */
private const val STREAK_CENTER = 21f

/** The pending Unmake's ring closes from 12 units outside the enemy by 10 units each loop of its strip. */
private const val UNMAKE_START = 12f
private const val UNMAKE_TRAVEL = 10f

/** Pips of every ring hugging the Core (the blast jump, phase, primed, plasma and time bubble rings). */
private const val CORE_RING_PIPS = 8

/** Ablaze and Frictionless show their worn frames once their remaining share falls below this. */
private const val STATE_WORN = 0.35f

/** Frames of each worn and fresh range: Ablaze flickers on 0-5 and dies on 6-8; Frictionless 0-3 then 4-5. */
private const val ABLAZE_FRESH = 6
private const val SKATE_FRESH = 4

/** A flame's cell centre lies this far out from its root (3.6 texels, units); a skate line's 10.5 texels. */
private const val ABLAZE_CENTER = 7.2f
private const val SKATE_CENTER = 21f

/** Frames of each coil of a coiled Dash (inner, middle, outer). */
private const val COIL_FRAMES = 4

/**
 * The frame of [strip] looping over its frames [first] until [end] with their own holds, [phase] ticks ahead, at
 * [time] seconds. ([PixelFxClock.loopFrame] holds every frame one tick; a state loop keeps its authored timing.)
 */
internal fun heldLoopFrame(strip: PixelStrip, time: Float, first: Int = 0, end: Int = strip.frames, phase: Int = 0): Int {
    var span = 0
    for (frame in first until end) span += strip.holds[frame] - '0'
    var tick = (PixelFxClock.tick(max(0f, time), strip.fps) + phase) % span
    for (frame in first until end) {
        tick -= strip.holds[frame] - '0'
        if (tick < 0) return frame
    }
    return end - 1
}

/** A pip of [cardinal] (or its [diagonal] twin) on the circle of [radius] round ([x], [y]) at [angle], aimed outward. */
internal fun DrawScope.drawStatePip(
    cardinal: PixelStrip,
    diagonal: PixelStrip,
    frame: Int,
    x: Float,
    y: Float,
    radius: Float,
    angle: Float,
    scale: Float,
    palette: ColorFilter,
) = drawPixelAimed(cardinal, diagonal, frame, x + cos(angle) * radius, y + sin(angle) * radius, angle, scale, palette)

/**
 * Control and mark glyphs around an enemy (screen space, never turned): the daze stars, the silence
 * mark, throw streaks, Blackfire flames, the pending Unmake, its mark, the Void Anchor clamps, the attractor bars and
 * the Apex strike brackets. A crowded screen keeps the mark as a dot, one flame, one star and the anchor and strike
 * marks; a packed one only those two and a dot.
 */
internal fun DrawScope.drawEnemyControl(
    engine: GameplayRenderModel,
    enemy: EnemyProjection,
    center: Offset,
    ring: Float,
    roles: KkRolePalette,
) {
    val bits = enemy.controlBits
    if (bits == 0) return
    val time = engine.renderElapsed
    val scale = engine.worldScale
    val crowd = FxFrame.crowd
    val packed = crowd > FxFrame.CROWD_SIG_ONLY
    val crowded = crowd > FxFrame.CROWD_SECONDARY_OFF
    val radius = enemy.radius
    // Each enemy runs its loops from its own place, so a crowd under one state does not pulse in step.
    val phase = enemy.id and 15
    if (bits and EnemyControlBits.VOID_ANCHOR != 0 && FxDrawBudget.take(FxPool.GLYPHS, ANCHOR_CLAMPS * 2, signature = true)) {
        drawAnchorClamps(center, ring, time, scale, PixelPalettes.of(roles.element(VOID)))
    }
    if (bits and EnemyControlBits.APEX_STRIKE_TARGET != 0 && FxDrawBudget.take(FxPool.GLYPHS, 4, signature = true)) {
        drawStrikeBrackets(center, radius + 14f, time, scale, PixelPalettes.of(roles.gold.base))
    }
    val blackfire = bits and EnemyControlBits.BLACKFIRE != 0
    if (packed) {
        if (blackfire && FxDrawBudget.take(FxPool.GLYPHS, 1, signature = false)) {
            drawPixelFrame(PixelStrip.MARK_BLACKFIRE, BLACKFIRE_DOT, center.x, center.y - radius * 0.5f, scale,
                PixelPalettes.duo(roles.element(THERMAL), roles.element(VOID)))
        }
        return
    }
    val flames = if (crowded) 1 else 3
    if (blackfire && FxDrawBudget.take(FxPool.GLYPHS, flames, signature = false)) {
        drawBlackfire(center, radius, flames, time, phase, scale, PixelPalettes.duo(roles.element(THERMAL), roles.element(VOID)))
    }
    val stars = if (crowded) 1 else 3
    if (bits and EnemyControlBits.STOPPED != 0 && FxDrawBudget.take(FxPool.GLYPHS, stars, signature = false)) {
        drawDaze(center.x, center.y - ring - 10f, stars, time, phase, scale, PixelPalettes.of(Kk.Bone))
    }
    val mark = EnemyControlBits.mark(bits)
    if (mark != null && FxDrawBudget.take(FxPool.GLYPHS, 1, signature = false)) {
        drawMark(engine, enemy, mark, center.x - ring - MARK_GAP, center.y, crowded, time, phase, scale, roles)
    }
    if (crowded) return
    if (bits and EnemyControlBits.SILENCED != 0 && FxDrawBudget.take(FxPool.GLYPHS, 1, signature = false)) {
        val at = polar(center, ring + SILENCE_GAP, -0.75f * PI.toFloat())
        drawPixelFrame(PixelStrip.MARK_SILENCE, heldLoopFrame(PixelStrip.MARK_SILENCE, time, phase = phase), at.x, at.y, scale,
            PixelPalettes.duo(roles.element(SONIC), roles.element(VOID)))
    }
    if (bits and EnemyControlBits.THROWN != 0) drawThrowStreaks(enemy, center, time, phase, scale)
    if (bits and EnemyControlBits.UNMAKE_PENDING != 0 && FxDrawBudget.take(FxPool.GLYPHS, 5, signature = false)) {
        drawUnmake(center, radius, time, scale, PixelPalettes.of(roles.element(VOID)))
    }
    if (bits and EnemyControlBits.ATTRACTOR_ANCHOR != 0 && FxDrawBudget.take(FxPool.GLYPHS, 1, signature = false)) {
        drawPixelFrame(PixelStrip.MARK_ATTRACTOR, heldLoopFrame(PixelStrip.MARK_ATTRACTOR, time, phase = phase), center.x,
            center.y + ring + 8f, scale, PixelPalettes.duo(roles.element(ION), roles.element(GRAVITIC)))
    }
}

/** The Void Anchor: two rings of clamps at 10 and 16 units outside the status ring, turning against each other. */
private fun DrawScope.drawAnchorClamps(center: Offset, ring: Float, time: Float, scale: Float, palette: ColorFilter) {
    val strip = PixelStrip.MARK_ANCHOR
    val turn = PixelFxClock.stepped(time, strip.fps) * ANCHOR_TURN
    for (circle in 0..1) {
        val r = ring + if (circle == 0) 10f else 16f
        val spin = if (circle == 0) turn else -turn
        for (clamp in 0 until ANCHOR_CLAMPS) {
            val angle = spin + clamp * (TAU / ANCHOR_CLAMPS) + ANCHOR_CLAMP_MIDDLE
            drawStatePip(strip, PixelStrip.MARK_ANCHOR_D, heldLoopFrame(strip, time, phase = clamp + circle * 3), center.x, center.y, r, angle,
                scale, palette)
        }
    }
}

/** The Apex strike brackets on the four corners [reach] out from the target, biting in together every 1.2 s. */
private fun DrawScope.drawStrikeBrackets(center: Offset, reach: Float, time: Float, scale: Float, palette: ColorFilter) {
    val strip = PixelStrip.MARK_STRIKE
    val frame = heldLoopFrame(strip, time)
    val inset = reach - STRIKE_CORNER
    for (corner in 0 until 4) {
        val right = corner and 1 != 0
        val down = corner and 2 != 0
        drawPixelFrame(strip, frame, center.x + if (right) inset else -inset, center.y + if (down) inset else -inset, scale, palette,
            flipX = right, flipY = down)
    }
}

/** Blackfire: [flames] flames rising from 0.45 radii above the centre, the outer two leaning 25 degrees away. */
private fun DrawScope.drawBlackfire(center: Offset, radius: Float, flames: Int, time: Float, phase: Int, scale: Float, palette: ColorFilter) {
    val strip = PixelStrip.MARK_BLACKFIRE
    for (flame in 0 until flames) {
        val side = if (flames == 1) 0 else flame - 1
        val base = polar(center, radius * 0.45f, (-90f + side * 25f) * CanvasMath.DEG_TO_RAD)
        val frame = if (side == 0) heldLoopFrame(strip, time, 0, BLACKFIRE_LEAN, phase + flame * 2)
        else heldLoopFrame(strip, time, BLACKFIRE_LEAN, BLACKFIRE_DOT, phase + flame * 2)
        drawPixelFrame(strip, frame, base.x + side * FLAME_LEAN_X, base.y - FLAME_ROOT_Y, scale, palette, flipX = side < 0)
    }
}

/** Daze stars orbiting an ellipse 14 by 5 units round ([x], [y]), dimmer on its far half. */
private fun DrawScope.drawDaze(x: Float, y: Float, stars: Int, time: Float, phase: Int, scale: Float, palette: ColorFilter) {
    val strip = PixelStrip.MARK_DAZE
    val orbit = PixelFxClock.stepped(time, strip.fps) * TAU / DAZE_ORBIT_SECONDS
    for (star in 0 until stars) {
        val angle = orbit + star * TAU / 3f
        val frame = if (sin(angle) >= 0f) heldLoopFrame(strip, time, 0, DAZE_NEAR_FRAMES, phase + star)
        else heldLoopFrame(strip, time, DAZE_NEAR_FRAMES, strip.frames, phase + star)
        drawPixelFrame(strip, frame, x + cos(angle) * 14f, y + sin(angle) * 5f, scale, palette)
    }
}

/** A mark's element hue (its crowd dot). */
private fun markHue(mark: EnemyMarkVisual, roles: KkRolePalette): Color = when (mark) {
    EnemyMarkVisual.SPALL -> roles.element(THERMAL)
    EnemyMarkVisual.BOOMED -> roles.element(SONIC)
    else -> roles.element(ENTROPY)
}

/** A mark at ([x], [y]): its loop, the armed Cracked loop while the Core brakes nearby, or its crowd dot when [dot]. */
private fun DrawScope.drawMark(
    engine: GameplayRenderModel,
    enemy: EnemyProjection,
    mark: EnemyMarkVisual,
    x: Float,
    y: Float,
    dot: Boolean,
    time: Float,
    phase: Int,
    scale: Float,
    roles: KkRolePalette,
) {
    val strip = when (mark) {
        EnemyMarkVisual.SPALL -> PixelStrip.MARK_SPALL
        EnemyMarkVisual.CRACKED -> if (!dot && crackedArmed(engine, enemy)) PixelStrip.MARK_CRACKED_ARMED else PixelStrip.MARK_CRACKED
        EnemyMarkVisual.SPORED -> PixelStrip.MARK_SPORED
        EnemyMarkVisual.BOOMED -> PixelStrip.MARK_BOOMED
    }
    // The final frame is the crowd dot except for the armed Cracked strip.
    val frame = when {
        dot -> strip.frames - 1
        strip == PixelStrip.MARK_CRACKED_ARMED -> heldLoopFrame(strip, time, phase = phase)
        else -> heldLoopFrame(strip, time, 0, strip.frames - 1, phase)
    }
    drawPixelFrame(strip, frame, x, y, scale, PixelPalettes.of(markHue(mark, roles)))
}

/** Armed for Breakpoint while the Core brakes within [CRACKED_ARMED_REACH]: brighter and thicker. */
private fun crackedArmed(engine: GameplayRenderModel, enemy: EnemyProjection): Boolean {
    val dx = enemy.x - engine.coreX
    val dy = enemy.y - engine.coreY
    return engine.braking && dx * dx + dy * dy <= CRACKED_ARMED_REACH * CRACKED_ARMED_REACH
}

/**
 * Throw streaks trailing from 4 units behind a thrown enemy, against its travel: a glyph operation, taken only when the
 * enemy moves and so has a travel to trail.
 */
private fun DrawScope.drawThrowStreaks(enemy: EnemyProjection, center: Offset, time: Float, phase: Int, scale: Float) {
    val speed = sqrt(enemy.vx * enemy.vx + enemy.vy * enemy.vy)
    if (speed <= 1f || !FxDrawBudget.take(FxPool.GLYPHS, 1, signature = false)) return
    val bx = -enemy.vx / speed
    val by = -enemy.vy / speed
    val reach = enemy.radius + 4f + STREAK_CENTER
    drawPixelAimed(PixelStrip.MARK_THROWN, PixelStrip.MARK_THROWN_D, heldLoopFrame(PixelStrip.MARK_THROWN, time, phase = phase),
        center.x + bx * reach, center.y + by * reach, atan2(by, bx), scale, PixelPalettes.of(Kk.Bone))
}

/** The pending Unmake: four dashes of a ring closing on the enemy every 0.4 s, onto the ink eye at its centre. */
private fun DrawScope.drawUnmake(center: Offset, radius: Float, time: Float, scale: Float, palette: ColorFilter) {
    val strip = PixelStrip.MARK_UNMAKE
    val ticks = PixelFxClock.ticks(strip)
    val closed = (PixelFxClock.tick(max(0f, time), strip.fps) % ticks + 0.5f) / ticks
    val r = radius + UNMAKE_START - UNMAKE_TRAVEL * closed
    val frame = heldLoopFrame(strip, time)
    for (side in 0 until 4) {
        val dx = if (side == 0) r else if (side == 2) -r else 0f
        val dy = if (side == 1) r else if (side == 3) -r else 0f
        drawPixelFrame(strip, frame, center.x + dx, center.y + dy, scale, palette, turns = side)
    }
    drawPixelFrame(PixelStrip.MARK_UNMAKE_CORE, heldLoopFrame(PixelStrip.MARK_UNMAKE_CORE, time), center.x, center.y, scale, palette)
}

/**
 * Lingering reaction and skill states on the Core ([GameplayRenderModel.coreState]): Ablaze flames,
 * Frictionless skate lines, the Blast Jump rings, the Arc Weave orbit, guard shards, the Void Step phase, primed and coiled Dashes and the fields the Core stands in. Rings sit on the Core's real
 * radius.
 */
internal fun DrawScope.drawCoreStates(engine: GameplayRenderModel, core: Offset, roles: KkRolePalette) {
    val state = engine.coreState
    val radius = engine.coreRadius
    val time = engine.renderElapsed
    val scale = engine.worldScale
    if (state.ablaze01 > 0f && FxDrawBudget.take(FxPool.CORE_STATES, CORE_RING_PIPS, signature = false)) {
        drawAblaze(core, radius, state.ablaze01 < STATE_WORN, time, scale, PixelPalettes.of(roles.element(THERMAL)))
    }
    if (state.frictionless01 > 0f && engine.speed > 1f && FxDrawBudget.take(FxPool.CORE_STATES, 1, signature = false)) {
        val bx = -engine.velocityX / engine.speed
        val by = -engine.velocityY / engine.speed
        val strip = PixelStrip.STATE_SKATE
        val frame = if (state.frictionless01 < STATE_WORN) heldLoopFrame(strip, time, SKATE_FRESH, strip.frames) else heldLoopFrame(strip, time, 0, SKATE_FRESH)
        drawPixelAimed(strip, PixelStrip.STATE_SKATE_D, frame, core.x + bx * (radius + SKATE_CENTER), core.y + by * (radius + SKATE_CENTER),
            atan2(by, bx), scale, PixelPalettes.of(roles.element(CRYO)))
    }
    if (state.blastJumpReady && FxDrawBudget.take(FxPool.CORE_STATES, CORE_RING_PIPS, signature = false)) {
        drawCoreRing(PixelStrip.STATE_BLASTJUMP, PixelStrip.STATE_BLASTJUMP_D, core, radius + 18f, time, scale,
            PixelPalettes.duo(roles.element(THERMAL), roles.element(SONIC)), stride = 0, sweep = false)
    }
    if (state.weave01 > 0f && FxDrawBudget.take(FxPool.CORE_STATES, 3, signature = false)) {
        drawWeave(core, radius + 18f, time, scale, PixelPalettes.duo(roles.element(ION), roles.element(SONIC)))
    }
    val shards = state.guardShards
    if (shards > 0 && FxDrawBudget.take(FxPool.CORE_STATES, shards, signature = false)) {
        drawGuardShards(core, radius + 74f, shards, time, scale, PixelPalettes.of(roles.element(CRYO)))
    }
    if (state.phased && FxDrawBudget.take(FxPool.CORE_STATES, CORE_RING_PIPS, signature = false)) {
        drawCoreRing(PixelStrip.STATE_PHASED, PixelStrip.STATE_PHASED_D, core, radius + 3f, time, scale, PixelPalettes.of(roles.element(VOID)),
            stride = 3, sweep = false)
    }
    if (state.primedDash && FxDrawBudget.take(FxPool.CORE_STATES, CORE_RING_PIPS, signature = false)) {
        drawCoreRing(PixelStrip.STATE_PRIMED, PixelStrip.STATE_PRIMED_D, core, radius + 10f, time, scale, PixelPalettes.of(roles.you),
            stride = 0, sweep = true)
    }
    if (state.coiledDash && FxDrawBudget.take(FxPool.CORE_STATES, 3, signature = false)) {
        drawCoils(core, radius, time, scale, PixelPalettes.of(roles.element(CRYO)))
    }
    val mask = state.inFieldMask
    if (mask and (1 shl CombatFieldVisual.PLASMA.ordinal) != 0 && FxDrawBudget.take(FxPool.CORE_STATES, CORE_RING_PIPS, signature = false)) {
        drawCoreRing(PixelStrip.STATE_PLASMA, PixelStrip.STATE_PLASMA_D, core, radius + 4f, time, scale, PixelPalettes.of(roles.element(THERMAL)),
            stride = 0, sweep = false)
    }
    if (mask and (1 shl CombatFieldVisual.TIME_BUBBLE.ordinal) != 0 && FxDrawBudget.take(FxPool.CORE_STATES, CORE_RING_PIPS, signature = false)) {
        drawCoreRing(PixelStrip.STATE_TIMEBUBBLE, PixelStrip.STATE_TIMEBUBBLE_D, core, radius + 8f, time, scale,
            PixelPalettes.of(roles.element(CRYO)), stride = 0, sweep = true)
    }
}

/**
 * Eight pips of a ring round the Core at [radius], one each eighth of a turn. A [sweep] ring lights one pip at a time
 * round the ring on its strip's ticks (frame 0 lit, later frames its fading tail); otherwise each pip loops the strip,
 * [stride] ticks after the one before.
 */
private fun DrawScope.drawCoreRing(
    cardinal: PixelStrip,
    diagonal: PixelStrip,
    core: Offset,
    radius: Float,
    time: Float,
    scale: Float,
    palette: ColorFilter,
    stride: Int,
    sweep: Boolean,
) {
    val tick = PixelFxClock.tick(max(0f, time), cardinal.fps)
    for (pip in 0 until CORE_RING_PIPS) {
        val frame = if (sweep) min(cardinal.frames - 1, (tick - pip).mod(CORE_RING_PIPS)) else heldLoopFrame(cardinal, time, phase = pip * stride)
        drawStatePip(cardinal, diagonal, frame, core.x, core.y, radius, pip * TAU / CORE_RING_PIPS, scale, palette)
    }
}

/** Ablaze: eight flames licking outward from 6 units outside the Core; dying frames once the state runs low. */
private fun DrawScope.drawAblaze(core: Offset, radius: Float, worn: Boolean, time: Float, scale: Float, palette: ColorFilter) {
    val strip = PixelStrip.STATE_ABLAZE
    for (flame in 0 until CORE_RING_PIPS) {
        val frame = if (worn) heldLoopFrame(strip, time, ABLAZE_FRESH, strip.frames, flame) else heldLoopFrame(strip, time, 0, ABLAZE_FRESH, flame * 2)
        drawStatePip(strip, PixelStrip.STATE_ABLAZE_D, frame, core.x, core.y, radius + 6f + ABLAZE_CENTER, flame * TAU / CORE_RING_PIPS, scale,
            palette)
    }
}

/** Arc Weave: three ion charges riding the orbit at [orbit], 6 radians a second clockwise, each aimed along its travel. */
private fun DrawScope.drawWeave(core: Offset, orbit: Float, time: Float, scale: Float, palette: ColorFilter) {
    val strip = PixelStrip.STATE_WEAVE
    val turn = PixelFxClock.stepped(time, strip.fps) * 6f
    for (charge in 0 until 3) {
        val angle = turn + charge * TAU / 3f
        drawPixelAimed(strip, PixelStrip.STATE_WEAVE_D, heldLoopFrame(strip, time, phase = charge), core.x + cos(angle) * orbit,
            core.y + sin(angle) * orbit, angle + TAU * 0.25f, scale, palette)
    }
}

/** Guard shards on their orbit at [orbit], 3 radians a second, one cell per shard. */
private fun DrawScope.drawGuardShards(core: Offset, orbit: Float, shards: Int, time: Float, scale: Float, palette: ColorFilter) {
    val strip = PixelStrip.STATE_GUARD
    val turn = PixelFxClock.stepped(time, strip.fps) * 3f
    for (shard in 0 until shards) {
        drawStatePip(strip, PixelStrip.STATE_GUARD_D, heldLoopFrame(strip, time, phase = shard * 2), core.x, core.y, orbit,
            turn + shard * TAU / shards, scale, palette)
    }
}

/** A coiled Dash: three coils at 10, 14 and 18 units outside the Core a third of a turn apart, 3 radians a second. */
private fun DrawScope.drawCoils(core: Offset, radius: Float, time: Float, scale: Float, palette: ColorFilter) {
    val strip = PixelStrip.STATE_COIL
    val turn = PixelFxClock.stepped(time, strip.fps) * 3f
    for (coil in 0 until 3) {
        // Each coil spans a quarter turn from its start; its cell is aimed at the middle.
        val angle = turn + coil * TAU / 3f + TAU / 8f
        val frame = heldLoopFrame(strip, time, coil * COIL_FRAMES, (coil + 1) * COIL_FRAMES)
        drawStatePip(strip, PixelStrip.STATE_COIL_D, frame, core.x, core.y, radius + 10f + coil * 4f, angle, scale, palette)
    }
}
