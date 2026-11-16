// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.interaction.canvas.world

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.gameplay.interaction.canvas.CanvasMath
import kinetickk.ball.gameplay.interaction.canvas.fx.FxDrawBudget
import kinetickk.ball.gameplay.interaction.canvas.fx.FxPool
import kinetickk.ball.gameplay.interaction.canvas.fx.PixelSheet
import kinetickk.ball.gameplay.interaction.canvas.renderElapsed
import kinetickk.ball.gameplay.interaction.canvas.worldScale
import kinetickk.ball.gameplay.interaction.fx.PixelFxClock
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.foundation.design.KkRolePalette
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private const val STUDS = 6
private const val TIMER_PIPS = 16
private const val STUD_FPS = 20
private const val STUD_TURN_TICKS = 10
private const val PIP_FPS = 12
private const val TAU = CanvasMath.TAU
private const val TIMER_START = -TAU * 0.25f

/** Small filled marks in local pixel coordinates, shared across the ring and the timer. */
private val ApexGem = Path().apply {
    moveTo(-1f, -3f)
    lineTo(1f, -3f)
    lineTo(1f, -2f)
    lineTo(2f, -2f)
    lineTo(2f, -1f)
    lineTo(3f, -1f)
    lineTo(3f, 1f)
    lineTo(2f, 1f)
    lineTo(2f, 2f)
    lineTo(1f, 2f)
    lineTo(1f, 3f)
    lineTo(-1f, 3f)
    lineTo(-1f, 2f)
    lineTo(-2f, 2f)
    lineTo(-2f, 1f)
    lineTo(-3f, 1f)
    lineTo(-3f, -1f)
    lineTo(-2f, -1f)
    lineTo(-2f, -2f)
    lineTo(-1f, -2f)
    close()
}

private val FacetFrame = Path().apply {
    fillType = PathFillType.EvenOdd
    addRect(Rect(-5f, -5f, 5f, 5f))
    addRect(Rect(-3f, -3f, 3f, 3f))
}

/** The active Apex's rotating gold studs, countdown pips and character mark. */
internal fun DrawScope.drawApexActive(engine: GameplayRenderModel, core: Offset, roles: KkRolePalette) {
    val active = engine.apex.active01.coerceIn(0f, 1f)
    if (active <= 0f) return
    val time = engine.renderElapsed
    val studsTick = PixelFxClock.tick(time, STUD_FPS)
    val turn = studsTick / STUD_TURN_TICKS * TAU / 48f
    val pipTick = PixelFxClock.tick(time, PIP_FPS)
    val timer = ceil(active * TIMER_PIPS).toInt()
    val scale = engine.worldScale
    val unit = PixelSheet.step(scale) / scale
    val flare = 1f - abs(studsTick % 24 - 8) / 16f
    val shadow = roles.gold.base.copy(alpha = 0.32f + 0.24f * flare)
    val highlight = roles.gold.light.copy(alpha = 0.76f + 0.2f * flare)
    FxDrawBudget.take(FxPool.CORE_STATES, 2 * STUDS + timer, signature = true)
    for (stud in 0 until STUDS) {
        val angle = turn + stud * TAU / STUDS
        val point = apexPoint(core, engine.coreRadius + 30f, angle, scale)
        drawApexGem(point, unit * (1.2f + 0.2f * flare), shadow)
        drawApexGem(point, unit * 0.7f, highlight)
    }
    for (pip in 0 until timer) {
        val angle = TIMER_START + (pip + 0.5f) * TAU / TIMER_PIPS
        val blink = (pipTick + pip) % 2 == 0
        val color = roles.gold.light.copy(alpha = if (pip == timer - 1 && blink) 0.35f else 0.9f)
        drawApexGem(apexPoint(core, engine.coreRadius + 36f, angle, scale), unit * 0.6f, color)
    }
    when (engine.coreShape) {
        CoreShape.ORB -> drawHeldData(engine, core, pipTick, scale, unit, roles.gold.light)
        CoreShape.PRISM -> drawApexFacets(engine, core, pipTick, scale, unit, roles.gold.base)
        CoreShape.SHARD -> Unit
    }
}

private fun DrawScope.drawHeldData(engine: GameplayRenderModel, core: Offset, tick: Int, scale: Float, unit: Float, color: Color) {
    val held = engine.apex.held.coerceIn(0, 24)
    if (held == 0) return
    FxDrawBudget.take(FxPool.CORE_STATES, held, signature = true)
    val turn = 2f * tick / PIP_FPS
    for (bead in 0 until held) {
        val angle = turn + bead * TAU / held
        val point = apexPoint(core, engine.coreRadius + 52f, angle, scale)
        val glint = (tick + bead) % 4
        drawApexGem(point, unit * if (glint == 0) 0.85f else 0.7f, color.copy(alpha = 0.7f + 0.08f * glint))
    }
}

private fun DrawScope.drawApexFacets(engine: GameplayRenderModel, core: Offset, tick: Int, scale: Float, unit: Float, color: Color) {
    val facets = engine.characterAbility.facets.coerceIn(0, 4)
    if (facets == 0) return
    FxDrawBudget.take(FxPool.CORE_STATES, facets, signature = true)
    val turn = tick.toFloat() / PIP_FPS * 0.6f
    for (facet in 0 until facets) {
        val angle = turn + facet * TAU / 4f
        val point = apexPoint(core, 27f, angle, scale)
        val spin = ((45f + angle * CanvasMath.RAD_TO_DEG) / 15f).toInt() * 15f
        rotate(spin, point) {
            translate(point.x, point.y) {
                scale(unit, unit, Offset.Zero) {
                    drawPath(FacetFrame, color.copy(alpha = 0.9f))
                }
            }
        }
    }
}

private fun DrawScope.drawApexGem(point: Offset, unit: Float, color: Color) {
    translate(point.x, point.y) {
        scale(unit, unit, Offset.Zero) {
            drawPath(ApexGem, color)
        }
    }
}

private fun apexPoint(center: Offset, radius: Float, angle: Float, scale: Float): Offset =
    Offset(
        ((center.x + cos(angle) * radius) * scale).roundToInt() / scale,
        ((center.y + sin(angle) * radius) * scale).roundToInt() / scale,
    )
