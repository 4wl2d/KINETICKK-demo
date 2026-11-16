// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.flow.session.nucleus

import kinetickk.ball.gameplay.api.GameplayRunPhase
import kinetickk.ball.gameplay.api.GameplayRunStatusProjection
import kinetickk.ball.gameplay.api.RunMode
import kinetickk.ball.profile.api.ProfileRunBootstrapResult
import kinetickk.flow.session.api.AppDestination
import kinetickk.flow.session.api.SessionInteractionPulse
import kinetickk.flow.session.api.SessionLifecycle
import kinetickk.flow.session.api.SessionRejection
import kinetickk.flow.session.api.SessionShortcut
import kinetickk.flow.session.api.isOverlayDestination
import kinetickk.foundation.collections.immutableListOf

internal fun decideInteraction(
    state: AppSessionState,
    intent: SessionInteractionPulse,
    context: AppSessionReads,
): AppSessionDecision {
    if (state.pendingWorkflow != null) {
        return rejected(SessionRejection.ParticipantCommandPending)
    }
    if (state.lifecycle != SessionLifecycle.READY) {
        return rejected(SessionRejection.BootstrapUnavailable)
    }
    return when (intent) {
        SessionInteractionPulse.StartRunRequested -> startRun(state, context, RunStartReason.START)
        SessionInteractionPulse.RestartRunRequested -> startRun(state, context, RunStartReason.RESTART)
        SessionInteractionPulse.ExitRunRequested -> exitRun(state, context)
        is SessionInteractionPulse.OpenOverlay -> openOverlay(state, intent.destination, context)
        SessionInteractionPulse.CloseOverlay -> closeOverlay(state, context)
        is SessionInteractionPulse.ShortcutObserved -> shortcut(state, intent.shortcut, context)
        SessionInteractionPulse.ToggleMuteRequested -> toggleMute(state)
        is SessionInteractionPulse.SelectCoreShapeRequested -> selectCoreShape(state, intent.shape)
        is SessionInteractionPulse.SelectRunModeRequested -> selectRunMode(state, intent.mode)
    }
}

/** Baby mode is chosen between runs, on the bare Home only; the mode then rides every start until changed. */
private fun selectRunMode(state: AppSessionState, mode: RunMode): AppSessionDecision {
    if (state.base != AppDestination.Home || state.overlay != null) {
        return rejected(SessionRejection.RunModeSelectionUnavailable)
    }
    return accepted(state.copy(runMode = mode))
}

private fun startRun(
    state: AppSessionState,
    context: AppSessionReads,
    reason: RunStartReason,
): AppSessionDecision {
    val bootstrap = context.runBootstrap()
    if (bootstrap.result !is ProfileRunBootstrapResult.Ready || state.overlay != null) {
        return rejected(reason.startUnavailableReason)
    }
    val status = state.gameplayStatusIfActive(context)
    val reservation = when (reason) {
        RunStartReason.START -> {
            if (state.base != AppDestination.Home) {
                return rejected(SessionRejection.StartUnavailable)
            }
            if (status?.phase == GameplayRunPhase.CREATED && !status.progressPending) {
                RunReservation(status.instanceId.runId, state.nextRunId, ensure = false)
            } else {
                if (status != null && !status.phase.canBeReplaced()) {
                    return rejected(SessionRejection.StartUnavailable)
                }
                state.reserveRun() ?: return rejected(SessionRejection.RunIdExhausted)
            }
        }
        RunStartReason.RESTART -> {
            if (
                state.base != AppDestination.Gameplay ||
                status?.phase != GameplayRunPhase.GAME_OVER &&
                status?.phase != GameplayRunPhase.VICTORY
            ) {
                return rejected(SessionRejection.RestartUnavailable)
            }
            state.reserveRun() ?: return rejected(SessionRejection.RunIdExhausted)
        }
    }

    val next = state.copy(
        activeRunId = reservation.runId,
        gameplayPhase = GameplayRunPhase.CREATED,
        pendingWorkflow = PendingWorkflow.StartingRun(
            reason = reason,
            runId = reservation.runId,
        ),
        nextRunId = reservation.nextRunId,
    )
    val send = AppSessionOutput.StartRun(reservation.runId, state.runMode)
    return accepted(
        next,
        if (reservation.ensure) {
            immutableListOf(AppSessionOutput.EnsureGameplayRun(reservation.runId), send)
        } else {
            immutableListOf(send)
        },
    )
}

