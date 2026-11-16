// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.interaction.canvas.fx

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.gameplay.interaction.fx.PixelFxClock
import kinetickk.ball.gameplay.interaction.fx.PixelStrip
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkRolePalette
import kinetickk.foundation.design.kkMix
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

internal const val PIXEL_FX_TEXEL = 2f
internal const val MAX_LINK_TILES = 48
internal const val PIXEL_FILL_LEVELS = 5
internal const val PIXEL_RING_OPS = 4

/** Shared device-pixel scale for geometric effects. */
internal object PixelSheet {
    fun step(scale: Float, texel: Float = PIXEL_FX_TEXEL): Int = max(1, (texel * scale).roundToInt())
}

/** Retains the small set of tint filters used by the world renderer. */
internal object PixelPalettes {
    private const val SLOTS = 64
    private val colors = LongArray(SLOTS)
    private val filters = arrayOfNulls<ColorFilter>(SLOTS)
    private var count = 0
    private var oldest = 0
    private var prepared: KkRolePalette? = null

    fun prepare(roles: KkRolePalette) {
        if (prepared === roles) return
        prepared = roles
        of(roles.you)
        of(roles.threat)
        of(roles.kinetic)
        of(roles.soft)
        of(roles.gold.base)
        of(roles.gold.light)
        of(Kk.Bone)
        of(Kk.Bone2)
        of(Kk.Mute2)
        for (element in 0..6) of(roles.element(element))
        for (reaction in ReactionId.entries) duo(roles.element(reaction.first.ordinal), roles.element(reaction.second.ordinal))
    }

    fun duo(a: Color, b: Color): ColorFilter = of(kkMix(a, b, 0.5f))

    fun of(hue: Color): ColorFilter {
        val key = hue.value.toLong()
        for (index in 0 until count) if (colors[index] == key) return filters[index]!!
        val slot = if (count < SLOTS) count++ else oldest++ % SLOTS
        return ColorFilter.tint(hue).also {
            colors[slot] = key
            filters[slot] = it
        }
    }
}

/** These draw-thread resources are independent of the viewport and effect. */
private object EffectPaint {
    val paint by lazy { Paint().apply { isAntiAlias = true } }
    val diamond by lazy {
        Path().apply {
            moveTo(1f, 0f)
            lineTo(0f, 1f)
            lineTo(-1f, 0f)
            lineTo(0f, -1f)
            close()
        }
    }
    val chevron by lazy {
        Path().apply {
            moveTo(-1f, -1f)
            lineTo(1f, 0f)
            lineTo(-1f, 1f)
        }
    }

    fun fill(palette: ColorFilter, alpha: Float): Paint = paint.apply {
        color = Color.White
        colorFilter = palette
        this.alpha = alpha
        style = PaintingStyle.Fill
    }

    fun stroke(palette: ColorFilter, alpha: Float, width: Float): Paint = fill(palette, alpha).apply {
        style = PaintingStyle.Stroke
        strokeWidth = width
    }
}

/** Draws a spark or expanding outline at the cue's current animation frame. */
internal fun DrawScope.drawPixelFrame(
    strip: PixelStrip,
    frame: Int,
    x: Float,
    y: Float,
    scale: Float,
    palette: ColorFilter,
    step: Int = PixelSheet.step(scale),
    alpha: Float = 1f,
    flipX: Boolean = false,
    flipY: Boolean = false,
    turns: Int = 0,
) {
    drawPieceFrame(strip, frame, x, y, scale, palette, step, alpha, flipX, flipY, turns * 90f, aimed = false)
}

private fun DrawScope.drawPieceFrame(
    strip: PixelStrip,
    frame: Int,
    x: Float,
    y: Float,
    scale: Float,
    palette: ColorFilter,
    step: Int,
    alpha: Float,
    flipX: Boolean,
    flipY: Boolean,
    angle: Float,
    aimed: Boolean,
) {
    if (frame < 0 || alpha <= 0f) return
    val progress = if (strip.frames > 1) frame.toFloat() / (strip.frames - 1) else 0f
    val fade = alpha * (1f - 0.72f * progress)
    val unit = step / scale
    val broad = strip.w >= 32 && strip.h >= 32
    val extent = if (broad) 0.14f + 0.29f * progress else 0.24f - 0.09f * progress
    val halfWidth = strip.w * unit * extent
    val halfHeight = strip.h * unit * extent
    val canvas = drawContext.canvas
    canvas.save()
    canvas.translate((x * scale).roundToInt() / scale, (y * scale).roundToInt() / scale)
    if (angle != 0f) canvas.rotate(angle)
    canvas.scale(if (flipX) -halfWidth else halfWidth, if (flipY) -halfHeight else halfHeight)
    when {
        aimed -> canvas.drawPath(EffectPaint.chevron, EffectPaint.stroke(palette, fade, 0.24f))
        broad -> canvas.drawPath(EffectPaint.diamond, EffectPaint.stroke(palette, fade, 0.13f))
        else -> canvas.drawPath(EffectPaint.diamond, EffectPaint.fill(palette, fade))
    }
    canvas.restore()
}

