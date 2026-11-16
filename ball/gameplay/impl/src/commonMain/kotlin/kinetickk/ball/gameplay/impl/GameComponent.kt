// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.impl

import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.gameplay.api.GameplayAcceptance
import kinetickk.ball.gameplay.api.GameplayActiveWeaponProjection
import kinetickk.ball.gameplay.api.GameplayBuildSummaryProjection
import kinetickk.ball.gameplay.api.GameplayInteractionPulse
import kinetickk.ball.gameplay.api.GameplayPresentationPort
import kinetickk.ball.gameplay.api.GameplayQuery
import kinetickk.ball.gameplay.api.GameplayRevision
import kinetickk.ball.gameplay.api.GameplayRunPhase
import kinetickk.ball.gameplay.api.GameplayRunStatusProjection
import kinetickk.ball.gameplay.api.GameplayRunPort
import kinetickk.ball.gameplay.api.RunId
import kinetickk.ball.gameplay.api.RunMode
import kinetickk.ball.gameplay.api.GameplaySettingsApplied
import kinetickk.ball.gameplay.api.GameplayRefusal
import kinetickk.ball.gameplay.api.GameplayRunStarted
import kinetickk.ball.gameplay.api.GameplayOverlayPaused
import kinetickk.ball.gameplay.interaction.GameplayInteractionPort
import kinetickk.ball.gameplay.interaction.fx.InteractionFxReducer
import kinetickk.ball.gameplay.interaction.fx.VisualFxProjection
import kinetickk.ball.gameplay.nucleus.MAX_GAMEPLAY_OUTPUTS_PER_DECISION
import kinetickk.ball.gameplay.nucleus.GameplayAcceptedFrame
import kinetickk.ball.gameplay.nucleus.GameplayContext
import kinetickk.ball.gameplay.nucleus.GameplayDecision
import kinetickk.ball.gameplay.nucleus.GameplayNucleus
import kinetickk.ball.gameplay.nucleus.GameplayNucleusPulse
import kinetickk.ball.gameplay.nucleus.GameplayOutput
import kinetickk.ball.gameplay.nucleus.GameplayStartContext
import kinetickk.ball.gameplay.nucleus.GameplayStartInputs
import kinetickk.ball.gameplay.nucleus.GameplayState
import kinetickk.ball.gameplay.nucleus.GameplayTickOutputs
import kinetickk.ball.gameplay.nucleus.GameplayTickResult
import kinetickk.ball.gameplay.nucleus.protocol.GameplayAudioCue
import kinetickk.ball.gameplay.nucleus.protocol.VisualFxCue
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderSnapshot
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.api.ProfileQuery
import kinetickk.ball.profile.api.ProfileRunBootstrapResult
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.toImmutableList
import kinetickk.foundation.dispatch.BoundedCompletionDeque
import kinetickk.foundation.dispatch.InlineAcceptance
import kinetickk.foundation.dispatch.InlineReply
import kinetickk.foundation.dispatch.call
import kinetickk.ball.profile.api.ProfileReadPort
import kinetickk.ball.profile.api.ProfileProgress
import kinetickk.ball.gameplay.api.GameplayRunExited

