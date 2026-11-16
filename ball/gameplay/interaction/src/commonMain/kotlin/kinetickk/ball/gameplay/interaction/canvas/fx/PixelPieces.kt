// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.interaction.canvas.fx

import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import kinetickk.ball.gameplay.interaction.canvas.CanvasMath
import kinetickk.ball.gameplay.interaction.fx.PieceMotion
import kinetickk.ball.gameplay.interaction.fx.PixelFxClock
import kinetickk.ball.gameplay.interaction.fx.PixelPieceRules
import kinetickk.ball.gameplay.interaction.fx.PixelStrip
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Pieces start (or end) this far from the centre, inside the look's own strip (units). */
private const val PIECE_INNER = 20f

private const val PIECE_STAGGER = PixelPieceRules.STAGGER

/** Pieces a look may ask for. */
internal const val MAX_PIECES = PixelPieceRules.MAX

/** A look scatters one piece per this many units of its reach, at least [MIN_PIECES]. */
private const val PIECE_REACH_STEP = 9f
private const val MIN_PIECES = 6

/** The pieces of a run stand this far apart along it (units), so each reads on its own. */
private const val RUN_SPACING = 28f

/** Pieces of a run [reach] long: one per [RUN_SPACING], at least two. */
internal fun runCount(reach: Float): Int = (reach / RUN_SPACING).toInt().coerceIn(2, MAX_PIECES)

/** A fan throws at least this many pieces, however narrow it is. */
private const val MIN_FAN_PIECES = 5

/**
 * Pieces of a look with [reach]: more for a wider look, so its rim reads at any size. A look that fans its pieces
 * over [fan] radians (0 for all round) throws that share of them, so a fan is no denser than a full ring.
 */
internal fun pieceCount(reach: Float, fan: Float = 0f): Int {
    val round = (reach / PIECE_REACH_STEP).toInt().coerceIn(MIN_PIECES, MAX_PIECES)
    return if (fan <= 0f) round else max(MIN_FAN_PIECES, (round * fan / CanvasMath.TAU).roundToInt())
}

/**
 * The pieces a look throws to show how far it reaches: [count] cells of [strip] round ([x], [y]), each playing the
 * strip once from [age] and travelling by [motion] on the strip's own ticks, so a piece moves and changes frame at
 * the same moments (each piece of an [PieceMotion.ALONG] run on its own, from its own start). Every other piece of a
 * set runs [PixelPieceRules.LAG_TICKS] behind the rest. A strip with a [diagonal] twin (the same piece drawn pointing down-right, [strip] pointing right)
 * is aimed along its travel in eighths of a turn; one without is drawn upright, mirrored on the look's left half.
 * [seed] turns the whole set. The first [signatures] pieces always draw; the rest give way to the budget of [pool].
 * [dirX], [dirY] is the unit direction of an [PieceMotion.ALONG] run, and the middle of the fan when [arc] (radians)
 * is less than a full turn: the pieces then spread evenly over that arc instead of the whole circle. Allocation-free.
 */
internal fun DrawScope.drawPixelPieces(
    strip: PixelStrip,
    diagonal: PixelStrip?,
    motion: PieceMotion,
    x: Float,
    y: Float,
    reach: Float,
    age: Float,
    seed: Int,
    count: Int,
    scale: Float,
    palette: ColorFilter,
    alpha: Float = 1f,
    signatures: Int = count,
    dirX: Float = 1f,
    dirY: Float = 0f,
    arc: Float = CanvasMath.TAU,
    pool: FxPool = FxPool.TRANSIENT,
) {
    val heading = atan2(dirY, dirX)
    if (motion == PieceMotion.ALONG) {
        for (index in 0 until count) {
            val frame = PixelFxClock.frame(strip, age - index * PIECE_STAGGER)
            if (frame < 0) continue
            if (!FxDrawBudget.take(pool, 1, index < signatures)) return
            val along = (index + 0.75f) * reach / count
            drawPiece(strip, diagonal, frame, x + dirX * along, y + dirY * along, heading, scale, palette, alpha)
        }
        return
    }
    val tick = PixelFxClock.tick(age, strip.fps)
    val ticks = PixelFxClock.ticks(strip)
    if (tick < 0 || tick >= ticks + PixelPieceRules.LAG_TICKS) return
    val inner = min(reach, PIECE_INNER)
    val lag = PixelPieceRules.LAG_TICKS / strip.fps.toFloat()
    val fan = arc < CanvasMath.TAU
    val turn = if (fan) heading - arc * 0.5f else fxHash(seed, 0) * CanvasMath.TAU
    val inward = if (motion == PieceMotion.IN) CanvasMath.TAU * 0.5f else 0f
    for (index in 0 until count) {
        // Every other piece runs a tick behind, so the set never moves as one rigid ring.
        val late = index and 1
        val own = tick - late * PixelPieceRules.LAG_TICKS
        if (own < 0 || own >= ticks) continue
        if (!FxDrawBudget.take(pool, 1, index < signatures)) return
        val radius = pieceRadius(motion, own, ticks, inner, reach)
        val angle = turn + (if (fan) index + 0.5f else index.toFloat()) * arc / count
        drawPiece(strip, diagonal, PixelFxClock.frame(strip, age - late * lag), x + cos(angle) * radius, y + sin(angle) * radius, angle + inward, scale,
            palette, alpha)
    }
}

/** How far from the centre a piece stands on tick [tick] of the [ticks] its strip lasts. */
private fun pieceRadius(motion: PieceMotion, tick: Int, ticks: Int, inner: Float, reach: Float): Float = when (motion) {
    // Thrown pieces slow as they arrive; the first frame already stands clear of the centre.
    PieceMotion.OUT -> ((ticks - tick - 0.5f) / ticks).let { left -> inner + (reach - inner) * (1f - left * left * left) }
    // Pulled pieces quicken as they close, and stand on the centre's edge on their last frame.
    PieceMotion.IN -> ((tick + 1f) / ticks).let { gone -> reach - (reach - inner) * gone * gone }
    else -> reach
}

private fun DrawScope.drawPiece(strip: PixelStrip, diagonal: PixelStrip?, frame: Int, x: Float, y: Float, heading: Float, scale: Float,
    palette: ColorFilter, alpha: Float) {
    if (diagonal != null) drawPixelAimed(strip, diagonal, frame, x, y, heading, scale, palette, alpha)
    else drawPixelFrame(strip, frame, x, y, scale, palette, alpha = alpha, flipX = cos(heading) < 0f)
}
