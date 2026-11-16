// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.nucleus.simulation.variety

import kinetickk.ball.content.api.RunMutator
import kinetickk.ball.gameplay.nucleus.model.Enemy
import kinetickk.ball.gameplay.nucleus.model.distanceSquared
import kinetickk.ball.gameplay.nucleus.simulation.elements.isEliteClass
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.render.VolatileBurstProjection
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.combat.*
import kinetickk.ball.gameplay.nucleus.simulation.dimensions.hostileElapsed
import kinetickk.foundation.collections.toImmutableList
import kotlin.math.min

/** Delay between an ordinary enemy's death and its Volatile Remains burst. */
internal const val VOLATILE_BURST_DELAY_SECONDS: Float = 0.35f
/** Radius of a Volatile Remains burst (and its telegraph ring). */
internal const val VOLATILE_BURST_RADIUS: Float = 70f
/** Base burst damage; it grows with the run like every hostile attack. */
internal const val VOLATILE_BURST_DAMAGE: Float = 6f
/** A Core at least this fast slips out of a burst. */
internal const val VOLATILE_ESCAPE_SPEED: Float = 400f

/** Blackout: a Data pickup the magnet has not caught fades after this long. */
internal const val BLACKOUT_DATA_LIFE_SECONDS: Float = 10f

/** Ordinary enemies are neither Elites nor boss class. */
internal val Enemy.isOrdinary: Boolean get() = !isEliteClass

/** When Volatile Remains is active, an ordinary enemy's death arms a burst [VOLATILE_BURST_DELAY_SECONDS] later inside a telegraph ring. */
internal fun MutableGameState.queueVolatileRemains(enemy: Enemy) {
    if (!runMutatorActive(RunMutator.VOLATILE_REMAINS) || !enemy.isOrdinary) return
    queueVolatileBurst(enemy.x, enemy.y, VOLATILE_BURST_RADIUS, VOLATILE_BURST_DELAY_SECONDS)
}

/**
 * Arms a burst of [radius] at ([x], [y]) that detonates [delay] seconds from now under the Volatile Remains law
 * (Remains, fire traps and Volatile Elites alike), on the hostile clock ([hostileElapsed]: the Archive's Desync slows a
 * fuse with the rest of hostile time). None is armed while [MutableGameState.MAX_VOLATILE_BURSTS] bursts wait.
 */
internal fun MutableGameState.queueVolatileBurst(x: Float, y: Float, radius: Float, delay: Float) {
    if (volatileBursts.size >= MutableGameState.MAX_VOLATILE_BURSTS) return
    val now = hostileElapsed()
    volatileBursts = (volatileBursts + VolatileBurstProjection(
        x = x,
        y = y,
        radius = radius,
        armedAt = now,
        detonatesAt = now + delay,
    )).toImmutableList()
}

/**
 * Detonates every due burst. A burst hurts a Core inside its ring that moves slower than
 * [VOLATILE_ESCAPE_SPEED] and is not in a Dash phase; the hit window keeps simultaneous bursts to one hit.
 */
internal fun MutableGameState.updateVolatileRemains() {
    if (volatileBursts.isEmpty() || phase != GamePhase.RUNNING) return
    val now = hostileElapsed()
    var due = 0
    for (index in volatileBursts.indices) if (volatileBursts[index].detonatesAt <= now) due++
    if (due == 0) return
    val bursts = volatileBursts
    volatileBursts = bursts.filter { it.detonatesAt > now }.toImmutableList()
    for (index in bursts.indices) {
        val burst = bursts[index]
        if (burst.detonatesAt > now) continue
        burst(burst.x, burst.y, 8, 2)
        val reach = burst.radius + coreRadius
        if (phase == GamePhase.RUNNING && dashPhaseTime <= 0f && speed < VOLATILE_ESCAPE_SPEED &&
            distanceSquared(burst.x, burst.y, coreX, coreY) <= reach * reach
        ) {
            val damage = hostileDamage(null, VOLATILE_BURST_DAMAGE)
            takeDamage(damage)
        }
    }
}

/** Blackout: a fresh Data pickup fades after [BLACKOUT_DATA_LIFE_SECONDS] unless the magnet latches it first. */
internal fun MutableGameState.blackoutDataLife(life: Float): Float =
    if (runMutatorActive(RunMutator.BLACKOUT)) min(life, BLACKOUT_DATA_LIFE_SECONDS) else life
