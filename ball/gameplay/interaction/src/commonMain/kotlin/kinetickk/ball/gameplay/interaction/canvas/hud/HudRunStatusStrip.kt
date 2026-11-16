// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.interaction.canvas.hud

import androidx.compose.ui.graphics.drawscope.DrawScope
import kinetickk.ball.content.api.WorldLayer
import kinetickk.ball.gameplay.interaction.canvas.world.drawEasyStripPlate
import kinetickk.ball.gameplay.interaction.layout.GameplayLayoutMode
import kinetickk.ball.gameplay.interaction.layout.PORTRAIT_CHIP_ROW_DP
import kinetickk.ball.gameplay.interaction.layout.PORTRAIT_CHIP_ROW_HEIGHT_DP
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.foundation.design.TextMeasurer
import kotlin.math.min

/** The active dimension or Baby mode marker. The portrait layout shares space with the chain. */
internal fun DrawScope.drawRunStatusStrip(engine: GameplayRenderModel, hostMeasurer: TextMeasurer, frame: HudFrame) {
    if (!engine.easyMode && engine.dimension.layer == WorldLayer.BASE) return
    val centerY: Float
    val height: Float
    val stripLeft: Float
    when (frame.mode) {
        GameplayLayoutMode.REGULAR -> {
            centerY = HudScratch.badgeTop + (HudScratch.badgeBottom - HudScratch.badgeTop) * 0.5f
            height = min(frame.u(34f), HudScratch.badgeBottom - HudScratch.badgeTop)
            stripLeft = HudScratch.badgeRight + frame.u(18f)
        }
        GameplayLayoutMode.COMPACT_LANDSCAPE -> {
            centerY = HudScratch.badgeTop + (HudScratch.badgeBottom - HudScratch.badgeTop) * 0.5f
            height = min(frame.u(28f), HudScratch.badgeBottom - HudScratch.badgeTop)
            stripLeft = HudScratch.economyRight + frame.u(12f)
        }
        GameplayLayoutMode.COMPACT_PORTRAIT -> {
            height = frame.u(PORTRAIT_CHIP_ROW_HEIGHT_DP)
            centerY = frame.top + frame.u(PORTRAIT_CHIP_ROW_DP) + height * 0.5f
            stripLeft = HudScratch.economyRight + frame.u(10f)
        }
    }
    val right = if (frame.mode == GameplayLayoutMode.COMPACT_PORTRAIT) {
        // The chain ends the chip row: keep its usual width free (so the marker stays put as it
        // comes and goes) and never reach a wider one.
        min(frame.width - frame.margin - frame.u(PORTRAIT_CHAIN_RESERVE_DP), HudScratch.chainLeft - frame.u(10f))
    } else {
        // Keep clear of the clock at the top center.
        val clock = HudDrawCache.peekLayout(HudText.TIMER)?.size?.width?.toFloat() ?: frame.u(120f)
        frame.width * 0.5f - clock * 0.5f - frame.u(14f)
    }
    // A dimension visit's plate takes the strip's slot while it lasts.
    if (drawDimensionPlate(engine, hostMeasurer, frame, stripLeft, right, centerY, height)) return
    if (!engine.easyMode) return
    val plate = min(height, right - stripLeft)
    if (plate < frame.u(MIN_PLATE_DP)) return
    val top = centerY - height * 0.5f
    drawEasyStripPlate(hostMeasurer.roles, stripLeft + plate * 0.5f, centerY, plate * 0.5f, frame)
    HudLayoutProbe.record(HudBlock.RUN_STATUS, stripLeft, top, stripLeft + plate, top + height)
}

/** Chip-row width (reference dp) the portrait strip leaves for the chain at the row's end. */
private const val PORTRAIT_CHAIN_RESERVE_DP = 72f

/** The smallest legible plate (reference dp). */
internal const val MIN_PLATE_DP = 14f
