// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.interaction.canvas.world

import kinetickk.foundation.design.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kinetickk.ball.gameplay.interaction.fx.InteractionFxLimits
import kinetickk.ball.gameplay.interaction.fx.PixelFxClock
import kinetickk.ball.gameplay.interaction.fx.PixelStrip
import kinetickk.ball.gameplay.interaction.fx.VisualFxProjection
import kinetickk.ball.gameplay.nucleus.render.damageNumberTier
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.profile.api.DamageNumberSize
import kinetickk.ball.profile.api.ReactionEffectsLevel
import kinetickk.foundation.common.localization.AppLanguage
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kinetickk.ball.gameplay.interaction.canvas.*
import kinetickk.ball.gameplay.interaction.canvas.fx.*
import kinetickk.ball.gameplay.interaction.canvas.look.*

/** How far a damage number drifts away from the Core over its life (world units). */
private const val DAMAGE_NUMBER_DRIFT = 52f

/** Damage numbers pop, drift and leave on ticks of this frame rate. */
internal const val DAMAGE_NUMBER_FPS = 24

/** The default Text size setting (`PlayerPreferences.textScale`): numbers have their standard texel size at it. */
private const val STANDARD_TEXT_SCALE = 1.25f

private const val CRITICAL_GAP_TEXELS = 2
private const val GLYPH_OUTLINE_TEXELS = 1

/** Slanted pixel damage labels and critical-hit stamps move on the cue’s stepped clock. */
internal fun DrawScope.drawDamageNumbers(
    engine: GameplayRenderModel,
    visualFx: VisualFxProjection,
    shakeX: Float,
    shakeY: Float,
    textMeasurer: TextMeasurer,
) {
    val numbers = visualFx.damageNumbers
    val settings = engine.settings
    if (!settings.damageNumbers || numbers.isEmpty()) return
    val roles = textMeasurer.roles
    val worldStep = PixelSheet.step(engine.worldScale)
    // The Text size setting scales numbers as it scales every label; the default size keeps the standard texel.
    val unit = PIXEL_FX_TEXEL * density * fontScale * textMeasurer.scale / STANDARD_TEXT_SCALE
    val sizeStep = damageNumberSizeStep(unit, settings.damageNumberSize)
    val sparks = FxFrame.level != ReactionEffectsLevel.MINIMAL && FxFrame.crowd <= FxFrame.CROWD_SIG_ONLY
    val edge = d(6f)
    for (index in numbers.indices) {
        val number = numbers[index]
        val age = engine.fxAge(number.life, InteractionFxLimits.DAMAGE_NUMBER_LIFE_SECONDS)
        val color = damageNumberColor(number.tone, roles)
        val anchor = worldOnScreen(engine, number.x, number.y, shakeX, shakeY)
        val spark = PixelFxClock.frame(PixelStrip.HIT, age)
        if (sparks && spark >= 0 && isOnScreen(anchor, 60f) && FxDrawBudget.take(FxPool.TRANSIENT, 1, signature = false)) {
            drawPixelFrame(PixelStrip.HIT, spark, anchor.x, anchor.y, 1f, PixelPalettes.of(color), worldStep)
        }
        val tick = PixelFxClock.tick(age, DAMAGE_NUMBER_FPS)
        val tier = damageNumberTier(number.amount, settings.damageNumberTierThreshold, number.critical)
        // Each tier is at least one step larger than the tier below, so the four tiers differ at every density.
        val settled = max((unit * settings.damageNumberSize.scale * damageNumberScale(tier)).roundToInt(), sizeStep + tier.ordinal)
        val step = damageNumberStep(settled, tick)
        if (step <= 0) continue
        val progress = (PixelFxClock.stepped(age, DAMAGE_NUMBER_FPS) * 1_000f / KkTime.Drift).coerceIn(0f, 1f)
        val drift = KkEase.Out.transform(progress) * DAMAGE_NUMBER_DRIFT
        val location = worldOnScreen(engine, number.x + number.driftX * drift, number.y + number.driftY * drift, shakeX, shakeY)
        if (!isOnScreen(location, 80f)) continue
        val text = number.formattedAmount(settings.damageNumberFormat, textMeasurer.language)
        val crit = if (textMeasurer.language == AppLanguage.Russian) "КРИТ" else "CRIT"
        val stampStep = max(1, damageNumberStep(sizeStep, tick))
        val stampWidth = if (number.critical) (combatLabelWidth(crit) + GLYPH_OUTLINE_TEXELS) * stampStep else 0
        val stampGap = if (number.critical) CRITICAL_GAP_TEXELS * stampStep else 0
        val texels = combatLabelWidth(text, slanted = true)
        val availableStep = ((size.width - 2f * edge - stampWidth - stampGap) / (texels + 2)).toInt().coerceAtLeast(1)
        val labelStep = min(step, availableStep)
        val half = texels * labelStep * 0.5f
        val halfHeight = COMBAT_LETTER_HEIGHT * labelStep * 0.5f
        val right = half + stampGap + stampWidth + GLYPH_OUTLINE_TEXELS * labelStep
        val above = halfHeight + max(labelStep, if (number.critical) stampStep else 0)
        val below = if (number.critical) {
            max(halfHeight + labelStep, (COMBAT_LETTER_HEIGHT + GLYPH_OUTLINE_TEXELS) * stampStep - halfHeight)
        } else halfHeight + labelStep
        val x = keepInside(location.x, half + labelStep, right, size.width, edge)
        val y = keepInside(location.y, above, below, size.height, edge)
        if (number.critical) {
            drawCriticalHit(age, Offset(x, y), roles.threat, worldStep)
            drawCombatLabel(crit, x + half + stampGap, y - halfHeight, stampStep, roles.threat)
        }
        drawCombatLabel(text, x - half, y - halfHeight, labelStep, color, slanted = true)
    }
}

