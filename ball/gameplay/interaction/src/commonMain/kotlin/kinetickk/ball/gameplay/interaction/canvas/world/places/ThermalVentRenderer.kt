// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.interaction.canvas.world.places

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import kinetickk.ball.content.api.ObstacleKindTuning
import kinetickk.ball.content.api.ventErupting
import kinetickk.ball.content.api.ventEruptionEnd
import kinetickk.ball.content.api.ventSecondsToEruption
import kinetickk.ball.gameplay.interaction.canvas.fx.FxDrawBudget
import kinetickk.ball.gameplay.interaction.canvas.fx.PixelPalettes
import kinetickk.ball.gameplay.interaction.canvas.world.enemies.TELEGRAPH_POOL
import kinetickk.ball.gameplay.interaction.fx.PixelFxClock
import kinetickk.ball.gameplay.interaction.fx.PixelStrip
import kinetickk.ball.gameplay.nucleus.render.ObstacleProjection
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkRolePalette
import kinetickk.foundation.design.KkStrokeWidth
import kinetickk.foundation.design.kkStroke
import kotlin.math.max
import kotlin.math.min

/*
 * Thermal vents in the world and on the minimap: the vent's body stays vector; its eruption warning, plume and licks
 * are pixel cells on the vent's own clock. Allocation-free per frame.
 */

/**
 * A thermal vent: a dark ground disc with a heat rim, heat cracks and an ember at its centre. Before it erupts an inner
 * glow swells; while it erupts its plume flickers in heat beyond the body, a ring of licks leaves the body every quarter
 * second and rises to the plume's edge, and the edge stays lit. The plume is not solid: it heats and lifts.
 */
internal fun DrawScope.drawThermalVent(
    obstacle: ObstacleProjection,
    center: Offset,
    time: Float,
    kinds: ObstacleKindTuning,
    roles: KkRolePalette,
    scale: Float,
) {
    val body = obstacle.radius
    val seed = obstacle.shapeSeed
    val heat = roles.heat
    val erupting = kinds.ventErupting(time, seed)
    val plume = body + kinds.ventPlumeMargin
    val palette = PixelPalettes.of(heat)
    // The eruption's own time, so its first frame is its strongest whatever the vent's cycle.
    val since = kinds.ventEruptSeconds - (kinds.ventEruptionEnd(time, seed) - time)
    if (erupting) drawVentPlume(center, plume, since, scale, palette)
    drawCircle(Kk.Ink1, body, center)
    drawCircle(heat.copy(alpha = 0.45f), body, center, style = kkStroke(KkStrokeWidth.Line2))
    val cracks = ObstaclePaths.cracks(seed, body)
    translate(center.x, center.y) {
        drawPath(cracks, heat.copy(alpha = 0.5f), style = kkStroke(KkStrokeWidth.Line2))
    }
    drawCircle(heat.copy(alpha = 0.4f), body * 0.12f, center)
    if (erupting) {
        drawVentLicks(center, body, plume, since, kinds.ventEruptSeconds, scale, palette)
        return
    }
    val telegraph = kinds.ventTelegraphSeconds
    val untilEruption = kinds.ventSecondsToEruption(time, seed)
    if (telegraph > 0f && untilEruption <= telegraph) {
        drawVentWarning(center, body, plume, telegraph - untilEruption, telegraph, scale, palette)
    }
}

/** Steps of a vent's eruption warning, each the [PixelStrip.VENT_PIP] frame of its number: faint, dim, lit, then hot. */
private const val VENT_WARNING_STEPS = 4

/** The plume's rim stays lit while the vent erupts. */
private const val VENT_PIP_LIT = 2

/** A vent's rim pips sit about this many units apart round its plume, in dashes. */
private const val VENT_PIP_SPACING = 9.8f
private const val VENT_PIP_DASHES = 12

/** The plume's halftone holds each of its two levels for this many ticks of the lick strip: three changes a second. */
private const val VENT_FLICKER_TICKS = 4

/** The licks of a rising ring sit about this many units apart. */
private const val VENT_LICK_SPACING = 15f

/** A ring of licks leaves the body every this many ticks of the lick strip: four rings a second. */
private const val VENT_LICK_EVERY_TICKS = 3