/** Sole owner, acceptor, atomic publisher, and ordered-output dispatcher for one GameplayRun. */
internal class GameComponent private constructor(
    initialState: GameplayState,
    private val profilePort: ProfileReadPort,
    private val profileProgress: ProfileProgress,
    private val audioExecutor: GameplayAudioExecutor,
    private val seed: Int,
) : GameplayRunPort, GameplayPresentationPort, GameplayInteractionPort {
    private val acceptance = InlineAcceptance(gameplayCompletionDeque<GameplayWorkItem>())

    private val localOutputItem = GameplayWorkItem.Local
    // Published together by [publish]: the owner is single-threaded, so the pair never splits.
    private var committedState: GameplayState = initialState
    private var committedRenderSnapshot: GameplayRenderSnapshot = GameplayNucleus.renderSnapshot(initialState)
    private var interactionFxReducer: InteractionFxReducer? = null
    /** Reused by every local tick; read only between the tick and the next one. */
    private val tickOutputs = GameplayTickOutputs()
    /** A tick that threw may have left the run mid-step; it never simulates again. */
    private var tickFault: Throwable? = null
    /** A pointer move was restamped: the committed model still shows the previous pointer until the next projection. */
    private var pointerProjectionDue = false

    override val instanceId
        get() = committedState.instanceId

    override fun accept(pulse: GameplayInteractionPulse): GameplayAcceptance =
        dispatchLocal(pulse)

    override fun applyPreferences(
        preferences: PlayerPreferences,
        reply: InlineReply<GameplaySettingsApplied, GameplayRefusal>,
    ) = dispatchDirect(reply) { GameplayWorkItem.Settings(GameplayNucleusPulse.ApplyPreferences(preferences), reply) }

    override fun startRun(mode: RunMode, reply: InlineReply<GameplayRunStarted, GameplayRefusal>) =
        dispatchDirect(reply) { GameplayWorkItem.Starting(trustedStartContext(mode), reply) }

    override fun pauseForOverlay(reply: InlineReply<GameplayOverlayPaused, GameplayRefusal>) =
        dispatchDirect(reply, MAX_PROGRESS_REVISIONS_PER_DISPATCH) { GameplayWorkItem.Pausing(reply) }

    override fun exitRun(reply: InlineReply<GameplayRunExited, GameplayRefusal>) =
        dispatchDirect(reply, MAX_PROGRESS_REVISIONS_PER_DISPATCH) { GameplayWorkItem.Exiting(reply) }

    private fun <Result : Any> dispatchDirect(
        reply: InlineReply<Result, GameplayRefusal>,
        requiredRevisions: Long = 1L,
        itemForAcceptedCall: () -> GameplayWorkItem.Direct<Result>,
    ) {
        reply.checkAvailable()
        if (acceptance.isDispatching || !acceptance.isEmpty) {
            reply.refused(GameplayRefusal.Busy)
            return
        }
        if (!hasGameplayRevisionCapacity(committedState.revision, requiredRevisions)) {
            reply.refused(GameplayRefusal.RevisionCapacityExhausted)
            return
        }
        acceptance.dispatch {
            val item = itemForAcceptedCall()
            when (val decision = GameplayNucleus.decide(committedState, item.pulse, item.context)) {
                is GameplayDecision.Rejected -> reply.refused(GameplayRefusal.DecisionRejected(decision.reason))
                is GameplayDecision.Accepted -> dispatchAccepted(decision.frame, item)
            }
        }
    }

    override fun query(query: GameplayQuery.GetRunStatus): GameplayRunStatusProjection =
        GameplayNucleus.query(committedState, query)

    override fun query(query: GameplayQuery.GetActiveWeapon): GameplayActiveWeaponProjection =
        GameplayNucleus.query(committedState, query)

    override fun query(query: GameplayQuery.GetBuildSummary): GameplayBuildSummaryProjection =
        GameplayNucleus.query(committedState, query)

    override fun renderSnapshot(): GameplayRenderSnapshot = committedRenderSnapshot

    override fun visualFxSnapshot(): VisualFxProjection =
        interactionFxReducer?.snapshot() ?: VisualFxProjection.EMPTY

    internal fun stateSnapshot(): GameplayState = committedState

    private fun trustedStartContext(mode: RunMode): GameplayContext {
        val projection = profilePort.query(ProfileQuery.GetRunBootstrap)
        check(projection.instanceId == profilePort.instanceId) {
            "Profile bootstrap projection came from the wrong instance"
        }
        return when (val result = projection.result) {
            is ProfileRunBootstrapResult.Ready -> GameplayContext(
                start = GameplayStartContext.Ready(
                    GameplayStartInputs(
                        content = committedState.content,
                        profile = result.snapshot,
                        seed = seed,
                        mode = mode,
                    ),
                ),
            )
            is ProfileRunBootstrapResult.Unavailable -> GameplayContext(
                start = GameplayStartContext.ProfileUnavailable,
            )
        }
    }

    /**
     * Frames and inputs tick the committed engine in place. Without Profile progress the owner restamps or projects the
     * snapshot, publishes it and executes FX then audio directly; progress takes the ordered output and completion route.
     */
    private fun dispatchLocal(pulse: GameplayInteractionPulse): GameplayAcceptance =
        acceptance.dispatch {
            check(committedState.revision.value <= Long.MAX_VALUE - MAX_LOCAL_REVISIONS_PER_DISPATCH) {
                "Gameplay local revision capacity exhausted before Intent construction"
            }
            tickFault?.let { fault -> throw IllegalStateException("A failed Gameplay tick stopped this run", fault) }
            val before = committedState
            val result = try {
                GameplayNucleus.tick(before, pulse, tickOutputs)
            } catch (failure: Throwable) {
                tickFault = failure
                throw failure
            }
            when (result) {
                is GameplayTickResult.Rejected -> GameplayAcceptance.Rejected(
                    instanceId = before.instanceId,
                    observedRevision = before.revision,
                    reason = result.reason,
                )
                is GameplayTickResult.Accepted -> {
                    val next = result.nextState
                    val renderSnapshot = tickRenderSnapshot(pulse, next)
                    if (tickOutputs.progressUpdate == null) {
                        acceptTick(next, renderSnapshot)
                    } else {
                        dispatchAccepted(tickOutputs.toAcceptedFrame(next), localOutputItem, tickSnapshot = renderSnapshot)
                    }
                    GameplayAcceptance.Accepted(instanceId = next.instanceId, revision = next.revision)
                }
            }
        }

    /** The tick fast path: publish [renderSnapshot], then run every effect and rethrow the first fault. */
    private fun acceptTick(next: GameplayState, renderSnapshot: GameplayRenderSnapshot) {
        publish(next, renderSnapshot)
        val outputs = tickOutputs
        var fault: Throwable? = null
        if (outputs.visualFxCues.isNotEmpty()) fault = runEffect(fault) { applyVisualFx(outputs.visualFxCues) }
        if (outputs.frameAccepted || outputs.audioCues.isNotEmpty()) {
            fault = runEffect(fault) { advanceAudio(outputs.audioCues, outputs.frameAccepted, outputs.frameRealDeltaSeconds) }
        }
        if (outputs.ensuresAudioUnlocked) fault = runEffect(fault) { audioExecutor.ensureUnlocked() }
        fault?.let { throw it }
    }

    /**
     * The tick mutated the committed engine: restamp a render-neutral tick, project any other against the last snapshot.
     * A pointer move changes only the pointer the model shows, which is drawn once per frame: it is restamped too, and
     * the next tick projects it, so a high-rate mouse costs one projection per frame rather than one per event.
     */
    private fun tickRenderSnapshot(pulse: GameplayInteractionPulse, next: GameplayState): GameplayRenderSnapshot {
        if (pulse is GameplayInteractionPulse.PointerMoved) {
            if (!tickOutputs.renderNeutral) pointerProjectionDue = true
        } else if (!tickOutputs.renderNeutral || pointerProjectionDue) {
            pointerProjectionDue = false
            return GameplayNucleus.renderSnapshot(next, committedRenderSnapshot)
        }
        return GameplayNucleus.restampRenderSnapshot(next, committedRenderSnapshot)
    }

    /**
     * The ordered route: accepts [rootFrame] (a tick's carries its [tickSnapshot]), runs each frame's outputs and then
     * every completion they queued. Inline, so the local dispatch path allocates no callbacks.
     */
    @Suppress("NOTHING_TO_INLINE")
    private inline fun dispatchAccepted(
        rootFrame: GameplayAcceptedFrame,
        rootItem: GameplayWorkItem,
        tickSnapshot: GameplayRenderSnapshot? = null,
    ) {
        acceptance.acceptAndDrain(
            rootItem = rootItem,
            rootFrame = rootFrame,
            outputs = { it.outputs },
            acceptFrame = { item, frame ->
                val before = committedState
                preflight(before, item, frame)
                val renderSnapshot =
                    tickSnapshot?.takeIf { item === rootItem } ?: decidedRenderSnapshot(before, frame.nextState)
                publish(frame.nextState, renderSnapshot)
                initializeInteractionFxIfStarted(before, item)
            },
            decideCompletion = { completion ->
                when (val decision = GameplayNucleus.decide(committedState, completion.pulse, completion.context)) {
                    is GameplayDecision.Accepted -> decision.frame
                    is GameplayDecision.Rejected ->
                        error("A trusted Gameplay completion was rejected: ${decision.reason}")
                }
            },
            execute = { output, item -> execute(output, item) },
        )
    }

    /**
     * The owner's own admission of an accepted frame before it is published: a live reply, exactly the one typed result
     * the work item expects, and the progress hand-off. The nucleus types guarantee the rest: [GameplayAcceptedFrame]
     * the output bound and order, [GameplayState] the engine phase, and every snapshot the stamps it was built from.
     */
    private fun preflight(before: GameplayState, item: GameplayWorkItem, frame: GameplayAcceptedFrame) {
        val next = frame.nextState
        if (item is GameplayWorkItem.Direct<*>) item.reply.checkAvailable()
        var profileOutputCount = 0
        var directResultCount = 0
        var outputIndex = 0
        while (outputIndex < frame.outputs.size) {
            when (val output = frame.outputs[outputIndex]) {
                is GameplayOutput.SendProfileCommand -> profileOutputCount++
                is GameplayOutput.RunExited -> {
                    directResultCount++
                    checkNotNull(item.exitReplyOrNull()).checkAvailable()
                    check(output.result.runId == next.instanceId.runId && output.result.revision == next.revision)
                    check(next.phase == GameplayRunPhase.EXITED && !next.progressPending)
                }
                is GameplayOutput.SettingsApplied -> {
                    directResultCount++
                    check(item is GameplayWorkItem.Settings)
                    check(output.result.runId == next.instanceId.runId && output.result.revision == next.revision)
                }
                is GameplayOutput.RunStarted -> {
                    directResultCount++
                    check(item is GameplayWorkItem.Starting)
                    check(output.result.runId == next.instanceId.runId && output.result.revision == next.revision)
                    check(next.phase == GameplayRunPhase.RUNNING)
                }
                is GameplayOutput.OverlayPaused -> {
                    directResultCount++
                    check(item is GameplayWorkItem.Pausing)
                    check(output.result.runId == next.instanceId.runId && output.result.revision == next.revision)
                    check(next.phase == GameplayRunPhase.PAUSED)
                }
                is GameplayOutput.AdvanceAudio,
                is GameplayOutput.EmitVisualFx,
                GameplayOutput.EnsureAudioUnlocked,
                -> Unit
            }
            outputIndex++
        }
        val expectsExitResult = item.exitReplyOrNull() != null && !next.progressPending
        val expectsImmediateResult = item is GameplayWorkItem.Direct<*> && item !is GameplayWorkItem.Exiting
        check(directResultCount == if (expectsImmediateResult || expectsExitResult) 1 else 0) {
            "Gameplay command must accept its one typed result"
        }
        requireGameplayProfileOutputFanoutBound(profileOutputCount)
        if (profileOutputCount > 0) {
            check(!before.progressPending && next.progressPending)
            requireGameplayCompletionCapacity(acceptance.remainingCapacity, requiredCompletions = 1)
        } else if (!before.progressPending) {
            check(!next.progressPending)
        } else if (item is GameplayWorkItem.ProgressCompletion) {
            check(!next.progressPending)
        } else {
            check(next.progressPending)
        }
    }

    /** A decided frame kept the engine (restamp the committed snapshot) or copied it (project with identity reuse). */
    private fun decidedRenderSnapshot(before: GameplayState, next: GameplayState): GameplayRenderSnapshot =
        if (next.engine === before.engine) {
            GameplayNucleus.reuseRenderSnapshot(next, before, committedRenderSnapshot)
        } else {
            pointerProjectionDue = false
            GameplayNucleus.renderSnapshot(next, before, committedRenderSnapshot)
        }

    private fun publish(state: GameplayState, renderSnapshot: GameplayRenderSnapshot) {
        committedState = state
        committedRenderSnapshot = renderSnapshot
    }

    private fun initializeInteractionFxIfStarted(
        before: GameplayState,
        item: GameplayWorkItem,
    ) {
        if (before.phase != GameplayRunPhase.CREATED) return
        if (item.pulse !== GameplayNucleusPulse.StartRun) return
        check(interactionFxReducer == null)
        interactionFxReducer = InteractionFxReducer(
            (checkNotNull(item.context.start) as GameplayStartContext.Ready).inputs.seed,
        )
    }

    private fun execute(output: GameplayOutput, item: GameplayWorkItem) {
        when (output) {
            is GameplayOutput.EmitVisualFx -> applyVisualFx(output.cues)
            is GameplayOutput.SendProfileCommand -> executeProfileCommand(output, item)
            // Music advances only for the frame this tick accepted; lifecycle audio only plays its cues.
            is GameplayOutput.AdvanceAudio ->
                advanceAudio(output.cues, item === localOutputItem && tickOutputs.frameAccepted, output.realDeltaSeconds)
            GameplayOutput.EnsureAudioUnlocked -> audioExecutor.ensureUnlocked()
            is GameplayOutput.RunExited -> checkNotNull(item.exitReplyOrNull()).accepted(output.result)
            is GameplayOutput.SettingsApplied -> when (item) {
                is GameplayWorkItem.Settings -> item.reply.accepted(output.result)
                else -> error("Gameplay settings result has no current typed call")
            }
            is GameplayOutput.RunStarted -> when (item) {
                is GameplayWorkItem.Starting -> item.reply.accepted(output.result)
                else -> error("Gameplay start result has no current typed call")
            }
            is GameplayOutput.OverlayPaused -> when (item) {
                is GameplayWorkItem.Pausing -> item.reply.accepted(output.result)
                else -> error("Gameplay pause result has no current typed call")
            }
        }
    }

    private fun applyVisualFx(cues: ImmutableList<VisualFxCue>) = checkNotNull(interactionFxReducer).apply(cues)

    /** Plays [cues], then advances music by [frameDeltaSeconds] when the frame was accepted. */
    private fun advanceAudio(cues: ImmutableList<GameplayAudioCue>, frameAccepted: Boolean, frameDeltaSeconds: Float) {
        if (cues.isNotEmpty()) audioExecutor.play(cues)
        if (frameAccepted) audioExecutor.tick(frameDeltaSeconds)
    }

    private fun executeProfileCommand(output: GameplayOutput.SendProfileCommand, item: GameplayWorkItem) {
        val exitReply = item.exitReplyOrNull()
        acceptance.call(
            invoke = { reply -> profileProgress.applyGameplayProgress(output.update, reply) },
            acceptedInput = { GameplayWorkItem.ProgressCompletion(GameplayNucleusPulse.ProgressApplied(it), exitReply) },
            refusedInput = { GameplayWorkItem.ProgressCompletion(GameplayNucleusPulse.ProgressRefused(it), exitReply) },
        )
    }

    companion object {
        fun create(
            runId: RunId,
            content: GameplayContentSnapshot,
            profilePort: ProfileReadPort,
            profileProgress: ProfileProgress,
            audioExecutor: GameplayAudioExecutor,
            seed: Int,
        ): GameComponent = GameComponent(
            initialState = GameplayState.initial(runId, content),
            profilePort = profilePort,
            audioExecutor = audioExecutor,
            profileProgress = profileProgress,
            seed = seed,
        )
    }
}

