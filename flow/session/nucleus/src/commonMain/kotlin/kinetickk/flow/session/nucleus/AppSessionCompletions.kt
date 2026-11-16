// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.flow.session.nucleus

import kinetickk.ball.gameplay.api.GameplayRunExited
import kinetickk.ball.gameplay.api.GameplayRunPhase
import kinetickk.ball.gameplay.api.RunId
import kinetickk.ball.profile.api.ProfileSettingsChanged
import kinetickk.ball.profile.api.ProfileCoreShapeSelected
import kinetickk.flow.session.api.AppDestination
import kinetickk.foundation.collections.immutableListOf

internal fun completeCoreShape(state: AppSessionState, result: ProfileCoreShapeSelected): AppSessionDecision {
    val pending = checkNotNull(state.pendingWorkflow as? PendingWorkflow.SelectingCoreShape)
    check(result.shape == pending.shape) { "Profile selected a different core shape" }
    return accepted(state.copy(pendingWorkflow = null))
}

internal fun rejectCoreShape(state: AppSessionState): AppSessionDecision {
    check(state.pendingWorkflow is PendingWorkflow.SelectingCoreShape)
    return accepted(state.copy(pendingWorkflow = null))
}

internal fun completeMuteProfileResult(
    state: AppSessionState,
    result: ProfileSettingsChanged,
): AppSessionDecision {
    check(state.pendingWorkflow === PendingWorkflow.TogglingMute) {
        "Profile settings result does not match the current Session workflow"
    }
    val preferences = result.preferences
    if (state.activeRunId != null && state.gameplayPhase?.acceptsPreferenceUpdate() == true) {
        return accepted(
            state.copy(
                pendingWorkflow = PendingWorkflow.PropagatingMute(
                    preferences,
                    state.activeRunId,
                ),
            ),
            immutableListOf(
                AppSessionOutput.ApplyPreferences(state.activeRunId, preferences),
                AppSessionOutput.SynchronizeAudioPreferences(preferences),
                AppSessionOutput.PlayMuteFeedback,
            ),
        )
    }
    return accepted(
        state.copy(pendingWorkflow = null),
        immutableListOf(
            AppSessionOutput.SynchronizeAudioPreferences(preferences),
            AppSessionOutput.PlayMuteFeedback,
        ),
    )
}

internal fun completeStart(state: AppSessionState, pulse: AppSessionNucleusPulse.Result.GameplayRunStarted): AppSessionDecision {
    val expectedRun = state.pendingStartRunId()
    check(pulse.result.runId == expectedRun && expectedRun == state.activeRunId) { "Gameplay start result came from another run" }
    return accepted(state.copy(
        routeToken = state.nextRouteToken(),
        base = AppDestination.Gameplay,
        overlay = null,
        gameplayPhase = GameplayRunPhase.RUNNING,
        pendingWorkflow = null,
    ))
}

internal fun completePause(state: AppSessionState, pulse: AppSessionNucleusPulse.Result.GameplayOverlayPaused): AppSessionDecision {
    val pending = checkNotNull(state.pendingWorkflow as? PendingWorkflow.PausingForOverlay)
    check(pulse.result.runId == pending.runId && pending.runId == state.activeRunId) { "Gameplay pause result came from another run" }
    return accepted(state.copy(
        routeToken = state.nextRouteToken(),
        overlay = pending.destination,
        gameplayPhase = GameplayRunPhase.PAUSED,
        pendingWorkflow = null,
    ))
}

internal fun rejectStart(state: AppSessionState, pulse: AppSessionNucleusPulse.Refusal.GameplayStartRefused): AppSessionDecision {
    check(pulse.runId == state.pendingStartRunId() && pulse.runId == state.activeRunId)
    return accepted(state.copy(
        routeToken = if (state.base != AppDestination.Home || state.overlay != null) state.nextRouteToken() else state.routeToken,
        base = AppDestination.Home,
        overlay = null,
        gameplayPhase = GameplayRunPhase.CREATED,
        pendingWorkflow = null,
    ))
}

