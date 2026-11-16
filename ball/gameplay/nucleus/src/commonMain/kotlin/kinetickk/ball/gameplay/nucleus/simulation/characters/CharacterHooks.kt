// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.nucleus.simulation.characters

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.gameplay.nucleus.model.*
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.combat.*
import kinetickk.ball.gameplay.nucleus.simulation.elements.*

// ---------------------------------------------------------------------------------------------
// Dash, impact, hit, kill, pickup and turn hooks.

internal fun MutableGameState.beginCharacterDash() {
    val ability = characterRuntime
    ability.dashSequence++
}

/** A Dash's first contact with [enemy]: counts the Dash hit and marks it for the Shard. */
internal fun MutableGameState.onCharacterDashContact(enemy: Enemy) {
    if (dashPhaseTime <= 0f || enemy.lastCharacterDash == characterRuntime.dashSequence || enemy.dead || enemy.hp <= 0f) return
    enemy.lastCharacterDash = characterRuntime.dashSequence
    pendingDashHits++
    if (coreShape == CoreShape.SHARD) enemy.characterMarkTime = MARK_SECONDS
}

/**
 * An impact or a qualifying weapon hit on [enemy]: releases Orbit, and opens Shard marks unless the
 * hit is a [dash] impact.
 */
internal fun MutableGameState.onCharacterPrimaryHit(enemy: Enemy, dash: Boolean = false) {
    val ability = characterRuntime
    when (coreShape) {
        CoreShape.ORB -> if (ability.charge >= 1f) releaseOrbit(coreX, coreY, enemy)
        CoreShape.SHARD -> if (!dash && punctureReady(enemy) && consumeMark(enemy)) puncture(enemy, 1f)
        else -> Unit
    }
}

/** Releases a charged Orbit ring around ([x], [y]); the [struck] enemy carries the release's element. */
private fun MutableGameState.releaseOrbit(x: Float, y: Float, struck: Enemy?) {
    val ability = characterRuntime
    ability.charge = 0f
    ability.turnArc = 0f
    ability.turnDistance = 0f
    val damage = ORBIT_DAMAGE * abilityPower()
    val dominant = build.dominant
    // During Perihelion a release reaches boss-class enemies only when the base rules would have charged it too, and
    // only such a release spends the base rules' charge (their arc and distance start again, as a base release's do).
    val perihelion = apexActive()
    val reachesBosses = !perihelion || skillRuntime.apex(ApexCell.AUX) >= 1f
    if (perihelion && reachesBosses) {
        skillRuntime.setApex(ApexCell.AUX, 0f)
        skillRuntime.setApex(ApexCell.BASE_ARC, 0f)
        skillRuntime.setApex(ApexCell.BASE_DISTANCE, 0f)
    }
    for (index in enemies.indices) {
        val target = enemies[index]
        if (!target.alive || distanceSquared(x, y, target.x, target.y) > square(ORBIT_RADIUS + target.radius)) continue
        if (target.isBossClass && !reachesBosses) continue
        dealCharacterDamage(target, damage)
    }
    if (dominant != null && struck != null && struck.alive) applyStatus(struck, dominant, ORBIT_STACKS, 0)
    shockwave(x, y, 0.35f, ORBIT_RADIUS, 2)
    if (apexActive()) captureHostileShots()
}

/** Perihelion: a release captures the hostile bolts around the Core into the held store (at most the bolt budget). */
private fun MutableGameState.captureHostileShots() {
    val runtime = skillRuntime
    val capacity = apexDefinition.boltBudget.toFloat()
    val reach = apexValue(PERIHELION_CAPTURE_RADIUS)
    for (index in projectiles.indices) {
        if (runtime.apex(ApexCell.HELD) >= capacity || runtime.apex(ApexCell.BOLTS_LEFT) < 1f) return
        val shot = projectiles[index]
        if (!shot.hostile || distanceSquared(coreX, coreY, shot.x, shot.y) > square(reach + shot.radius)) continue
        if (eraseApexShot(shot, shot.x, shot.y)) runtime.setApex(ApexCell.HELD, runtime.apex(ApexCell.HELD) + 1f)
    }
}

/** A charged Orbit releases around the skill's origin. */
internal fun MutableGameState.onCharacterSkillCast(anchorX: Float, anchorY: Float, primary: Enemy?) {
    if (orbitCharged) releaseOrbit(anchorX, anchorY, primary)
}

