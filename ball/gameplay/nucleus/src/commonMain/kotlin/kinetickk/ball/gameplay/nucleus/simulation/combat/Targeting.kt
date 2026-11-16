// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.nucleus.simulation.combat

import kinetickk.ball.content.api.EffectTrigger
import kinetickk.ball.gameplay.nucleus.model.*
import kinetickk.ball.gameplay.nucleus.protocol.LinkStyleCode
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.elements.*
import kinetickk.ball.gameplay.nucleus.simulation.enemies.*
import kinetickk.ball.gameplay.nucleus.simulation.world.*
import kotlin.math.max

// ---------------------------------------------------------------------------------------------
// Bounded targeting helpers shared by items, relics, reactions and characters. A shifting Architect
// takes nothing, so none of them targets it.

internal fun MutableGameState.nearestOtherEnemy(x: Float, y: Float, excludedId: Int, range: Float): Enemy? =
    nearestEnemyWhere(x, y, range) { candidate -> candidate.id == excludedId || !candidate.alive || isShifting(candidate) }

/**
 * Chains from [origin] through up to [count] distinct nearest enemies (bounded by [MAX_CHAIN_TARGETS]).
 * [onLink] receives each target. A link may trigger another chain (a reaction inside an Overload), so
 * each nesting level owns its own slice of the candidate-private scratch buffer; nesting deeper than
 * [MAX_CHAIN_NESTING] is dropped.
 */
internal inline fun MutableGameState.chain(
    originX: Float,
    originY: Float,
    originId: Int,
    count: Int,
    range: Float,
    style: Int = LinkStyleCode.WEAPON,
    onLink: (Enemy) -> Unit,
) {
    if (scratch.chainDepth >= MAX_CHAIN_NESTING) return
    val base = scratch.chainDepth * CHAIN_SLICE
    scratch.chainDepth++
    try {
        val links = count.coerceIn(0, MAX_CHAIN_TARGETS)
        val used = scratch.chainScratch
        used[base] = originId
        var usedCount = 1
        var fromX = originX
        var fromY = originY
        for (link in 0 until links) {
            var target: Enemy? = null
            var targetDistanceSquared = range * range
            for (enemyIndex in enemies.indices) {
                val candidate = enemies[enemyIndex]
                if (!candidate.alive || isShifting(candidate)) continue
                var alreadyUsed = false
                for (usedIndex in 0 until usedCount) {
                    if (used[base + usedIndex] == candidate.id) {
                        alreadyUsed = true
                        break
                    }
                }
                if (alreadyUsed) continue
                val candidateDistanceSquared = distanceSquared(fromX, fromY, candidate.x, candidate.y)
                if (candidateDistanceSquared <= targetDistanceSquared &&
                    (target == null || candidateDistanceSquared < targetDistanceSquared)
                ) {
                    target = candidate
                    targetDistanceSquared = candidateDistanceSquared
                }
            }
            val chosen = target ?: break
            used[base + usedCount++] = chosen.id
            addRelicArc(fromX, fromY, chosen.x, chosen.y, style)
            onLink(chosen)
            fromX = chosen.x
            fromY = chosen.y
        }
    } finally {
        scratch.chainDepth--
    }
}

internal const val MAX_CHAIN_TARGETS: Int = 6
internal const val MAX_CHAIN_NESTING: Int = 3
internal const val CHAIN_SLICE: Int = MAX_CHAIN_TARGETS + 1

/** Area damage of the proc kind around a point. */
internal fun MutableGameState.areaDamage(
    x: Float,
    y: Float,
    radius: Float,
    damage: Float,
    excludedId: Int = -1,
    rescale: Boolean = true,
    shareCapped: Boolean = false,
) {
    for (index in enemies.indices) {
        val target = enemies[index]
        if (target.id != excludedId && target.alive && distanceSquared(x, y, target.x, target.y) <= square(radius + target.radius)) {
            dealProcDamage(target, damage, rescale, shareCapped = shareCapped)
        }
    }
    shockwave(x, y, 0.24f, radius, 2)
}

/** A short link; [style] is the [LinkStyleCode] of its source (weapons, items and Relics keep the weapon look). */
internal fun MutableGameState.addRelicArc(
    fromX: Float,
    fromY: Float,
    toX: Float,
    toY: Float,
    style: Int = LinkStyleCode.WEAPON,
) {
    addWeaponArc(fromX, fromY, toX, toY, 0.12f, style)
}

internal fun MutableGameState.pushEnemies(x: Float, y: Float, radius: Float, strength: Float, inward: Boolean) {
    for (index in enemies.indices) {
        val target = enemies[index]
        if (!target.alive) continue
        val dx = target.x - x
        val dy = target.y - y
        val distance = max(1f, length(dx, dy))
        if (distance > radius + target.radius) continue
        val knockback = knockbackTaken(target)
        if (knockback <= 0f && build.knockbackToDamage <= 0f) continue
        if (knockbackAsDamage(target, knockback, strength) || knockback <= 0f) continue
        val falloff = 1f - (distance / (radius + target.radius)) * 0.5f
        val sign = if (inward) -1f else 1f
        target.vx += dx / distance * strength * falloff * sign * knockback
        target.vy += dy / distance * strength * falloff * sign * knockback
        markThrown(target)
    }
}

/** True when [trigger] is one the clause engine listens to for this build (cheap early-out). */
internal fun MutableGameState.listens(trigger: EffectTrigger): Boolean = build.clauses(trigger).isNotEmpty()
