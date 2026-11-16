// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.interaction.canvas.world

import kinetickk.foundation.design.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.nucleus.render.PickupType
import kinetickk.ball.gameplay.nucleus.render.ProjectileKind
import kinetickk.ball.gameplay.interaction.fx.InteractionFxLimits
import kinetickk.ball.gameplay.interaction.fx.PixelFxClock
import kinetickk.ball.gameplay.interaction.fx.PixelStrip
import kinetickk.ball.gameplay.interaction.fx.VisualFxProjection
import kinetickk.ball.gameplay.interaction.canvas.fx.PixelPalettes
import kinetickk.ball.gameplay.interaction.canvas.fx.PixelSheet
import kinetickk.ball.gameplay.interaction.canvas.fx.drawPixelFrame
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kinetickk.ball.gameplay.interaction.canvas.*
import kinetickk.ball.gameplay.interaction.canvas.look.*
import kinetickk.ball.gameplay.interaction.canvas.world.enemies.*

internal fun DrawScope.drawProjectiles(engine: GameplayRenderModel, shakeX: Float, shakeY: Float, roles: KkRolePalette) {
    for (index in engine.projectiles.indices) {
        val projectile = engine.projectiles[index]
        // Drawn between its last two simulated positions, with a one-step streak behind it.
        val x = engine.interpolated(projectile.previousX, projectile.x)
        val y = engine.interpolated(projectile.previousY, projectile.y)
        val center = world(engine, x, y, shakeX, shakeY)
        if (!isOnScreen(center, projectile.radius + 28f)) continue
        val previous = world(engine, x - (projectile.x - projectile.previousX), y - (projectile.y - projectile.previousY), shakeX, shakeY)
        if (projectile.hostile && projectile.kind == ProjectileKind.MINE) {
            drawMine(engine, center, projectile.radius, projectile.life, roles)
        } else if (projectile.hostile) {
            val threat = roles.threat
            drawLine(threat.copy(alpha = 0.22f), previous, center, projectile.radius * 1.1f)
            drawCircle(threat.copy(alpha = 0.1f), projectile.radius + 6f, center)
            drawCircle(threat.copy(alpha = 0.22f), projectile.radius + 2.5f, center)
            drawCircle(threat, projectile.radius, center)
            if (roles.hatchThreats) {
                translate(center.x, center.y) {
                    drawKkThreatHatch(WorldPaths.silhouette(EnemySilhouette.OCTAGON, projectile.radius), roles, Kk.Ink)
                }
            }
        } else {
            // Apex shots (colour index 15) fly gold; every other shot of the player in its colour.
            val accent = if (projectile.colorIndex == APEX_GOLD_COLOR) roles.gold.base else roles.you
            drawLine(accent.copy(alpha = 0.3f), previous, center, max(1.5f, projectile.radius * 1.2f))
            drawCircle(accent.copy(alpha = 0.18f), projectile.radius + 4f, center)
            drawCircle(if (projectile.colorIndex == APEX_GOLD_COLOR) roles.gold.light else Kk.Bone, max(2f, projectile.radius * 0.8f), center)
        }
    }
}

/** A mine: a threat diamond with a blinking core; once armed the telegraphs' live reach cells mark its trigger reach. */
private fun DrawScope.drawMine(engine: GameplayRenderModel, center: Offset, radius: Float, life: Float, roles: KkRolePalette) {
    val threat = roles.threat
    val armed = life <= MINE_ARMED_LIFE
    rotate(45f, center) {
        drawRect(Kk.Ink1, Offset(center.x - radius, center.y - radius), Size(radius * 2f, radius * 2f))
        drawRect(threat, Offset(center.x - radius, center.y - radius), Size(radius * 2f, radius * 2f), style = kkStroke(KkStrokeWidth.Icon))
    }
    val blink = if (armed) (if (cos(engine.renderElapsed * 9f) > 0f) 1f else 0.3f) else 0.25f
    drawCircle(threat.copy(alpha = blink), radius * 0.4f, center)
    if (armed) drawReach(engine, center, MINE_TRIGGER_REACH, reachLive(engine), PixelPalettes.of(threat))
}

