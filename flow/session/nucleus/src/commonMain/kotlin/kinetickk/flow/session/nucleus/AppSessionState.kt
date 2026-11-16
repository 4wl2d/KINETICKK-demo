// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.flow.session.nucleus

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.gameplay.api.GameplayRunPhase
import kinetickk.ball.gameplay.api.RunId
import kinetickk.ball.gameplay.api.RunMode
import kinetickk.ball.profile.api.LOCAL_PROFILE_INSTANCE_ID
import kinetickk.ball.profile.api.PersistenceStatusProjection
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.api.ProfileBootstrapStatus
import kinetickk.flow.session.api.AppDestination
import kinetickk.flow.session.api.SessionLifecycle
import kinetickk.flow.session.api.isBaseDestination
import kinetickk.flow.session.api.isOverlayDestination

enum class RunStartReason {
    START,
    RESTART,
}

sealed interface SettingsContinuation {
    data object Close : SettingsContinuation
    data class Open(val destination: AppDestination) : SettingsContinuation
}

sealed interface PendingWorkflow {
    data class StartingRun(
        val reason: RunStartReason,
        val runId: RunId,
    ) : PendingWorkflow

    data class PausingForOverlay(
        val destination: AppDestination,
        val runId: RunId,
    ) : PendingWorkflow

    data class ApplyingSettings(
        val preferences: PlayerPreferences,
        val continuation: SettingsContinuation,
        val runId: RunId,
    ) : PendingWorkflow

    data class SelectingCoreShape(
        val shape: CoreShape,
    ) : PendingWorkflow

    data object TogglingMute : PendingWorkflow

    data class PropagatingMute(
        val preferences: PlayerPreferences,
        val runId: RunId,
    ) : PendingWorkflow

    data class ExitingRun(val runId: RunId) : PendingWorkflow

}

/** Complete immutable snapshot owned and published by the singleton AppSession acceptor. */
data class AppSessionState(
    /** Changes with every accepted frame that changes [base] or [overlay]; screens key their per-visit state by it. */
    val routeToken: Long,
    val base: AppDestination,
    val overlay: AppDestination?,
    val activeRunId: RunId?,
    val gameplayPhase: GameplayRunPhase?,
    val pendingWorkflow: PendingWorkflow?,
    val lifecycle: SessionLifecycle,
    val nextRunId: RunId?,
    /** Baby mode or not for every next start; chosen on the bare Home, held in memory only. */
    val runMode: RunMode,
) {
    init {
        require(routeToken >= 0L) { "Session route token must be non-negative" }
        require(base.isBaseDestination()) { "Only Home and Gameplay may be base destinations" }
        require(overlay == null || overlay.isOverlayDestination()) {
            "Only feature routes may be overlays"
        }
        require((activeRunId == null) == (gameplayPhase == null)) {
            "The retained Gameplay phase must belong to the active RunId"
        }
        require(base != AppDestination.Gameplay || activeRunId != null) {
            "Gameplay base requires an active GameplayRun"
        }
    }

    companion object {
        fun initial(persistenceStatus: PersistenceStatusProjection): AppSessionState {
            require(persistenceStatus.instanceId == LOCAL_PROFILE_INSTANCE_ID) {
                "Session bootstrap must observe the local Profile"
            }
            return AppSessionState(
                routeToken = 0L,
                base = AppDestination.Home,
                overlay = null,
                activeRunId = null,
                gameplayPhase = null,
                pendingWorkflow = null,
                lifecycle = persistenceStatus.toSessionLifecycle(),
                nextRunId = RunId(0L),
                runMode = RunMode.STANDARD,
            )
        }
    }
}

internal fun PersistenceStatusProjection.toSessionLifecycle(): SessionLifecycle =
    if (bootstrap == ProfileBootstrapStatus.Ready) {
        SessionLifecycle.READY
    } else {
        SessionLifecycle.BOOTSTRAP_UNAVAILABLE
    }
