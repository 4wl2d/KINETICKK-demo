// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.impl

import kinetickk.ball.gameplay.api.GameplayRunHost
import kinetickk.ball.gameplay.interaction.GameplayPresentation

/** Assembly composite for the active GameplayRun host and its presentation. */
interface GameplayCompositionComponent : GameplayRunHost, GameplayPresentation {
    /** Publishes held Profile progress and acquisition history without changing the run's phase. */
    fun flushOnBackground()

    /** Pauses a running run and publishes its remaining Profile progress and acquisition history on close. */
    fun pauseForClose()
}
