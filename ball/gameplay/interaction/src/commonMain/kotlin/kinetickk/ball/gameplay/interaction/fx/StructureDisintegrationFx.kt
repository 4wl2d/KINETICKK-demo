// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.interaction.fx

import kinetickk.ball.content.api.StructureTemplateId
import kinetickk.ball.gameplay.nucleus.protocol.VisualFxCue

/**
 * The structure disintegrations of [InteractionFxReducer]: one per [VisualFxCue.StructureDisintegrated], at most
 * [InteractionFxLimits.MAX_DISINTEGRATIONS], each living [InteractionFxLimits.DISINTEGRATION_SECONDS]; a rebase moves
 * their shards.
 */
internal class StructureDisintegrationTrack :
    WorldFxTrack<StructureDisintegrationTrack.Disintegration, StructureDisintegrationProjection>(InteractionFxLimits.MAX_DISINTEGRATIONS) {
    /** Starts the disintegration of [cue] with the presentation-only [seed] of its shards. */
    fun add(cue: VisualFxCue.StructureDisintegrated, seed: Int): Boolean {
        val life = InteractionFxLimits.DISINTEGRATION_SECONDS
        return start(Disintegration(cue.template, cue.rotation, cue.x, cue.y, life, life, seed))
    }

    override fun publish(entry: Disintegration): StructureDisintegrationProjection =
        StructureDisintegrationProjection(entry.template, entry.rotation, entry.x, entry.y, entry.life, entry.maxLife, entry.seed)

    class Disintegration(
        val template: StructureTemplateId,
        val rotation: Int,
        var x: Float,
        var y: Float,
        life: Float,
        val maxLife: Float,
        val seed: Int,
    ) : Entry(life) {
        override fun shift(shiftX: Float, shiftY: Float) {
            x -= shiftX
            y -= shiftY
        }
    }
}
