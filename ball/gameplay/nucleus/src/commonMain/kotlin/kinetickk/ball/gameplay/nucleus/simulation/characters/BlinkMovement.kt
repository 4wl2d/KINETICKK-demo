// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.nucleus.simulation.characters

import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.world.obstacleBlocksSegment
import kotlin.math.max

private const val BLINK_OBSTACLE_STEP = 20f

/**
 * [range] of a Blink from the Core along (directionX, directionY), shortened in [BLINK_OBSTACLE_STEP] steps
 * until the path clears every obstacle: a Blink stops short of a rock, never lands in or past it.
 */
internal fun MutableGameState.blinkRangeClearOfObstacles(directionX: Float, directionY: Float, range: Float): Float {
    var clear = range
    while (clear > 0f && obstacleBlocksSegment(coreX, coreY, coreX + directionX * clear, coreY + directionY * clear, coreRadius)) {
        clear = max(0f, clear - BLINK_OBSTACLE_STEP)
    }
    return clear
}

/** Records a Blink from (startX, startY) to (endX, endY) for the rest of the step (see [blinkThisStep]). */
internal fun MutableGameState.markBlink(startX: Float, startY: Float, endX: Float, endY: Float) {
    val ability = characterRuntime
    ability.blinkFlagSteps = 1
    ability.blinkFromX = startX
    ability.blinkFromY = startY
    ability.blinkToX = endX
    ability.blinkToY = endY
}
