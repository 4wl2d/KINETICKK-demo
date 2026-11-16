// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.nucleus.simulation.skills

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.SkillId
import kinetickk.ball.content.api.SkillTargeting
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.gameplay.nucleus.model.*
import kinetickk.ball.gameplay.nucleus.protocol.GameplayAudioCue
import kinetickk.ball.gameplay.nucleus.protocol.VisualFxCue
import kinetickk.ball.gameplay.nucleus.render.SkillSlotProjection
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.builds.*
import kinetickk.ball.gameplay.nucleus.simulation.characters.*
import kinetickk.ball.gameplay.nucleus.simulation.combat.*
import kinetickk.ball.gameplay.nucleus.simulation.elements.*
import kinetickk.ball.gameplay.nucleus.simulation.enemies.*
import kotlin.math.max
import kotlin.math.min

// ---------------------------------------------------------------------------------------------
// Casting.

/**
 * Casts [slot] when it can: a filled slot whose cooldown ran out and, for a NEAREST skill, a target. A refused
 * cast spends nothing and leaves its request buffered; the slot shows why (cooling or no target). On a cast the
 * effect resolves, the cooldown starts and the character, cue and sound follow. True when the slot cast.
 */
internal fun MutableGameState.castSkill(slot: Int): Boolean {
    val id = skillLoadout.skill(slot) ?: return false
    val runtime = skillRuntime
    if (elapsed < runtime[slot, SkillCell.READY_AT]) {
        refuseSkill(slot, SkillSlotProjection.REFUSAL_COOLING)
        return false
    }
    val definition = content.skill(id)
    val rank = skillLoadout.rank(slot)
    val values = definition.rank(rank).values
    val pointerX = cameraX + pointerX - screenWidth * 0.5f
    val pointerY = cameraY + pointerY - screenHeight * 0.5f
    val toPointer = length(pointerX - coreX, pointerY - coreY)
    val aimX = if (toPointer > AIM_DEADZONE) (pointerX - coreX) / toPointer else lastAimDirectionX
    val aimY = if (toPointer > AIM_DEADZONE) (pointerY - coreY) / toPointer else lastAimDirectionY
    var anchorX = coreX
    var anchorY = coreY
    var target: Enemy? = null
    when (definition.targeting) {
        SkillTargeting.SELF, SkillTargeting.AIM -> Unit
        SkillTargeting.AREA -> if (toPointer > AIM_DEADZONE) {
            val reach = min(toPointer, definition.reach)
            anchorX = coreX + aimX * reach
            anchorY = coreY + aimY * reach
        }
        SkillTargeting.NEAREST -> {
            target = nearestSkillTarget(pointerX, pointerY, values[ARC_HOOK_REACH])
            if (target == null) {
                refuseSkill(slot, SkillSlotProjection.REFUSAL_NO_TARGET)
                return false
            }
            anchorX = target.x
            anchorY = target.y
        }
    }
    // Measured before the cast: a Prism with every facet up casts at a shorter cooldown.
    val prismDiscount = coreShape == CoreShape.PRISM && characterRuntime.facets >= CharacterRuntime.MAX_FACETS
    var radius = 0f
    val primaryId = withSkillEffect(slot) {
        radius = resolveSkillCast(slot, id, rank, values, aimX, aimY, anchorX, anchorY, target)
    }
    val primary = enemyById(primaryId) ?: target
    var cooldown = definition.rank(rank).cooldownSeconds * max(MIN_SKILL_COOLDOWN_SCALE, build.skillCooldownScale(definition.element))
    if (prismDiscount) cooldown *= PRISM_SKILL_COOLDOWN
    cooldown = max(MIN_SKILL_RECAST_SECONDS, cooldown)
    runtime[slot, SkillCell.READY_AT] = elapsed + cooldown
    runtime[slot, SkillCell.LAST_COOLDOWN] = cooldown
    runtime[slot, SkillCell.REFUNDED] = 0f
    runtime[slot, SkillCell.CAST_AT] = elapsed
    runtime[slot, SkillCell.CAST_COUNT] = runtime[slot, SkillCell.CAST_COUNT] + 1f
    runtime[slot, SkillCell.REFUSAL] = SkillSlotProjection.REFUSAL_NONE.toFloat()
    onCharacterSkillCast(anchorX, anchorY, primary)
    emitVisualFx(VisualFxCue.SkillCast(id, anchorX, anchorY, aimX, aimY, radius, rank))
    emitSound(GameplayAudioCue.SKILL)
    if (listens(kinetickk.ball.content.api.EffectTrigger.SKILL_CAST)) {
        val origin = primary?.takeIf { it.alive }
        fireClauses(
            kinetickk.ball.content.api.EffectTrigger.SKILL_CAST, origin, element = skillElement(id), skill = id,
            originX = if (origin == null) anchorX else Float.NaN, originY = if (origin == null) anchorY else Float.NaN,
        )
    }
    return true
}

