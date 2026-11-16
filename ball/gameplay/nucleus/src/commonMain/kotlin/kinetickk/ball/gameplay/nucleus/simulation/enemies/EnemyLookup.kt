// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.nucleus.simulation.enemies

import kinetickk.ball.gameplay.nucleus.model.*
import kinetickk.ball.gameplay.nucleus.simulation.*

internal fun MutableGameState.enemyById(id: Int): Enemy? {
    if (id < 0) return null
    for (index in enemies.indices) if (enemies[index].id == id) return enemies[index]
    return null
}

/** Nearest live enemy within [range]; a shifting Architect is never a target. */
internal fun MutableGameState.nearestEnemy(x: Float, y: Float, range: Float): Enemy? =
    nearestEnemyWhere(x, y, range) { candidate -> candidate.dead || !(candidate.hp > 0f) || isShifting(candidate) }

/**
 * The enemy nearest to ([x], [y]) within [range] that [skip] lets through, in list order: the first one at the
 * smallest distance wins a tie (a NaN distance never does). Every target scan shares this rule; [skip] runs once per
 * enemy, before its distance.
 */
internal inline fun MutableGameState.nearestEnemyWhere(x: Float, y: Float, range: Float, skip: (Enemy) -> Boolean): Enemy? {
    var nearest: Enemy? = null
    var nearestDistanceSquared = range * range
    for (index in enemies.indices) {
        val candidate = enemies[index]
        if (skip(candidate)) continue
        val candidateDistanceSquared = distanceSquared(x, y, candidate.x, candidate.y)
        if (
            candidateDistanceSquared <= nearestDistanceSquared &&
            (nearest == null || candidateDistanceSquared < nearestDistanceSquared)
        ) {
            nearest = candidate
            nearestDistanceSquared = candidateDistanceSquared
        }
    }
    return nearest
}
