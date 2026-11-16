// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.interaction.canvas.world

import kinetickk.foundation.design.*

import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import kinetickk.ball.content.api.SkillId
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.gameplay.interaction.fx.PixelFxClock
import kinetickk.ball.gameplay.interaction.fx.PixelStrip
import kinetickk.ball.gameplay.nucleus.render.EnemyControlBits
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kinetickk.ball.gameplay.interaction.canvas.*
import kinetickk.ball.gameplay.interaction.canvas.fx.*

private const val TAU = CanvasMath.TAU
private const val QUARTER = (PI / 2.0).toFloat()

/** The Void Anchor tether shows while its enemy is this close to the Core (world units). */
private const val ANCHOR_TETHER_REACH = 520f

/** The next Dash blinks to the anchor while the pointer is within this angle of it (radians). */
private val ANCHOR_BLINK_ANGLE = 30f * (PI / 180.0).toFloat()

/** The armed anchor's ring lies this far outside the enemy, one pip per [ANCHOR_PIP_SPACING] units of it. */
private const val ANCHOR_RING_GAP = 8f
private const val ANCHOR_PIP_SPACING = 18f
private const val MIN_ANCHOR_PIPS = 8
internal const val MAX_ANCHOR_PIPS = 16

/** Void Step's rift radius (units) where it swaps to its middle and its full cell. */
private const val RIFT_MIDDLE = 26f
private const val RIFT_FULL = 40f

/** An on-Core marker breaks up over the last fifth of its run and is nearly gone over the last tenth. */
private const val MARKER_ENDING = 0.2f
private const val MARKER_ENDED = 0.1f

/**
 * Mass Driver's band: pips on the circle this far outside the Core (the old band's radius), each looping eight frames a
 * pip behind its clockwise neighbour, so four swellings (frame 0 their crest) roll round the band; then the band's two
 * running-out frames. Its heading plates stand this far out and loop four frames, then crack for the run's last tenth.
 */
private const val MASS_DRIVER_BAND = 6f
private const val MASS_DRIVER_PIPS = 32
private const val MASS_DRIVER_LOOP = 8
private const val MASS_DRIVER_PLATE = 14f
private const val PLATE_LOOP = 4

/** Show Void Step's rift, Mass Driver's heavy band, and the Slingshot window around the Core. */
internal fun DrawScope.drawSkillMarkers(engine: GameplayRenderModel, shakeX: Float, shakeY: Float, roles: KkRolePalette) {
    val slots = engine.skillBar.slots
    val scale = engine.worldScale
    val elapsed = engine.renderElapsed
    for (index in slots.indices) {
        val slot = slots[index]
        val skill = slot.skillId ?: continue
        when (skill) {
            SkillId.VOID_STEP -> if (slot.pending01 > 0f) {
                val anchor = world(engine, slot.anchorX, slot.anchorY, shakeX, shakeY)
                val radius = 90f * (0.2f + 0.4f * (1f - slot.pending01.coerceIn(0f, 1f)))
                val strip = if (radius < RIFT_MIDDLE) PixelStrip.MARKER_RIFT_S else if (radius < RIFT_FULL) PixelStrip.MARKER_RIFT_M else PixelStrip.MARKER_RIFT_L
                FxDrawBudget.take(FxPool.FIELDS, 1, signature = true)
                drawPixelFrame(strip, PixelFxClock.loopFrame(strip, elapsed), anchor.x, anchor.y, scale, PixelPalettes.of(roles.element(StatusElement.VOID.ordinal)))
            }
            SkillId.MASS_DRIVER -> if (slot.active01 > 0f) drawMassDriver(engine, slot.active01, shakeX, shakeY, roles)
            SkillId.SLINGSHOT -> if (slot.active01 > 0f) {
                val core = world(engine, engine.renderCoreX, engine.renderCoreY, shakeX, shakeY)
                val strip = PixelStrip.MARKER_SLINGSHOT
                FxDrawBudget.take(FxPool.CORE_STATES, 1, signature = true)
                drawPixelFrame(strip, PixelFxClock.loopFrame(strip, elapsed), core.x, core.y, scale, PixelPalettes.of(roles.kinetic))
            }
            else -> Unit
        }
    }
}

/** An on-Core marker's frame: [looping] while plenty of its run is [left], then its two running-out frames after [loop]. */
private fun markerFrame(left: Float, looping: Int, loop: Int): Int = when {
    left > MARKER_ENDING -> looping
    left > MARKER_ENDED -> loop
    else -> loop + 1
}

/**
 * Mass Driver: the heavy band as pips 6 units outside the Core with four swellings rolling round it, breaking into links
 * that fall away as the run ends, and four plates 14 units out on its heading until the run's end.
 */
