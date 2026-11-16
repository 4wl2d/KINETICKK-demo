// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.foundation.design

import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asComposeShader
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.MipmapMode

internal actual val KkCardImagesSupported: Boolean = true

internal actual fun kkCardImage(width: Int, height: Int, density: Density, block: DrawScope.() -> Unit): KkCardImage {
    val pixels = kkSkiaRaster(width, height, density, block)
    // Immutable pixels are shared with the shader's image instead of copied.
    val shader = try {
        pixels.setImmutable().makeShader(FilterTileMode.DECAL, FilterTileMode.DECAL, FilterMipmap(FilterMode.LINEAR, MipmapMode.LINEAR))
    } catch (failure: Throwable) {
        pixels.close()
        throw failure
    }
    return KkCardImage(ShaderBrush(shader.asComposeShader()), width, height) {
        shader.close()
        pixels.close()
    }
}
