// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.nucleus.simulation.skills

import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.gameplay.nucleus.model.*
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.combat.*
import kinetickk.ball.gameplay.nucleus.simulation.elements.*
import kinetickk.ball.gameplay.nucleus.simulation.enemies.*
import kinetickk.ball.gameplay.nucleus.simulation.progression.*
import kinetickk.ball.gameplay.nucleus.simulation.world.*
import kotlin.math.exp
import kotlin.math.max

// ---------------------------------------------------------------------------------------------
// Skill fields (kind branches the field pass calls).

/** True when ([x], [y]) is within [extra] of the field at [index] (a Frost Line measures to its segment). */
internal fun MutableGameState.fieldContains(index: Int, x: Float, y: Float, extra: Float): Boolean {
    val table = combatFieldTable
    val reach = table[index, FieldCell.RADIUS] + extra
    return if (table.kind(index) == CombatFieldKind.FROST_LINE) {
        pointToSegmentDistanceSquared(x, y, table[index, FieldCell.X], table[index, FieldCell.Y],
            table[index, FieldCell.X2], table[index, FieldCell.Y2]) <= reach * reach
    } else {
        distanceSquared(table[index, FieldCell.X], table[index, FieldCell.Y], x, y) <= reach * reach
    }
}

/**
 * Every step a Frost Line slows the ordinary enemies on it, and a thrown enemy that crosses it slams into it
 * and takes Chill.
 */
internal fun MutableGameState.frostLineStep(index: Int, delta: Float) {
    val table = combatFieldTable
    val x0 = table[index, FieldCell.X]
    val y0 = table[index, FieldCell.Y]
    val x1 = table[index, FieldCell.X2]
    val y1 = table[index, FieldCell.Y2]
    val damping = exp(-FROST_LINE_DAMPING * delta)
    for (enemyIndex in enemies.indices) {
        val enemy = enemies[enemyIndex]
        if (!enemy.alive || isShifting(enemy)) continue
        val thrown = enemy.thrownUntil > elapsed
        if (thrown && segmentsCross(enemy.previousX, enemy.previousY, enemy.x, enemy.y, x0, y0, x1, y1)) {
            if (applySlamDamage(enemy, length(enemy.vx, enemy.vy)) && enemy.alive) {
                applyStatus(enemy, StatusElement.CRYO, FROST_LINE_SLAM_CHILL, 0)
            }
        }
        if (!enemy.alive || enemy.isEliteClass || !fieldContains(index, enemy.x, enemy.y, enemy.radius)) continue
        enemy.vx *= damping
        enemy.vy *= damping
    }
}

/** A Frost Line tick: Chill on the line (rank III: Embrittled while it stays). */
internal fun MutableGameState.frostLineTick(index: Int) {
    val table = combatFieldTable
    val chill = table[index, FieldCell.STACKS].toInt()
    val rankThree = table.hasFlag(index, FieldFlags.RANK_III)
    val element = table.element(index) ?: StatusElement.CRYO
    for (enemyIndex in enemies.indices) {
        val enemy = enemies[enemyIndex]
        if (!enemy.alive || !fieldContains(index, enemy.x, enemy.y, enemy.radius)) continue
        applyStatus(enemy, element, chill, 0)
        if (rankThree && enemy.alive) {
            enemy.status[StatusCell.EMBRITTLED_UNTIL] = max(enemy.status[StatusCell.EMBRITTLED_UNTIL], elapsed + FROST_LINE_EMBRITTLE_SECONDS)
        }
    }
}
