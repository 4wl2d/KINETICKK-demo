// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.interaction.canvas.look

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import kinetickk.foundation.design.KkRasterImage
import kinetickk.foundation.design.drawRuneMedallion
import kinetickk.foundation.design.kkRasterImage

/**
 * Relic runes ([drawRelicIcon] geometry) rendered once per relic, pixel size and color into small
 * images, so the HUD, the pause view and the report draw each relic's own rune every frame without
 * building its paths and strokes again. Bounded round robin, a replaced image closed at once (its brush
 * is drawn only by [drawRelicRune], right after the lookup); draw-thread confined.
 */
internal object RelicRuneImages {
    private const val CAPACITY = 40
    private val keys = LongArray(CAPACITY) { -1L }
    private val colors = LongArray(CAPACITY)
    private val images = arrayOfNulls<KkRasterImage>(CAPACITY)
    private var next = 0

    /**
     * The rune of [definition] in a [side] px square as a brush anchored at the origin (draw it
     * under a translate); a shader brush draws without per-frame image wrapping.
     */
    fun image(definition: kinetickk.ball.content.api.RelicDefinition, side: Int, color: Color): Brush {
        val key = (definition.id.ordinal.toLong() shl 32) or side.toLong()
        val colorKey = color.value.toLong()
        for (index in 0 until CAPACITY) {
            if (keys[index] == key && colors[index] == colorKey) return images[index]!!.brush
        }
        val s = side.toFloat()
        val image = iconImage(side) {
            drawRuneMedallion(
                style = definition.id.toCanvasRuneStyle(),
                center = Offset(s * 0.5f, s * 0.5f),
                radius = s / RUNE_IMAGE_SPAN,
                accent = color,
                frameSides = relicFrameSides(definition.aspect),
                frameRotation = -kotlin.math.PI.toFloat() * 0.5f,
            )
        }
        images[next]?.close()
        keys[next] = key
        colors[next] = colorKey
        images[next] = image
        next = (next + 1) % CAPACITY
        return image.brush
    }

    /** Closes every rune image (a run or window ended). */
    fun clear() {
        for (index in 0 until CAPACITY) {
            images[index]?.close()
            images[index] = null
            keys[index] = -1L
        }
    }

    /** Image side over the medallion radius: its outer glow reaches 1.22 radii. */
    const val RUNE_IMAGE_SPAN = 2.6f
}

/**
 * Sides of a relic medallion's frame by aspect, as [drawRelicIcon] draws them (exhaustive, so a
 * new aspect cannot fall back to another's frame).
 */
internal fun relicFrameSides(aspect: kinetickk.ball.content.api.RelicAspect): Int = when (aspect) {
    kinetickk.ball.content.api.RelicAspect.KINETIC -> 3
    kinetickk.ball.content.api.RelicAspect.THERMAL -> 6
    kinetickk.ball.content.api.RelicAspect.CRYO -> 8
    kinetickk.ball.content.api.RelicAspect.ION -> 4
    kinetickk.ball.content.api.RelicAspect.GRAVITIC -> 5
    kinetickk.ball.content.api.RelicAspect.ENTROPY -> 7
}

/**
 * A relic's own rune (medallion radius [radius] px) centered at [center], in [color] at [alpha],
 * from [RelicRuneImages]. Allocation-free once the image exists.
 */
internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRelicRune(
    definition: kinetickk.ball.content.api.RelicDefinition,
    center: Offset,
    radius: Float,
    color: Color,
    alpha: Float = 1f,
) {
    val side = kotlin.math.ceil(radius * RelicRuneImages.RUNE_IMAGE_SPAN).toInt().coerceIn(4, 256)
    drawImageBrush(RelicRuneImages.image(definition, side, color), center, side, alpha)
}

/**
 * An icon drawn once into a [side] px square at density 1, clear outside: its brush is allocation-free
 * to draw, unlike wrapping a bitmap per draw.
 */
private fun iconImage(side: Int, block: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit): KkRasterImage =
    kkRasterImage(side, side, androidx.compose.ui.unit.Density(1f), androidx.compose.ui.graphics.TileMode.Decal, block)

/** Draws a cached icon image's [brush] of [side] px centered at [center]. */
internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawImageBrush(brush: Brush, center: Offset, side: Int, alpha: Float) {
    if (alpha <= 0f) return
    val left = center.x - side * 0.5f
    val top = center.y - side * 0.5f
    translate(left, top) {
        drawRect(brush, Offset.Zero, androidx.compose.ui.geometry.Size(side.toFloat(), side.toFloat()), alpha = alpha.coerceIn(0f, 1f))
    }
}

/**
 * Item icons ([drawItemIcon] geometry with its stack arc) rendered once per item, stack count,
 * pixel size and color, so the pause grid redraws its icons without building their paths. A replaced
 * image is closed at once (its brush is drawn only by [drawItemImage], right after the lookup).
 */
internal object ItemIconImages {
    private const val CAPACITY = 96
    private val keys = LongArray(CAPACITY) { -1L }
    private val colors = LongArray(CAPACITY)
    private val images = arrayOfNulls<KkRasterImage>(CAPACITY)
    private var next = 0

    fun image(item: kinetickk.ball.content.api.ItemDefinition, stack: Int, side: Int, color: Color): Brush {
        val key = (item.id.toLong() shl 40) or (stack.toLong() shl 20) or side.toLong()
        val colorKey = color.value.toLong()
        for (index in 0 until CAPACITY) {
            if (keys[index] == key && colors[index] == colorKey) return images[index]!!.brush
        }
        val s = side.toFloat()
        val image = iconImage(side) { drawItemIcon(item, Offset(s * 0.5f, s * 0.5f), s / ITEM_IMAGE_SPAN, color, stack) }
        images[next]?.close()
        keys[next] = key
        colors[next] = colorKey
        images[next] = image
        next = (next + 1) % CAPACITY
        return image.brush
    }

    /** Closes every item image (a run or window ended). */
    fun clear() {
        for (index in 0 until CAPACITY) {
            images[index]?.close()
            images[index] = null
            keys[index] = -1L
        }
    }

    /** Image side over the glyph radius (its frame and stack arc reach past the radius). */
    const val ITEM_IMAGE_SPAN = 2.8f
}

/** An item icon of glyph radius [radius] px centered at [center], from [ItemIconImages]. */
internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawItemImage(
    item: kinetickk.ball.content.api.ItemDefinition,
    center: Offset,
    radius: Float,
    color: Color,
    stack: Int,
    alpha: Float = 1f,
) {
    val side = kotlin.math.ceil(radius * ItemIconImages.ITEM_IMAGE_SPAN).toInt().coerceIn(4, 256)
    drawImageBrush(ItemIconImages.image(item, stack, side, color), center, side, alpha)
}
