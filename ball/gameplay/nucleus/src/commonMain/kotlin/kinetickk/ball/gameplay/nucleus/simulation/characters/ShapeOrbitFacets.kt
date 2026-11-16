// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.nucleus.simulation.characters

import kinetickk.ball.gameplay.nucleus.model.*
import kinetickk.ball.gameplay.nucleus.simulation.*
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min

// ---------------------------------------------------------------------------------------------
// Mechanics.

internal fun MutableGameState.updateOrbit(ability: CharacterRuntime, turn: Float, moved: Float, currentSpeed: Float) {
    // Perihelion: Orbit charges on half the arc, over a shorter distance and at a lower speed.
    val perihelion = apexActive()
    val minimumSpeed = if (perihelion) PERIHELION_MIN_SPEED else ORBIT_MIN_SPEED
    if (!braking && currentSpeed >= minimumSpeed && ability.previousSpeed >= minimumSpeed && moved > 0f) {
        val sameDirection = ability.turnArc == 0f || turn * ability.turnArc >= 0f
        val arc = if (sameDirection) ability.turnArc + turn else turn
        val distance = if (sameDirection) ability.turnDistance + moved else moved
        val requiredArc = if (perihelion) apexValue(PERIHELION_ARC) else orbitArc()
        val requiredDistance = if (perihelion) PERIHELION_MIN_DISTANCE else ORBIT_MIN_DISTANCE
        val earned = if (abs(arc) >= requiredArc && distance >= requiredDistance) abs(turn) / PI.toFloat() else 0f
        ability.turnArc = arc.coerceIn(-TAU, TAU)
        ability.turnDistance = min(2_000f, distance)
        ability.charge = min(1f, ability.charge + earned)
    } else {
        ability.turnArc = 0f
        ability.turnDistance = 0f
    }
    if (perihelion) {
        updateBaseOrbitCharge(ability, turn, moved, currentSpeed)
        // Against boss-class enemies Perihelion keeps the base cadence: once the base rules would have charged Orbit,
        // it is charged, so the next hit releases it where a base release would land.
        if (skillRuntime.apex(ApexCell.AUX) >= 1f) ability.charge = 1f
    }
}

/**
 * During Perihelion, the charge the base rules would have earned ([ApexCell.AUX]; only a release it completes reaches
 * boss-class enemies), on the base arc, distance and speed and on its own arc ([ApexCell.BASE_ARC],
 * [ApexCell.BASE_DISTANCE]): a release that skips bosses leaves it running, as no base release happened.
 */
private fun MutableGameState.updateBaseOrbitCharge(ability: CharacterRuntime, turn: Float, moved: Float, currentSpeed: Float) {
    val runtime = skillRuntime
    if (!braking && currentSpeed >= ORBIT_MIN_SPEED && ability.previousSpeed >= ORBIT_MIN_SPEED && moved > 0f) {
        val previousArc = runtime.apex(ApexCell.BASE_ARC)
        val sameDirection = previousArc == 0f || turn * previousArc >= 0f
        val arc = if (sameDirection) previousArc + turn else turn
        val distance = if (sameDirection) runtime.apex(ApexCell.BASE_DISTANCE) + moved else moved
        val earned = if (abs(arc) >= orbitArc() && distance >= ORBIT_MIN_DISTANCE) abs(turn) / PI.toFloat() else 0f
        runtime.setApex(ApexCell.AUX, min(1f, runtime.apex(ApexCell.AUX) + earned))
        runtime.setApex(ApexCell.BASE_ARC, arc.coerceIn(-TAU, TAU))
        runtime.setApex(ApexCell.BASE_DISTANCE, min(2_000f, distance))
    } else {
        runtime.setApex(ApexCell.BASE_ARC, 0f)
        runtime.setApex(ApexCell.BASE_DISTANCE, 0f)
    }
}

internal fun MutableGameState.updateFacets(ability: CharacterRuntime, delta: Float) {
    // Monolith: facets regrow fast and a hit no longer pauses them.
    val monolith = apexActive()
    val regrowth = facetRegrowthSeconds()
    if (ability.facets < CharacterRuntime.MAX_FACETS) {
        if (monolith || elapsed >= ability.facetPauseUntil) ability.facetTimer += delta
        if (ability.facetTimer >= regrowth) {
            ability.facetTimer -= regrowth
            ability.facets++
        }
    }
    if (ability.facets >= CharacterRuntime.MAX_FACETS) ability.facetTimer = 0f
    ability.charge = ability.facets / CharacterRuntime.MAX_FACETS.toFloat()
}