private fun openOverlay(
    state: AppSessionState,
    destination: AppDestination,
    context: AppSessionReads,
): AppSessionDecision {
    if (!destination.isOverlayDestination()) {
        return rejected(SessionRejection.OverlayUnavailable(destination))
    }
    val status = state.gameplayStatusIfActive(context)
    if (state.overlay == AppDestination.Settings && destination != AppDestination.Settings) {
        return finishSettings(
            state,
            context,
            status,
            SettingsContinuation.Open(destination),
        )
    }
    if (state.base == AppDestination.Gameplay) {
        val phase = checkNotNull(status).phase
        when (phase) {
            GameplayRunPhase.CHOICE -> if (destination != AppDestination.Codex) {
                return rejected(SessionRejection.OverlayUnavailable(destination))
            }
            GameplayRunPhase.GAME_OVER,
            GameplayRunPhase.VICTORY,
            -> return rejected(SessionRejection.OverlayUnavailable(destination))
            GameplayRunPhase.RUNNING -> {
                return accepted(
                    state.copy(
                        gameplayPhase = phase,
                        pendingWorkflow = PendingWorkflow.PausingForOverlay(
                            destination,
                            status.instanceId.runId,
                        ),
                    ),
                    immutableListOf(AppSessionOutput.PauseForOverlay(status.instanceId.runId)),
                )
            }
            GameplayRunPhase.CREATED,
            GameplayRunPhase.PAUSED,
            GameplayRunPhase.EXITED,
            -> Unit
        }
    }

    return accepted(
        state.copy(
            routeToken = if (state.overlay != destination) state.nextRouteToken() else state.routeToken,
            overlay = destination,
            gameplayPhase = status?.phase ?: state.gameplayPhase,
        ),
    )
}

private fun closeOverlay(
    state: AppSessionState,
    context: AppSessionReads,
): AppSessionDecision {
    val overlay = state.overlay ?: return rejected(SessionRejection.CloseUnavailable)
    val status = state.gameplayStatusIfActive(context)
    if (overlay == AppDestination.Settings) {
        return finishSettings(state, context, status, SettingsContinuation.Close)
    }
    return accepted(
        state.copy(
            routeToken = state.nextRouteToken(),
            overlay = null,
            gameplayPhase = status?.phase ?: state.gameplayPhase,
        ),
    )
}

private fun finishSettings(
    state: AppSessionState,
    context: AppSessionReads,
    status: GameplayRunStatusProjection?,
    continuation: SettingsContinuation,
): AppSessionDecision {
    val preferences = context.preferences().preferences
    if (status != null && status.phase.acceptsPreferenceUpdate()) {
        return accepted(
            state.copy(
                gameplayPhase = status.phase,
                pendingWorkflow = PendingWorkflow.ApplyingSettings(
                    preferences,
                    continuation,
                    status.instanceId.runId,
                ),
            ),
            immutableListOf(AppSessionOutput.ApplyPreferences(status.instanceId.runId, preferences)),
        )
    }
    return accepted(
        state.copy(
            routeToken = if (state.overlay != continuation.overlayAfterCompletion) state.nextRouteToken() else state.routeToken,
            overlay = continuation.overlayAfterCompletion,
            gameplayPhase = status?.phase ?: state.gameplayPhase,
        ),
        immutableListOf(AppSessionOutput.SynchronizeAudioPreferences(preferences)),
    )
}

private fun shortcut(
    state: AppSessionState,
    shortcut: SessionShortcut,
    context: AppSessionReads,
): AppSessionDecision = when (shortcut) {
    SessionShortcut.SETTINGS -> openOverlay(state, AppDestination.Settings, context)
    SessionShortcut.WORKSHOP -> if (state.overlay == AppDestination.Workshop) closeOverlay(state, context)
        else openOverlay(state, AppDestination.Workshop, context)
    SessionShortcut.CODEX -> if (state.overlay == AppDestination.Codex) closeOverlay(state, context)
        else openOverlay(state, AppDestination.Codex, context)
    SessionShortcut.MUTE -> toggleMute(state)
    SessionShortcut.BACK -> if (state.overlay != null) {
        closeOverlay(state, context)
    } else {
        rejected(SessionRejection.ShortcutUnavailable)
    }
    SessionShortcut.ENTER -> when {
        state.overlay != null -> closeOverlay(state, context)
        state.base == AppDestination.Home -> startRun(state, context, RunStartReason.START)
        else -> rejected(SessionRejection.ShortcutUnavailable)
    }
}

private fun toggleMute(state: AppSessionState): AppSessionDecision {
    return accepted(
        state.copy(pendingWorkflow = PendingWorkflow.TogglingMute),
        immutableListOf(AppSessionOutput.ToggleMute),
    )
}

private fun selectCoreShape(
    state: AppSessionState,
    shape: kinetickk.ball.content.api.CoreShape,
): AppSessionDecision {
    // Characters change between runs: only over the Home base, bare or under the Workshop.
    if (state.base != AppDestination.Home || (state.overlay != null && state.overlay != AppDestination.Workshop)) {
        return rejected(SessionRejection.CoreShapeSelectionUnavailable)
    }
    return accepted(
        state.copy(pendingWorkflow = PendingWorkflow.SelectingCoreShape(shape)),
        immutableListOf(AppSessionOutput.SelectCoreShape(shape)),
    )
}

private fun exitRun(
    state: AppSessionState,
    context: AppSessionReads,
): AppSessionDecision {
    if (state.base != AppDestination.Gameplay || state.overlay != null) {
        return rejected(SessionRejection.ExitUnavailable)
    }
    val status = checkNotNull(state.gameplayStatusIfActive(context))
    if (!status.phase.canExit()) return rejected(SessionRejection.ExitUnavailable)
    return accepted(
        state.copy(
            gameplayPhase = status.phase,
            pendingWorkflow = PendingWorkflow.ExitingRun(status.instanceId.runId),
        ),
        immutableListOf(AppSessionOutput.ExitRun(status.instanceId.runId)),
    )
}
