// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.nucleus.simulation.combat

import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.gameplay.nucleus.model.*
import kotlin.math.max
import kotlin.math.min
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.weapons.*

internal fun MutableGameState.resolveWeaponDamage(delta: Float) {
    val power = effectiveWeaponPower()
    when (weapon) {
        WeaponId.FLUX_WAKE -> resolveFluxWake(delta, power)
        WeaponId.MORNINGSTAR -> resolveMorningstar(power)
        WeaponId.PHASE_LATTICE -> resolvePhaseLattice(delta, power)
        WeaponId.NULL_LANCE, WeaponId.GRAVITY_MINES, WeaponId.ARC_COIL,
        WeaponId.GYRE_DISC, WeaponId.SCATTER_BLOOM -> Unit
    }
    resolveWeaponEntityHits(delta)
}

/**
 * Flux Wake: [FLUX_DPS] per enemy touching a live trail point (no speed term: it scales with speed), Scorch every
 * [FLUX_SCORCH_INTERVAL]. The trail's heat spreads thin over a crowd: with more than [FLUX_FULL_HEAT_ENEMIES]
 * enemies in it at once, they share that many enemies' worth. Nothing in the pass writes the trail, so the box around
 * its burning points (live, with a positive scale), measured once, holds for the whole pass: an enemy outside it by
 * more than its radius, the width and [FLUX_BOUNDS_MARGIN] touches no point and skips the per-point test.
 */
private fun MutableGameState.resolveFluxWake(delta: Float, power: Float) {
    val width = FLUX_WIDTH * (if (hasMasteryForm(1)) FLUX_WIDTH_FORM else 1f)
    val earlyIgnite = hasMasteryForm(3)
    var minX = Float.POSITIVE_INFINITY
    var minY = Float.POSITIVE_INFINITY
    var maxX = Float.NEGATIVE_INFINITY
    var maxY = Float.NEGATIVE_INFINITY
    var burningPoints = false
    for (trailIndex in trail.indices) {
        val point = trail[trailIndex]
        if (!(point.age < point.life && point.scale > 0f)) continue
        burningPoints = true
        minX = min(minX, point.x)
        minY = min(minY, point.y)
        maxX = max(maxX, point.x)
        maxY = max(maxY, point.y)
    }
    // No point burns: both passes would find every enemy's scale 0.
    if (!burningPoints) return
    var burning = 0
    for (enemyIndex in enemies.indices) {
        val enemy = enemies[enemyIndex]
        if (enemy.alive && fluxTrailScale(enemy, width, minX, minY, maxX, maxY) > 0f) burning++
    }
    val spread = if (burning > FLUX_FULL_HEAT_ENEMIES) FLUX_FULL_HEAT_ENEMIES.toFloat() / burning else 1f
    for (enemyIndex in enemies.indices) {
        val enemy = enemies[enemyIndex]
        if (!enemy.alive) continue
        val scale = fluxTrailScale(enemy, width, minX, minY, maxX, maxY)
        if (scale > 0f) {
            val landed = dealWeaponDamage(enemy, FLUX_DPS * delta * power * scale * spread,
                cadence = WeaponHitCadence.CONTINUOUS).amount > 0f
            if (landed) weaponElementTick(enemy, StatusElement.THERMAL, FLUX_SCORCH_INTERVAL, extraBeforeBreak = earlyIgnite)
        }
    }
}

/**
 * The strongest live trail point [enemy] touches (a turning trail's is larger), or 0. The box ([minX], [minY]) to
 * ([maxX], [maxY]) holds every burning point; an enemy beyond it touches none (a NaN bound or position rejects
 * nothing, so it takes the per-point test).
 */
private fun MutableGameState.fluxTrailScale(enemy: Enemy, width: Float, minX: Float, minY: Float, maxX: Float, maxY: Float): Float {
    val reach = enemy.radius + width + FLUX_BOUNDS_MARGIN
    if (enemy.x < minX - reach || enemy.x > maxX + reach || enemy.y < minY - reach || enemy.y > maxY + reach) return 0f
    var scale = 0f
    for (trailIndex in trail.indices) {
        val point = trail[trailIndex]
        if (point.age < point.life && point.scale > scale &&
            distanceSquared(point.x, point.y, enemy.x, enemy.y) < square(enemy.radius + width)
        ) {
            scale = point.scale
        }
    }
    return scale
}