/** Today's ordered outputs of a tick that published Profile progress: FX, the Profile command, then audio. */
private fun GameplayTickOutputs.toAcceptedFrame(next: GameplayState): GameplayAcceptedFrame {
    val outputs = ArrayList<GameplayOutput>(MAX_GAMEPLAY_OUTPUTS_PER_DECISION)
    if (visualFxCues.isNotEmpty()) outputs += GameplayOutput.EmitVisualFx(visualFxCues)
    outputs += GameplayOutput.SendProfileCommand(checkNotNull(progressUpdate))
    if (frameAccepted || audioCues.isNotEmpty()) {
        outputs += GameplayOutput.AdvanceAudio(if (frameAccepted) frameRealDeltaSeconds else 0f, audioCues)
    }
    return GameplayAcceptedFrame(nextState = next, outputs = outputs.toImmutableList())
}

/** Runs [effect]; a fault joins [fault] (the first one wins, later ones are suppressed into it). */
private inline fun runEffect(fault: Throwable?, effect: () -> Unit): Throwable? = try {
    effect()
    fault
} catch (failure: Throwable) {
    if (fault == null) failure else fault.also { if (it !== failure) it.addSuppressed(failure) }
}

internal fun <T> gameplayCompletionDeque(): BoundedCompletionDeque<T> =
    BoundedCompletionDeque(GAMEPLAY_COMPLETION_CAPACITY)

