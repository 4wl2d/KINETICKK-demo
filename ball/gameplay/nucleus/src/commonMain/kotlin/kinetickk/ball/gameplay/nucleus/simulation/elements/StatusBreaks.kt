// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.nucleus.simulation.elements

import kinetickk.ball.content.api.EffectTrigger
import kinetickk.ball.content.api.ElementDefinition
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.gameplay.nucleus.model.*
import kinetickk.ball.gameplay.nucleus.protocol.FxColorCode
import kinetickk.ball.gameplay.nucleus.protocol.LinkStyleCode
import kinetickk.ball.gameplay.nucleus.protocol.ShockwaveKind
import kinetickk.ball.gameplay.nucleus.protocol.VisualFxCue
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.builds.*
import kinetickk.ball.gameplay.nucleus.simulation.combat.*
import kinetickk.ball.gameplay.nucleus.simulation.enemies.*
import kinetickk.ball.gameplay.nucleus.simulation.world.*
import kotlin.math.max
import kotlin.math.min

// ---------------------------------------------------------------------------------------------
// Breaks.

internal fun MutableGameState.tryBreak(enemy: Enemy, element: StatusElement, forced: Boolean = false) {
    if (isShifting(enemy)) return
    val index = element.ordinal
    val count = enemy.status[StatusCell.STACKS + index].toInt()
    if (count <= 0) return
    if (!forced && count < breakThreshold(element, enemy)) return
    if (enemy.status[StatusCell.REARM_AT + index] > elapsed) return
    if (element == StatusElement.CRYO && (isFrozen(enemy) || enemy.status[StatusCell.FREEZE_IMMUNE_UNTIL] > elapsed)) return
    val generation = enemy.status[StatusCell.GENERATION + index].toInt()
    if (!admitBreak(generation)) return
    enemy.status[StatusCell.STACKS + index] = 0f
    enemy.status[StatusCell.REARM_AT + index] = elapsed + BREAK_REARM_SECONDS
    resolveBreak(enemy, element, count, generation)
}

/** Forces [element]'s Break on [enemy] now if it holds any stacks (a weapon form's Freeze, Overload or Shatter). */
internal fun MutableGameState.forceElementBreak(enemy: Enemy, element: StatusElement) {
    tryBreak(enemy, element, forced = true)
}

/** Forces every status on [enemy] that is at least [fraction] of its threshold to Break (ANCHOR III). */
internal fun MutableGameState.forceBreaks(enemy: Enemy, fraction: Float) {
    for (element in StatusElement.entries) {
        val count = stacks(enemy, element)
        if (count > 0 && count >= breakThreshold(element, enemy) * fraction) tryBreak(enemy, element, forced = true)
    }
}

private fun MutableGameState.resolveBreak(enemy: Enemy, element: StatusElement, count: Int, generation: Int) {
    val heldCues = scratch.heldStatusCues
    val definition = content.build.element(element)
    val power = definition.breakPower * build.breakPower(element)
    val potency = build.potency(element)
    // Damage dealt by the Break itself; the Freeze's duration and the spread stacks are not damage.
    val scale = runScale() * breakDamageScale()
    val outputGeneration = generation + 1
    when (element) {
        StatusElement.THERMAL -> thermalBreak(enemy, definition, count, power, potency, scale)
        StatusElement.CRYO -> cryoBreak(enemy, power)
        StatusElement.ION -> ionBreak(enemy, definition, count, power, potency, scale, outputGeneration)
        StatusElement.GRAVITIC -> graviticBreak(enemy, definition, count, power, potency, scale)
        StatusElement.ENTROPY -> entropyBreak(enemy, definition)
        StatusElement.SONIC -> sonicBreak(enemy, definition, count, power, potency, scale)
        StatusElement.VOID -> voidBreak(enemy, power)
    }
    val spread = build.breakSpread(element)
    if (spread > 0) applyStatusAround(enemy.x, enemy.y, build.breakSpreadRadius(element), element, spread.toFloat(), outputGeneration, enemy.id)
    if (affinityTier(element) >= 3) applyStatusAround(enemy.x, enemy.y, 130f, element, 2f, outputGeneration, enemy.id)
    recordOwnStatusCue(VisualFxCue.StatusBroken(enemy.x, enemy.y, element.ordinal), heldCues)
    if (generation == 0 || listens(EffectTrigger.BREAK)) onBreak(enemy, element)
}

private fun MutableGameState.thermalBreak(
    enemy: Enemy,
    definition: ElementDefinition,
    count: Int,
    power: Float,
    potency: Float,
    scale: Float,
) {
    val damage = power * count * potency * scale
    val radius = definition.breakRadius
    for (targetIndex in enemies.indices) {
        val target = enemies[targetIndex]
        if (target.alive && distanceSquared(enemy.x, enemy.y, target.x, target.y) <= square(radius + target.radius)) {
            dealStatusDamage(target, damage, StatusElement.THERMAL)
        }
    }
    shockwave(enemy.x, enemy.y, 0.28f, radius, FxColorCode.elementTone(StatusElement.THERMAL), statusWaveKind())
}