internal fun DrawScope.drawPickups(engine: GameplayRenderModel, shakeX: Float, shakeY: Float, roles: KkRolePalette) {
    for (index in engine.pickups.indices) {
        val pickup = engine.pickups[index]
        val x = engine.interpolated(pickup.previousX, pickup.x)
        val y = engine.interpolated(pickup.previousY, pickup.y)
        val center = world(engine, x, y, shakeX, shakeY)
        if (!isOnScreen(center, 28f)) continue
        val previous = world(engine, x - (pickup.x - pickup.previousX), y - (pickup.y - pickup.previousY), shakeX, shakeY)
        when (pickup.type) {
            PickupType.DATA -> {
                drawLine(Kk.Bone.copy(alpha = 0.22f), previous, center, 2f)
                // Bone diamond; sizes vary a little per pickup like the boards.
                val half = 3.2f + (index % 3) * 0.6f
                rotate(45f, center) { drawRect(Kk.Bone, Offset(center.x - half, center.y - half), Size(half * 2f, half * 2f)) }
            }
            PickupType.KEY -> {
                drawLine(roles.you.copy(alpha = 0.25f), previous, center, 2f)
                drawPickupPlate(center, roles.you)
                drawKkIcon(KkIcon.SYSTEM_KEY, center, 15f, roles.you)
            }
            PickupType.REPAIR -> {
                drawLine(Kk.Bone.copy(alpha = 0.22f), previous, center, 2f)
                drawPickupPlate(center, Kk.Bone)
                drawKkIcon(KkIcon.SYSTEM_INTEGRITY, center, 14f, Kk.Bone)
                drawRect(Kk.Bone, Offset(center.x - 3f, center.y - 0.75f), Size(6f, 1.5f))
                drawRect(Kk.Bone, Offset(center.x - 0.75f, center.y - 3f), Size(1.5f, 6f))
            }
            PickupType.RELIC -> {
                drawLine(roles.gold.light.copy(alpha = 0.3f), previous, center, 2.5f)
                drawUnresolvedRelicIcon(center, 11f, engine.renderElapsed, roles.gold.light)
            }
        }
    }
}

/** A small ink-2 sheared plate with an outline, the chip look for world pickups. */
private fun DrawScope.drawPickupPlate(center: Offset, outline: androidx.compose.ui.graphics.Color) {
    val plate = WorldPaths.shearedPlate(28f)
    translate(center.x, center.y) {
        drawPath(plate, Kk.Ink2)
        drawPath(plate, outline.copy(alpha = 0.7f), style = kkStroke(KkStrokeWidth.Line))
    }
}

/**
 * Particles as pixel sprites: the first shards of a burst are tumbling chips, the rest sparks streaked along their
 * travel. A particle changes frame and place only on its strip's ticks (it is drawn back along its velocity to the
 * start of its tick), so sparks fly in steps of their own frame rate at any display rate.
 */
internal fun DrawScope.drawParticles(
    engine: GameplayRenderModel,
    visualFx: VisualFxProjection,
    shakeX: Float,
    shakeY: Float,
    roles: KkRolePalette,
) {
    val scale = engine.worldScale
    val step = PixelSheet.step(scale)
    for (index in visualFx.particles.indices) {
        val particle = visualFx.particles[index]
        val shard = particle.size >= InteractionFxLimits.SHARD_MIN_SIZE
        val across = abs(particle.vx) > 2f * abs(particle.vy)
        val along = abs(particle.vy) > 2f * abs(particle.vx)
        val strip = if (shard) PixelStrip.CHIP else if (across) PixelStrip.SPARK_H else if (along) PixelStrip.SPARK_V else PixelStrip.SPARK_D
        val age = PixelFxClock.stepped(engine.fxAge(particle.life, particle.maxLife), strip.fps)
        // Time from the tick being shown to the particle's last simulated step, and how far it flew in that time:
        // its velocity decays, so it was faster then (the same curve the FX reducer advances it along).
        val behind = particle.maxLife - particle.life - age
        val back = if (behind <= 0f) 0f else (exp(InteractionFxLimits.PARTICLE_DAMPING * behind) - 1f) / InteractionFxLimits.PARTICLE_DAMPING
        val center = world(engine, particle.x - particle.vx * back, particle.y - particle.vy * back, shakeX, shakeY)
        if (!isOnScreen(center, 24f)) continue
        val spent = (age / particle.maxLife).coerceIn(0f, 1f)
        // A chip's phase comes from its own random life, so it never changes when other particles leave the list.
        val frame = if (shard) chipFrame(age, particle.maxLife, particle.maxLife.toRawBits() ushr 7)
        else (spent * strip.frames).toInt().coerceAtMost(strip.frames - 1)
        drawPixelFrame(strip, frame, center.x, center.y, scale, PixelPalettes.of(fxColor(particle.colorIndex, roles)), step,
            flipX = particle.vx < 0f, flipY = !shard && particle.vy < 0f)
    }
}

/**
 * A chip of [maxLife] seconds at [age]: it tumbles through its six loop frames from its own [phase] and shows its two
 * dying frames on its last two ticks, so both always appear.
 */
internal fun chipFrame(age: Float, maxLife: Float, phase: Int): Int {
    val strip = PixelStrip.CHIP
    val last = ceil(maxLife * strip.fps).toInt() - 1
    val tick = PixelFxClock.tick(age, strip.fps)
    return when {
        tick >= last -> 7
        tick == last - 1 -> 6
        else -> PixelFxClock.loopFrame(strip, age, 0, 6, phase and 0xFF)
    }
}
