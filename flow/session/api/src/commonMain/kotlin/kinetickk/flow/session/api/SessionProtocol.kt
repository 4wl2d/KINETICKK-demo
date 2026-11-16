// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.flow.session.api

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.gameplay.api.RunMode

/** Closed Interaction intent inventory of the singleton AppSession Flow. */
sealed interface SessionInteractionPulse {
    data object StartRunRequested : SessionInteractionPulse
    data object RestartRunRequested : SessionInteractionPulse
    data object ExitRunRequested : SessionInteractionPulse
    data class OpenOverlay(val destination: AppDestination) : SessionInteractionPulse
    data object CloseOverlay : SessionInteractionPulse
    data class ShortcutObserved(val shortcut: SessionShortcut) : SessionInteractionPulse
    data object ToggleMuteRequested : SessionInteractionPulse
    data class SelectCoreShapeRequested(val shape: CoreShape) : SessionInteractionPulse

    /** Selects the next runs' mode; accepted only over a bare Home (no overlay, no pending workflow). */
    data class SelectRunModeRequested(val mode: RunMode) : SessionInteractionPulse
}

sealed interface SessionRejection {
    data object RunIdExhausted : SessionRejection
    data object ParticipantCommandPending : SessionRejection
    data object BootstrapUnavailable : SessionRejection
    data object StartUnavailable : SessionRejection
    data object RestartUnavailable : SessionRejection
    data object ExitUnavailable : SessionRejection
    data object CloseUnavailable : SessionRejection
    data object ShortcutUnavailable : SessionRejection
    data object CoreShapeSelectionUnavailable : SessionRejection
    data object RunModeSelectionUnavailable : SessionRejection

    data class OverlayUnavailable(
        val destination: AppDestination,
    ) : SessionRejection
}

sealed interface SessionAcceptance {
    data object Accepted : SessionAcceptance

    data class Rejected(val reason: SessionRejection) : SessionAcceptance
}
