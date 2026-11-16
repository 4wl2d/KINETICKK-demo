// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.nucleus.simulation.weapons

import kinetickk.ball.gameplay.nucleus.simulation.MutableGameState

/** Arc Coil chains to two enemies, plus one each at its first and third mastery forms. */
internal fun MutableGameState.arcCoilTargetCount(): Int =
    2 + (if (hasMasteryForm(1)) 1 else 0) + (if (hasMasteryForm(3)) 1 else 0)
