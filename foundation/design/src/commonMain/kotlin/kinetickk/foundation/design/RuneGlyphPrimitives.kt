// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.foundation.design

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import kotlin.math.cos
import kotlin.math.PI
import kotlin.math.sin

private val RuneInk = Color(0xFFF4F6FF)
private val RuneBackground = Kk.Ink
private const val RUNE_TAU = 6.2831855f

/** Geometric rune styles; Content identifiers are mapped only inside Interaction. */
enum class CanvasRuneStyle {
    RADIAL_WHEEL,
    OFFSET_RINGS,
    BOLT_AND_DOT,
    FOUR_SWIRL_ARMS,
    NESTED_DIAMONDS,
    CROSSED_DIAMOND,
    CRACKED_RING,
    STITCHED_DIAGONAL,
    SIX_PETAL_ROSETTE,
    DIAMOND_FLAME,
}

/** Draws a rune and polygonal frame. */
fun DrawScope.drawRuneMedallion(
    style: CanvasRuneStyle,
    center: Offset,
    radius: Float,
    accent: Color,
    frameSides: Int,
    frameRotation: Float = -PI.toFloat() * 0.5f,
    markerCount: Int = 0,
    filledMarkerCount: Int = 0,
) {
    if (radius <= 0f || accent.alpha <= 0f) return
    val stroke = (radius * 0.085f).coerceAtLeast(0.65f)
    drawCircle(accent.copy(alpha = accent.alpha * 0.09f), radius * 1.22f, center)
    drawPolygon(center, radius, frameSides, frameRotation, RuneBackground.copy(alpha = 0.9f), Fill)
    drawPolygon(center, radius, frameSides, frameRotation, accent.copy(alpha = accent.alpha * 0.82f), Stroke(stroke))
    drawCircle(accent.copy(alpha = accent.alpha * 0.18f), radius * 0.78f, center, style = Stroke(stroke * 0.72f))
    drawRune(style, center, radius * 0.62f, accent)
    repeat(markerCount) { index ->
        val angle = PI.toFloat() * 0.72f + index * PI.toFloat() * 0.14f
        drawCircle(
            color = if (index < filledMarkerCount) RuneInk.copy(alpha = accent.alpha)
                else accent.copy(alpha = accent.alpha * 0.22f),
            radius = (radius * 0.052f).coerceAtLeast(0.7f),
            center = runePolar(center, radius * 1.08f, angle),
        )
    }
}

private fun DrawScope.drawRune(style: CanvasRuneStyle, center: Offset, radius: Float, color: Color) {
    val stroke = (radius * 0.14f).coerceAtLeast(0.7f)
    val thin = (radius * 0.085f).coerceAtLeast(0.55f)
    when (style) {
        CanvasRuneStyle.RADIAL_WHEEL -> drawRadialWheelRune(center, radius, color, stroke, thin)
        CanvasRuneStyle.OFFSET_RINGS -> drawOffsetRingsRune(center, radius, color, stroke, thin)
        CanvasRuneStyle.BOLT_AND_DOT -> drawBoltAndDotRune(center, radius, color)
        CanvasRuneStyle.FOUR_SWIRL_ARMS -> drawFourSwirlArmsRune(center, radius, color, stroke)
        CanvasRuneStyle.NESTED_DIAMONDS -> drawNestedDiamondsRune(center, radius, color, stroke, thin)
        CanvasRuneStyle.CROSSED_DIAMOND -> drawCrossedDiamondRune(center, radius, color, stroke, thin)
        CanvasRuneStyle.CRACKED_RING -> drawCrackedRingRune(center, radius, color, stroke, thin)
        CanvasRuneStyle.STITCHED_DIAGONAL -> drawStitchedDiagonalRune(center, radius, color, stroke, thin)
        CanvasRuneStyle.SIX_PETAL_ROSETTE -> drawSixPetalRosetteRune(center, radius, color, thin)
        CanvasRuneStyle.DIAMOND_FLAME -> drawDiamondFlameRune(center, radius, color)
    }
}

private fun DrawScope.drawRadialWheelRune(center: Offset, radius: Float, color: Color, stroke: Float, thin: Float) {
    drawCircle(color, radius * 0.72f, center, style = Stroke(stroke))
    repeat(6) { index -> drawLine(color, center, runePolar(center, radius * 0.72f, index * RUNE_TAU / 6f), thin) }
    drawCircle(RuneInk, radius * 0.16f, center)
}

private fun DrawScope.drawOffsetRingsRune(center: Offset, radius: Float, color: Color, stroke: Float, thin: Float) {
    drawCircle(color.copy(alpha = color.alpha * 0.45f), radius * 0.7f, Offset(center.x - radius * 0.22f, center.y), style = Stroke(stroke))
    drawCircle(RuneInk, radius * 0.55f, Offset(center.x + radius * 0.24f, center.y), style = Stroke(thin))
    drawCircle(color, radius * 0.16f, center)
}

private fun DrawScope.drawBoltAndDotRune(center: Offset, radius: Float, color: Color) {
    drawBolt(center, radius, color)
    drawCircle(RuneInk, radius * 0.16f, Offset(center.x + radius * 0.72f, center.y - radius * 0.72f))
}

