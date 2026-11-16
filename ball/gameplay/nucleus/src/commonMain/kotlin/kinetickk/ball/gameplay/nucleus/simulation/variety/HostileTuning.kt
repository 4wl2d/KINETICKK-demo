// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.nucleus.simulation.variety

import kotlin.math.max

internal const val MAX_ACTIVE_ENEMIES = 120
internal const val OPENING_ENEMY_COUNT = 5
private const val MIN_SPAWN_INTERVAL_SECONDS = 0.09f
private const val MIN_ELITE_INTERVAL_SECONDS = 24f

/** Hostile intensity for one run, affected by the player's choices during that run. */
internal data class HostileTuning(
    val enemyHealthMultiplier: Float = 1f,
    val enemySpeedMultiplier: Float = 1f,
    val incomingDamageMultiplier: Float = 1f,
    val spawnRateMultiplier: Float = 1f,
    val eliteRateMultiplier: Float = 1f,
) {
    fun enemyHealth(base: Float): Float = base * enemyHealthMultiplier
    fun spawnInterval(base: Float): Float = max(MIN_SPAWN_INTERVAL_SECONDS, base / spawnRateMultiplier)
    fun eliteInterval(base: Float): Float = max(MIN_ELITE_INTERVAL_SECONDS, base / eliteRateMultiplier)
}