/** The element [id] casts with: the attuned element when the build attunes skills, else its own (null: element-less). */
internal fun MutableGameState.skillElement(id: SkillId): StatusElement? =
    build.skillAttunement?.let(::resolvePick) ?: content.skill(id).element

/** [native], or the attuned element when the build attunes skills (and the pick resolves). */
internal fun MutableGameState.attuned(native: StatusElement): StatusElement =
    build.skillAttunement?.let(::resolvePick) ?: native

/** Shows why a buffered cast waits, once per buffered request. */
private fun MutableGameState.refuseSkill(slot: Int, refusal: Int) {
    val runtime = skillRuntime
    if (runtime[slot, SkillCell.REFUSAL].toInt() == refusal &&
        runtime[slot, SkillCell.REFUSED_AT] > elapsed - MutableGameState.SKILL_INPUT_BUFFER_SECONDS
    ) return
    runtime[slot, SkillCell.REFUSAL] = refusal.toFloat()
    runtime[slot, SkillCell.REFUSED_AT] = elapsed
}

/**
 * Runs [effect] as part of [slot]'s cast: damage it deals counts once per boss-class enemy, the first
 * [MAX_SKILL_HITS_PER_CAST] enemies it damages are its skill hits, and the first enemy it touches is its
 * primary target, whose id it returns (-1 for none). The cast scratch is idle again afterwards.
 */
internal inline fun MutableGameState.withSkillEffect(slot: Int, effect: () -> Unit): Int {
    skillCast.castSlot = slot
    skillCast.castHits = 0
    skillCast.castAttunedStacks = 0
    skillCast.castPrimaryId = -1
    skillCast.castBossHitFirst = -1
    skillCast.castBossHitSecond = -1
    try {
        effect()
        return skillCast.castPrimaryId
    } finally {
        skillCast.castSlot = -1
        skillCast.castHits = 0
        skillCast.castAttunedStacks = 0
        skillCast.castPrimaryId = -1
        skillCast.castBossHitFirst = -1
        skillCast.castBossHitSecond = -1
    }
}

/**
 * The enemy nearest the pointer at ([pointerX], [pointerY]) among live, non-shifting enemies within [reach] of the
 * Core, at least [HOOK_SINGULARITY_GAP] from the singularity and with no obstacle between it and the Core (the hook
 * would fling the Core into the rock); ties keep scan order.
 */
internal fun MutableGameState.nearestSkillTarget(pointerX: Float, pointerY: Float, reach: Float): Enemy? {
    var best: Enemy? = null
    var bestDistance = Float.POSITIVE_INFINITY
    val reachSquared = reach * reach
    for (index in enemies.indices) {
        val enemy = enemies[index]
        if (!enemy.alive || isShifting(enemy)) continue
        if (distanceSquared(coreX, coreY, enemy.x, enemy.y) > reachSquared) continue
        val fromPointer = distanceSquared(pointerX, pointerY, enemy.x, enemy.y)
        if (fromPointer < square(HOOK_SINGULARITY_GAP)) continue
        if (fromPointer < bestDistance) {
            if (hookLineBlocked(enemy)) continue
            best = enemy
            bestDistance = fromPointer
        }
    }
    return best
}

