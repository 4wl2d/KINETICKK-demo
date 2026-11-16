// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.nucleus.simulation.weapons

import kinetickk.ball.gameplay.nucleus.model.*
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.characters.*

/** Prism signature: damage and size per intact facet. */
internal fun MutableGameState.morningstarScale(): Float = if (signatureActive()) 1f + 0.1f * intactFacets else 1f

internal fun MutableGameState.morningstarOrbitRadius(): Float =
    (104f + min(70f, softVelocity(speed) * 0.055f)) * if (hasMasteryForm(1)) 1.15f else 1f

internal fun MutableGameState.updateMorningstar(delta: Float) {
    val angularSpeed = (3.2f + softVelocity(speed) / 135f) * continuousWeaponRate()
    morningstarAngle = (morningstarAngle + angularSpeed * delta) % TAU
    val radius = morningstarOrbitRadius()
    previousMorningstarX = morningstarX + blinkShiftX()
    previousMorningstarY = morningstarY + blinkShiftY()
    morningstarX = coreX + cos(morningstarAngle) * radius
    morningstarY = coreY + sin(morningstarAngle) * radius
}