private const val EIGHTHS_PER_RADIAN = 1.2732395f

internal fun DrawScope.drawPixelAimed(
    cardinal: PixelStrip,
    diagonal: PixelStrip,
    frame: Int,
    x: Float,
    y: Float,
    angle: Float,
    scale: Float,
    palette: ColorFilter,
    alpha: Float = 1f,
) {
    val eighth = (angle * EIGHTHS_PER_RADIAN).roundToInt() and 7
    val strip = if (eighth and 1 == 0) cardinal else diagonal
    drawPieceFrame(strip, frame, x, y, scale, palette, PixelSheet.step(scale), alpha,
        flipX = false, flipY = false, angle = eighth * 45f, aimed = true)
}

/** Fills a hazard or screen boundary with a stepped level of opacity. */
internal fun DrawScope.drawPixelFill(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    level: Int,
    scale: Float,
    palette: ColorFilter,
    alpha: Float = 1f,
): Int {
    val x = (left * scale).roundToInt() / scale
    val y = (top * scale).roundToInt() / scale
    val endX = (right * scale).roundToInt() / scale
    val endY = (bottom * scale).roundToInt() / scale
    if (endX <= x || endY <= y || alpha <= 0f) return 0
    val opacity = (level.coerceIn(0, PIXEL_FILL_LEVELS - 1) + 1f) / PIXEL_FILL_LEVELS
    drawContext.canvas.drawRect(x, y, endX, endY, EffectPaint.fill(palette, alpha * opacity))
    return 1
}

/** Draws an expanding ring whose four arcs can alternate between two effect colours. */
internal fun DrawScope.drawPixelRing(
    x: Float,
    y: Float,
    radius: Float,
    stage: Int,
    scale: Float,
    palette: ColorFilter,
    paletteB: ColorFilter = palette,
    alpha: Float = 1f,
) {
    if (radius <= 0f || alpha <= 0f) return
    val phase = stage.coerceIn(0, 4)
    val width = (3f - phase * 0.48f) * PixelSheet.step(scale) / scale
    val fade = alpha * (1f - phase * 0.17f)
    val canvas = drawContext.canvas
    for (quadrant in 0..3) {
        val paint = EffectPaint.stroke(if (quadrant and 1 == 0) palette else paletteB, fade, width)
        canvas.drawArc(x - radius, y - radius, x + radius, y + radius, quadrant * 90f, 90f - phase * 11f, false, paint)
    }
}

/** Draws a link up to its cue's length and draw budget. */
internal fun DrawScope.drawPixelLink(
    strip: PixelStrip,
    frame: Int,
    fromX: Float,
    fromY: Float,
    toX: Float,
    toY: Float,
    scale: Float,
    palette: ColorFilter,
    alpha: Float = 1f,
    stretched: Boolean = false,
    maxTiles: Int = MAX_LINK_TILES,
): Int {
    val dx = toX - fromX
    val dy = toY - fromY
    val reach = sqrt(dx * dx + dy * dy)
    val unit = PixelSheet.step(scale) / scale
    if (frame < 0 || reach < unit || alpha <= 0f || maxTiles <= 0) return 0
    val extent = if (stretched) 1f else min(1f, maxTiles * strip.w * unit / reach)
    val width = (strip.h * 0.18f + 0.3f * (frame and 1)) * unit
    val paint = EffectPaint.stroke(palette, alpha, width)
    drawContext.canvas.drawLine(Offset(fromX, fromY), Offset(fromX + dx * extent, fromY + dy * extent), paint)
    return 1
}

internal fun ringStage(progress: Float): Int = when {
    progress < 0.16f -> 0
    progress < 0.42f -> 1
    progress < 0.66f -> 2
    progress < 0.86f -> 3
    else -> 4
}

internal fun steppedProgress(age: Float, life: Float, fps: Int): Float =
    if (life <= 0f) 1f else ((PixelFxClock.tick(age, fps) + 0.5f) / (fps * life)).coerceIn(0f, 1f)