/**
 * Skill power of [id]: the run scale times one plus the build's skill bonus for its element, Skill Power buffs and
 * while-modifiers. Every non-kinetic skill number scales with it.
 */
internal fun MutableGameState.skillPower(id: SkillId): Float = runScale() * kineticSkillScale(id)

/** Kinetic skills scale off impact power, which already carries the run growth: only the build and buff bonuses apply. */
internal fun MutableGameState.kineticSkillScale(id: SkillId): Float =
    1f + build.skillPowerBonus(content.skill(id).element)

/**
 * Skill damage of the running cast on [enemy]: through [dealSkillDamage], once per boss-class enemy per cast.
 * The first [MAX_SKILL_HITS_PER_CAST] enemies it damages count as the cast's skill hits.
 */
internal fun MutableGameState.skillHit(enemy: Enemy, amount: Float, element: StatusElement?, kinetic: Boolean = false): Boolean {
    if (!enemy.alive || amount <= 0f) return false
    if (enemy.isBossClass) {
        if (skillCast.castBossHitFirst == enemy.id || skillCast.castBossHitSecond == enemy.id) return false
        if (skillCast.castBossHitFirst < 0) skillCast.castBossHitFirst = enemy.id else skillCast.castBossHitSecond = enemy.id
    }
    val integrity = enemy.hp
    val result = dealSkillDamage(enemy, amount, element, kinetic)
    if (result.amount <= 0f) return false
    recordSkillDamage(skillCast.castSlot, min(result.amount, integrity.coerceAtLeast(0f)))
    noteCastTarget(enemy)
    attuneHit(enemy)
    if (skillCast.castHits < MAX_SKILL_HITS_PER_CAST) {
        skillCast.castHits++
        onSkillHit(enemy, result.amount)
    }
    return true
}

/**
 * SkillAttunement: an element-less skill's cast applies one stack of the attuned element to each enemy it damages,
 * at most [MAX_ATTUNED_STACKS_PER_CAST] per cast.
 */
private fun MutableGameState.attuneHit(enemy: Enemy) {
    val pick = build.skillAttunement ?: return
    if (skillCast.castSlot < 0 || skillCast.castAttunedStacks >= MAX_ATTUNED_STACKS_PER_CAST || !enemy.alive) return
    val id = skillLoadout.skill(skillCast.castSlot) ?: return
    if (content.skill(id).element != null) return
    val element = resolvePick(pick) ?: return
    skillCast.castAttunedStacks++
    applyStatus(enemy, element, 1, 0)
}

/** Counts [dealt] skill damage for the run and, when [slot] is known, for the slot and the active effect it holds. */
private fun MutableGameState.recordSkillDamage(slot: Int, dealt: Float) {
    val runtime = skillRuntime
    runtime.skillDamage += dealt.toDouble()
    if (slot < 0) return
    runtime[slot, SkillCell.DAMAGE] = runtime[slot, SkillCell.DAMAGE] + dealt
    skillLoadout.skill(slot)?.let { runtime.addEffectDamage(it, dealt) }
}

internal fun MutableGameState.noteCastTarget(enemy: Enemy) {
    if (skillCast.castSlot >= 0 && skillCast.castPrimaryId < 0) skillCast.castPrimaryId = enemy.id
}

/** A skill hit on [enemy] for [dealt]: the Triangle marks it for Puncture and SKILL_HIT clauses fire. */
private fun MutableGameState.onSkillHit(enemy: Enemy, dealt: Float) {
    if (enemy.alive) markForPuncture(enemy)
    val id = skillLoadout.skill(skillCast.castSlot) ?: return
    if (!listens(kinetickk.ball.content.api.EffectTrigger.SKILL_HIT)) return
    fireClauses(kinetickk.ball.content.api.EffectTrigger.SKILL_HIT, enemy, dealt, element = skillElement(id), skill = id)
}
