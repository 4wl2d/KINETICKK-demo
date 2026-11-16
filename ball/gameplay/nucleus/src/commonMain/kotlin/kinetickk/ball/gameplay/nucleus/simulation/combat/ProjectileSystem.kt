// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.nucleus.simulation.combat

import kinetickk.ball.gameplay.nucleus.model.*
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.dimensions.*

internal fun MutableGameState.updateProjectiles(delta: Float) {
    // Hostile shots follow the world layer's time; the player's own shots never do.
    val hostileStep = delta * layerTimeScale(null)
    for (projectileIndex in projectiles.indices) {
        val projectile = projectiles[projectileIndex]
        val step = if (projectile.hostile) hostileStep else delta
        projectile.previousX = projectile.x
        projectile.previousY = projectile.y
        projectile.x += projectile.vx * step
        projectile.y += projectile.vy * step
        projectile.life -= step
    }
    projectiles.removeMatchingStable { projectile ->
        projectile.life <= 0f ||
            distanceSquared(projectile.x, projectile.y, coreX, coreY) > 1_600f * 1_600f
    }
    while (projectiles.size > MutableGameState.MAX_PROJECTILES) projectiles.removeAt(0)
}