private fun DrawScope.drawMassDriver(engine: GameplayRenderModel, left: Float, shakeX: Float, shakeY: Float, roles: KkRolePalette) {
    val scale = engine.worldScale
    val elapsed = engine.renderElapsed
    val core = world(engine, engine.renderCoreX, engine.renderCoreY, shakeX, shakeY)
    val gravitic = PixelPalettes.of(roles.element(StatusElement.GRAVITIC.ordinal))
    val band = PixelStrip.MARKER_MASSDRIVER
    val ended = left <= MARKER_ENDED
    val reach = engine.coreRadius + MASS_DRIVER_BAND
    FxDrawBudget.take(FxPool.CORE_STATES, (if (ended) MASS_DRIVER_PIPS / 2 else MASS_DRIVER_PIPS) + 4, signature = true)
    val tick = PixelFxClock.tick(elapsed, band.fps)
    // Crests go last, so their ink parts them from the band either side; at the very end every other link has fallen.
    for (crests in 0..1) for (pip in 0 until MASS_DRIVER_PIPS) {
        if (ended && pip % 2 == 1) continue
        val frame = markerFrame(left, (pip - tick).mod(MASS_DRIVER_LOOP), MASS_DRIVER_LOOP)
        if ((frame == 0) != (crests == 1)) continue
        val angle = -QUARTER + pip * TAU / MASS_DRIVER_PIPS
        drawPixelFrame(band, frame, core.x + cos(angle) * reach, core.y + sin(angle) * reach, scale, gravitic)
    }
    val heading = if (engine.speed > 1f) atan2(engine.velocityY, engine.velocityX) else 0f
    val out = engine.coreRadius + MASS_DRIVER_PLATE
    val plate = PixelStrip.MARKER_MASSDRIVER_PLATE
    for (index in 0 until 4) {
        val angle = heading + index * QUARTER
        val frame = if (ended) PLATE_LOOP else PixelFxClock.loopFrame(plate, elapsed, 0, PLATE_LOOP, index)
        drawPixelAimed(plate, PixelStrip.MARKER_MASSDRIVER_PLATE_D, frame, core.x + cos(angle) * out, core.y + sin(angle) * out, angle, scale, gravitic)
    }
}

/**
 * [count] pips of [strip] on the circle of [radius] round ([x], [y]), clockwise from the top turned by [turn]
 * (radians), each looping frames [first] until [first] + [loop] a frame behind its clockwise neighbour (a chase).
 */
private fun DrawScope.drawPipCircle(strip: PixelStrip, count: Int, x: Float, y: Float, radius: Float, turn: Float, scale: Float, palette: ColorFilter,
    first: Int, loop: Int, time: Float) {
    for (pip in 0 until count) {
        val angle = -QUARTER + turn + pip * TAU / count
        drawPixelFrame(strip, PixelFxClock.loopFrame(strip, time, first, first + loop, -pip), x + cos(angle) * radius, y + sin(angle) * radius, scale,
            palette)
    }
}

/** Tiles of [strip] a link of [length] units lays at [scale] (a texel is a whole number of device pixels). */
private fun linkTiles(strip: PixelStrip, length: Float, scale: Float): Int =
    max(1, ceil(length * scale / (strip.w * PixelSheet.step(scale))).toInt())

/**
 * Persistent links from the render model: the Void Anchor tether from the Core to each anchored enemy within 520, as
 * faint dashes marching to the enemy; while the pointer lies within 30° of it (the next Dash blinks there) a taut bright
 * line and a ring of links chasing round the enemy. Over the link pool a tether is one cell stretched from end to end;
 * the armed ring always draws.
 */
internal fun DrawScope.drawPersistentLinks(engine: GameplayRenderModel, shakeX: Float, shakeY: Float, roles: KkRolePalette) {
    val enemies = engine.enemies
    var any = false
    for (index in enemies.indices) if (enemies[index].controlBits and EnemyControlBits.VOID_ANCHOR != 0) {
        any = true
        break
    }
    if (!any) return
    val scale = engine.worldScale
    val elapsed = engine.renderElapsed
    val coreX = engine.renderCoreX
    val coreY = engine.renderCoreY
    val core = world(engine, coreX, coreY, shakeX, shakeY)
    // The pointer in world units (it is screen dp around the camera).
    val pointerX = engine.renderCameraX + engine.pointerX - engine.screenWidth * 0.5f
    val pointerY = engine.renderCameraY + engine.pointerY - engine.screenHeight * 0.5f
    val aim = atan2(pointerY - coreY, pointerX - coreX)
    val void = PixelPalettes.of(roles.element(StatusElement.VOID.ordinal))
    for (index in enemies.indices) {
        val enemy = enemies[index]
        if (enemy.controlBits and EnemyControlBits.VOID_ANCHOR == 0 || enemy.dead) continue
        val x = engine.interpolated(enemy.previousX, enemy.x)
        val y = engine.interpolated(enemy.previousY, enemy.y)
        val dx = x - coreX
        val dy = y - coreY
        if (dx * dx + dy * dy > ANCHOR_TETHER_REACH * ANCHOR_TETHER_REACH) continue
        val target = world(engine, x, y, shakeX, shakeY)
        var turn = atan2(dy, dx) - aim
        while (turn > PI) turn -= TAU
        while (turn < -PI) turn += TAU
        val armed = abs(turn) <= ANCHOR_BLINK_ANGLE
        val strip = if (armed) PixelStrip.LINK_ANCHOR_ARMED else PixelStrip.LINK_ANCHOR_IDLE
        val tiles = linkTiles(strip, sqrt(dx * dx + dy * dy), scale)
        val stretched = !FxDrawBudget.take(FxPool.LINKS, tiles, signature = false)
        if (stretched) FxDrawBudget.take(FxPool.LINKS, 1, signature = true)
        drawPixelLink(strip, PixelFxClock.loopFrame(strip, elapsed), core.x, core.y, target.x, target.y, scale, void, stretched = stretched)
        if (armed) {
            val ring = enemy.radius + ANCHOR_RING_GAP
            val pips = (ring * TAU / ANCHOR_PIP_SPACING).roundToInt().coerceIn(MIN_ANCHOR_PIPS, MAX_ANCHOR_PIPS)
            FxDrawBudget.take(FxPool.LINKS, pips, signature = true)
            drawPipCircle(PixelStrip.MARKER_ANCHOR_PIP, pips, target.x, target.y, ring, 0f, scale, void, 0, PixelStrip.MARKER_ANCHOR_PIP.frames, elapsed)
        }
    }
}
