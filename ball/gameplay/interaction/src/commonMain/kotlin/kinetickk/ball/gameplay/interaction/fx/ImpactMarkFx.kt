// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.interaction.fx

import kinetickk.ball.gameplay.nucleus.protocol.VisualFxCue

/**
 * The impact marks of [InteractionFxReducer]: one per kill, elite death or ram shockwave, by the cue's kind
 * ([PixelLooks.impactStrip]), at most [InteractionFxLimits.MAX_IMPACT_MARKS]. A mark lives exactly as long as its own
 * strip, whatever its shockwave's life, so the pop, the blast, the dismantled mark and the slash always play to their
 * last frame.
 */
internal class ImpactMarkTrack : WorldFxTrack<ImpactMarkTrack.Mark, ImpactMarkProjection>(InteractionFxLimits.MAX_IMPACT_MARKS) {
    /** Starts the mark of [cue] when its kind has one. */
    fun add(cue: VisualFxCue.ShockwaveAdded): Boolean {
        val strip = PixelLooks.impactStrip(cue.kind, cue.maxRadius) ?: return false
        val life = PixelFxClock.seconds(strip)
        return start(Mark(cue.x, cue.y, cue.kind, cue.maxRadius, life, life))
    }

    override fun publish(entry: Mark): ImpactMarkProjection =
        ImpactMarkProjection(entry.x, entry.y, entry.kind, entry.maxRadius, entry.life, entry.maxLife)

    class Mark(var x: Float, var y: Float, val kind: Int, val maxRadius: Float, life: Float, val maxLife: Float) : Entry(life) {
        override fun shift(shiftX: Float, shiftY: Float) {
            x -= shiftX
            y -= shiftY
        }
    }
}
