// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.nucleus.simulation.enemies

import kinetickk.ball.gameplay.nucleus.model.Enemy
import kinetickk.ball.gameplay.nucleus.model.Projectile
import kinetickk.ball.gameplay.nucleus.model.clamp
import kinetickk.ball.gameplay.nucleus.model.distanceSquared
import kinetickk.ball.gameplay.nucleus.model.segmentCircleIntersects
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.render.ProjectileKind
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.combat.*
import kinetickk.ball.gameplay.nucleus.simulation.director.shotDamage
import kinetickk.ball.gameplay.nucleus.simulation.world.*
import kotlin.math.atan2

internal fun MutableGameState.updateMinelayer(enemy: Enemy, dx: Float, dy: Float, distance: Float, cadence: Float, delta: Float) {
    val radial = clamp((distance - 380f) * 0.5f, -70f, 70f)
    steerOrbit(enemy, dx, dy, distance, radial, 85f, 1.6f, delta)
    enemy.actionTimer -= cadence
    if (enemy.actionTimer <= 0f) {
        enemy.actionTimer = MINE_DROP_SECONDS
        if (canAttack(enemy) && distance in MINE_MINIMUM_CORE_DISTANCE..900f && liveMines() < MAX_LIVE_MINES) {
            addProjectile(Projectile(
                x = enemy.x,
                y = enemy.y,
                vx = 0f,
                vy = 0f,
                radius = MINE_RADIUS,
                life = MINE_LIFE_SECONDS,
                damage = shotDamage(enemy),
                kind = ProjectileKind.MINE,
            ))
        }
    }
}

internal fun MutableGameState.liveMines(): Int {
    var count = 0
    for (index in projectiles.indices) if (projectiles[index].kind == ProjectileKind.MINE) count++
    return count
}

/** True once a mine has armed; it lives [MINE_LIFE_SECONDS] and arms after [MINE_ARM_SECONDS]. */
internal fun mineArmed(mine: Projectile): Boolean = mine.life <= MINE_LIFE_SECONDS - MINE_ARM_SECONDS

/**
 * Resolves a hostile mine against the Core this step; true when it detonated (the caller removes it).
 * An armed mine triggers within [MINE_TRIGGER_RADIUS]; a dash-phase contact sets any mine off harmlessly.
 */
internal fun MutableGameState.resolveMine(mine: Projectile): Boolean {
    val touched = segmentCircleIntersects(previousCoreX, previousCoreY, coreX, coreY, mine.x, mine.y, MINE_TRIGGER_RADIUS)
    if (!touched) return false
    val dashing = dashPhaseTime > 0f
    if (!dashing && !mineArmed(mine)) return false
    shockwave(mine.x, mine.y, 0.3f, MINE_BLAST_RADIUS, 4)
    burst(mine.x, mine.y, 8, 4)
    if (!dashing) {
        val reach = MINE_BLAST_RADIUS + coreRadius
        if (distanceSquared(mine.x, mine.y, coreX, coreY) <= reach * reach) {
            hurtByHostileBlast(mine.damage, IncomingDamage.PROJECTILE)
        }
    }
    return true
}

/**
 * Teleports [enemy] to ([startX], [startY]), first nudged up to four times 50 u along ([dirX], [dirY]) while obstacles
 * block the point, with no interpolation from where it was (the Cipher and the Cartographer's open jump).
 */
internal fun MutableGameState.placeClear(enemy: Enemy, startX: Float, startY: Float, dirX: Float, dirY: Float) {
    var x = startX
    var y = startY
    var attempts = 0
    while (attempts < 4 && isSpawnPointBlocked(x, y, enemy.radius)) {
        x += dirX * 50f
        y += dirY * 50f
        attempts++
    }
    enemy.x = x
    enemy.y = y
    enemy.previousX = x
    enemy.previousY = y
}

/**
 * True when a hit arriving from ([fromX], [fromY]) meets a directional guard: one of
 * the shield arcs of the Throne or the Antecedent.
 */
internal fun shieldFaces(enemy: Enemy, fromX: Float, fromY: Float): Boolean {
    val theta = atan2(fromY - enemy.y, fromX - enemy.x)
    return when (enemy.type) {
        EnemyType.THRONE, EnemyType.ANTECEDENT -> arcGuards(enemy, theta)
        else -> false
    }
}