private fun DrawScope.drawCriticalHit(age: Float, anchor: Offset, color: Color, worldStep: Int) {
    val frame = PixelFxClock.frame(PixelStrip.CRIT, age)
    if (frame < 0) return
    FxDrawBudget.take(FxPool.TRANSIENT, 1, signature = true)
    drawPixelFrame(PixelStrip.CRIT, frame, anchor.x, anchor.y, 1f, PixelPalettes.of(color), worldStep)
}

/**
 * Moves a label centered at [center] with extents [before]/[after] along one axis so it lies
 * within [edge]..[length] − [edge]; centered when it cannot fit.
 */
internal fun keepInside(center: Float, before: Float, after: Float, length: Float, edge: Float): Float {
    val low = edge + before
    val high = length - edge - after
    return if (low > high) (low + high) * 0.5f else center.coerceIn(low, high)
}

/**
 * Whole device pixels per texel of a standard number at [size], where a texel is [unit] device pixels at the Normal
 * size: Normal rounds to its own value, and every other size is at least one step from its neighbour toward Normal,
 * so the four sizes differ at every screen density (the smallest never below one pixel).
 */
internal fun damageNumberSizeStep(unit: Float, size: DamageNumberSize): Int {
    var step = max(1, (unit * DamageNumberSize.NORMAL.scale).roundToInt())
    var at = DamageNumberSize.NORMAL.ordinal
    while (at < size.ordinal) step = max((unit * DamageNumberSize.entries[++at].scale).roundToInt(), step + 1)
    while (at > size.ordinal) step = max(1, min((unit * DamageNumberSize.entries[--at].scale).roundToInt(), step - 1))
    return step
}

/**
 * Device pixels per texel of a number that rests at [settled], on [tick] of its life at [DAMAGE_NUMBER_FPS]: about
 * half again as large on contact and a quarter larger on the next tick (always at least two steps and one step up),
 * its own size after that, then smaller over its last two ticks; 0 once it is gone.
 */
internal fun damageNumberStep(settled: Int, tick: Int): Int {
    val last = (InteractionFxLimits.DAMAGE_NUMBER_LIFE_SECONDS * DAMAGE_NUMBER_FPS).toInt()
    return when {
        tick < 0 || tick > last -> 0
        tick == 0 -> max((settled * 1.5f).roundToInt(), settled + 2)
        tick == 1 -> max((settled * 1.25f).roundToInt(), settled + 1)
        tick >= last -> max(1, min((settled * 0.5f).roundToInt(), settled - 2))
        tick == last - 1 -> max(1, min((settled * 0.75f).roundToInt(), settled - 1))
        else -> settled
    }
}
