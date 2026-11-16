// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.nucleus.simulation.weapons

import kinetickk.ball.gameplay.nucleus.model.*
import kotlin.math.max
import kotlin.math.sqrt
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.characters.orbitCharged
import kinetickk.ball.gameplay.nucleus.simulation.combat.*
import kinetickk.ball.gameplay.nucleus.simulation.elements.knockbackTaken
import kinetickk.ball.gameplay.nucleus.simulation.enemies.*

// ---------------------------------------------------------------------------------------------
// Contacts and fields.

private fun MutableGameState.sweptContact(entity: WeaponEntity, enemy: Enemy): Boolean =
    segmentCircleIntersects(
        entity.previousX - enemy.previousX,
        entity.previousY - enemy.previousY,
        entity.x - enemy.x,
        entity.y - enemy.y,
        0f,
        0f,
        entity.radius + enemy.radius,
    )

/**
 * A directional guard meets an entity's hit by where the hit comes from
 * ([fromX], [fromY]), as it meets a shot, not by where the Core is; its Core-facing guard holds again after the hit.
 */
internal inline fun <T> hitFrom(enemy: Enemy, fromX: Float, fromY: Float, hit: () -> T): T {
    if (!enemy.type.profile.directionalGuard) return hit()
    val shieldBefore = enemy.guarded
    enemy.guarded = shieldFaces(enemy, fromX, fromY)
    try {
        return hit()
    } finally {
        enemy.guarded = shieldBefore
    }
}

internal fun MutableGameState.discHits(disc: WeaponEntity) {
    // Orb signature: a disc thrown or flying while Orbit is charged hits harder; the third form's return pass too.
    val scale = (if (signatureActive() && orbitCharged) GYRE_ORBIT_BONUS else 1f) *
        (if (disc.phase == 1 && hasMasteryForm(3)) GYRE_RETURN_BONUS else 1f)
    for (index in enemies.indices) {
        val enemy = enemies[index]
        if (!enemy.alive || isShifting(enemy) || disc.hasHit(enemy.id) || !sweptContact(disc, enemy)) continue
        disc.recordHit(enemy.id)
        hitFrom(enemy, disc.previousX, disc.previousY) { dealWeaponDamage(enemy, disc.damage * scale, canCrit = true, procKey = disc.procKey) }
        burst(enemy.x, enemy.y, 4, 0)
    }
}

internal fun MutableGameState.wellTick(well: WeaponEntity, delta: Float) {
    val pull = MINE_WELL_PULL * delta
    for (index in enemies.indices) {
        val enemy = enemies[index]
        if (!enemy.alive) continue
        val dx = well.x - enemy.x
        val dy = well.y - enemy.y
        val distanceSquared = dx * dx + dy * dy
        if (distanceSquared > square(well.radius + enemy.radius)) continue
        val distance = max(1f, sqrt(distanceSquared))
        val knockback = knockbackTaken(enemy)
        enemy.vx += dx / distance * pull * knockback
        enemy.vy += dy / distance * pull * knockback
        dealWeaponDamage(enemy, well.damage * delta, procKey = well.procKey)
    }
}
