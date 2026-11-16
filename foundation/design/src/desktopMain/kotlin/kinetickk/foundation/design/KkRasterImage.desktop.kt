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
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.IRect
import org.jetbrains.skia.Image
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Shader
import org.jetbrains.skia.impl.BufferUtil
import org.jetbrains.skia.impl.use
import java.lang.ref.Reference
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
    // Immutable pixels are shared by the image and its shader; Skia copies a mutable bitmap's.
    val image = kkSkiaRaster(width, height, density, block).use { Image.makeFromBitmap(it.setImmutable()) }
    val mode = tileMode.filterTileMode()
    return KkRasterImage(image, image.makeShader(mode, mode, SamplingMode.DEFAULT), 0, 0)
}

actual fun kkInkImage(width: Int, height: Int, density: Density, block: DrawScope.() -> Unit): KkRasterImage? =
    kkSkiaRaster(width, height, density, block).use { raster ->
        val bounds = inkBounds(raster) ?: return null
        // An image of the mutable inked subset copies just those pixels; the padded raster is freed here.
        val image = Bitmap().use { subset ->
            check(raster.extractSubset(subset, IRect.makeLTRB(bounds.left, bounds.top, bounds.right, bounds.bottom)))
            Image.makeFromBitmap(subset)
        }
        KkRasterImage(image, image.makeShader(FilterTileMode.DECAL, FilterTileMode.DECAL, SamplingMode.LINEAR), bounds.left, bounds.top)
    }

/** A raster laid out exactly like Compose's `ImageBitmap(width, height)`, drawn and its canvas closed. */
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

private fun TileMode.filterTileMode(): FilterTileMode = when (this) {
    TileMode.Repeated -> FilterTileMode.REPEAT
    TileMode.Mirror -> FilterTileMode.MIRROR
    TileMode.Decal -> FilterTileMode.DECAL
    else -> FilterTileMode.CLAMP
}

/** Reads alpha straight from the pixels; avoids Compose Skia's temporary full-image ByteArray. */
private fun inkBounds(native: Bitmap): androidx.compose.ui.unit.IntRect? {
    val width = native.width
    val height = native.height
    val type = native.colorInfo.colorType
    val rowBytes = native.rowBytes
    // The last row need not own its trailing stride padding. End the view at its last pixel.
    val byteCount = (height - 1L) * rowBytes + width.toLong() * 4
    if (width > 0 && height > 0 && (type == ColorType.BGRA_8888 || type == ColorType.RGBA_8888) &&
        rowBytes.toLong() >= width.toLong() * 4 && byteCount in 1L..Int.MAX_VALUE.toLong()
    ) {
        val pixmap = native.peekPixels()
        if (pixmap != null) {
            try {
                // The same bounded pixel view used by Skiko's Bitmap.toBufferedImage. It is read
                // only here, never retained, and its owning bitmap stays alive through the scan.
                val pixels = BufferUtil.getByteBufferFromPointer(pixmap.addr, byteCount.toInt())
                return kkInkBounds(width, height) { x, y -> pixels.get(y * rowBytes + x * 4 + 3).toInt() != 0 }
            } finally {
                pixmap.close()
                Reference.reachabilityFence(native)
            }
        }
    }
    return kkInkBounds(width, height) { x, y -> native.getAlphaf(x, y) > 0f }
}
