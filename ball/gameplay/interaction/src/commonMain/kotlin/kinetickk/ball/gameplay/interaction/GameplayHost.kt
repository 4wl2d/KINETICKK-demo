// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.interaction

import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.focus.FocusRequester
import kinetickk.ball.gameplay.api.GameplayAcceptance
import kinetickk.ball.gameplay.api.GameplayInteractionPulse
import kinetickk.ball.gameplay.interaction.canvas.hud.GameplayFpsCounter
import kinetickk.ball.gameplay.interaction.canvas.hud.HudPresentationMemory
import kinetickk.ball.gameplay.interaction.canvas.hud.RunningControlPressLog
import kinetickk.ball.gameplay.interaction.canvas.look.badgeState
import kinetickk.ball.gameplay.interaction.input.GameInteractionValidator
import kinetickk.ball.gameplay.interaction.input.GameplayInput
import kinetickk.ball.gameplay.interaction.input.GameplayKeyInput
import kinetickk.ball.gameplay.interaction.input.GameplayPointerHost
import kinetickk.ball.gameplay.interaction.input.InteractionValidationResult
import kinetickk.ball.gameplay.interaction.input.ValidationFailure
import kinetickk.ball.gameplay.interaction.input.filledSkillSlots
import kinetickk.ball.gameplay.interaction.input.runningControlInput
import kinetickk.ball.gameplay.interaction.layout.RunningControlTarget
import kinetickk.ball.gameplay.interaction.layout.skillSlot
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderSnapshot
import kinetickk.foundation.design.KkApexState

/**
 * What [GameplayContent] keeps for one [component]: the published render model and FX projection, the render clock,
 * the HUD and control memories, the FPS readout, the trial panel's open states, window focus and viewport tracking.
 * Remembered per component; every value here is presentation state of the host.
 */
internal class GameplayHostState(component: GameplayInteractionPort) {
    val focusRequester = FocusRequester()
    val interactionValidator = GameInteractionValidator()

    // The owner republishes the same snapshot instances while nothing changed, so a reference check
    // stands in for a structural comparison of the whole model on every frame.
    val renderModel = mutableStateOf(requireNotNull(component.renderSnapshot().renderModel), referentialEqualityPolicy())
    /** The published snapshot's interpolation alpha: a frame without a fixed step changes only this, not [renderModel]. */
    val interpolationAlpha = mutableFloatStateOf(component.renderSnapshot().interpolationAlpha)
    val visualFxProjection = mutableStateOf(component.visualFxSnapshot(), referentialEqualityPolicy())
    val renderTimeSeconds = mutableFloatStateOf(0f)
    val hudMemory = HudPresentationMemory()
    val fpsCounter = GameplayFpsCounter()
    val fpsReadout = mutableIntStateOf(GameplayFpsCounter.NO_VALUE)
    val controlLog = RunningControlPressLog(fpsReadout)

    // Names in the skill and Apex semantics; a component plays one content snapshot.
    val content = requireNotNull(component.renderSnapshot().renderModel).content
    val rewardUiReady = mutableStateOf(false)

    // The trial panel's (!) opens by keyboard focus, a tap/click (toggle) or a hovering mouse.
    val trialInfoFocused = mutableStateOf(false)
    val trialInfoOpen = mutableStateOf(false)
    val trialInfoHovered = mutableStateOf(false)

    // The composition reads only these facts; the Canvas draws the full model every frame, so a
    // running frame invalidates the draw without recomposing the gameplay host. Unchanged facts keep
    // their instance; the control states recompose only the controls' semantics.
    private var lastFacts: GameplayCompositionFacts? = null
    val compositionFacts: State<GameplayCompositionFacts> = derivedStateOf(structuralEqualityPolicy()) {
        renderModel.value.compositionFacts(lastFacts).also { lastFacts = it }
    }
    val controlStates: State<GameplayControlStates> =
        derivedStateOf(structuralEqualityPolicy()) { renderModel.value.controlStates() }
    val windowFocus = GameplayWindowFocus()
    val viewport = GameplayViewportPixels()

    /** The running run was last told that the host takes no input (see the input availability effect). */
    var inputHeldReported = false

    /** Takes [snapshot]'s model (the same instance while nothing it shows changed) and its frame's alpha. */
    fun publish(snapshot: GameplayRenderSnapshot) {
        renderModel.value = requireNotNull(snapshot.renderModel)
        interpolationAlpha.floatValue = snapshot.interpolationAlpha
    }
}

/**
 * The gameplay host dispatches validated pulses and publishes the owner snapshot.
 */
