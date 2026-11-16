// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.interaction.fx

import kinetickk.ball.content.api.StructureTemplateId
import kinetickk.ball.gameplay.nucleus.protocol.DungeonMomentKind
import kinetickk.ball.gameplay.nucleus.protocol.VisualFxCue

/**
 * The dungeon moments of [InteractionFxReducer]: one per [VisualFxCue.DungeonMoment], at most
 * [InteractionFxLimits.MAX_DUNGEON_MOMENTS], each living [dungeonMomentSeconds] of its kind; a rebase moves the moment
 * and its focus.
 */
internal class DungeonMomentTrack : WorldFxTrack<DungeonMomentTrack.Moment, DungeonMomentProjection>(
    InteractionFxLimits.MAX_DUNGEON_MOMENTS,
) {
    /** Starts the moment of [cue] with the presentation-only [seed] of its sparks. */
    fun add(cue: VisualFxCue.DungeonMoment, seed: Int): Boolean {
        val life = dungeonMomentSeconds(cue.kind)
        return start(Moment(cue.kind, cue.template, cue.x, cue.y, cue.halfWidth, cue.halfHeight, cue.focusX, cue.focusY, life, life, seed))
    }

    override fun publish(entry: Moment): DungeonMomentProjection = DungeonMomentProjection(
        entry.kind, entry.template, entry.x, entry.y, entry.halfWidth, entry.halfHeight, entry.focusX, entry.focusY,
        entry.life, entry.maxLife, entry.seed,
    )

    class Moment(
        val kind: DungeonMomentKind,
        val template: StructureTemplateId,
        var x: Float,
        var y: Float,
        val halfWidth: Float,
        val halfHeight: Float,
        var focusX: Float,
        var focusY: Float,
        life: Float,
        val maxLife: Float,
        val seed: Int,
    ) : Entry(life) {
        override fun shift(shiftX: Float, shiftY: Float) {
            x -= shiftX
            y -= shiftY
            focusX -= shiftX
            focusY -= shiftY
        }
    }
}

/**
 * How long a dungeon moment plays: the doors' slam and barrier ignition, a wave's breach flare, the guardian's
 * entrance, a room's light burst and rune ignition, and the whole structure's clear.
 */
internal fun dungeonMomentSeconds(kind: DungeonMomentKind): Float = when (kind) {
    DungeonMomentKind.SEALED -> 1f
    DungeonMomentKind.WAVE -> 0.8f
    DungeonMomentKind.GUARDIAN -> 1.6f
    DungeonMomentKind.ROOM_CLEARED -> 1.5f
    DungeonMomentKind.STRUCTURE_CLEARED -> 2.2f
}

/**
 * The world-anchored tracks of [InteractionFxReducer], cleared, rebased and aged together: structures shattering,
 * dungeon moments and impact marks.
 */
internal class WorldFxTracks {
    val disintegrations = StructureDisintegrationTrack()
    val moments = DungeonMomentTrack()
    val impacts = ImpactMarkTrack()

    val isEmpty: Boolean get() = disintegrations.isEmpty && moments.isEmpty && impacts.isEmpty

    fun clear(): Boolean {
        val shards = disintegrations.clear()
        val marks = impacts.clear()
        return moments.clear() || shards || marks
    }

    fun rebase(shiftX: Float, shiftY: Float): Boolean {
        val shards = disintegrations.rebase(shiftX, shiftY)
        val marks = impacts.rebase(shiftX, shiftY)
        return moments.rebase(shiftX, shiftY) || shards || marks
    }

    fun advance(delta: Float): Boolean {
        val shards = disintegrations.advance(delta)
        val marks = impacts.advance(delta)
        return moments.advance(delta) || shards || marks
    }
}