/**
 * A thermal vent's eruption warning [since] seconds into its [telegraph]: a dashed ring of ember pips on the plume's
 * exact edge brightening a step at a time, and the swell over the middle of the body as a halftone stepping up a level
 * with it. The steps change on the pip strip's ticks, each about a quarter of the warning (the last is shorter).
 * Signature work, like every telegraph.
 */
private fun DrawScope.drawVentWarning(center: Offset, body: Float, plume: Float, since: Float, telegraph: Float, scale: Float, heat: ColorFilter) {
    val share = PixelFxClock.stepped(since, PixelStrip.VENT_PIP.fps) / telegraph
    val step = (share * VENT_WARNING_STEPS).toInt().coerceIn(0, VENT_WARNING_STEPS - 1)
    var ops = drawPixelDisc(center.x, center.y, body * 0.6f, step, scale, heat)
    ops += drawPipRing(PixelStrip.VENT_PIP, center.x, center.y, plume, VENT_PIP_SPACING, scale, heat, dashes = VENT_PIP_DASHES) { step }
    FxDrawBudget.take(TELEGRAPH_POOL, ops, signature = true)
}

/**
 * An erupting vent's plume [since] seconds into the eruption, under its body: the heated ground as a halftone out to
 * the plume's edge, flickering between two levels on the lick strip's ticks, the denser one first.
 */
private fun DrawScope.drawVentPlume(center: Offset, plume: Float, since: Float, scale: Float, heat: ColorFilter) {
    val level = 1 - PixelFxClock.tick(since, PixelStrip.VENT_LICK.fps) / VENT_FLICKER_TICKS % 2
    FxDrawBudget.take(TELEGRAPH_POOL, drawPixelDisc(center.x, center.y, plume, level, scale, heat), signature = true)
}

/**
 * An erupting vent's heat [since] seconds into the [erupt] seconds it lasts, over its body: the plume's exact edge as
 * lit ember pips, and a ring of licks leaving the body every [VENT_LICK_EVERY_TICKS] ticks of the lick strip. A ring
 * plays the strip once, a frame a tick, stepping out from the body (its hot first frame) to the plume's edge (its last
 * dot), each ring turned half a step from the one before. A ring leaves only if it can show its last frame before the
 * eruption ends (the first always leaves), so no ring is cut off; at most the two newest are alive.
 */
private fun DrawScope.drawVentLicks(center: Offset, body: Float, plume: Float, since: Float, erupt: Float, scale: Float, heat: ColorFilter) {
    val lick = PixelStrip.VENT_LICK
    val last = lick.frames - 1
    var ops = drawPipRing(PixelStrip.VENT_PIP, center.x, center.y, plume, VENT_PIP_SPACING, scale, heat, dashes = VENT_PIP_DASHES) { VENT_PIP_LIT }
    val tick = PixelFxClock.tick(since, lick.fps)
    val lastRing = max(0, ((erupt * lick.fps - lick.frames) / VENT_LICK_EVERY_TICKS).toInt())
    val newest = min(tick / VENT_LICK_EVERY_TICKS, lastRing)
    for (ring in max(0, newest - 1)..newest) {
        val frame = tick - ring * VENT_LICK_EVERY_TICKS
        if (frame !in 0..last) continue
        ops += drawPipRing(lick, center.x, center.y, body + (plume - body) * frame / last, VENT_LICK_SPACING, scale, heat,
            turn = (ring and 1) * VENT_LICK_SPACING * 0.5f / plume) { frame }
    }
    FxDrawBudget.take(TELEGRAPH_POOL, ops, signature = true)
}

/** A thermal vent on the minimap: a heat ring, filled while it erupts. */
internal fun DrawScope.drawMinimapThermalVent(
    obstacle: ObstacleProjection,
    center: Offset,
    radius: Float,
    dot: Float,
    time: Float,
    kinds: ObstacleKindTuning,
    roles: KkRolePalette,
) {
    if (kinds.ventErupting(time, obstacle.shapeSeed)) drawCircle(roles.heat.copy(alpha = 0.5f), radius, center)
    drawCircle(roles.heat.copy(alpha = 0.6f), radius, center, style = kkStroke(max(1f, dot * 0.6f)))
}
