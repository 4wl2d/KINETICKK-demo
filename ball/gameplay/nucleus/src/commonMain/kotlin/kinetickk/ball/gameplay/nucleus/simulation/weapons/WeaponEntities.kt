// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.nucleus.simulation.weapons

import kinetickk.ball.gameplay.nucleus.model.*
import kinetickk.ball.gameplay.nucleus.render.*
import kotlin.math.abs
import kinetickk.ball.gameplay.nucleus.simulation.*

/** Moves discs and dissipating mine wells before enemies advance. */
internal fun MutableGameState.advanceWeaponEntities(delta: Float) {
    val entities = weaponRuntime.entities
    for (index in entities.indices) {
        val entity = entities[index]
        if (entity.life <= 0f) continue
        when (entity.kind) {
            WeaponEntityKind.DISC -> advanceDisc(entity, delta)
            WeaponEntityKind.WELL -> advanceWell(entity, delta)
        }
    }
    entities.removeMatchingStable { it.life <= 0f }
}

/** Resolves disc hits and mine well fields after enemies advance. */
internal fun MutableGameState.resolveWeaponEntityHits(delta: Float) {
    val entities = weaponRuntime.entities
    for (index in entities.indices) {
        if (phase != GamePhase.RUNNING) break
        val entity = entities[index]
        if (entity.life <= 0f) continue
        when (entity.kind) {
            WeaponEntityKind.DISC -> discHits(entity)
            WeaponEntityKind.WELL -> wellTick(entity, delta)
        }
    }
    entities.removeMatchingStable { it.life <= 0f }
}