private fun DrawScope.drawFourSwirlArmsRune(center: Offset, radius: Float, color: Color, stroke: Float) {
    repeat(4) { index ->
        val angle = index * RUNE_TAU / 4f
        drawLine(color, runePolar(center, radius * 0.38f, angle), runePolar(center, radius * 0.9f, angle + 0.24f), stroke, StrokeCap.Round)
    }
    drawPolygon(center, radius * 0.25f, 4, PI.toFloat() / 4f, RuneInk, Fill)
}

private fun DrawScope.drawNestedDiamondsRune(center: Offset, radius: Float, color: Color, stroke: Float, thin: Float) {
    repeat(3) { index -> drawPolygon(center, radius * (0.92f - index * 0.25f), 4, PI.toFloat() / 4f, if (index == 2) RuneInk else color.copy(alpha = color.alpha * (1f - index * 0.2f)), Stroke(if (index == 2) stroke else thin)) }
}

private fun DrawScope.drawCrossedDiamondRune(center: Offset, radius: Float, color: Color, stroke: Float, thin: Float) {
    drawPolygon(center, radius * 0.88f, 4, PI.toFloat() / 4f, color, Stroke(stroke))
    drawLine(RuneInk, Offset(center.x - radius * 0.52f, center.y - radius * 0.52f), Offset(center.x + radius * 0.52f, center.y + radius * 0.52f), thin)
    drawLine(RuneInk, Offset(center.x + radius * 0.52f, center.y - radius * 0.52f), Offset(center.x - radius * 0.52f, center.y + radius * 0.52f), thin)
    drawCircle(color, radius * 0.13f, center)
}

private fun DrawScope.drawCrackedRingRune(center: Offset, radius: Float, color: Color, stroke: Float, thin: Float) {
    drawCircle(color, radius * 0.76f, center, style = Stroke(stroke))
    drawLine(RuneInk, Offset(center.x - radius * 0.15f, center.y - radius * 0.74f), Offset(center.x + radius * 0.12f, center.y - radius * 0.1f), thin)
    drawLine(RuneInk, Offset(center.x + radius * 0.12f, center.y - radius * 0.1f), Offset(center.x - radius * 0.38f, center.y + radius * 0.68f), thin)
    drawLine(RuneInk, Offset(center.x + radius * 0.12f, center.y - radius * 0.1f), Offset(center.x + radius * 0.68f, center.y + radius * 0.42f), thin)
}

private fun DrawScope.drawStitchedDiagonalRune(center: Offset, radius: Float, color: Color, stroke: Float, thin: Float) {
    drawLine(color, Offset(center.x - radius * 0.72f, center.y - radius * 0.82f), Offset(center.x + radius * 0.72f, center.y + radius * 0.82f), stroke, StrokeCap.Round)
    repeat(4) { index ->
        val t = (index + 1f) / 5f
        val p = Offset(center.x - radius * 0.72f + radius * 1.44f * t, center.y - radius * 0.82f + radius * 1.64f * t)
        drawLine(RuneInk, Offset(p.x - radius * 0.28f, p.y + radius * 0.22f), Offset(p.x + radius * 0.28f, p.y - radius * 0.22f), thin, StrokeCap.Round)
    }
}

private fun DrawScope.drawSixPetalRosetteRune(center: Offset, radius: Float, color: Color, thin: Float) {
    repeat(6) { index ->
        val angle = index * RUNE_TAU / 6f
        val petal = runePolar(center, radius * 0.5f, angle)
        drawCircle(color.copy(alpha = color.alpha * 0.28f), radius * 0.38f, petal)
        drawCircle(color, radius * 0.38f, petal, style = Stroke(thin))
    }
    drawCircle(RuneInk, radius * 0.2f, center)
}

private fun DrawScope.drawDiamondFlameRune(center: Offset, radius: Float, color: Color) {
    val flame = Path().apply {
        moveTo(center.x, center.y - radius)
        cubicTo(center.x + radius * 0.75f, center.y - radius * 0.2f, center.x + radius * 0.65f, center.y + radius * 0.72f, center.x, center.y + radius * 0.9f)
        cubicTo(center.x - radius * 0.72f, center.y + radius * 0.42f, center.x - radius * 0.35f, center.y - radius * 0.18f, center.x, center.y - radius)
        close()
    }
    drawPath(flame, color, style = Fill)
    drawPolygon(Offset(center.x, center.y + radius * 0.22f), radius * 0.28f, 4, PI.toFloat() / 4f, RuneInk, Fill)
}

private fun DrawScope.drawBolt(center: Offset, radius: Float, color: Color) {
    val bolt = Path().apply {
        moveTo(center.x + radius * 0.18f, center.y - radius)
        lineTo(center.x - radius * 0.55f, center.y + radius * 0.05f)
        lineTo(center.x - radius * 0.08f, center.y)
        lineTo(center.x - radius * 0.35f, center.y + radius)
        lineTo(center.x + radius * 0.62f, center.y - radius * 0.18f)
        lineTo(center.x + radius * 0.1f, center.y - radius * 0.08f)
        close()
    }
    drawPath(bolt, color, style = Fill)
}

private fun runePolar(center: Offset, radius: Float, angle: Float): Offset =
    Offset(center.x + cos(angle) * radius, center.y + sin(angle) * radius)
