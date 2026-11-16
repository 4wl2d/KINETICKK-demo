// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.nucleus.simulation.characters

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.gameplay.nucleus.model.length
import kinetickk.ball.gameplay.nucleus.simulation.*
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** Ages the Blink event at the start of each fixed step. */
internal fun MutableGameState.ageCharacterStepFlags() {
    val ability = characterRuntime
    if (ability.blinkFlagSteps > 0) ability.blinkFlagSteps--
}

/** Applies the character's base traits; called by the run reset before the Matrix and items. */
internal fun MutableGameState.applyCharacterTraits() {
    val traits = content.coreShape(coreShape).traits
    mass *= traits.massMultiplier
    maxHp += traits.integrityBonus
    dragCoefficient *= traits.dragMultiplier
    dashHeatCost *= traits.dashHeatMultiplier
    critChance += traits.critChanceBonus
    critMultiplier += traits.critDamageBonus
    pickupRadius += traits.pickupRadiusBonus
    coreRadius = MutableGameState.CORE_RADIUS * traits.radiusMultiplier
}

internal fun MutableGameState.updateCharacterRuntime(delta: Float) {
    val ability = characterRuntime
    val currentSpeed = speed
    val moved = length(coreX - previousCoreX, coreY - previousCoreY)
    val heading = atan2(smoothedVelocityY, smoothedVelocityX)
    val turn = atan2(sin(heading - ability.previousHeading), cos(heading - ability.previousHeading))
    for (index in enemies.indices) {
        val enemy = enemies[index]
        if (enemy.characterMarkTime > 0f) enemy.characterMarkTime = max(0f, enemy.characterMarkTime - delta)
    }
    when (coreShape) {
        CoreShape.ORB -> updateOrbit(ability, turn, moved, currentSpeed)
        CoreShape.PRISM -> updateFacets(ability, delta)
        CoreShape.SHARD -> ability.charge = if (elapsed >= ability.punctureReadyAt) 1f else 0f
    }
    if (apexUnlocked) updateApexForm()
    ability.previousSpeed = currentSpeed
    ability.previousHeading = heading
}
