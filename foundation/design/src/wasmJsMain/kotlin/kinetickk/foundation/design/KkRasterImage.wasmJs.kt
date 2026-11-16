// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.foundation.design

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.graphics.asComposeShader
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Shader
import org.jetbrains.skia.impl.use
import org.jetbrains.skia.Canvas as SkCanvas

actual class KkRasterImage internal constructor(
    private val image: Image,
    private val shader: Shader,
    actual val left: Int,
    actual val top: Int,
) {
    actual val width: Int get() = image.width
    actual val height: Int get() = image.height
    actual val brush: Brush = ShaderBrush(shader.asComposeShader())

    actual fun close() {
        shader.close()
        image.close()
    }
}

actual fun kkRasterImage(width: Int, height: Int, density: Density, tileMode: TileMode, block: DrawScope.() -> Unit): KkRasterImage {
    val image = raster(width, height, density, block)
    val mode = when (tileMode) {
        TileMode.Repeated -> FilterTileMode.REPEAT
        TileMode.Mirror -> FilterTileMode.MIRROR
        TileMode.Decal -> FilterTileMode.DECAL
        else -> FilterTileMode.CLAMP
    }
    return KkRasterImage(image, image.makeShader(mode, mode, SamplingMode.DEFAULT), 0, 0)
}

// The whole raster stays until a browser measurement justifies reading its pixels back to crop.
actual fun kkInkImage(width: Int, height: Int, density: Density, block: DrawScope.() -> Unit): KkRasterImage? {
    val image = raster(width, height, density, block)
    return KkRasterImage(image, image.makeShader(FilterTileMode.DECAL, FilterTileMode.DECAL, SamplingMode.LINEAR), 0, 0)
}

private fun raster(width: Int, height: Int, density: Density, block: DrawScope.() -> Unit): Image =
    kkSkiaRaster(width, height, density, block).use { Image.makeFromBitmap(it.setImmutable()) }

/** A raster laid out like Compose's `ImageBitmap(width, height)`, drawn and its canvas closed. */
internal fun kkSkiaRaster(width: Int, height: Int, density: Density, block: DrawScope.() -> Unit): Bitmap {
    val bitmap = ImageBitmap(width, height).asSkiaBitmap()
    try {
        SkCanvas(bitmap).use { canvas ->
            CanvasDrawScope().draw(density, LayoutDirection.Ltr, canvas.asComposeCanvas(), Size(width.toFloat(), height.toFloat()), block)
        }
        return bitmap
    } catch (failure: Throwable) {
        bitmap.close()
        throw failure
    }
}