internal fun requireGameplayProfileOutputFanoutBound(profileCommandCount: Int) {
    check(profileCommandCount in 0..1) {
        "A Gameplay decision may issue at most one Profile command"
    }
}

internal fun requireGameplayCompletionCapacity(remainingCapacity: Int, requiredCompletions: Int) {
    check(requiredCompletions >= 0 && remainingCapacity >= requiredCompletions) {
        "Gameplay completion capacity exhausted before acceptance"
    }
}

internal fun hasGameplayRevisionCapacity(
    revision: GameplayRevision,
    requiredRevisions: Long,
): Boolean {
    require(requiredRevisions > 0L)
    return revision.value <= Long.MAX_VALUE - requiredRevisions
}

private sealed interface GameplayWorkItem {
    val pulse: GameplayNucleusPulse
    val context: GameplayContext

    sealed interface Direct<Result : Any> : GameplayWorkItem {
        val reply: InlineReply<Result, GameplayRefusal>
    }

    data object Local : GameplayWorkItem {
        override val pulse: GameplayNucleusPulse = GameplayNucleusPulse.Intent(GameplayInteractionPulse.UserGestureObserved)
        override val context: GameplayContext = GameplayContext.Empty
    }

    data class ProgressCompletion(
        override val pulse: GameplayNucleusPulse,
        val exitReply: InlineReply<GameplayRunExited, GameplayRefusal>?,
    ) : GameplayWorkItem {
        override val context: GameplayContext = GameplayContext.Empty
    }