internal class GameplayHostDispatch(
    private val component: GameplayInteractionPort,
    private val host: GameplayHostState,
) {
    fun dispatch(pulse: GameplayInteractionPulse) {
        val acceptance = try {
            component.accept(pulse)
        } catch (failure: Throwable) {
            // A target can publish atomically and then surface a deferred side-effect fault.
            // Republishing the immutable values is also harmless for a pre-commit fault because
            // Compose suppresses assigning the same instance, so the hot success path needs no pre-query.
            try {
                val committed = component.renderSnapshot()
                host.publish(committed)
                host.visualFxProjection.value = component.visualFxSnapshot()
            } catch (snapshotFailure: Throwable) {
                if (snapshotFailure !== failure) failure.addSuppressed(snapshotFailure)
            }
            throw failure
        }
        when (acceptance) {
            is GameplayAcceptance.Accepted -> {
                host.publish(component.renderSnapshot())
                host.visualFxProjection.value = component.visualFxSnapshot()
            }
            is GameplayAcceptance.Rejected -> Unit
        }
    }

    fun dispatchValidated(result: InteractionValidationResult<GameplayInteractionPulse>) {
        when (result) {
            is InteractionValidationResult.Valid -> dispatch(result.intent)
            is InteractionValidationResult.Invalid -> Unit
        }
    }
}

/**
 * The gameplay host's input routes: host inputs through [dispatchInput], running controls through
 * [pressRunningControl] and key events through [applyKeyInput]; it is also the pointer routes' host. Remembered per
 * pulse route and output, as the composition's routes were.
 */
internal class GameplayHostInputs(
    private val host: GameplayHostState,
    private val pulses: GameplayHostDispatch,
    private val interfaceScaleState: State<Float>,
    private val onOutput: (GameplayInteractionOutput) -> Unit,
) : GameplayPointerHost {
    fun dispatchInput(input: GameplayInput) {
        when (input) {
            is GameplayInput.Action -> pulses.dispatch(input.action)
            GameplayInput.OpenSettings -> onOutput(GameplayInteractionOutput.OpenSettings)
            GameplayInput.OpenWorkshop -> onOutput(GameplayInteractionOutput.OpenWorkshop)
            GameplayInput.OpenCodex -> onOutput(GameplayInteractionOutput.OpenCodex)
            GameplayInput.ExitToHome -> onOutput(GameplayInteractionOutput.ExitToHome)
            GameplayInput.RestartRun -> onOutput(GameplayInteractionOutput.RestartRun)
        }
    }

    /**
     * A running control was pressed (touch, mouse, key or accessibility): the HUD's press flash, the
     * Apex refusal shake when it is cooling, then the control's input. An empty skill slot is a HUD
     * position that does nothing.
     */
    fun pressRunningControl(target: RunningControlTarget) {
        val model = host.renderModel.value
        val skillSlots = model.filledSkillSlots()
        val slot = target.skillSlot
        if (slot >= 0 && skillSlots and (1 shl slot) == 0) return
        host.controlLog.press(target, host.renderTimeSeconds.floatValue)
        if (target == RunningControlTarget.APEX && model.apex.badgeState() == KkApexState.COOLING) {
            host.controlLog.refuseApex(host.renderTimeSeconds.floatValue)
        }
        if (slot >= 0) {
            pulses.dispatchValidated(host.interactionValidator.skillRequested(slot))
            return
        }
        runningControlInput(target, skillSlots)?.let(::dispatchInput)
    }

    /** Applies a key event's [GameplayKeyInput] in its documented order and says whether it is consumed. */
    fun applyKeyInput(key: GameplayKeyInput): Boolean {
        if (key.keyboardPlay) {
            host.controlLog.keyHints = true
            host.controlLog.touchInput = false
        }
        if (key.gesture) pulses.dispatch(GameplayInteractionPulse.UserGestureObserved)
        key.control?.let(::pressRunningControl)
        if (key.choice != GameplayKeyInput.NO_CHOICE) pulses.dispatchValidated(host.interactionValidator.choiceSelected(key.choice))
        key.input?.let(::dispatchInput)
        return key.consumed
    }

    // Read at every pointer event: the latest model, interface scale and trial rules state.
    override val renderModel: GameplayRenderModel get() = host.renderModel.value
    override val interfaceScale: Float get() = interfaceScaleState.value
    override var trialInfoOpen: Boolean
        get() = host.trialInfoOpen.value
        set(value) { host.trialInfoOpen.value = value }
    override var trialInfoHovered: Boolean
        get() = host.trialInfoHovered.value
        set(value) { host.trialInfoHovered.value = value }
    override fun onPulse(pulse: GameplayInteractionPulse) = pulses.dispatch(pulse)
    override fun onInput(input: GameplayInput) = dispatchInput(input)
    override fun onControl(target: RunningControlTarget) = pressRunningControl(target)
    override fun onTouch() {
        host.controlLog.keyHints = false
        host.controlLog.touchInput = true
    }
    override fun onInvalid(failure: ValidationFailure) = Unit
}
