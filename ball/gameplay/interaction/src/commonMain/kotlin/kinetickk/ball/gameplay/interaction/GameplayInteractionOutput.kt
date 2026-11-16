// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.interaction

/** Presentation-owned requests delivered to the AppSession host. */
sealed interface GameplayInteractionOutput {
    data object OpenCodex : GameplayInteractionOutput
    data object OpenSettings : GameplayInteractionOutput
    data object OpenWorkshop : GameplayInteractionOutput
    data object ExitToHome : GameplayInteractionOutput
    data object RestartRun : GameplayInteractionOutput
}
