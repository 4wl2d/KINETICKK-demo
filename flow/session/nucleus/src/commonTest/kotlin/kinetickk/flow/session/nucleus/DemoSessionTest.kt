// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.flow.session.nucleus

import kinetickk.ball.gameplay.api.GameplayInstanceId
import kinetickk.ball.gameplay.api.GameplayRevision
import kinetickk.ball.gameplay.api.GameplayRunPhase
import kinetickk.ball.gameplay.api.GameplayRunStarted
import kinetickk.ball.gameplay.api.GameplayRunStatusProjection
import kinetickk.ball.gameplay.api.RunId
import kinetickk.ball.gameplay.api.RunMode
import kinetickk.ball.profile.api.GameplayProfileSnapshot
import kinetickk.ball.profile.api.LOCAL_PROFILE_INSTANCE_ID
import kinetickk.ball.profile.api.MatrixProgress
import kinetickk.ball.profile.api.PersistenceStatusProjection
import kinetickk.ball.profile.api.PlayerCollection
import kinetickk.ball.profile.api.PlayerEconomy
import kinetickk.ball.profile.api.PlayerLoadout
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.api.ProfileBootstrapStatus
import kinetickk.ball.profile.api.ProfilePersistenceStatus
import kinetickk.ball.profile.api.ProfileRevision
import kinetickk.ball.profile.api.ProfileRunBootstrapResult
import kinetickk.ball.profile.api.RunBootstrapProjection
import kinetickk.flow.session.api.AppDestination
import kinetickk.flow.session.api.SessionInteractionPulse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DemoSessionTest {
    @Test
    fun standardRunCanRestartAfterEveryVictory() {
        val revision = ProfileRevision(1)
        val bootstrap = RunBootstrapProjection(
            LOCAL_PROFILE_INSTANCE_ID,
            revision,
            ProfileRunBootstrapResult.Ready(
                GameplayProfileSnapshot(PlayerPreferences(), PlayerEconomy(), PlayerLoadout(), MatrixProgress(), PlayerCollection()),
            ),
        )
        val initial = AppSessionState.initial(
            PersistenceStatusProjection(
                LOCAL_PROFILE_INSTANCE_ID,
                revision,
                ProfileBootstrapStatus.Ready,
                ProfilePersistenceStatus.Persisted(revision),
            ),
        )
        val first = AppSessionNucleus.decide(
            initial,
            AppSessionNucleusPulse.Intent(SessionInteractionPulse.StartRunRequested),
            AppSessionContext(runBootstrap = bootstrap),
        ).accepted()
        var running = completeStart(first, RunId(0))

        for (run in 0L until 4L) {
            val next = AppSessionNucleus.decide(
                running,
                AppSessionNucleusPulse.Intent(SessionInteractionPulse.RestartRunRequested),
                AppSessionContext(
                    runBootstrap = bootstrap,
                    gameplayStatus = GameplayRunStatusProjection(
                        GameplayInstanceId(RunId(run)), GameplayRevision(2), GameplayRunPhase.VICTORY, progressPending = false,
                    ),
                ),
            ).accepted()
            val nextRun = RunId(run + 1)
            assertEquals(
                listOf(AppSessionOutput.EnsureGameplayRun(nextRun), AppSessionOutput.StartRun(nextRun, RunMode.STANDARD)),
                next.outputs.toList(),
            )
            running = completeStart(next, nextRun)
        }
    }

    private fun completeStart(frame: AppSessionAcceptedFrame, runId: RunId): AppSessionState {
        val running = AppSessionNucleus.decide(
            frame.nextState,
            AppSessionNucleusPulse.Result.GameplayRunStarted(GameplayRunStarted(runId, GameplayRevision(1))),
        ).accepted().nextState
        assertEquals(AppDestination.Gameplay, running.base)
        assertEquals(GameplayRunPhase.RUNNING, running.gameplayPhase)
        assertEquals(runId, running.activeRunId)
        return running
    }

    private fun AppSessionDecision.accepted(): AppSessionAcceptedFrame = assertIs<AppSessionDecision.Accepted>(this).frame
}
