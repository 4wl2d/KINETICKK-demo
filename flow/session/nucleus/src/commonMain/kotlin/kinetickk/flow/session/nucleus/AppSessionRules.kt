// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.flow.session.nucleus

import kinetickk.ball.gameplay.api.GameplayRunPhase
import kinetickk.ball.gameplay.api.GameplayRunStatusProjection
import kinetickk.ball.gameplay.api.RunId
import kinetickk.flow.session.api.AppDestination
import kinetickk.flow.session.api.AppShellProjection
import kinetickk.flow.session.api.SessionRejection
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.immutableListOf

internal data class RunReservation(
    val runId: RunId,
    val nextRunId: RunId?,
    val ensure: Boolean = true,
)

/** The route token of an accepted frame that changes the base or the overlay. */
internal fun AppSessionState.nextRouteToken(): Long {
    check(routeToken < Long.MAX_VALUE) { "Session route token exhausted before acceptance" }
    return routeToken + 1L
}

internal fun AppSessionState.reserveRun(): RunReservation? {
    val reserved = nextRunId ?: return null
    val next = if (reserved.value == Long.MAX_VALUE) null else RunId(reserved.value + 1L)
    return RunReservation(reserved, next)
}

internal fun AppSessionState.reusableCreatedRunId(): RunId? =
    activeRunId?.takeIf { gameplayPhase == GameplayRunPhase.CREATED }

internal fun AppSessionState.gameplayStatusIfActive(
    context: AppSessionReads,
): GameplayRunStatusProjection? = if (activeRunId == null) null else context.gameplayStatus()

internal fun GameplayRunPhase.canBeReplaced(): Boolean = when (this) {
    GameplayRunPhase.GAME_OVER,
    GameplayRunPhase.VICTORY,
    GameplayRunPhase.EXITED,
    -> true
    GameplayRunPhase.CREATED,
    GameplayRunPhase.RUNNING,
    GameplayRunPhase.PAUSED,
    GameplayRunPhase.CHOICE,
    -> false
}

internal fun GameplayRunPhase.acceptsPreferenceUpdate(): Boolean = when (this) {
    GameplayRunPhase.RUNNING,
    GameplayRunPhase.PAUSED,
    GameplayRunPhase.CHOICE,
    GameplayRunPhase.GAME_OVER,
    GameplayRunPhase.VICTORY,
    -> true
    GameplayRunPhase.CREATED,
    GameplayRunPhase.EXITED,
    -> false
}

internal fun GameplayRunPhase.canExit(): Boolean = acceptsPreferenceUpdate()

internal val RunStartReason.startUnavailableReason: SessionRejection
    get() = if (this == RunStartReason.RESTART) {
        SessionRejection.RestartUnavailable
    } else {
        SessionRejection.StartUnavailable
    }

internal val SettingsContinuation.overlayAfterCompletion: AppDestination?
    get() = when (this) {
        SettingsContinuation.Close -> null
        is SettingsContinuation.Open -> destination
    }

internal fun AppSessionState.toShellProjection(): AppShellProjection = AppShellProjection(
    routeToken = routeToken,
    base = base,
    overlay = overlay,
    activeRunId = activeRunId,
    lifecycle = lifecycle,
    runMode = runMode,
    participantCommandPending = pendingWorkflow != null,
)

internal fun accepted(
    nextState: AppSessionState,
    outputs: ImmutableList<AppSessionOutput> = immutableListOf(),
): AppSessionDecision = AppSessionDecision.Accepted(
    AppSessionAcceptedFrame(
        nextState = nextState,
        outputs = outputs,
    ),
)

internal fun rejected(reason: SessionRejection): AppSessionDecision =
    AppSessionDecision.Rejected(reason)
