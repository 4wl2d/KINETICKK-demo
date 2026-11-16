// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.flow.session.impl

import kinetickk.ball.gameplay.api.GameplayQuery
import kinetickk.ball.gameplay.api.GameplayRunHost
import kinetickk.ball.gameplay.api.GameplayRunPort
import kinetickk.ball.gameplay.api.GameplayRunStatusProjection
import kinetickk.ball.gameplay.api.RunId
import kinetickk.ball.profile.api.LOCAL_PROFILE_INSTANCE_ID
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.api.PreferencesProjection
import kinetickk.ball.profile.api.ProfileLoadout
import kinetickk.ball.profile.api.ProfileProjection
import kinetickk.ball.profile.api.ProfileQuery
import kinetickk.ball.profile.api.ProfileReadPort
import kinetickk.ball.profile.api.ProfileSettings
import kinetickk.ball.profile.api.RunBootstrapProjection
import kinetickk.flow.session.api.AppSessionPort
import kinetickk.flow.session.api.AppShellProjection
import kinetickk.flow.session.api.SessionAcceptance
import kinetickk.flow.session.api.SessionInteractionPulse
import kinetickk.flow.session.nucleus.AppSessionDecision
import kinetickk.flow.session.nucleus.AppSessionNucleus
import kinetickk.flow.session.nucleus.AppSessionNucleusPulse
import kinetickk.flow.session.nucleus.AppSessionOutput
import kinetickk.flow.session.nucleus.AppSessionReads
import kinetickk.flow.session.nucleus.AppSessionState
import kinetickk.foundation.dispatch.BoundedCompletionDeque
import kinetickk.foundation.dispatch.InlineAcceptance
import kinetickk.foundation.dispatch.call

