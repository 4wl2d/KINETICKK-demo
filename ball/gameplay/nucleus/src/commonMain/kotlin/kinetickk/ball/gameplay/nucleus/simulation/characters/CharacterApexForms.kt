// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.nucleus.simulation.characters

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.gameplay.nucleus.model.*
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.combat.*
import kinetickk.ball.gameplay.nucleus.simulation.elements.isBossClass
import kinetickk.ball.gameplay.nucleus.simulation.enemies.*
import kotlin.math.max
import kotlin.math.min

// ---------------------------------------------------------------------------------------------
// Apex forms (combat-final §5.3). [castApex] starts one, [updateApex] ends it; the step below runs what each form
// adds while it is active (and Perihelion's release after it). Against boss-class enemies every form keeps the base
// numbers and cadence and adds only Apex strikes; bolts are erased only within the activation's budget.

/** The character's Apex form begins (its activation cells are already set). */
internal fun MutableGameState.beginApexForm() {
    val ability = characterRuntime
    val runtime = skillRuntime
    when (coreShape) {
        // The base rules' Orbit charge, arc and distance carry into the activation.
        CoreShape.ORB -> {
            runtime.setApex(ApexCell.AUX, ability.charge)
            runtime.setApex(ApexCell.BASE_ARC, ability.turnArc)
            runtime.setApex(ApexCell.BASE_DISTANCE, ability.turnDistance)
        }
        CoreShape.PRISM -> {
            ability.facets = CharacterRuntime.MAX_FACETS
            ability.facetTimer = 0f
        }
        CoreShape.SHARD -> {
            // The last free Dash: none yet, and boss punctures follow the last puncture's base interval.
            runtime.setApex(ApexCell.AUX, Float.NEGATIVE_INFINITY)
            runtime.setApex(ApexCell.BOSS_BASE_READY_AT, ability.punctureReadyAt)
        }
    }
}

/** The character's Apex form ended. */
internal fun MutableGameState.endApexForm() {
    when (coreShape) {
        CoreShape.ORB -> if (skillRuntime.apex(ApexCell.HELD) >= 1f) {
            // Held shots fly back now, or half a second after a shifting boss returns (never more than 9 s away).
            var releaseAt = elapsed
            for (index in enemies.indices) {
                val enemy = enemies[index]
                if (enemy.isBossClass && enemy.alive && isShifting(enemy)) releaseAt = max(releaseAt, enemy.shiftUntil + PERIHELION_SHIFT_GRACE)
            }
            skillRuntime.setApex(ApexCell.HELD_RELEASE_AT, min(releaseAt, elapsed + PERIHELION_MAX_WAIT))
        }
        CoreShape.PRISM, CoreShape.SHARD -> Unit
    }
}

/** Releases Orb's held shots after Perihelion. */
internal fun MutableGameState.updateApexForm() {
    if (coreShape == CoreShape.ORB) releaseHeldShots()
}

/**
 * Perihelion's release: after the activation, one held shot per interval flies at the boss while strikes remain
 * (each a full-cap strike; a boss that starts shifting meanwhile is waited for until half a second past its shift),
 * else at the nearest ordinary enemy within reach with one dominant stack. With nothing in reach the shots wait.
 */
private fun MutableGameState.releaseHeldShots() {
    val runtime = skillRuntime
    val held = runtime.apex(ApexCell.HELD)
    val releaseAt = runtime.apex(ApexCell.HELD_RELEASE_AT)
    if (held < 1f || apexActive() || elapsed < releaseAt) return
    // A boss that starts shifting mid-release is waited for, as at the end of the activation, while strikes remain.
    if (runtime.apex(ApexCell.STRIKES_LEFT) >= 1f) {
        for (index in enemies.indices) {
            val enemy = enemies[index]
            if (enemy.isBossClass && enemy.alive && isShifting(enemy)) {
                runtime.setApex(ApexCell.HELD_RELEASE_AT, enemy.shiftUntil + PERIHELION_SHIFT_GRACE)
                return
            }
        }
    }
    val target = apexShotTarget(coreX, coreY, PERIHELION_RELEASE_RANGE) ?: return
    runtime.setApex(ApexCell.HELD, held - 1f)
    // On the release schedule (a shot that waited for a target restarts it), so fixed steps add no drift.
    val interval = apexValue(PERIHELION_RELEASE_INTERVAL)
    runtime.setApex(ApexCell.HELD_RELEASE_AT, max(releaseAt + interval, elapsed + interval - MutableGameState.FIXED_STEP))
    fireApexShot(coreX, coreY, target, apexValue(PERIHELION_HELD_DAMAGE) * abilityPower(),
        if (target.isBossClass) null else build.dominant)
}

internal fun MutableGameState.apexFreeDashReady(): Boolean =
    coreShape == CoreShape.SHARD && apexActive() && elapsed >= skillRuntime.apex(ApexCell.AUX) + apexValue(VIVISECT_FREE_DASH_GAP)

/** A Dash during Vivisect cuts into the boss-class enemies in reach of the Core: Apex strikes. */
internal fun MutableGameState.vivisectDash() {
    if (coreShape == CoreShape.SHARD && apexActive()) apexStrikeBossesNear(coreX, coreY)
}

/** Spends Vivisect's free Dash; true when this Dash costs no heat. */
internal fun MutableGameState.spendApexFreeDash(): Boolean {
    if (!apexFreeDashReady()) return false
    skillRuntime.setApex(ApexCell.AUX, elapsed)
    return true
}
