// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.nucleus.simulation.weapons

import kinetickk.ball.gameplay.nucleus.model.*
import kotlin.math.max
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.structures.*
import kinetickk.ball.gameplay.nucleus.simulation.world.*

// ---------------------------------------------------------------------------------------------
// Movement and fuses.

/*
 * A disc flies out ([WeaponEntity.timer] counts the distance) and returns to the Core (phase 1, where the timer
 * holds back the next spark while the disc glances along a rock). Asteroids turn a disc back, or end a returning one; the third form
 * ricochets an outgoing disc on and makes a returning one glance along the rock toward the Core, so it comes
 * home around it. Either way the disc keeps moving on the step it meets the rock.
 */
internal fun MutableGameState.advanceDisc(disc: WeaponEntity, delta: Float) {
    disc.life -= delta
    if (disc.phase == 1) {
        val dx = coreX - disc.x
        val dy = coreY - disc.y
        val distance = length(dx, dy)
        if (distance <= GYRE_CATCH_RADIUS) {
            disc.life = 0f
            return
        }
        disc.vx = dx / distance * GYRE_RETURN_SPEED
        disc.vy = dy / distance * GYRE_RETURN_SPEED
    }
    disc.angle += GYRE_SPIN * delta
    if (disc.phase == 1) disc.timer = max(0f, disc.timer - delta)
    val blocker = obstacleOnPath(disc.x, disc.y, disc.x + disc.vx * delta, disc.y + disc.vy * delta, disc.radius)
    val wallIndex = if (blocker == null) {
        wallOnPath(disc.x, disc.y, disc.x + disc.vx * delta, disc.y + disc.vy * delta, disc.radius)
    } else -1
    if (blocker != null || wallIndex >= 0) {
        if (!hasMasteryForm(3)) {
            if (disc.phase == 0) beginDiscReturn(disc) else disc.life = 0f
            return
        }
        if (blocker != null) {
            val nx = disc.x - blocker.x
            val ny = disc.y - blocker.y
            val distance = max(1f, length(nx, ny))
            deflectDisc(disc, nx / distance, ny / distance)
            // The new heading runs along or away from that rock; a second rock on it holds the disc for this step.
            val next = obstacleOnPath(disc.x, disc.y, disc.x + disc.vx * delta, disc.y + disc.vy * delta, disc.radius)
            if (next != null && next !== blocker) return
        } else {
            // A wall deflects like a rock, off the normal from its nearest point.
            val wall = structureField.walls[wallIndex]
            val along = segmentNearest(disc.x, disc.y, wall.ax, wall.ay, wall.bx, wall.by)
            val nx = disc.x - (wall.ax + (wall.bx - wall.ax) * along)
            val ny = disc.y - (wall.ay + (wall.by - wall.ay) * along)
            val distance = max(1f, length(nx, ny))
            deflectDisc(disc, nx / distance, ny / distance)
            val nextX = disc.x + disc.vx * delta
            val nextY = disc.y + disc.vy * delta
            val nextWall = wallOnPath(disc.x, disc.y, nextX, nextY, disc.radius)
            if ((nextWall >= 0 && nextWall != wallIndex) || obstacleOnPath(disc.x, disc.y, nextX, nextY, disc.radius) != null) return
        }
    }
    val stepX = disc.vx * delta
    val stepY = disc.vy * delta
    disc.x += stepX
    disc.y += stepY
    if (disc.phase == 0) {
        disc.timer += length(stepX, stepY)
        if (disc.timer >= disc.extent) beginDiscReturn(disc)
    }
}

private fun beginDiscReturn(disc: WeaponEntity) {
    disc.phase = 1
    disc.timer = 0f
    disc.clearHits()
}

/**
 * The third form at a rock or wall with outward normal ([ux], [uy]): an outgoing disc reflects off it; a returning one
 * glances along it toward the Core.
 */
private fun MutableGameState.deflectDisc(disc: WeaponEntity, ux: Float, uy: Float) {
    val along = disc.vx * ux + disc.vy * uy
    if (along >= 0f) return
    if (disc.phase == 0) {
        disc.vx -= 2f * along * ux
        disc.vy -= 2f * along * uy
        burst(disc.x, disc.y, 4, 0)
        return
    }
    // The heading's part into the rock goes; what is left runs along its surface on the Core's side.
    disc.vx -= along * ux
    disc.vy -= along * uy
    val tangent = length(disc.vx, disc.vy)
    if (tangent < 1f) {
        // Straight behind the rock: either way round is as short.
        disc.vx = -uy * GYRE_RETURN_SPEED
        disc.vy = ux * GYRE_RETURN_SPEED
    } else {
        disc.vx *= GYRE_RETURN_SPEED / tangent
        disc.vy *= GYRE_RETURN_SPEED / tangent
    }
    // One spark as the glance begins, not one per step along the rock.
    if (disc.timer <= 0f) burst(disc.x, disc.y, 4, 0)
    disc.timer = GYRE_GLANCE_SPARK_GAP
}

internal fun MutableGameState.advanceWell(well: WeaponEntity, delta: Float) {
    well.life -= delta
    well.angle += 2.4f * delta
}

/**
 * The first shot-stopping obstacle a circle of [radius] runs into from (x0, y0) to (x1, y1) (one it starts
 * overlapping only when it moves inward, see [obstacleStepEntry]); bumpers and kinds with no solid body let it pass.
 */
private fun MutableGameState.obstacleOnPath(x0: Float, y0: Float, x1: Float, y1: Float, radius: Float): Obstacle? {
    if (obstacleField.size == 0) return null
    val reach = length(x1 - x0, y1 - y0) * 0.5f + radius
    val kinds = content.obstacles.kinds
    forEachObstacleNear((x0 + x1) * 0.5f, (y0 + y1) * 0.5f, reach) { _, obstacle ->
        if (obstacle.stopsShotsAt(elapsed, kinds) &&
            obstacleStepEntry(x0, y0, x1, y1, obstacle.x, obstacle.y, obstacle.radius, radius) >= 0f
        ) {
            return obstacle
        }
    }
    return null
}
