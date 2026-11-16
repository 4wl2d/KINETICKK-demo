// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.nucleus.simulation.enemies

import kinetickk.ball.content.api.BossEncounterSpec
import kinetickk.ball.gameplay.nucleus.model.Enemy
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.dimensions.*
import kotlin.math.min

/*
 * The phase gate every boss-class kind with a content encounter spec shares: equal integrity parts, a per-instance hit
 * cap on the boss's own integrity, a shift without damage at each gate and an optional timed enrage. The Architect's numbers
 * are its spec, and these functions run the same float operations in the same order as its own gate did.
 */

/** The content encounter spec of [enemy]'s kind; null for every kind without a phase gate. */
internal fun MutableGameState.encounterSpec(enemy: Enemy): BossEncounterSpec? = content.enemy(enemy.type).encounter

/** Integrity at which [enemy]'s current phase ends: the equal parts of its bar. */
internal fun MutableGameState.bossGate(enemy: Enemy, spec: BossEncounterSpec): Float {
    val finalPhase = spec.phases - 1
    return enemy.maxHp * (finalPhase - enemy.phase) / (finalPhase + 1f)
}

internal fun MutableGameState.bossHitCap(
    enemy: Enemy,
    spec: BossEncounterSpec = checkNotNull(encounterSpec(enemy)),
): Float =
    spec.hitCapShare * enemy.maxHp / objectiveBossIntegrityShare(enemy)

/**
 * Applies the phase gate to a requested amount: nothing while shifting, at most [bossHitCap], and never past the
 * current phase's gate. Never raises integrity.
 */
internal fun MutableGameState.gateBossDamage(enemy: Enemy, spec: BossEncounterSpec, amount: Float): Float {
    if (isShifting(enemy)) return 0f
    var gated = min(amount, bossHitCap(enemy, spec))
    if (enemy.phase < spec.phases - 1) {
        val gate = bossGate(enemy, spec)
        if (enemy.hp > gate) gated = min(gated, enemy.hp - gate)
    }
    return gated
}

/** Called after every integrity write on a gated boss: reaching a gate starts the next phase. */
internal fun MutableGameState.onBossDamaged(enemy: Enemy, spec: BossEncounterSpec) {
    if (enemy.phase >= spec.phases - 1 || enemy.hp <= 0f) return
    if (enemy.hp <= bossGate(enemy, spec) + enemy.maxHp * ARCHITECT_GATE_TOLERANCE) startBossPhase(enemy, spec)
}

/**
 * Enters the next phase: a shift without damage. Only boss state changes inside the damage pipeline; the Architect's
 * aspect and escorts are set here as always, every other kind's phase start runs from [updateBossPhaseStarts].
 */
private fun MutableGameState.startBossPhase(enemy: Enemy, spec: BossEncounterSpec) {
    enemy.phase++
    enemy.shiftUntil = elapsed + spec.shiftSeconds
    if (enemy.isBoss) startArchitectPhaseEffects(enemy) else enemy.phaseStartPending = true
}

/**
 * True while [enemy] is enraged: [BossEncounterSpec.enrageAfterSeconds] after
 * its encounter started (an untagged boss has no encounter clock).
 */
internal fun MutableGameState.bossEnraged(enemy: Enemy, spec: BossEncounterSpec): Boolean {
    if (spec.enrageAfterSeconds <= 0f) return false
    val start = encounterStartedAt(enemy.ownerTag)
    return !start.isNaN() && elapsed - start >= spec.enrageAfterSeconds
}

/** Attack cadence factor of a boss-class kind: its spec's enrage rate while enraged, else 1. */
internal fun MutableGameState.bossAttackScale(enemy: Enemy): Float {
    val spec = encounterSpec(enemy) ?: return 1f
    return if (bossEnraged(enemy, spec)) spec.enrageAttackRate else 1f
}

/**
 * Runs the phase starts the damage pipeline left pending, once per step after enemy movement and outside every enemy
 * loop, so a Frozen boss still starts its phase and nothing spawns inside the damage pipeline. A secret boss's enrage
 * notice comes from the same step.
 */
internal fun MutableGameState.updateBossPhaseStarts() {
    for (index in enemies.indices) {
        val enemy = enemies[index]
        if (!enemy.alive) continue
        if (enemy.isSecretBoss) announceSecretBossEnrage(enemy)
        if (!enemy.phaseStartPending) continue
        enemy.phaseStartPending = false
        startMiniOrSecretPhase(enemy)
    }
}

/** The phase-start effects of the mini and secret bosses; a kind without its own runs none. */
private fun MutableGameState.startMiniOrSecretPhase(enemy: Enemy) {
    when (enemy.type) {
        EnemyType.WARDEN_PRIME, EnemyType.BROOD_QUEEN, EnemyType.THRONE, EnemyType.PYRE_HERALD -> startMiniBossPhase(enemy)
        EnemyType.CARTOGRAPHER, EnemyType.ANTECEDENT -> startSecretBossPhase(enemy)
        else -> Unit
    }
}
