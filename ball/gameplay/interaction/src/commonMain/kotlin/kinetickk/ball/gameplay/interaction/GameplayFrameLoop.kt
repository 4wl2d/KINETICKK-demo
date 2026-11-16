// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.interaction

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalWindowInfo
import kinetickk.ball.gameplay.api.GameplayInteractionPulse
import kinetickk.ball.gameplay.interaction.canvas.hud.GameplayFpsCounter
import kinetickk.ball.gameplay.interaction.input.GameInteractionValidator
import kinetickk.ball.gameplay.interaction.input.InteractionValidationResult
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.foundation.design.LocalKkFrameRateCap
import kinetickk.foundation.design.kkPresentationDelta
import kinetickk.foundation.design.pacing.KkFrameCallback
import kinetickk.foundation.design.pacing.KkPresentationPacer
import kinetickk.foundation.design.pacing.LocalKkDisplayPacing

/** Focus, the frame loop, pause on focus loss and the viewport's density: the host's effects, in its own scope. */
@Composable
@NonRestartableComposable
internal fun GameplayHostEffects(
    component: GameplayInteractionPort,
    host: GameplayHostState,
    pulses: GameplayHostDispatch,
    inputEnabled: Boolean,
    phase: GamePhase,
    density: Float,
    rewardUiReady: Boolean,
) {
    val interactionValidator = host.interactionValidator
    SideEffect(component, inputEnabled, phase) {
        if (inputEnabled) host.focusRequester.requestFocus()
    }
    // A session workflow landing takes the player's input while the run still runs: the run is told, so Core Freeze
    // holds, and told again once input is back or the run stops. Only those changes are sent (available is the run's
    // default); a paused run with an overlay open hears nothing.
    LaunchedEffect(component, inputEnabled, phase) {
        val held = !inputEnabled && phase == GamePhase.RUNNING
        if (held == host.inputHeldReported) return@LaunchedEffect
        host.inputHeldReported = held
        pulses.dispatch(GameplayInteractionPulse.InputAvailabilityChanged(available = !held))
    }

    // Keep the frame clock alive when an overlay changes the cap or the display so music receives the whole interval.
    val frameRateCap = LocalKkFrameRateCap.current
    val display = LocalKkDisplayPacing.current
    val pacer = remember(component, interactionValidator, rewardUiReady) {
        KkPresentationPacer(frameRateCap, display)
    }
    SideEffect(pacer, frameRateCap, display) {
        pacer.frameRateCap = frameRateCap
        pacer.display = display
    }
    LaunchedEffect(pacer) {
        if (!rewardUiReady) return@LaunchedEffect
        runGameplayFrameLoop(
            validator = interactionValidator,
            pacer = pacer,
            renderModel = host.renderModel,
            renderTimeSeconds = host.renderTimeSeconds,
            fpsCounter = host.fpsCounter,
            fpsReadout = host.fpsReadout,
            dispatch = pulses::dispatch,
        )
    }

    // Pause when unfocused: the window losing focus pauses a running run (an edge, never a level).
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    val windowFocus = host.windowFocus
    LaunchedEffect(component, windowFocused) {
        val model = host.renderModel.value
        val pause = pausesOnFocusLoss(windowFocus.focused, windowFocused, model.phase, model.settings.pauseOnFocusLoss, inputEnabled)
        windowFocus.focused = windowFocused
        if (pause) pulses.dispatch(GameplayInteractionPulse.PauseToggled)
    }

    // The Nucleus receives the viewport in dp, so a density change at an unchanged pixel size (a
    // display scale change) resends it; onSizeChanged covers every pixel size change.
    val viewport = host.viewport
    LaunchedEffect(component, density) {
        if (viewport.width > 0 && viewport.height > 0 && viewport.density != density) {
            viewport.density = density
            pulses.dispatchValidated(
                interactionValidator.viewportChanged(
                    rawWidthPx = viewport.width.toFloat(),
                    rawHeightPx = viewport.height.toFloat(),
                    rawDensity = density,
                ),
            )
        }
    }
}

/**
 * The gameplay host's frame loop runs until its effect is cancelled. [pacer] controls the frame rate (the
 * fixed-step simulation plays the same at any frame rate) and runs each content frame inside the frame callback, so
 * the FrameElapsed it dispatches and the state it writes are drawn in that same frame. Every content frame feeds the
 * Show FPS counter ([renderModel]'s setting), so it counts the frames whose content changed (display frames that hold
 * the last content under V-Sync are not counted), advances [renderTimeSeconds] by the capped presentation delta
 * ([kkPresentationDelta]) and dispatches the validated FrameElapsed.
 */
internal suspend fun runGameplayFrameLoop(
    validator: GameInteractionValidator,
    pacer: KkPresentationPacer,
    renderModel: State<GameplayRenderModel>,
    renderTimeSeconds: MutableFloatState,
    fpsCounter: GameplayFpsCounter,
    fpsReadout: MutableIntState,
    dispatch: (GameplayInteractionPulse) -> Unit,
): Nothing = pacer.run(
    GameplayFrames(
        validator, renderModel, renderTimeSeconds, fpsCounter, fpsReadout, dispatch,
    ),
)

/** One gameplay frame, called by the pacer inside the frame callback with the Compose frame time and its interval. */
private class GameplayFrames(
    private val validator: GameInteractionValidator,
    private val renderModel: State<GameplayRenderModel>,
    private val renderTimeSeconds: MutableFloatState,
    private val fpsCounter: GameplayFpsCounter,
    private val fpsReadout: MutableIntState,
    private val dispatch: (GameplayInteractionPulse) -> Unit,
) : KkFrameCallback {
    override fun onFrame(frameNanos: Long, contentDeltaNanos: Long) {
        val frame = frameNanos
        val delta = contentDeltaNanos / 1_000_000_000f
        val fpsChanged = if (renderModel.value.settings.showFps) fpsCounter.frame(frame) else fpsCounter.hide()
        if (fpsChanged) fpsReadout.intValue = fpsCounter.fps
        when (val result = validator.frameElapsed(delta)) {
            is InteractionValidationResult.Valid -> {
                renderTimeSeconds.floatValue += kkPresentationDelta(result.intent.realDeltaSeconds)
                dispatch(result.intent)
            }
            is InteractionValidationResult.Invalid -> Unit
        }
    }
}
