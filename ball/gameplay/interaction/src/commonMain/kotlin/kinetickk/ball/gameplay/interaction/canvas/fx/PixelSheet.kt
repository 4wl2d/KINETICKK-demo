// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.interaction.canvas.fx

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.DrawScope
import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.gameplay.interaction.fx.PixelFxClock
import kinetickk.ball.gameplay.interaction.fx.PixelStrip
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkRolePalette
import kinetickk.foundation.design.kkMix
import kotlin.math.atan2
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
    fun prepare() {
        EffectSprites.of(PixelStrip.HIT, 0, aimed = false)
        EffectRings.warmUp()
        EffectDither.shader(0)
        EffectLinks.shader(PixelStrip.LINK_COIL, 0)
    }

    fun step(scale: Float, texel: Float = PIXEL_FX_TEXEL): Int = max(1, (texel * scale).roundToInt())
}

/** Retains the small set of tint filters used by the world renderer. */
internal object PixelPalettes {
    private const val SLOTS = 64
    private val bodies = LongArray(SLOTS)
    private val highlights = LongArray(SLOTS)
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

    fun duo(a: Color, b: Color): ColorFilter = of(a, b)

    fun of(hue: Color): ColorFilter = of(hue, kkMix(hue, Color.White, 0.6f))

    private fun of(body: Color, highlight: Color): ColorFilter {
        val bodyKey = body.value.toLong()
        val highlightKey = highlight.value.toLong()
        for (index in 0 until count) {
            if (bodies[index] == bodyKey && highlights[index] == highlightKey) return filters[index]!!
        }
        val slot = if (count < SLOTS) count++ else oldest++ % SLOTS
        return ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
            body.red, highlight.red, 0f, 0f, 0f,
            body.green, highlight.green, 0f, 0f, 0f,
            body.blue, highlight.blue, 0f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        ))).also {
            bodies[slot] = bodyKey
            highlights[slot] = highlightKey
            filters[slot] = it
        }
    }
}

/** A draw-thread paint shared by the retained pixel paths and repeating textures. */
internal object EffectPaint {
    private val paint by lazy { Paint().apply {
        isAntiAlias = false
        filterQuality = FilterQuality.None
    } }

    fun fill(palette: ColorFilter, alpha: Float): Paint = paint.apply {
        shader = null
        color = Color.Red
        colorFilter = palette
        this.alpha = alpha
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
    val progress = if (strip.frames > 1) (frame.toFloat() / (strip.frames - 1)).coerceIn(0f, 1f) else 0f
    val sprite = EffectSprites.of(strip, frame, aimed)
    val broad = strip.w >= 32 && strip.h >= 32
    val share = if (broad) 0.36f + 0.44f * progress else 0.82f - 0.41f * progress
    val unitX = max(1, (step * strip.w * share / sprite.width).roundToInt()) / scale
    val unitY = max(1, (step * strip.h * share / sprite.height).roundToInt()) / scale
    val fade = alpha * (1f - 0.68f * progress)
    val canvas = drawContext.canvas
    canvas.save()
    canvas.translate((x * scale).roundToInt() / scale, (y * scale).roundToInt() / scale)
    if (angle != 0f) canvas.rotate(angle)
    canvas.scale(if (flipX) -unitX else unitX, if (flipY) -unitY else unitY)
    canvas.translate(-(sprite.width / 2).toFloat(), -(sprite.height / 2).toFloat())
    canvas.drawImage(sprite.bitmap, Offset.Zero, EffectPaint.fill(palette, fade))
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

/** Fills a hazard or screen boundary with a repeating pixel pattern. */
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
    val unit = PixelSheet.step(scale) / scale
    val canvas = drawContext.canvas
    canvas.save()
    canvas.translate(x, y)
    canvas.scale(unit, unit)
    val paint = EffectPaint.fill(palette, alpha)
    paint.shader = EffectDither.shader(level.coerceIn(0, PIXEL_FILL_LEVELS - 1))
    canvas.drawRect(0f, 0f, (endX - x) / unit, (endY - y) / unit, paint)
    canvas.restore()
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
    EffectRings.draw(drawContext.canvas, x, y, radius, stage, scale, palette, paletteB, alpha)
}

private const val LINK_PATTERN_WIDTH = 16f
private const val LINK_PATTERN_HEIGHT = 5f
private const val DEGREES_PER_RADIAN = 57.29578f

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
    val tileWidth = if (stretched) reach else strip.w * unit
    val drawn = if (stretched) reach else min(reach, maxTiles * tileWidth)
    val width = tileWidth / LINK_PATTERN_WIDTH
    val height = (strip.h * 0.6f).coerceAtLeast(1f) * unit / LINK_PATTERN_HEIGHT
    val canvas = drawContext.canvas
    canvas.save()
    canvas.translate(fromX, fromY)
    canvas.rotate(atan2(dy, dx) * DEGREES_PER_RADIAN)
    canvas.scale(width, height)
    canvas.translate(0f, -LINK_PATTERN_HEIGHT * 0.5f)
    val paint = EffectPaint.fill(palette, alpha)
    paint.shader = EffectLinks.shader(strip, frame)
    canvas.drawRect(0f, 0f, drawn / width, LINK_PATTERN_HEIGHT, paint)
    canvas.restore()
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