private fun MutableGameState.cryoBreak(enemy: Enemy, power: Float) {
    freeze(enemy, min(2f, power))
    shockwave(enemy.x, enemy.y, 0.3f, enemy.radius * 2.4f, FxColorCode.elementTone(StatusElement.CRYO), statusWaveKind())
}

private fun MutableGameState.ionBreak(
    enemy: Enemy,
    definition: ElementDefinition,
    count: Int,
    power: Float,
    potency: Float,
    scale: Float,
    outputGeneration: Int,
) {
    val damage = power * count * potency * scale
    chain(enemy.x, enemy.y, enemy.id, 3, definition.breakRadius, LinkStyleCode.elementBreak(StatusElement.ION)) { target ->
        dealStatusDamage(target, damage, StatusElement.ION)
        if (outputGeneration < 2) applyStatus(target, StatusElement.ION, 2, outputGeneration)
    }
    dealStatusDamage(enemy, damage, StatusElement.ION)
}

private fun MutableGameState.graviticBreak(
    enemy: Enemy,
    definition: ElementDefinition,
    count: Int,
    power: Float,
    potency: Float,
    scale: Float,
) {
    var pulled = 0
    for (targetIndex in enemies.indices) {
        val target = enemies[targetIndex]
        if (target.id == enemy.id || !target.alive) continue
        if (distanceSquared(enemy.x, enemy.y, target.x, target.y) <= square(definition.breakRadius + target.radius)) {
            pulled++
            val knockback = knockbackTaken(target)
            if (knockback > 0f) {
                val dx = enemy.x - target.x
                val dy = enemy.y - target.y
                val distance = max(1f, length(dx, dy))
                target.vx += dx / distance * 520f * knockback
                target.vy += dy / distance * 520f * knockback
                markThrown(target)
            }
        }
    }
    val damage = power * count * (1f + min(pulled, 5)) * potency * scale
    for (targetIndex in enemies.indices) {
        val target = enemies[targetIndex]
        if (target.alive && distanceSquared(enemy.x, enemy.y, target.x, target.y) <= square(definition.breakRadius + target.radius)) {
            dealStatusDamage(target, damage, StatusElement.GRAVITIC)
        }
    }
    shockwave(enemy.x, enemy.y, 0.32f, definition.breakRadius, FxColorCode.elementTone(StatusElement.GRAVITIC), statusWaveKind())
}

private fun MutableGameState.entropyBreak(enemy: Enemy, definition: ElementDefinition) {
    if (enemy.status[StatusCell.MAX_HP_PROC_READY_AT] <= elapsed) {
        enemy.status[StatusCell.MAX_HP_PROC_READY_AT] = elapsed + maxHpProcCooldown(enemy)
        dealStatusDamage(enemy, enemy.maxHp * victimFactor(enemy) * definition.breakPower * breakDamageScale(), StatusElement.ENTROPY)
    }
    shockwave(enemy.x, enemy.y, 0.3f, enemy.radius * 2.2f, FxColorCode.elementTone(StatusElement.ENTROPY), statusWaveKind())
}

private fun MutableGameState.sonicBreak(
    enemy: Enemy,
    definition: ElementDefinition,
    count: Int,
    power: Float,
    potency: Float,
    scale: Float,
) {
    val damage = power * count * potency * scale
    val radius = definition.breakRadius
    for (targetIndex in enemies.indices) {
        val target = enemies[targetIndex]
        if (!target.alive || distanceSquared(enemy.x, enemy.y, target.x, target.y) > square(radius + target.radius)) continue
        dealStatusDamage(target, damage, StatusElement.SONIC)
        throwFrom(enemy, target)
    }
    shockwave(enemy.x, enemy.y, 0.34f, radius, FxColorCode.elementTone(StatusElement.SONIC), statusWaveKind())
}

private fun MutableGameState.voidBreak(enemy: Enemy, power: Float) {
    var remembered = enemy.status[StatusCell.HOLLOW_MEMORY]
    enemy.status[StatusCell.HOLLOW_MEMORY] = 0f
    remembered *= power * breakDamageScale()
    val cap = enemy.maxHp * UNMAKE_INTEGRITY_CAP * if (enemy.isBossClass) victimFactor(enemy) else 1f
    val damage = min(remembered, cap)
    if (damage > 0f) {
        enemy.status[StatusCell.UNMAKE_AT] = elapsed + UNMAKE_DELAY_SECONDS
        enemy.status[StatusCell.UNMAKE_DAMAGE] += damage
    }
    shockwave(enemy.x, enemy.y, 0.3f, enemy.radius * 2.6f, FxColorCode.elementTone(StatusElement.VOID), statusWaveKind())
}

/** Shatter's throw: away from the Shatter's center, or away from the Core for the shattered enemy. */
private fun MutableGameState.throwFrom(center: Enemy, target: Enemy) {
    val knockback = knockbackTaken(target)
    if (knockback <= 0f) return
    var dx = target.x - center.x
    var dy = target.y - center.y
    if (target.id == center.id || length(dx, dy) < 1f) {
        dx = target.x - coreX
        dy = target.y - coreY
    }
    val distance = max(1f, length(dx, dy))
    target.vx += dx / distance * SHATTER_THROW_SPEED * knockback
    target.vy += dy / distance * SHATTER_THROW_SPEED * knockback
    markThrown(target)
}

