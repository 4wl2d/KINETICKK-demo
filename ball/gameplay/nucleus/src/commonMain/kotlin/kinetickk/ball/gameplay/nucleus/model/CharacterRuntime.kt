// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.nucleus.model

/** Owned state for the active Core's mechanic and the last in-step teleport. */
internal class CharacterRuntime {
    /** Mechanic readiness 0..1 (Orbit charge, intact facets or Puncture readiness). */
    var charge: Float = 0f
    var previousSpeed: Float = 0f
    var previousHeading: Float = 0f
    var turnArc: Float = 0f
    var turnDistance: Float = 0f
    var dashSequence: Int = 0

    /** Puncture (Shard): when the next mark may be opened. */
    var punctureReadyAt: Float = 0f

    /** Facets (Prism): intact facets, regeneration, pause and impact cut gate. */
    var facets: Int = MAX_FACETS
    var facetTimer: Float = 0f
    var facetPauseUntil: Float = 0f
    var facetCutReadyAt: Float = 0f

    /** A teleport's endpoints, for the rest of the fixed step that moved the Core. */
    var blinkFlagSteps: Int = 0
    var blinkFromX: Float = 0f
    var blinkFromY: Float = 0f
    var blinkToX: Float = 0f
    var blinkToY: Float = 0f

    var abilityDamage: Double = 0.0
    var damagePrevented: Double = 0.0

    fun fork(): CharacterRuntime = CharacterRuntime().also { copy ->
        copy.charge = charge
        copy.previousSpeed = previousSpeed
        copy.previousHeading = previousHeading
        copy.turnArc = turnArc
        copy.turnDistance = turnDistance
        copy.dashSequence = dashSequence
        copy.punctureReadyAt = punctureReadyAt
        copy.facets = facets
        copy.facetTimer = facetTimer
        copy.facetPauseUntil = facetPauseUntil
        copy.facetCutReadyAt = facetCutReadyAt
        copy.blinkFlagSteps = blinkFlagSteps
        copy.blinkFromX = blinkFromX
        copy.blinkFromY = blinkFromY
        copy.blinkToX = blinkToX
        copy.blinkToY = blinkToY
        copy.abilityDamage = abilityDamage
        copy.damagePrevented = damagePrevented
    }

    /** Every retained fact in a stable order for diagnostics. */
    override fun toString(): String = buildString {
        append("CharacterRuntime(")
        append(charge).append(',').append(previousSpeed).append(',').append(previousHeading).append(',')
        append(turnArc).append(',').append(turnDistance).append(',').append(dashSequence).append(',')
        append(punctureReadyAt).append(',').append(facets).append(',').append(facetTimer).append(',')
        append(facetPauseUntil).append(',').append(facetCutReadyAt).append(',').append(blinkFlagSteps).append(',')
        append(blinkFromX).append(',').append(blinkFromY).append(',').append(blinkToX).append(',').append(blinkToY).append(',')
        append(abilityDamage).append(',').append(damagePrevented).append(')')
    }

    companion object {
        const val MAX_FACETS: Int = 4
    }
}
