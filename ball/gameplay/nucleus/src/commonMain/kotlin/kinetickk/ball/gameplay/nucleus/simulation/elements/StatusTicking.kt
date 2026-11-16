// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.nucleus.simulation.elements

import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.gameplay.nucleus.model.*
import kinetickk.ball.gameplay.nucleus.render.EnemyAffinity
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.characters.*
import kinetickk.ball.gameplay.nucleus.simulation.combat.*
import kinetickk.ball.gameplay.nucleus.simulation.enemies.*
import kinetickk.ball.gameplay.nucleus.simulation.skills.*
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

// ---------------------------------------------------------------------------------------------
// Ticking.

internal fun MutableGameState.updateStatuses(delta: Float) {
    val policy = content.build.policy
    breakTokens = min(MutableGameState.BREAK_BURST, breakTokens + policy.breaksPerSecond * delta)
    reactionTokens = min(MutableGameState.REACTION_BURST, reactionTokens + policy.reactionsPerSecond * delta)
    val scale = runScale()
    var centroidX = 0f
    var centroidY = 0f
    var centroidWeight = 0f
    for (index in enemies.indices) {
        val enemy = enemies[index]
        if (!enemy.alive) continue
        val gravitic = stacks(enemy, StatusElement.GRAVITIC)
        if (gravitic > 0) {
            centroidX += enemy.x * gravitic
            centroidY += enemy.y * gravitic
            centroidWeight += gravitic
        }
    }
    if (centroidWeight > 0f) {
        centroidX /= centroidWeight
        centroidY /= centroidWeight
    }
    for (index in enemies.indices) {
        val enemy = enemies[index]
        if (!enemy.alive) continue
        if (enemy.hitQualificationCooldown.toRawBits() != 0) {
            enemy.hitQualificationCooldown = max(0f, enemy.hitQualificationCooldown - delta)
        }
        val unmakeAt = enemy.status[StatusCell.UNMAKE_AT]
        if (unmakeAt > 0f && unmakeAt <= elapsed) {
            val damage = enemy.status[StatusCell.UNMAKE_DAMAGE]
            enemy.status[StatusCell.UNMAKE_AT] = 0f
            enemy.status[StatusCell.UNMAKE_DAMAGE] = 0f
            strikeUnmake(enemy, damage)
            if (!enemy.alive) continue
        }
        if (enemy.status[StatusCell.BLACKFIRE_UNTIL] > 0f) burnBlackfire(enemy, delta)
        if (!enemy.alive || totalStacks(enemy) == 0) continue
        for (element in StatusElement.entries) {
            val cell = StatusCell.STACKS + element.ordinal
            var count = enemy.status[cell].toInt()
            if (count <= 0) continue
            val decayCell = StatusCell.DECAY_AT + element.ordinal
            if (enemy.status[decayCell] <= elapsed) {
                count--
                enemy.status[cell] = count.toFloat()
                enemy.status[decayCell] = elapsed + decaySeconds(element)
                if (element == StatusElement.VOID) forgetHollowWhenEmpty(enemy)
                if (count <= 0) continue
            }
        }
        // Damage-over-time pulses at 4 Hz, staggered by enemy id, without per-step damage numbers.
        val phase = (enemy.id % 30) / 30f
        val pulse = floor((elapsed + phase / DOT_PULSES_PER_SECOND) * DOT_PULSES_PER_SECOND) >
            floor((elapsed - delta + phase / DOT_PULSES_PER_SECOND) * DOT_PULSES_PER_SECOND)
        if (pulse) {
            val thermal = stacks(enemy, StatusElement.THERMAL)
            if (thermal > 0) {
                val damage = thermal * content.build.element(StatusElement.THERMAL).perStack *
                    build.potency(StatusElement.THERMAL) * scale / DOT_PULSES_PER_SECOND
                dealStatusDamage(enemy, damage, StatusElement.THERMAL, showNumber = false)
            }
            val entropy = stacks(enemy, StatusElement.ENTROPY)
            if (entropy > 0 && enemy.alive) {
                dealStatusDamage(enemy, entropy * ENTROPY_DAMAGE_PER_STACK * build.potency(StatusElement.ENTROPY) * scale /
                    DOT_PULSES_PER_SECOND, StatusElement.ENTROPY, showNumber = false)
            }
        }
        val gravitic = stacks(enemy, StatusElement.GRAVITIC)
        if (gravitic > 0 && centroidWeight > gravitic && !isFrozen(enemy)) {
            val knockback = knockbackTaken(enemy)
            if (!(knockback > 0f)) continue
            val dx = centroidX - enemy.x
            val dy = centroidY - enemy.y
            val distance = length(dx, dy)
            if (distance > 1f) {
                val drift = min(40f, 6f * gravitic) * delta * knockback
                enemy.x += dx / distance * min(drift, distance)
                enemy.y += dy / distance * min(drift, distance)
            }
        }
    }
}

