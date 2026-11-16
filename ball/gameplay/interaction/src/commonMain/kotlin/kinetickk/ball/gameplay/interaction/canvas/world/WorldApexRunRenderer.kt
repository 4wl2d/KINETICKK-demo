// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.interaction.canvas.world

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.gameplay.interaction.canvas.CanvasMath
import kinetickk.ball.gameplay.interaction.canvas.renderElapsed
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.foundation.design.KkRolePalette
import kinetickk.foundation.design.KkStrokeWidth
import kinetickk.foundation.design.kkStroke
import kotlin.math.cos
import kotlin.math.sin

/** The current Apex timer, plus the Orb's held Data and the Prism's intact facets. */
internal fun DrawScope.drawApexActive(engine: GameplayRenderModel, core: Offset, roles: KkRolePalette) {
    val active = engine.apex.active01.coerceIn(0f, 1f)
    if (active <= 0f) return

    val rim = engine.coreRadius + 34f
    drawArc(
        roles.gold.light, -90f, 360f * active, useCenter = false,
        topLeft = Offset(core.x - rim, core.y - rim),
        size = Size(rim * 2f, rim * 2f),
        style = kkStroke(KkStrokeWidth.Icon),
    )
    val orb = engine.coreShape == CoreShape.ORB
    val pips = when (engine.coreShape) {
        CoreShape.ORB -> engine.apex.held.coerceIn(0, 24)
        CoreShape.PRISM -> engine.characterAbility.facets.coerceIn(0, 4)
        CoreShape.SHARD -> return
    }
    val reach = if (orb) engine.coreRadius + 52f else 27f
    val turn = engine.renderElapsed * if (orb) 2f else 0.6f
    for (pip in 0 until pips) {
        val angle = turn + pip * CanvasMath.TAU / pips
        val point = Offset(core.x + cos(angle) * reach, core.y + sin(angle) * reach)
        drawCircle(roles.gold.base, 3f, point)
    }
}