/** Intact facets amplify Prism impacts. The Apex bonus applies to ordinary enemies. */
internal fun MutableGameState.characterImpactMultiplier(enemy: Enemy? = null): Float =
    if (coreShape != CoreShape.PRISM || characterRuntime.facets < CharacterRuntime.MAX_FACETS) 1f
    else if (enemy != null && apexAmplifies(enemy)) apexValue(MONOLITH_IMPACT_BONUS)
    else FACET_IMPACT_BONUS

/** A heavy impact advances Prism's facet regeneration. */
internal fun MutableGameState.onCharacterImpact(enemy: Enemy, result: DamageResult, impactSpeed: Float) {
    if (coreShape == CoreShape.PRISM && result.amount >= enemy.maxHp * HEAVY_IMPACT_SHARE && impactSpeed >= HEAVY_IMPACT_SPEED) {
        cutFacetTimer()
    }
}

/** Prism recovers a facet on an Elite kill. Impact kills advance its facet regeneration. */
internal fun MutableGameState.onCharacterKill(enemy: Enemy, eliteReward: Boolean) {
    if (coreShape != CoreShape.PRISM) return
    val ability = characterRuntime
    if (eliteReward && ability.facets < CharacterRuntime.MAX_FACETS) ability.facets++
    val lastSource = enemy.status[StatusCell.LAST_SOURCE].toInt() - 1
    if (lastSource == HitSource.IMPACT.ordinal || lastSource == HitSource.DASH.ordinal) cutFacetTimer()
}

/**
 * Prism facets: an incoming hit that facets may cancel spends one and is negated completely. Every hit
 * pauses the regeneration; losing the last facet pushes nearby enemies away. Returns true when negated.
 */
internal fun MutableGameState.spendFacet(amount: Float): Boolean {
    if (coreShape != CoreShape.PRISM) return false
    val ability = characterRuntime
    if (ability.facets <= 0) return false
    ability.facets--
    ability.damagePrevented += amount.toDouble()
    hurtCooldown = 0.14f
    shockwave(coreX, coreY, 0.22f, coreRadius * 3f, 2)
    if (apexActive()) fireRefraction()
    if (ability.facets == 0) {
        pushEnemies(coreX, coreY, FACET_BREAK_RADIUS, FACET_BREAK_PUSH, inward = false)
        shockwave(coreX, coreY, 0.35f, FACET_BREAK_RADIUS, 2)
    }
    return true
}

/**
 * Monolith: a negated hit fires a refraction back, at the boss while strikes remain (within reach, not shifting),
 * else at the nearest ordinary enemy; the activation's bolt budget counts the refractions.
 */
private fun MutableGameState.fireRefraction() {
    val runtime = skillRuntime
    val left = runtime.apex(ApexCell.BOLTS_LEFT)
    if (left < 1f) return
    val target = apexShotTarget(coreX, coreY, APEX_SHOT_RANGE) ?: return
    runtime.setApex(ApexCell.BOLTS_LEFT, left - 1f)
    fireApexShot(coreX, coreY, target, apexValue(MONOLITH_REFRACTION_DAMAGE) * abilityPower())
}

/** True while Monolith runs: its facets take obstacle collisions as well as hits. */
internal fun MutableGameState.monolithTakesCollisions(): Boolean = coreShape == CoreShape.PRISM && apexActive()

/** Seconds per regrown facet: Monolith regrows them fast. */
internal fun MutableGameState.facetRegrowthSeconds(): Float =
    if (apexActive()) apexValue(MONOLITH_REGROWTH_SECONDS) else facetRegenSeconds()

/** Any incoming hit pauses facet regeneration. */
internal fun MutableGameState.pauseFacetRegeneration() {
    if (coreShape == CoreShape.PRISM) characterRuntime.facetPauseUntil = elapsed + FACET_PAUSE_SECONDS
}

private fun MutableGameState.cutFacetTimer() {
    val ability = characterRuntime
    if (ability.facets >= CharacterRuntime.MAX_FACETS || elapsed < ability.facetCutReadyAt) return
    ability.facetCutReadyAt = elapsed + FACET_CUT_INTERVAL
    ability.facetTimer += FACET_CUT_SECONDS
    val regen = facetRegenSeconds()
    if (ability.facetTimer >= regen) {
        ability.facetTimer -= regen
        ability.facets++
    }
}
