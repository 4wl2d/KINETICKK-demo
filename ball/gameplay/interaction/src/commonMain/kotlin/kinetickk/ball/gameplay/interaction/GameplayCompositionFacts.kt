// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.interaction

import kinetickk.ball.gameplay.interaction.layout.hudHeight
import kinetickk.ball.gameplay.interaction.layout.hudWidth
import kinetickk.ball.gameplay.interaction.layout.RunningControlMask
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kotlin.jvm.JvmInline

/**
 * What the composed gameplay host lays out from. A running frame changes positions every frame but
 * these facts rarely, so the host recomposes only when one of them changes. The controls' momentary
 * states (Dash ready, a skill running) flip several times a second in combat: they are
 * [GameplayControlStates], which only the controls' semantics read.
 */
internal data class GameplayCompositionFacts(
    val phase: GamePhase,
    val hudWidth: Float,
    val hudHeight: Float,
    val uiScale: Float,
    /** The viewport's raw density (the HUD scale at another interface scale, [hudScale]). */
    val viewportDensity: Float,
    val textScale: Float,
    val choiceCount: Int,
    val choicesCanReroll: Boolean,
    /** [RunningControlMask]: whether the skill row and the Apex control are placed. */
    val controlMask: Int,
    val coreShape: CoreShape,
    /** Per skill slot: the skill ordinal (−1 empty). */
    val skill0: Int,
    val skill1: Int,
    val skill2: Int,
) {
    fun skill(slot: Int): Int = when (slot) {
        0 -> skill0
        1 -> skill1
        else -> skill2
    }
}

/**
 * This model's facts: [previous] itself when they are unchanged, so a running frame allocates nothing (each fact is
 * compared, then built, in the same order).
 */
internal fun GameplayRenderModel.compositionFacts(previous: GameplayCompositionFacts? = null): GameplayCompositionFacts {
    val slots = skillBar.slots
    val controlMask = RunningControlMask.of(this)
    if (previous != null && previous.phase == phase && previous.hudWidth == hudWidth && previous.hudHeight == hudHeight &&
        previous.uiScale == uiScale && previous.viewportDensity == viewportDensity && previous.textScale == settings.textScale &&
        previous.choiceCount == choices.size && previous.choicesCanReroll == choicesCanReroll &&
        previous.controlMask == controlMask && previous.coreShape == coreShape &&
        previous.skill0 == slots[0].skill && previous.skill1 == slots[1].skill && previous.skill2 == slots[2].skill
    ) {
        return previous
    }
    return GameplayCompositionFacts(
        phase = phase,
        hudWidth = hudWidth,
        hudHeight = hudHeight,
        uiScale = uiScale,
        viewportDensity = viewportDensity,
        textScale = settings.textScale,
        choiceCount = choices.size,
        choicesCanReroll = choicesCanReroll,
        controlMask = controlMask,
        coreShape = coreShape,
        skill0 = slots[0].skill,
        skill1 = slots[1].skill,
        skill2 = slots[2].skill,
    )
}

/** The running controls' momentary states, one bit each: braking, Dash, each skill slot and the Apex. */
@JvmInline
internal value class GameplayControlStates(private val bits: Int) {
    val braking: Boolean get() = bits and BRAKING != 0
    val overheated: Boolean get() = bits and OVERHEATED != 0
    val dashReady: Boolean get() = bits and DASH_READY != 0
    val apexReady: Boolean get() = bits and APEX_READY != 0
    val apexActive: Boolean get() = bits and APEX_ACTIVE != 0

    fun skillReady(slot: Int): Boolean = bits and (SKILL_READY shl slot) != 0

    fun skillActive(slot: Int): Boolean = bits and (SKILL_ACTIVE shl slot) != 0

    companion object {
        const val BRAKING = 1
        const val OVERHEATED = 1 shl 1
        const val DASH_READY = 1 shl 2
        const val APEX_READY = 1 shl 3
        const val APEX_ACTIVE = 1 shl 4

        /** Slot 0's bit; slots 1 and 2 follow it. */
        const val SKILL_READY = 1 shl 5
        const val SKILL_ACTIVE = 1 shl 8
    }
}

internal fun GameplayRenderModel.controlStates(): GameplayControlStates {
    val slots = skillBar.slots
    var bits = 0
    if (braking) bits = bits or GameplayControlStates.BRAKING
    if (overheated) bits = bits or GameplayControlStates.OVERHEATED
    if (dashReady) bits = bits or GameplayControlStates.DASH_READY
    if (apex.ready) bits = bits or GameplayControlStates.APEX_READY
    if (apex.active01 > 0f) bits = bits or GameplayControlStates.APEX_ACTIVE
    for (slot in 0 until 3) {
        if (slots[slot].ready) bits = bits or (GameplayControlStates.SKILL_READY shl slot)
        if (slots[slot].active01 > 0f) bits = bits or (GameplayControlStates.SKILL_ACTIVE shl slot)
    }
    return GameplayControlStates(bits)
}

internal class GameplayLayoutDimensions(
    val width: Float,
    val height: Float,
    val scale: Float,
)
