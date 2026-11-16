// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.nucleus.simulation.characters

import kinetickk.ball.gameplay.nucleus.simulation.*

/** Shifts the last Blink's endpoints after a world rebase. */
internal fun MutableGameState.rebaseCharacterRuntime(shiftX: Float, shiftY: Float) {
    val ability = characterRuntime
    ability.blinkFromX -= shiftX
    ability.blinkFromY -= shiftY
    ability.blinkToX -= shiftX
    ability.blinkToY -= shiftY
}
