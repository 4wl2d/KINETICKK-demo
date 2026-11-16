// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.interaction.canvas.world

import kinetickk.foundation.design.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.interaction.fx.PixelFxClock
import kinetickk.ball.gameplay.interaction.fx.VisualFxProjection
import kinetickk.ball.gameplay.interaction.canvas.fx.COMBAT_LETTER_HEIGHT
import kinetickk.ball.gameplay.interaction.canvas.fx.PIXEL_FX_TEXEL
import kinetickk.ball.gameplay.interaction.canvas.fx.combatLabelWidth
import kinetickk.ball.gameplay.interaction.canvas.fx.drawCombatLabel
import kinetickk.ball.gameplay.interaction.canvas.*
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Reaction name stamps slam, hold and go out on ticks of this frame rate. */
internal const val REACTION_STAMP_FPS = 12

/** The default Text size setting (`PlayerPreferences.textScale`), at which the damage numbers' texel has its standard size. */
private const val STAMP_TEXT_SCALE = 1.25f

/** A resting stamp's texel is this many of the damage numbers' standard texel. */
private const val STAMP_TEXEL_MULTIPLE = 2

/** A stamp stands this many device pixels above its enemy. */
private const val STAMP_RISE = 46f

/** The first time a reaction is discovered, layer its pixel label over a second-hue echo and ink shadow. */
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
        val texels = combatLabelWidth(name, slanted = true)
        val echoTexels = reactionStampEcho(tick)
        val fits = ((size.width - edge * 2f) / (texels + echoTexels + 3)).toInt().coerceAtLeast(1)
        val step = min(reactionStampStep(STAMP_TEXEL_MULTIPLE * max(1, unit.roundToInt()), tick), fits)
        val echo = echoTexels * step
        val shadow = echo + step
        val half = texels * step * 0.5f
        val halfHeight = COMBAT_LETTER_HEIGHT * step * 0.5f
        val x = keepInside(center.x, half + step, half + shadow + step, size.width, edge) - half
        val y = keepInside(center.y - STAMP_RISE, halfHeight + step, halfHeight + shadow + step, size.height, edge) - halfHeight
        if (layers >= 2) drawCombatLabel(name, x + shadow, y + shadow, step, Kk.Ink, slanted = true, silhouetteOnly = true)
        if (layers >= 3) {
            val second = roles.element(definition.id.second.ordinal)
            drawCombatLabel(name, x + echo, y + echo, step, second, slanted = true, silhouetteOnly = true)
        }
        val first = roles.element(definition.id.first.ordinal)
        drawCombatLabel(name, x, y, step, kkMix(first, Color.White, 0.55f), slanted = true)
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