/** Releases an Unmake: the remembered damage strikes as Void status damage. */
internal fun MutableGameState.strikeUnmake(enemy: Enemy, damage: Float) {
    dealStatusDamage(enemy, damage, StatusElement.VOID)
    burst(enemy.x, enemy.y, 6, 2)
}

/**
 * Memory belongs to Hollow: once the last Void stack is gone (decayed, spent by a reaction or refused by an
 * immune aspect), nothing is remembered. Every writer that can empty the Void stacks calls this after it.
 */
internal fun forgetHollowWhenEmpty(enemy: Enemy) {
    if (stacks(enemy, StatusElement.VOID) <= 0) enemy.status[StatusCell.HOLLOW_MEMORY] = 0f
}

/**
 * Hollow (Void) memory: while the enemy carries Hollow it remembers a share of every damage it takes,
 * bounded by its maximum integrity. Called by the single enemy-integrity writer. Like every base derived
 * from a hit, the memory is measured without the run damage multiplier (Matrix and Melds), which the Unmake strike
 * applies once as a damage instance of its own.
 */
internal fun MutableGameState.rememberHollowDamage(enemy: Enemy, dealt: Float) {
    val hollow = stacks(enemy, StatusElement.VOID)
    if (hollow <= 0 || dealt <= 0f) return
    val applied = dealt / runDamageMultiplier()
    val share = hollow * HOLLOW_MEMORY_PER_STACK * build.potency(StatusElement.VOID)
    enemy.status[StatusCell.HOLLOW_MEMORY] = min(enemy.maxHp, enemy.status[StatusCell.HOLLOW_MEMORY] + applied * share)
}

/**
 * Hollow (Void) on the attacking enemy: each stack cuts its damage output, never below
 * [HOLLOW_OUTPUT_FLOOR]. 1 for sourceless hazards and enemies without Hollow.
 */
internal fun MutableGameState.hollowOutputFactor(enemy: Enemy?): Float {
    if (enemy == null) return 1f
    val hollow = stacks(enemy, StatusElement.VOID)
    if (hollow <= 0) return 1f
    val cut = hollow * content.build.element(StatusElement.VOID).perStack * build.potency(StatusElement.VOID)
    return (1f - cut).coerceIn(HOLLOW_OUTPUT_FLOOR, 1f)
}

internal fun maxHpProcCooldown(enemy: Enemy): Float = when {
    enemy.isBoss || enemy.isSecretBoss -> 4f
    enemy.isElite || enemy.isMiniBoss -> 2f
    else -> 1f
}

private fun MutableGameState.admitBreak(generation: Int): Boolean {
    // Generation 0 is always admitted but still spends, never below zero, so later cascades recover quickly.
    if (generation == 0) {
        breakTokens = max(0f, breakTokens - 1f)
        return true
    }
    if (breakTokens < 1f) return false
    breakTokens -= 1f
    return true
}

internal fun MutableGameState.admitReaction(generation: Int): Boolean {
    if (generation == 0) {
        reactionTokens = max(0f, reactionTokens - 1f)
        return true
    }
    if (reactionTokens < 1f) return false
    reactionTokens -= 1f
    return true
}

/**
 * The kind of a Break's or a reaction's wave, decided where the wave is emitted: [ShockwaveKind.ACCENT] when this step
 * can still record the status cue that shows the event's own look. A slot of the step's budget is then held for that
 * cue ([recordOwnStatusCue]), so no cue emitted between the wave and its own (a spread's immunity, a nested Break or
 * reaction) can take it and leave the event with neither ring nor look. A plain ring once the cap would drop the cue.
 * Presentation only: nothing in the simulation reads the budget.
 */
internal fun MutableGameState.statusWaveKind(): Int {
    if (scratch.statusCuesThisStep >= MAX_STATUS_CUES_PER_STEP) return ShockwaveKind.RING
    scratch.statusCuesThisStep++
    scratch.heldStatusCues++
    return ShockwaveKind.ACCENT
}

/**
 * Records the status cue of an event that started with [heldBefore] slots held ([StepScratch.heldStatusCues] read
 * before it resolved): into the slot its own wave held, when it held one, else under the step's cap like any cue.
 */
internal fun MutableGameState.recordOwnStatusCue(cue: VisualFxCue, heldBefore: Int) {
    if (scratch.heldStatusCues > heldBefore) {
        scratch.heldStatusCues--
        emitVisualFx(cue)
    } else {
        recordStatusCue(cue)
    }
}

internal fun MutableGameState.recordStatusCue(cue: VisualFxCue) {
    if (scratch.statusCuesThisStep < MAX_STATUS_CUES_PER_STEP) {
        scratch.statusCuesThisStep++
        emitVisualFx(cue)
    }
}
