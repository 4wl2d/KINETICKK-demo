// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.nucleus.simulation.weapons

import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.gameplay.nucleus.render.*
import kinetickk.ball.gameplay.nucleus.model.*
import kinetickk.ball.gameplay.nucleus.protocol.GameplayAudioCue
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.combat.*
import kinetickk.ball.gameplay.nucleus.simulation.elements.*
import kinetickk.ball.gameplay.nucleus.simulation.enemies.*
import kinetickk.ball.gameplay.nucleus.simulation.flight.*

internal fun MutableGameState.fireNullLance(power: Float) {
    weaponClock = cooldown(1.15f)
    val serial = beginWeaponActivation()
    val angle = aimedMovementAngle()
    val lanceSpeed = 940f + softVelocity(speed) * 0.22f
    val pierce = NULL_LANCE_PIERCE + if (hasMasteryForm(1)) 3 else 0
    firePlayerProjectile(coreX, coreY, angle, lanceSpeed, NULL_LANCE_DAMAGE * power, pierce, 6f, 0, serial)
    if (hasMasteryForm(2)) firePlayerProjectile(coreX, coreY, angle + TAU * 0.5f, lanceSpeed * 0.9f, NULL_LANCE_DAMAGE * NULL_LANCE_REAR_SHARE * power, 3, 5f, 0, serial)
    emitSound(GameplayAudioCue.WEAPON_LIGHT)
}

/**
 * Arc Coil: the nearest enemy within [ARC_COIL_FIRST_RANGE] of the Core, then the nearest unhit enemy within
 * [arcCoilHopRange] of the previous target, for [arcCoilTargetCount] targets. Each hop deals [ARC_COIL_FALLOFF]
 * of the last and adds a Charge; only the first hit fires on-hit effects.
 */
internal fun MutableGameState.fireArcCoil(baseDamage: Float, serial: Int = WeaponRuntime.PROC_ALWAYS) {
    val maximumTargets = arcCoilTargetCount()
    val targets = scratch.arcCoilTargets
    var targetCount = 0
    var fromX = coreX
    var fromY = coreY
    var range = ARC_COIL_FIRST_RANGE
    while (targetCount < maximumTargets) {
        val nearest = nearestEnemyWhere(fromX, fromY, range) { candidate ->
            candidate.dead || !(candidate.hp > 0f) || isShifting(candidate) || targets.selects(candidate, targetCount)
        }
        if (nearest == null) break
        targets[targetCount++] = nearest
        fromX = nearest.x
        fromY = nearest.y
        range = arcCoilHopRange()
    }
    fromX = coreX
    fromY = coreY
    for (index in 0 until targetCount) {
        val enemy = checkNotNull(targets[index])
        val targetX = enemy.x
        val targetY = enemy.y
        val landed = dealWeaponDamage(enemy, baseDamage * powFast(ARC_COIL_FALLOFF, index), canCrit = true, procKey = serial).amount > 0f
        if (landed) applyWeaponStatus(enemy, StatusElement.ION, ARC_COIL_CHARGE)
        // Third form: the last hop overloads.
        if (index == targetCount - 1 && hasMasteryForm(3) && enemy.alive) forceElementBreak(enemy, StatusElement.ION)
        addWeaponArc(fromX, fromY, targetX, targetY)
        fromX = targetX
        fromY = targetY
    }
    targets.fill(null, 0, targetCount)
}

/** Whether [candidate] is among the first [count] of these targets. */
private fun Array<Enemy?>.selects(candidate: Enemy, count: Int): Boolean {
    for (index in 0 until count) if (this[index] === candidate) return true
    return false
}

/** Hop range of Arc Coil's chain; its second form adds 100. */
internal fun MutableGameState.arcCoilHopRange(): Float = ARC_COIL_HOP_RANGE + if (hasMasteryForm(2)) 100f else 0f

internal fun MutableGameState.throwGyreDiscs(power: Float) {
    weaponClock = cooldown(GYRE_COOLDOWN)
    val serial = beginWeaponActivation()
    val angle = atan2(lastAimDirectionY, lastAimDirectionX)
    val count = if (hasMasteryForm(2)) 2 else 1
    val damage = GYRE_DAMAGE * power
    val radius = GYRE_RADIUS * if (hasMasteryForm(1)) GYRE_RADIUS_FORM else 1f
    for (disc in 0 until count) {
        val discAngle = angle + (disc - (count - 1) * 0.5f) * GYRE_SPREAD
        // The second form's twin carries 60 % of a disc's damage.
        val share = if (disc == 0) 1f else GYRE_EXTRA_SHARE
        addWeaponEntity(WeaponEntity(WeaponEntityKind.DISC, coreX, coreY, radius, GYRE_MAX_LIFE, GYRE_MAX_LIFE,
            vx = cos(discAngle) * GYRE_SPEED, vy = sin(discAngle) * GYRE_SPEED, damage = damage * share, angle = discAngle,
            extent = GYRE_REACH, procKey = serial))
    }
    emitSound(GameplayAudioCue.WEAPON_LIGHT)
}

internal fun MutableGameState.updateScatterBloom(delta: Float, power: Float, dashed: Boolean) {
    val runtime = weaponRuntime
    runtime.passiveClock -= delta
    val pellets = SCATTER_PELLETS + if (hasMasteryForm(1)) 2 else 0
    val damage = SCATTER_DAMAGE * power
    if (dashed) {
        // The bloom opens toward the nearest enemy in reach, else along the Dash.
        val near = nearestEnemy(coreX, coreY, SCATTER_RANGE)
        val angle = when {
            near != null -> atan2(near.y - coreY, near.x - coreX)
            speed > 20f -> atan2(velocityY, velocityX)
            else -> atan2(lastAimDirectionY, lastAimDirectionX)
        }
        // A Dash is no cooldown: attack speed raises the bloom's damage instead.
        fireScatterCone(angle, pellets, damage * continuousWeaponRate(), beginWeaponActivation())
    }
    if (runtime.passiveClock <= 0f) {
        runtime.passiveClock = cooldown(SCATTER_PASSIVE_INTERVAL)
        val target = nearestEnemy(coreX, coreY, SCATTER_RANGE)
        val angle = if (target != null) atan2(target.y - coreY, target.x - coreX) else atan2(lastAimDirectionY, lastAimDirectionX)
        fireScatterCone(angle, SCATTER_PASSIVE_PELLETS, damage, beginWeaponActivation(), spread = SCATTER_PASSIVE_SPREAD)
    }
}

private fun MutableGameState.fireScatterCone(angle: Float, pellets: Int, damage: Float, serial: Int, spread: Float = SCATTER_CONE) {
    val pierce = SCATTER_PIERCE + if (hasMasteryForm(2)) 1 else 0
    for (pellet in 0 until pellets) {
        val offset = if (pellets <= 1) 0f else (pellet / (pellets - 1f) - 0.5f) * spread
        firePlayerProjectile(coreX, coreY, angle + offset, SCATTER_SPEED, damage, pierce, 4f, 0, serial,
            life = SCATTER_RANGE / SCATTER_SPEED)
    }
    emitSound(GameplayAudioCue.WEAPON_LIGHT)
}