internal fun rejectPause(state: AppSessionState, pulse: AppSessionNucleusPulse.Refusal.GameplayPauseRefused): AppSessionDecision {
    val pending = checkNotNull(state.pendingWorkflow as? PendingWorkflow.PausingForOverlay)
    check(pulse.runId == pending.runId && pending.runId == state.activeRunId)
    return accepted(state.copy(pendingWorkflow = null))
}

private fun AppSessionState.pendingStartRunId(): RunId = when (val pending = pendingWorkflow) {
    is PendingWorkflow.StartingRun -> pending.runId
    else -> error("Gameplay start result does not match the current Session workflow")
}

internal fun completeGameplaySettings(
    state: AppSessionState,
    pulse: AppSessionNucleusPulse.Result.GameplaySettingsApplied,
): AppSessionDecision = when (val pending = state.pendingWorkflow) {
    is PendingWorkflow.ApplyingSettings -> {
        check(pulse.result.runId == pending.runId && pending.runId == state.activeRunId) {
            "Gameplay settings result came from another run"
        }
        accepted(
            state.copy(
                routeToken = state.nextRouteToken(),
                overlay = pending.continuation.overlayAfterCompletion,
                pendingWorkflow = null,
            ),
            immutableListOf(AppSessionOutput.SynchronizeAudioPreferences(pending.preferences)),
        )
    }
    is PendingWorkflow.PropagatingMute -> {
        check(pulse.result.runId == pending.runId && pending.runId == state.activeRunId) {
            "Gameplay settings result came from another run"
        }
        accepted(state.copy(pendingWorkflow = null))
    }
    else -> error("Gameplay settings result does not match the current Session workflow")
}

internal fun rejectProfileSettings(state: AppSessionState): AppSessionDecision {
    check(state.pendingWorkflow === PendingWorkflow.TogglingMute)
    return accepted(
        state.copy(pendingWorkflow = null),
        immutableListOf(AppSessionOutput.PlayMuteFeedback),
    )
}

internal fun rejectGameplaySettings(
    state: AppSessionState,
    pulse: AppSessionNucleusPulse.Refusal.GameplaySettingsRefused,
): AppSessionDecision {
    val expectedRun = when (val pending = state.pendingWorkflow) {
        is PendingWorkflow.ApplyingSettings -> pending.runId
        is PendingWorkflow.PropagatingMute -> pending.runId
        else -> error("Gameplay settings refusal does not match the current Session workflow")
    }
    check(pulse.runId == expectedRun && expectedRun == state.activeRunId)
    return accepted(state.copy(pendingWorkflow = null))
}

internal fun completeExitResult(
    state: AppSessionState,
    result: GameplayRunExited,
): AppSessionDecision {
    val pending = checkNotNull(state.pendingWorkflow as? PendingWorkflow.ExitingRun)
    check(result.runId == pending.runId && result.runId == state.activeRunId) { "Gameplay exit result came from another run" }
    return when (result.progress) {
        kinetickk.ball.gameplay.api.GameplayExitProgressResult.NoProgress,
        kinetickk.ball.gameplay.api.GameplayExitProgressResult.Applied,
        -> accepted(
            state.copy(
                base = AppDestination.Home,
                routeToken = state.nextRouteToken(),
                overlay = null,
                gameplayPhase = GameplayRunPhase.EXITED,
                pendingWorkflow = null,
            ),
        )
        kinetickk.ball.gameplay.api.GameplayExitProgressResult.NotApplied -> accepted(
            state.copy(
                gameplayPhase = GameplayRunPhase.EXITED,
                pendingWorkflow = null,
            ),
        )
    }
}

internal fun rejectExit(state: AppSessionState, pulse: AppSessionNucleusPulse.Refusal.GameplayExitRefused): AppSessionDecision {
    val pending = checkNotNull(state.pendingWorkflow as? PendingWorkflow.ExitingRun)
    check(pulse.runId == pending.runId && pulse.runId == state.activeRunId)
    return accepted(state.copy(pendingWorkflow = null))
}
