// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.interaction.canvas.world

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import kinetickk.ball.gameplay.interaction.canvas.fx.PixelPalettes
import kinetickk.ball.gameplay.interaction.canvas.fx.PixelSheet
import kinetickk.ball.gameplay.interaction.canvas.fx.drawPixelFill
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.foundation.design.KkRolePalette

/** The damage flash at which the threat edge shows four, three, two and one bands (the densest at level 3 to 0). */
private val ThreatBandFlash = floatArrayOf(0.8f, 0.55f, 0.3f, 0.06f)

/**
 * Each band of the threat edge is this many texels deep (the halftone's period, so bands meet without a seam); the
 * sparsest, innermost band is twice as deep, so the edge trails off.
 */
private const val THREAT_BAND_TEXELS = 4

/**
 * World-impact screen effects only (HUD state effects are HudRenderer's): threat halftone bands on every edge while
 * damage is fresh (the render model has no source direction). The bands step from dense at the edge to sparse inward;
 * as the damage flash decays, the densest band thins a level and the innermost goes, until none is left. Screen space at
 * the screen's density, so every texel covers whole device pixels. Ram impacts read through their slash, ring and screen
 * shake; they never flash the whole screen.
 */
internal fun DrawScope.drawScreenFx(engine: GameplayRenderModel, roles: KkRolePalette) {
    val flash = engine.damageFlash
    if (flash <= 0f) return
    val reached = ThreatBandFlash.indexOfFirst { flash >= it }
    if (reached < 0) return
    val top = ThreatBandFlash.size - 1 - reached
    val density = density
    val palette = PixelPalettes.of(roles.threat)
    val depth = THREAT_BAND_TEXELS * PixelSheet.step(density) / density
    val width = size.width / density
    val height = size.height / density
    scale(density, density, Offset.Zero) {
        for (band in 0..top) drawThreatBand(band * depth, if (band == top) depth * 2f else depth, width, height, top - band, density, palette)
    }
}

/** One band of the threat edge, [inset] in from every side and [depth] deep, at halftone [level] (screen units). */
private fun DrawScope.drawThreatBand(inset: Float, depth: Float, width: Float, height: Float, level: Int, scale: Float, palette: ColorFilter) {
    drawPixelFill(inset, inset, width - inset, inset + depth, level, scale, palette)
    drawPixelFill(inset, height - inset - depth, width - inset, height - inset, level, scale, palette)
    drawPixelFill(inset, inset + depth, inset + depth, height - inset - depth, level, scale, palette)
    drawPixelFill(width - inset - depth, inset + depth, width - inset, height - inset - depth, level, scale, palette)
}
