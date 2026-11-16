// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.foundation.collections.toImmutableList
import kinetickk.ball.gameplay.nucleus.protocol.GameplayAudioCue
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.immutableListOf
import kinetickk.ball.gameplay.nucleus.protocol.VisualFxCue
import kinetickk.ball.gameplay.nucleus.protocol.LinkStyleCode
import kinetickk.ball.gameplay.nucleus.protocol.ShockwaveKind

internal fun MutableGameState.takeSoundCues(): ImmutableList<GameplayAudioCue> {
    if (soundCues.isEmpty()) return immutableListOf()
    val result = soundCues.toImmutableList()
    soundCues.clear()
    return result
}

internal fun MutableGameState.takeVisualFxCues(): ImmutableList<VisualFxCue> = visualFxCues.drain()

/** A cue plays at most once per reduction: one already pending is not queued again. */
internal fun MutableGameState.emitSound(cue: GameplayAudioCue) {
    if (cue !in soundCues && soundCues.size < MutableGameState.MAX_GAMEPLAY_SOUND_CUES) soundCues += cue
}

internal fun MutableGameState.emitVisualFx(cue: VisualFxCue) {
    visualFxCues.record(cue)
}

/** The step's advance of the decorative FX by [delta]; the fixed step shares one immutable cue instead of a new one. */
internal fun MutableGameState.emitEffectsAdvanced(delta: Float) {
    emitVisualFx(if (delta == MutableGameState.FIXED_STEP) FixedStepEffectsAdvanced else VisualFxCue.EffectsAdvanced(delta))
}

/** The step's advance of the weapon arcs by [delta]; the fixed step shares one immutable cue instead of a new one. */
internal fun MutableGameState.emitWeaponArcsAdvanced(delta: Float) {
    emitVisualFx(if (delta == MutableGameState.FIXED_STEP) FixedStepWeaponArcsAdvanced else VisualFxCue.WeaponArcsAdvanced(delta))
}

private val FixedStepEffectsAdvanced = VisualFxCue.EffectsAdvanced(MutableGameState.FIXED_STEP)
private val FixedStepWeaponArcsAdvanced = VisualFxCue.WeaponArcsAdvanced(MutableGameState.FIXED_STEP)

internal fun MutableGameState.burst(x: Float, y: Float, requestedCount: Int, colorIndex: Int) {
    emitVisualFx(
        VisualFxCue.Burst(
            x = x,
            y = y,
            requestedCount = requestedCount,
            colorIndex = colorIndex,
            density = settings.particleDensity,
        ),
    )
}

internal fun MutableGameState.directionalBurst(
    x: Float,
    y: Float,
    requestedCount: Int,
    colorIndex: Int,
    directionX: Float,
    directionY: Float,
) {
    emitVisualFx(
        VisualFxCue.DirectionalBurst(
            x = x,
            y = y,
            requestedCount = requestedCount,
            colorIndex = colorIndex,
            directionX = directionX,
            directionY = directionY,
            density = settings.particleDensity,
        ),
    )
}

/** A shockwave cue; [kind] ([ShockwaveKind]) says what it is, for the presentation only. */
internal fun MutableGameState.shockwave(x: Float, y: Float, life: Float, maxRadius: Float, colorIndex: Int, kind: Int = ShockwaveKind.RING) {
    emitVisualFx(
        VisualFxCue.ShockwaveAdded(
            x = x,
            y = y,
            life = life,
            maxRadius = maxRadius,
            colorIndex = colorIndex,
            kind = kind,
        ),
    )
}

internal fun MutableGameState.addWeaponArc(
    fromX: Float,
    fromY: Float,
    toX: Float,
    toY: Float,
    life: Float = 0.14f,
    style: Int = LinkStyleCode.WEAPON,
) {
    emitVisualFx(
        VisualFxCue.WeaponArcAdded(
            fromX = fromX,
            fromY = fromY,
            toX = toX,
            toY = toY,
            life = life,
            style = style,
        ),
    )
}
