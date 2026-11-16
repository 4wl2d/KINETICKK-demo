// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.interaction.canvas.world

import androidx.compose.ui.graphics.drawscope.DrawScope
import kinetickk.ball.gameplay.interaction.canvas.*
import kinetickk.ball.gameplay.interaction.canvas.fx.FxDrawBudget
import kinetickk.ball.gameplay.interaction.canvas.fx.PixelPalettes
import kinetickk.ball.gameplay.interaction.canvas.fx.drawPixelFrame
import kinetickk.ball.gameplay.interaction.canvas.world.enemies.TELEGRAPH_POOL
import kinetickk.ball.gameplay.interaction.canvas.world.enemies.blastBlinkHalf
import kinetickk.ball.gameplay.interaction.canvas.world.enemies.blastPulse
import kinetickk.ball.gameplay.interaction.canvas.world.enemies.drawBlastWarning
import kinetickk.ball.gameplay.interaction.fx.PixelFxClock
import kinetickk.ball.gameplay.interaction.fx.PixelStrip
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.foundation.design.KkRolePalette
import kinetickk.foundation.design.isOnScreen
import kotlin.math.min

/** The cross on a burst's heart grows through three sizes; embers start once they clear its ink. */
private const val HEART_SIZES = 3
private const val HEART_CLEAR = 12f

/**
 * Fire traps of Imps, the Pyre Herald and the Antecedent: each pending burst shows the
 * blast warning ([drawBlastWarning]) at its radius, its rim blinking faster ([blastBlinkHalf]) and its embers stepping
 * out to the rim as the detonation nears, and a threat cross on an ink heart that grows and lights with the rim (a
 * fire trap never reads as a Harvest ember). Fuses run on the hostile clock, so the telegraph reads the burst's own
 * time at the interpolated hostile time, which the Archive's Desync slows or quickens, held to the rim strip's ticks.
 */
internal fun DrawScope.drawFireTraps(engine: GameplayRenderModel, shakeX: Float, shakeY: Float, roles: KkRolePalette) {
    val bursts = engine.volatileBursts
    if (bursts.isEmpty()) return
    val fps = PixelStrip.TELE_BLAST_TOOTH.fps
    val now = engine.renderHostileElapsed
    val palette = PixelPalettes.of(roles.threat)
    val scale = engine.worldScale
    for (index in bursts.indices) {
        val burst = bursts[index]
        val center = world(engine, burst.x, burst.y, shakeX, shakeY)
        if (!isOnScreen(center, burst.radius + 8f)) continue
        val span = (burst.detonatesAt - burst.armedAt).coerceAtLeast(0.01f)
        val since = now - burst.armedAt
        val progress = (PixelFxClock.stepped(since, fps) / span).coerceIn(0f, 1f)
        val half = blastBlinkHalf(since, span)
        drawBlastWarning(center.x, center.y, burst.radius, progress, blastPulse(half), HEART_CLEAR, scale, palette)
        FxDrawBudget.take(TELEGRAPH_POOL, 1, signature = true)
        val heart = min(HEART_SIZES - 1, (progress * HEART_SIZES).toInt()) * 2 + if (half % 2 == 0) 1 else 0
        drawPixelFrame(PixelStrip.TELE_BLAST_HEART, heart, center.x, center.y, scale, palette)
    }
}
