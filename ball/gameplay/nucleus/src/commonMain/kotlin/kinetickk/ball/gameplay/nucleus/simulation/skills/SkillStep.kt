// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.nucleus.simulation.skills

import kinetickk.ball.content.api.SkillId
import kinetickk.ball.gameplay.nucleus.model.*
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.builds.*
import kinetickk.ball.gameplay.nucleus.simulation.characters.*
import kinetickk.ball.gameplay.nucleus.simulation.enemies.*
import kotlin.math.max

/*
 * Active effects in the Z, X and C slots. Owned active items bind them ([SkillLoadout], rank = the item's stacks); their
 * timing and per-cast state live in [SkillRuntime]. Every cast and every refund goes through this package: a cast
 * spends its slot's cooldown, and every cooldown loop (Catalysis, character refunds, reaction verbs) draws on one refund
 * bank, so no combination more than doubles a skill's cast rate and no skill recasts sooner than
 * [MIN_SKILL_RECAST_SECONDS]. No gameplay or spawn randomness is drawn here.
 */

// ---------------------------------------------------------------------------------------------
// Step.

/**
 * Casts the buffered slots that can cast now (Z, X, C order), then resolves Void Step rifts and Arc Hook's kill window.
 * The Apex follows the slots ([updateApex]). Runs after the heat update and before the Core moves, so movement skills
 * act in the same step. Allocation-free.
 */
internal fun MutableGameState.updateSkills(delta: Float) {
    updateSkillSlots(delta)
    if (phase == kinetickk.ball.gameplay.nucleus.render.GamePhase.RUNNING) updateApex(delta)
}

private fun MutableGameState.updateSkillSlots(delta: Float) {
    val loadout = skillLoadout
    if (loadout.ownedCount == 0) {
        skillCast.skillBufferMask = 0
        skillCast.skillBufferTime = 0f
        return
    }
    if (skillCast.skillBufferMask != 0) {
        skillCast.skillBufferTime = max(0f, skillCast.skillBufferTime - delta)
        if (skillCast.skillBufferTime <= 0f) {
            skillCast.skillBufferMask = 0
        } else {
            for (slot in 0 until SkillRuntime.SLOTS) {
                val bit = 1 shl slot
                if (skillCast.skillBufferMask and bit == 0) continue
                if (castSkill(slot)) skillCast.skillBufferMask = skillCast.skillBufferMask and bit.inv()
                if (phase != kinetickk.ball.gameplay.nucleus.render.GamePhase.RUNNING) return
            }
            if (skillCast.skillBufferMask == 0) skillCast.skillBufferTime = 0f
        }
    }
    for (slot in 0 until SkillRuntime.SLOTS) {
        val id = loadout.skill(slot) ?: continue
        updatePendingSkillEffect(slot, id)
        if (phase != kinetickk.ball.gameplay.nucleus.render.GamePhase.RUNNING) return
    }
}

private fun MutableGameState.updatePendingSkillEffect(slot: Int, id: SkillId) {
    val runtime = skillRuntime
    val pendingAt = runtime[slot, SkillCell.PENDING_AT]
    when (id) {
        SkillId.VOID_STEP -> if (pendingAt >= 0f && elapsed >= pendingAt) {
            runtime[slot, SkillCell.PENDING_AT] = -1f
            withSkillEffect(slot) { voidRift(slot) }
        }
        SkillId.ARC_HOOK -> {
            val window = runtime[slot, SkillCell.AUX_UNTIL]
            if (window > 0f && (elapsed >= window || enemyById(runtime[slot, SkillCell.TARGET_ID].toInt())?.alive != true)) {
                // Rank III: a hooked target that dies within the window makes the hook ready 2.5 s after the cast.
                if (elapsed < window) {
                    val ready = runtime[slot, SkillCell.CAST_AT] + MIN_SKILL_RECAST_SECONDS
                    if (runtime[slot, SkillCell.READY_AT] > ready) runtime[slot, SkillCell.READY_AT] = ready
                }
                runtime[slot, SkillCell.AUX_UNTIL] = 0f
            }
        }
        else -> Unit
    }
}
