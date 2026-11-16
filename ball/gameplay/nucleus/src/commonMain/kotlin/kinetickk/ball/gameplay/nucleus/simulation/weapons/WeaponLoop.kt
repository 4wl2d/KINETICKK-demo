// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.nucleus.simulation.weapons

import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.gameplay.nucleus.model.*
import kinetickk.ball.gameplay.nucleus.protocol.GameplayAudioCue
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.characters.*
import kinetickk.ball.gameplay.nucleus.simulation.combat.*
import kinetickk.ball.gameplay.nucleus.simulation.elements.*
import kinetickk.ball.gameplay.nucleus.simulation.enemies.*
import kinetickk.ball.gameplay.nucleus.simulation.world.*
import kinetickk.ball.gameplay.nucleus.simulation.flight.*

/*
 * Weapon activations at base power 1. [effectiveWeaponPower] carries level,
 * mastery, run growth, Overdrive and the synergies. A discrete activation takes a proc serial
 * ([beginWeaponActivation]) that every hit it causes carries; continuous and contact damage resolves in
 * [resolveWeaponDamage], entity hits in [resolveWeaponEntityHits].
 */

internal fun MutableGameState.updateWeapons(delta: Float) {
    weaponClock -= delta
    val runtime = weaponRuntime
    val dashed = trackWeaponInputs(delta)
    for (index in runtime.entities.indices) runtime.entities[index].capturePrevious()
    val power = effectiveWeaponPower()
    when (weapon) {
        WeaponId.FLUX_WAKE -> sampleFluxTrail()
        WeaponId.MORNINGSTAR -> updateMorningstar(delta)
        WeaponId.PHASE_LATTICE -> Unit
        WeaponId.NULL_LANCE -> if (weaponClock <= 0f) fireNullLance(power)
        WeaponId.GRAVITY_MINES -> updateGravityMines()
        WeaponId.ARC_COIL -> if (weaponClock <= 0f && nearestEnemy(coreX, coreY, ARC_COIL_FIRST_RANGE) != null) {
            weaponClock = cooldown(ARC_COIL_COOLDOWN)
            fireArcCoil(ARC_COIL_DAMAGE * power, beginWeaponActivation())
            emitSound(GameplayAudioCue.WEAPON_LIGHT)
        }
        WeaponId.GYRE_DISC -> if (weaponClock <= 0f) throwGyreDiscs(power)
        WeaponId.SCATTER_BLOOM -> updateScatterBloom(delta, power, dashed)
    }

    trail.removeMatchingStable { point ->
        point.age += delta
        point.age > point.life + TRAIL_FADE_SECONDS
    }
    updateWeaponNodes(delta)
    advanceWeaponEntities(delta)
    emitWeaponArcsAdvanced(delta)
}

/**
 * The travel heading an aimed weapon fires along, nudged onto the enemy closest to it when one lies within
 * [AIM_ASSIST_CONE] of the heading and [AIM_ASSIST_RANGE] of the Core (a lance flown at an enemy finds it).
 */
internal fun MutableGameState.aimedMovementAngle(): Float {
    val heading = movementAngle()
    var best = heading
    var bestOffset = AIM_ASSIST_CONE
    for (index in enemies.indices) {
        val enemy = enemies[index]
        if (!enemy.alive || isShifting(enemy)) continue
        val dx = enemy.x - coreX
        val dy = enemy.y - coreY
        if (dx * dx + dy * dy > AIM_ASSIST_RANGE * AIM_ASSIST_RANGE) continue
        val bearing = atan2(dy, dx)
        val offset = abs(atan2(sin(bearing - heading), cos(bearing - heading)))
        if (offset < bestOffset) {
            bestOffset = offset
            best = bearing
        }
    }
    return best
}

/** Allocates the proc key shared by all hits from one activation. */
internal fun MutableGameState.beginWeaponActivation(): Int = weaponRuntime.beginActivation()

/**
 * Tracks Brake speed loss and returns whether the player dashed this step.
 */
private fun MutableGameState.trackWeaponInputs(delta: Float): Boolean {
    val runtime = weaponRuntime
    val sequence = characterRuntime.dashSequence
    val dashed = sequence != runtime.seenDashSequence
    if (dashed) {
        runtime.seenDashSequence = sequence
        runtime.lastDashAt = elapsed
    }
    val currentSpeed = speed
    runtime.recordShed(if (braking) max(0f, runtime.previousSpeed - currentSpeed) else 0f, delta)
    runtime.previousSpeed = currentSpeed
    return dashed
}

/** Applies the weapon's own element on a per-enemy interval (continuous weapons). */
internal fun MutableGameState.weaponElementTick(enemy: Enemy, element: StatusElement, interval: Float, extraBeforeBreak: Boolean = false) {
    if (!enemy.alive || enemy.hitQualificationCooldown > 0f) return
    // A faster weapon applies its element faster too.
    enemy.hitQualificationCooldown = interval / continuousWeaponRate()
    var count = 1
    // A form that breaks one stack sooner adds the last stack early.
    if (extraBeforeBreak && stacks(enemy, element) + 2 >= breakThreshold(element, enemy)) count = 2
    applyStatus(enemy, element, count, 0)
}

internal fun MutableGameState.applyWeaponStatus(enemy: Enemy, element: StatusElement, count: Int) {
    if (enemy.alive) applyStatus(enemy, element, count, 0)
}

/**
 * How far a Blink moved the Core this step, or 0. The Morningstar shifts its previous position by
 * that distance so it jumps with the Core across the gap.
 */
internal fun MutableGameState.blinkShiftX(): Float = if (blinkThisStep) blinkToX - blinkFromX else 0f
internal fun MutableGameState.blinkShiftY(): Float = if (blinkThisStep) blinkToY - blinkFromY else 0f

/** Knocks [enemy] away from (x, y); a thrown enemy can slam into obstacles and other enemies. */
internal fun MutableGameState.pushEnemy(enemy: Enemy, x: Float, y: Float, strength: Float) {
    val knockback = knockbackTaken(enemy)
    if (knockback <= 0f || !enemy.alive) return
    val dx = enemy.x - x
    val dy = enemy.y - y
    val distance = max(1f, length(dx, dy))
    enemy.vx += dx / distance * strength * knockback
    enemy.vy += dy / distance * strength * knockback
    markThrown(enemy)
}

/** At capacity, evicts a spent entity or the oldest live one. */
internal fun MutableGameState.addWeaponEntity(entity: WeaponEntity) {
    val entities = weaponRuntime.entities
    if (entities.size >= WeaponRuntime.MAX_WEAPON_ENTITIES) {
        val spent = entities.indexOfFirst { it.life <= 0f }
        entities.removeAt(if (spent >= 0) spent else 0)
    }
    entities += entity
}