/**
 * Blackfire: burns at its per-second rate in the 4 Hz damage pulses until it ends, ignoring the
 * target's aspect (resistance and immunity alike).
 */
private fun MutableGameState.burnBlackfire(enemy: Enemy, delta: Float) {
    if (enemy.status[StatusCell.BLACKFIRE_UNTIL] <= elapsed) {
        enemy.status[StatusCell.BLACKFIRE_UNTIL] = 0f
        enemy.status[StatusCell.BLACKFIRE_DPS] = 0f
        return
    }
    val phase = (enemy.id % 30) / 30f
    val pulse = floor((elapsed + phase / DOT_PULSES_PER_SECOND) * DOT_PULSES_PER_SECOND) >
        floor((elapsed - delta + phase / DOT_PULSES_PER_SECOND) * DOT_PULSES_PER_SECOND)
    if (pulse) {
        dealStatusDamage(enemy, enemy.status[StatusCell.BLACKFIRE_DPS] / DOT_PULSES_PER_SECOND, StatusElement.THERMAL,
            showNumber = false, ignoresAffinity = true)
    }
}

/**
 * Freezes [enemy] the way a Freeze Break does: ordinary enemies and Elites for [seconds], the Architect and secret
 * bosses for half a second, mini-bosses such as the Herald not at all (its Break still rearms and leaves it Brittle).
 * The target stops, turns Brittle and resists the next Freeze for a while.
 */
internal fun MutableGameState.freeze(enemy: Enemy, seconds: Float) {
    val immunity = freezeImmunitySeconds(enemy)
    val duration = if (enemy.isMiniBoss) 0f else if (enemy.isBoss || enemy.isSecretBoss) 0.5f else seconds
    enemy.status[StatusCell.FROZEN_UNTIL] = elapsed + duration
    enemy.status[StatusCell.FREEZE_IMMUNE_UNTIL] = elapsed + duration + immunity
    enemy.status[StatusCell.REARM_AT + StatusElement.CRYO.ordinal] = elapsed + duration + BREAK_REARM_SECONDS
    enemy.status[StatusCell.BRITTLE] = 1f
    enemy.status[StatusCell.CRYO_IMMUNE_CARRY] = 0f
    enemy.vx = 0f
    enemy.vy = 0f
}

/** Ends a Freeze early (a shatter) while keeping the post-thaw immunity. */
internal fun MutableGameState.thaw(enemy: Enemy) {
    if (!isFrozen(enemy)) return
    val immunity = freezeImmunitySeconds(enemy)
    enemy.status[StatusCell.FROZEN_UNTIL] = elapsed
    enemy.status[StatusCell.FREEZE_IMMUNE_UNTIL] = elapsed + immunity
    enemy.status[StatusCell.REARM_AT + StatusElement.CRYO.ordinal] = elapsed + BREAK_REARM_SECONDS
}

/** How long [enemy] resists the next Freeze once a Freeze ends. */
private fun freezeImmunitySeconds(enemy: Enemy): Float = when {
    enemy.isBoss || enemy.isSecretBoss -> ARCHITECT_FREEZE_IMMUNITY_SECONDS
    enemy.isElite -> ELITE_FREEZE_IMMUNITY_SECONDS
    else -> FREEZE_IMMUNITY_SECONDS
}

