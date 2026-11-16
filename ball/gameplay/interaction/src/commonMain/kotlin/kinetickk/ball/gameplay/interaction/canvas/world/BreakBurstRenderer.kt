// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.interaction.canvas.world

import kinetickk.foundation.design.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.interaction.fx.PixelFxClock
import kinetickk.ball.gameplay.interaction.fx.VisualFxProjection
import kinetickk.ball.gameplay.interaction.canvas.fx.PIXEL_FX_TEXEL
import kinetickk.ball.gameplay.interaction.canvas.*
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/** Reaction name stamps slam, hold and go out on ticks of this frame rate. */
internal const val REACTION_STAMP_FPS = 12

/** The default Text size setting (`PlayerPreferences.textScale`), at which the damage numbers' texel has its standard size. */
private const val STAMP_TEXT_SCALE = 1.25f

/** A resting stamp's texel is this many of the damage numbers' standard texel. */
private const val STAMP_TEXEL_MULTIPLE = 2

/** A stamp stands this many device pixels above its enemy. */
private const val STAMP_RISE = 46f

/** Uses the interface fonts to draw a brief mark when the player discovers a reaction. */
internal fun DrawScope.drawReactionStamps(
    engine: GameplayRenderModel,
    visualFx: VisualFxProjection,
    shakeX: Float,
    shakeY: Float,
    textMeasurer: TextMeasurer,
) {
    val stamps = visualFx.reactionStamps
    if (stamps.isEmpty()) return
    val roles = textMeasurer.roles
    val reactions = engine.content.build.reactions
    // The Text size setting scales stamps as it scales damage numbers.
    val unit = PIXEL_FX_TEXEL * density * fontScale * textMeasurer.scale / STAMP_TEXT_SCALE
    val edge = d(8f)
    for (index in stamps.indices) {
        val stamp = stamps[index]
        val definition = reactions.getOrNull(stamp.reaction) ?: continue
        val center = worldOnScreen(engine, stamp.x, stamp.y, shakeX, shakeY)
        if (!isOnScreen(center, 160f)) continue
        val tick = PixelFxClock.tick(engine.fxAge(stamp.life, stamp.maxLife), REACTION_STAMP_FPS)
        val layers = reactionStampLayers(tick, stamp.maxLife)
        if (layers == 0) continue
        val name = WorldStrings.reactionName(definition.name, stamp.reaction, textMeasurer.language)
        val step = reactionStampStep(STAMP_TEXEL_MULTIPLE * max(1, unit.roundToInt()), tick)
        val echo = reactionStampEcho(tick) * step
        val shadow = echo + step
        val style = textMeasurer.typography.condStyle(effectLabelSize(textMeasurer, step))
        val available = (size.width - (edge + shadow) * 2f).coerceAtLeast(1f)
        val label = measureKkText(textMeasurer, name, style, uppercase = true, maxWidth = available)
        val half = label.size.width * 0.5f
        val halfHeight = label.kkBoxHeight * 0.5f
        val x = keepInside(center.x, half, half + shadow, size.width, edge)
        val y = keepInside(center.y - STAMP_RISE, halfHeight, halfHeight + shadow, size.height, edge)
        if (layers >= 2) drawKkText(label, x + shadow, y + shadow, Kk.Ink, KkAlign.CENTER, KkVAlign.CENTER)
        if (layers >= 3) {
            val second = roles.element(definition.id.second.ordinal)
            drawKkText(label, x + echo, y + echo, second, KkAlign.CENTER, KkVAlign.CENTER)
        }
        drawKkText(label, x, y, roles.element(definition.id.first.ordinal), KkAlign.CENTER, KkVAlign.CENTER)
    }
}

/**
 * Device pixels per texel of a stamp that rests at [settled], on [tick] of its life: half again as large on contact
 * (at least two steps up), a step under its size on the next tick, its own size after that.
 */
internal fun reactionStampStep(settled: Int, tick: Int): Int = when (tick) {
    0 -> max((settled * 1.5f).roundToInt(), settled + 2)
    1 -> max(1, settled - 1)
    else -> settled
}

/** Texels the echo sits down and right of the face on [tick]: thrown out on contact, pressed in, then two. */
internal fun reactionStampEcho(tick: Int): Int = when (tick) {
    0 -> 3
    1 -> 1
    else -> 2
}

/**
 * Layers a stamp of [life] seconds shows on [tick]: all three (shadow, echo, face), then the echo goes four ticks
 * before the end, the shadow two ticks before it and the face on the last tick; 0 before it starts and once it is gone.
 */
internal fun reactionStampLayers(tick: Int, life: Float): Int {
    val last = ceil(life * REACTION_STAMP_FPS).toInt() - 1
    return when {
        tick < 0 || tick >= last -> 0
        tick >= last - 2 -> 1
        tick >= last - 4 -> 2
        else -> 3
    }
}