/** Slack of the Flux trail's box beyond the touch distance, far above any Float rounding of the per-point test. */
private const val FLUX_BOUNDS_MARGIN: Float = 1f

/** Morningstar contacts gain damage with velocity; mastery adds reach, a second star and stronger throws. */
private fun MutableGameState.resolveMorningstar(power: Float) {
    val sizeScale = morningstarScale()
    val hitReach = MORNINGSTAR_REACH * sizeScale
    val damage = (MORNINGSTAR_DAMAGE + softVelocity(speed) * MORNINGSTAR_SPEED_SHARE) * power * sizeScale
    val secondStar = hasMasteryForm(2)
    val secondX = coreX - (morningstarX - coreX) * MORNINGSTAR_SECOND_ORBIT
    val secondY = coreY - (morningstarY - coreY) * MORNINGSTAR_SECOND_ORBIT
    val knockback = MORNINGSTAR_KNOCKBACK * if (hasMasteryForm(3)) 2f else 1f
    for (enemyIndex in enemies.indices) {
        val enemy = enemies[enemyIndex]
        if (!enemy.alive || enemy.weaponCooldown > 0f) continue
        val reach = enemy.radius + hitReach
        val primaryHit = distanceSquared(morningstarX, morningstarY, enemy.x, enemy.y) < reach * reach
        val secondReach = enemy.radius + hitReach * MORNINGSTAR_SECOND_SIZE
        val secondHit = secondStar && distanceSquared(secondX, secondY, enemy.x, enemy.y) < secondReach * secondReach
        if (!primaryHit && !secondHit) continue
        val scale = if (primaryHit) 1f else MORNINGSTAR_SECOND_SIZE
        val sourceX = if (primaryHit) morningstarX else secondX
        val sourceY = if (primaryHit) morningstarY else secondY
        dealWeaponDamage(enemy, damage * scale, canCrit = true, cadence = WeaponHitCadence.CONTACT,
            procKey = WeaponRuntime.sourceKey(if (primaryHit) 0 else 1))
        pushEnemy(enemy, sourceX, sourceY, knockback * scale)
        enemy.weaponCooldown = 0.2f
        screenShake = max(screenShake, 5f)
    }
}

/** Phase Lattice: a ring 26/s with Chill every 0.6 s; mastery widens the ring and adds an outer ring. */
private fun MutableGameState.resolvePhaseLattice(delta: Float, power: Float) {
    val center = PHASE_LATTICE_CENTER
    val half = PHASE_LATTICE_HALF_WIDTH * if (hasMasteryForm(1)) 1.35f else 1f
    val inner = square(max(0f, center - half))
    val outer = square(center + half)
    val earlyFreeze = hasMasteryForm(3)
    val outerRing = hasMasteryForm(2)
    for (enemyIndex in enemies.indices) {
        val enemy = enemies[enemyIndex]
        if (!enemy.alive) continue
        val ring = distanceSquared(coreX, coreY, enemy.x, enemy.y)
        if (ring in inner..outer) {
            val landed = dealWeaponDamage(enemy, PHASE_LATTICE_DPS * delta * power, cadence = WeaponHitCadence.CONTINUOUS).amount > 0f
            if (landed) weaponElementTick(enemy, StatusElement.CRYO, PHASE_LATTICE_CHILL_INTERVAL, extraBeforeBreak = earlyFreeze)
        } else if (outerRing && ring in square(center + PHASE_OUTER_OFFSET - 16f)..square(center + PHASE_OUTER_OFFSET + 16f)) {
            dealWeaponDamage(enemy, PHASE_OUTER_DPS * delta * power, cadence = WeaponHitCadence.CONTINUOUS, procKey = WeaponRuntime.sourceKey(1))
        }
    }
}
