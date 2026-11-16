// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.nucleus.simulation.weapons

import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.gameplay.nucleus.render.*
import kinetickk.ball.gameplay.nucleus.model.*
import kinetickk.ball.gameplay.nucleus.protocol.GameplayAudioCue
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.combat.*
import kinetickk.ball.gameplay.nucleus.simulation.elements.knockbackTaken
import kinetickk.ball.gameplay.nucleus.simulation.enemies.*

internal fun MutableGameState.sampleFluxTrail() {
    val runtime = weaponRuntime
    val heading = atan2(velocityY, velocityX)
    val turn = abs(atan2(sin(heading - runtime.previousHeading), cos(heading - runtime.previousHeading)))
    runtime.previousHeading = heading
    if (speed <= 45f) {
        trailLastX = coreX
        trailLastY = coreY
        return
    }
    val dx = coreX - trailLastX
    val dy = coreY - trailLastY
    val distance = length(dx, dy)
    if (distance <= 0f) return
    // Orb signature: a trail laid while turning (a hard turn: 60° per 100 dp or tighter) burns harder and longer.
    val moved = length(coreX - previousCoreX, coreY - previousCoreY)
    val turning = signatureActive() && moved > 0f && turn / moved >= FLUX_TURN_CURVATURE
    val life = (if (hasMasteryForm(2)) FLUX_LONG_LIFE else TRAIL_LIFE_SECONDS) * (if (turning) 1.5f else 1f)
    val scale = if (turning) 1.5f else 1f
    // Samples spread so the bounded trail holds the longest live point's whole life and fade at this speed:
    // at most MAX_TRAIL_POINTS samples in that time, so the oldest burning point is never evicted early.
    var longestLife = life
    for (index in trail.indices) longestLife = max(longestLife, trail[index].life)
    val spacing = max(FLUX_SPACING, speed * (longestLife + TRAIL_FADE_SECONDS) / MutableGameState.MAX_TRAIL_POINTS)
    var sampleDistance = spacing - trailDistanceCarry
    var samples = 0
    while (sampleDistance <= distance && samples < MutableGameState.MAX_TRAIL_SAMPLES_PER_UPDATE) {
        val amount = sampleDistance / distance
        trail += TrailPoint(lerp(trailLastX, coreX, amount), lerp(trailLastY, coreY, amount), life = life, scale = scale)
        sampleDistance += spacing
        samples++
    }
    trailDistanceCarry = (trailDistanceCarry + distance) % spacing
    while (trail.size > MutableGameState.MAX_TRAIL_POINTS) trail.removeAt(0)
    trailLastX = coreX
    trailLastY = coreY

}

internal fun MutableGameState.updateGravityMines() {
    if (weaponClock <= 0f && (braking || speed > 220f)) {
        // The second form keeps more mines in the field: they come sooner.
        weaponClock = cooldown((if (braking) 0.72f else 1.15f) * if (hasMasteryForm(2)) 0.85f else 1f)
        val serial = beginWeaponActivation()
        val mineLife = 0.75f
        if (tryAddWeaponNode(WeaponNode(WeaponNodeType.GRAVITY_MINE, coreX, coreY, mineLife, mineLife, gravityMineRadius(),
                procKey = serial))
        ) {
            emitSound(GameplayAudioCue.WEAPON_LIGHT)
        }
    }
}

private fun MutableGameState.gravityMineRadius(): Float =
    GRAVITY_MINE_RADIUS * (if (hasMasteryForm(1)) 1.3f else 1f)

internal fun MutableGameState.tryAddWeaponNode(node: WeaponNode): Boolean {
    val limit = if (hasMasteryForm(2)) MutableGameState.MAX_WEAPON_NODES else BASE_MINE_LIMIT
    if (weaponNodes.size >= limit) return false
    weaponNodes += node
    return true
}

internal fun MutableGameState.updateWeaponNodes(delta: Float) {
    var index = 0
    while (index < weaponNodes.size) {
        val node = weaponNodes[index]
        node.life -= delta
        // An enemy stepping onto a mine sets it off early.
        if (node.life > 0f && enemyWithin(node.x, node.y, node.radius * MINE_PROXIMITY)) node.life = 0f
        if (node.life <= 0f) {
            weaponNodes.removeAt(index)
            explodeMine(node)
        } else {
            index++
        }
    }
}

private fun MutableGameState.enemyWithin(x: Float, y: Float, radius: Float): Boolean {
    for (index in enemies.indices) {
        val enemy = enemies[index]
        if (enemy.alive && !isShifting(enemy) && distanceSquared(x, y, enemy.x, enemy.y) <= square(radius + enemy.radius)) return true
    }
    return false
}

internal fun MutableGameState.explodeMine(node: WeaponNode) {
    val power = effectiveWeaponPower()
    for (index in enemies.indices) {
        val enemy = enemies[index]
        val distance = length(enemy.x - node.x, enemy.y - node.y)
        if (distance <= node.radius + enemy.radius) {
            val landed = dealWeaponDamage(enemy, MINE_DAMAGE * power * (1f - distance / (node.radius * 1.7f)).coerceAtLeast(0.35f),
                canCrit = true, procKey = node.procKey).amount > 0f
            if (landed) weaponElementTick(enemy, StatusElement.GRAVITIC, MINE_MASS_INTERVAL)
            val pull = (1f - distance / max(1f, node.radius)).coerceAtLeast(0f) * 170f * knockbackTaken(enemy)
            enemy.vx += (node.x - enemy.x) / max(1f, distance) * pull
            enemy.vy += (node.y - enemy.y) / max(1f, distance) * pull
        }
    }
    // Second form: a blast sets off nearby mines.
    if (hasMasteryForm(2)) {
        for (index in weaponNodes.indices) {
            val other = weaponNodes[index]
            if (distanceSquared(other.x, other.y, node.x, node.y) <= square(node.radius * MINE_CHAIN_REACH)) other.life = min(other.life, MINE_CHAIN_FUSE)
        }
    }
    // Third form: the mine collapses into a short well, which grows with weapon power like the blast.
    if (hasMasteryForm(3)) {
        addWeaponEntity(WeaponEntity(WeaponEntityKind.WELL, node.x, node.y, node.radius * MINE_WELL_REACH, MINE_WELL_SECONDS, MINE_WELL_SECONDS,
            damage = MINE_WELL_DPS * power, procKey = node.procKey, phase = 1))
    }
    burst(node.x, node.y, 18, 2)
    screenShake = max(screenShake, 5f)
    emitSound(GameplayAudioCue.IMPACT)
}