/** Sole owner, acceptor, publisher, and ordered-output dispatcher for AppSession. */
internal class DefaultAppSessionComponent private constructor(
    initialState: AppSessionState,
    private val profilePort: ProfileReadPort,
    private val profileSettings: ProfileSettings,
    private val profileLoadout: ProfileLoadout,
    private val gameplayRunHost: GameplayRunHost,
    private val updateAudioPreferences: (PlayerPreferences) -> Unit,
    private val playMuteFeedback: () -> Unit,
) : AppSessionPort {
    private val acceptance = InlineAcceptance(sessionCompletionDeque<AppSessionNucleusPulse>())
    private var committedState: AppSessionState = initialState
    private val reads = LiveReads()

    override fun accept(pulse: SessionInteractionPulse): SessionAcceptance =
        acceptance.dispatch {
            val root = AppSessionNucleusPulse.Intent(pulse)
            when (val decision = AppSessionNucleus.decide(committedState, root, reads)) {
                is AppSessionDecision.Rejected -> SessionAcceptance.Rejected(decision.reason)
                is AppSessionDecision.Accepted -> {
                    acceptance.acceptAndDrain(
                        rootItem = root,
                        rootFrame = decision.frame,
                        outputs = { it.outputs },
                        acceptFrame = { _, frame -> committedState = frame.nextState },
                        decideCompletion = { completion ->
                            when (val next = AppSessionNucleus.decide(committedState, completion, reads)) {
                                is AppSessionDecision.Accepted -> next.frame
                                is AppSessionDecision.Rejected -> error(
                                    "A trusted Session completion was rejected: ${next.reason}",
                                )
                            }
                        },
                        execute = { output, _ -> execute(output) },
                    )
                    SessionAcceptance.Accepted
                }
            }
        }

    override fun shell(): AppShellProjection = AppSessionNucleus.shell(committedState)

    internal fun stateSnapshot(): AppSessionState = committedState

    private fun execute(output: AppSessionOutput) {
        when (output) {
            is AppSessionOutput.EnsureGameplayRun -> ensureGameplayRun(output)
            is AppSessionOutput.ExitRun -> executeExit(output.runId)
            AppSessionOutput.ToggleMute -> acceptance.call(
                invoke = profileSettings::toggleMute,
                acceptedInput = { AppSessionNucleusPulse.Result.ProfileSettingsChanged(it) },
                refusedInput = { AppSessionNucleusPulse.Refusal.ProfileSettingsRefused(it) },
            )
            is AppSessionOutput.SelectCoreShape -> acceptance.call(
                invoke = { reply -> profileLoadout.selectCoreShape(output.shape, reply) },
                acceptedInput = { AppSessionNucleusPulse.Result.ProfileCoreShapeSelected(it) },
                refusedInput = { AppSessionNucleusPulse.Refusal.ProfileCoreShapeRefused(it) },
            )
            is AppSessionOutput.ApplyPreferences -> executeGameplaySettings(output)
            is AppSessionOutput.StartRun -> executeStart(output)
            is AppSessionOutput.PauseForOverlay -> executePause(output.runId)
            is AppSessionOutput.SynchronizeAudioPreferences ->
                updateAudioPreferences(output.preferences)
            AppSessionOutput.PlayMuteFeedback -> playMuteFeedback()
        }
    }

    private fun executeGameplaySettings(output: AppSessionOutput.ApplyPreferences) {
        val target = boundRun(output.runId)
        acceptance.call(
            invoke = { reply -> target.applyPreferences(output.preferences, reply) },
            acceptedInput = { AppSessionNucleusPulse.Result.GameplaySettingsApplied(it) },
            refusedInput = { AppSessionNucleusPulse.Refusal.GameplaySettingsRefused(output.runId, it) },
        )
    }

    private fun executeStart(output: AppSessionOutput.StartRun) {
        val runId = output.runId
        val target = boundRun(runId)
        acceptance.call(
            invoke = { reply -> target.startRun(output.mode, reply) },
            acceptedInput = { AppSessionNucleusPulse.Result.GameplayRunStarted(it) },
            refusedInput = { AppSessionNucleusPulse.Refusal.GameplayStartRefused(runId, it) },
        )
    }

    private fun executePause(runId: RunId) {
        val target = boundRun(runId)
        acceptance.call(
            invoke = target::pauseForOverlay,
            acceptedInput = { AppSessionNucleusPulse.Result.GameplayOverlayPaused(it) },
            refusedInput = { AppSessionNucleusPulse.Refusal.GameplayPauseRefused(runId, it) },
        )
    }

    private fun boundRun(runId: RunId): GameplayRunPort =
        checkNotNull(gameplayRunHost.activeRun()) { "Session has no bound GameplayRun" }.also {
            check(it.instanceId.runId == runId) { "Session command targets another GameplayRun" }
        }

    private fun ensureGameplayRun(output: AppSessionOutput.EnsureGameplayRun) {
        val active = gameplayRunHost.activeRun()
        val run = if (active?.instanceId?.runId == output.runId) {
            active
        } else {
            gameplayRunHost.createRun(output.runId)
        }
        check(run.instanceId.runId == output.runId) {
            "GameplayRunHost created a different RunId than Session reserved"
        }
        check(gameplayRunHost.activeRun() === run) {
            "GameplayRunHost did not retain the ensured GameplayRun"
        }
    }

    private fun executeExit(runId: RunId) {
        val run = boundRun(runId)
        acceptance.call(
            invoke = run::exitRun,
            acceptedInput = { AppSessionNucleusPulse.Result.GameplayRunExited(it) },
            refusedInput = { AppSessionNucleusPulse.Refusal.GameplayExitRefused(runId, it) },
        )
    }

    /** The reads a decision asks for, made when it asks and checked against this Profile and the bound run. */
    private inner class LiveReads : AppSessionReads {
        override fun runBootstrap(): RunBootstrapProjection =
            profilePort.query(ProfileQuery.GetRunBootstrap).also(::validateProfileProjection)

        override fun preferences(): PreferencesProjection =
            profilePort.query(ProfileQuery.GetPreferences).also(::validateProfileProjection)

        override fun gameplayStatus(): GameplayRunStatusProjection {
            val run = checkNotNull(gameplayRunHost.activeRun()) {
                "Session retained an active RunId without a bound GameplayRun"
            }
            check(run.instanceId.runId == committedState.activeRunId) {
                "Session active GameplayRun identity mismatch"
            }
            return run.query(GameplayQuery.GetRunStatus).also { projection ->
                check(projection.instanceId == run.instanceId) {
                    "Gameplay status projection came from the wrong run"
                }
            }
        }

        private fun validateProfileProjection(projection: ProfileProjection) {
            check(projection.instanceId == profilePort.instanceId) {
                "Profile projection came from the wrong instance"
            }
        }
    }

    companion object {
        fun create(
            profilePort: ProfileReadPort,
            profileSettings: ProfileSettings,
            profileLoadout: ProfileLoadout,
            gameplayRunHost: GameplayRunHost,
            updateAudioPreferences: (PlayerPreferences) -> Unit,
            playMuteFeedback: () -> Unit,
        ): DefaultAppSessionComponent {
            check(profilePort.instanceId == LOCAL_PROFILE_INSTANCE_ID) {
                "AppSession must bind the application-lifetime local Profile"
            }
            val persistence = profilePort.query(ProfileQuery.GetPersistenceStatus)
            val preferences = profilePort.query(ProfileQuery.GetPreferences)
            check(persistence.instanceId == profilePort.instanceId) {
                "Session construction bootstrap came from the wrong Profile"
            }
            check(preferences.instanceId == profilePort.instanceId) {
                "Session construction preferences came from the wrong Profile"
            }
            check(preferences.revision == persistence.revision) {
                "Session construction Profile projections do not share one revision"
            }
            return DefaultAppSessionComponent(
                initialState = AppSessionState.initial(persistence),
                profilePort = profilePort,
                profileSettings = profileSettings,
                profileLoadout = profileLoadout,
                gameplayRunHost = gameplayRunHost,
                updateAudioPreferences = updateAudioPreferences,
                playMuteFeedback = playMuteFeedback,
            ).also {
                updateAudioPreferences(preferences.preferences)
            }
        }
    }
}

internal fun <T> sessionCompletionDeque(): BoundedCompletionDeque<T> =
    BoundedCompletionDeque(SESSION_COMPLETION_CAPACITY)

private const val SESSION_COMPLETION_CAPACITY: Int = 8
