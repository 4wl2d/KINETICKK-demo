// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.interaction.canvas.fx

import androidx.compose.ui.graphics.drawscope.DrawScope
import kinetickk.ball.gameplay.interaction.canvas.fxAge
import kinetickk.ball.gameplay.interaction.canvas.world
import kinetickk.ball.gameplay.interaction.canvas.worldScale
import kinetickk.ball.gameplay.interaction.fx.PieceMotion
import kinetickk.ball.gameplay.interaction.fx.PixelApexLook
import kinetickk.ball.gameplay.interaction.fx.PixelFxClock
import kinetickk.ball.gameplay.interaction.fx.PixelStrip
import kinetickk.ball.gameplay.interaction.fx.VisualFxProjection
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.foundation.design.KkRolePalette

/** Draws an expanding gold mark at the Core and fragments moving to the edge of the burst. */
internal fun DrawScope.drawApexBursts(
    engine: GameplayRenderModel,
    visualFx: VisualFxProjection,
    shakeX: Float,
    shakeY: Float,
    roles: KkRolePalette,
) {
    val bursts = visualFx.apexBursts
    if (bursts.isEmpty()) return
    val scale = engine.worldScale
    val alpha = FxFrame.peakAlpha
    val gold = PixelPalettes.of(roles.gold.base)
    val light = PixelPalettes.of(roles.gold.light)
    val pieces = if (satellitesOn()) PixelApexLook.PIECES else PixelApexLook.SIGNATURE_PIECES
    for (index in bursts.indices) {
        val burst = bursts[index]
        val center = world(engine, burst.x, burst.y, shakeX, shakeY)
        val age = engine.fxAge(burst.life, burst.maxLife)
        val grow = PixelApexLook.markScale(PixelFxClock.tick(age, PixelStrip.APEX_MARK.fps))
        if (grow > 0 && FxDrawBudget.take(FxPool.TRANSIENT, 1, signature = true)) {
            drawPixelFrame(PixelStrip.APEX_MARK, grow - 1, center.x, center.y, scale, if (grow == 1) light else gold,
                step = PixelSheet.step(scale) * grow, alpha = alpha)
        }
        playStrip(PixelStrip.APEX_BURST, age, center.x, center.y, scale, gold, alpha, signature = true)
        drawPixelPieces(PixelStrip.APEX_SPIRE, PixelStrip.APEX_SPIRE_D, PieceMotion.OUT, center.x, center.y, PixelApexLook.REACH,
            age - PixelApexLook.piecesDelay, burst.seed, pieces, scale, gold, alpha, signatures = PixelApexLook.SIGNATURE_PIECES)
    }
}