/** Ion arcs from direct hits on charged targets; bounded by a per-enemy cooldown. */
internal fun MutableGameState.arcCharge(enemy: Enemy) {
    val charge = stacks(enemy, StatusElement.ION)
    if (charge <= 0 || enemy.status[StatusCell.ARC_READY_AT] > elapsed) return
    enemy.status[StatusCell.ARC_READY_AT] = elapsed + 0.35f
    nearestOtherEnemy(enemy.x, enemy.y, enemy.id, 220f)?.let { target ->
        addRelicArc(enemy.x, enemy.y, target.x, target.y)
        dealStatusDamage(target, charge * content.build.element(StatusElement.ION).perStack *
            build.potency(StatusElement.ION) * runScale(), StatusElement.ION)
    }
}

/** Reaction and skill rules that read deaths, including deaths that do not trigger kill clauses. */
internal fun MutableGameState.onStatusCarrierDied(enemy: Enemy) {
    onReactionCarrierDied(enemy)
    onSporedCarrierDied(enemy)
}
/**
 * Marks [enemy] with [kind] for [seconds]. An enemy carries one mark:
 * a newer mark replaces an older one, whatever its kind or writer.
 */
internal fun MutableGameState.setMark(enemy: Enemy, kind: MarkKind, seconds: Float) {
    enemy.status[StatusCell.MARK] = (kind.ordinal + 1).toFloat()
    enemy.status[StatusCell.MARK_UNTIL] = elapsed + seconds
}

/** The live mark on [enemy], or null (none, or lapsed). */
internal fun MutableGameState.markOf(enemy: Enemy): MarkKind? {
    if (enemy.status[StatusCell.MARK_UNTIL] <= elapsed) return null
    return MarkKind.entries.getOrNull(enemy.status[StatusCell.MARK].toInt() - 1)
}

internal fun MutableGameState.markIs(enemy: Enemy, kind: MarkKind): Boolean = markOf(enemy) == kind

/** Cashes a live [kind] mark: clears it and returns true; any other mark stays. */
internal fun MutableGameState.consumeMark(enemy: Enemy, kind: MarkKind): Boolean {
    if (!markIs(enemy, kind)) return false
    enemy.status[StatusCell.MARK] = 0f
    enemy.status[StatusCell.MARK_UNTIL] = 0f
    return true
}

/**
 * Packed per-enemy status for presentation: [StatusCell.ELEMENTS] 4-bit stack counts in element order
 * (bits 0..27), then the Frozen, Brittle, Embrittled and primed flags in bits 28..31.
 */
internal fun MutableGameState.statusBits(enemy: Enemy): Int {
    var bits = 0
    var primed = false
    for (element in StatusElement.entries) {
        val count = stacks(enemy, element)
        bits = bits or (min(15, count) shl (element.ordinal * 4))
        if (!primed && count > 0 && count == breakThreshold(element, enemy) - 1) primed = true
    }
    if (isFrozen(enemy)) bits = bits or STATUS_FROZEN_BIT
    if (enemy.status[StatusCell.BRITTLE] > 0f) bits = bits or STATUS_BRITTLE_BIT
    if (enemy.status[StatusCell.EMBRITTLED_UNTIL] > elapsed) bits = bits or STATUS_EMBRITTLED_BIT
    if (primed) bits = bits or STATUS_PRIMED_BIT
    return bits
}

/** Packed affinity for presentation; see [EnemyAffinity]. The shift flag depends on the run time. */
internal fun MutableGameState.affinityBits(enemy: Enemy): Int {
    val impact = enemy.impactTaken
    return EnemyAffinity.encode(
        aspect = enemy.aspectElement,
        immune = enemy.aspectIsImmune,
        weakness = enemy.weaknessElement,
        armored = impact < 1f,
        fragile = impact > 1f,
        shifting = isShifting(enemy),
        phase = if (enemy.isBossClass) enemy.phase else 0,
        affix = enemy.affix?.ordinal ?: EnemyAffinity.NONE,
        champion = enemy.champion,
    )
}

internal const val STATUS_STACK_BITS: Int = 4 * StatusCell.ELEMENTS
internal const val STATUS_FROZEN_BIT: Int = 1 shl STATUS_STACK_BITS
internal const val STATUS_BRITTLE_BIT: Int = 1 shl (STATUS_STACK_BITS + 1)
internal const val STATUS_EMBRITTLED_BIT: Int = 1 shl (STATUS_STACK_BITS + 2)
internal const val STATUS_PRIMED_BIT: Int = 1 shl (STATUS_STACK_BITS + 3)