    data class Exiting(
        override val reply: InlineReply<GameplayRunExited, GameplayRefusal>,
    ) : Direct<GameplayRunExited> {
        override val pulse: GameplayNucleusPulse = GameplayNucleusPulse.ExitRun
        override val context: GameplayContext = GameplayContext.Empty
    }

    data class Settings(
        override val pulse: GameplayNucleusPulse.ApplyPreferences,
        override val reply: InlineReply<GameplaySettingsApplied, GameplayRefusal>,
    ) : Direct<GameplaySettingsApplied> {
        override val context: GameplayContext = GameplayContext.Empty
    }

    data class Starting(
        override val context: GameplayContext,
        override val reply: InlineReply<GameplayRunStarted, GameplayRefusal>,
    ) : Direct<GameplayRunStarted> {
        override val pulse: GameplayNucleusPulse = GameplayNucleusPulse.StartRun
    }

    data class Pausing(
        override val reply: InlineReply<GameplayOverlayPaused, GameplayRefusal>,
    ) : Direct<GameplayOverlayPaused> {
        override val pulse: GameplayNucleusPulse = GameplayNucleusPulse.PauseForOverlay
        override val context: GameplayContext = GameplayContext.Empty
    }

}

private fun GameplayWorkItem.exitReplyOrNull(): InlineReply<GameplayRunExited, GameplayRefusal>? = when (this) {
    is GameplayWorkItem.Exiting -> reply
    is GameplayWorkItem.ProgressCompletion -> exitReply
    else -> null
}

private const val GAMEPLAY_COMPLETION_CAPACITY: Int = 8
private const val MAX_LOCAL_REVISIONS_PER_DISPATCH: Long = 2L
private const val MAX_PROGRESS_REVISIONS_PER_DISPATCH: Long = 2L
