// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.flow.session.interaction.home.impl

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import kinetickk.flow.session.interaction.home.api.HomeUiModel
import kinetickk.flow.session.interaction.localization.SessionRedesignText
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.CanvasTextMeasurer
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkPacifierSigilPaths
import kinetickk.foundation.design.KkStampVariant
import kinetickk.foundation.design.KkVAlign
import kinetickk.foundation.design.condStyle
import kinetickk.foundation.design.drawKkGem
import kinetickk.foundation.design.drawKkStamp
import kinetickk.foundation.design.drawKkText
import kinetickk.foundation.design.kkStampSize
import kinetickk.foundation.design.kkStroke
import kinetickk.foundation.design.measureKkText
import kinetickk.foundation.design.monoStyle
import kinetickk.foundation.design.wideStyle
import kinetickk.foundation.common.localization.formatGroupedNumber

internal fun DrawScope.drawHeader(
    model: HomeUiModel,
    text: CanvasTextMeasurer,
    layout: HomeLayoutGeometry,
    state: HomeDrawState,
    accent: Color,
) {
    val scene = layout.scene
    // Logo lockup (Title board): bone planet cut by an ink slash and an accent slash.
    val mark = scene.logoMark
    val unit = mark / 64f
    val origin = Offset(scene.logo.x, scene.logo.y - mark * 0.5f)
    drawCircle(Kk.Bone, 21f * unit, Offset(origin.x + 32f * unit, origin.y + 32f * unit))
    drawLine(Kk.Ink, Offset(origin.x + 4f * unit, origin.y + 46.6f * unit), Offset(origin.x + 60f * unit, origin.y + 17.4f * unit), 7f * unit)
    drawLine(accent, Offset(origin.x - 2f * unit, origin.y + 49.7f * unit), Offset(origin.x + 66f * unit, origin.y + 14.3f * unit), 3.5f * unit)
    val wordmark = drawKkText(
        text, "KINETICKK", text.typography.wideStyle(displaySize(text, scene.wordmarkSize), lineHeightEm = 1f),
        origin.x + mark + 12f * unit * 64f / 30f, scene.logo.y, Kk.Bone, valign = KkVAlign.CENTER,
    )
    val headerLeft = origin.x + mark + 12f * unit * 64f / 30f + wordmark.size.width + 16f * density
    val value = formatGroupedNumber(model.totalMatter, text.language)
    val label = text.language.text(SessionRedesignText.MATTER).takeUnless { scene.compactChips }
    drawMatterChip(text, scene, value, label, headerLeft)
    drawRunModeToggle(text, layout.runModeToggle.bounds, state.easy, state.runModeActive)
}

/**
 * The Baby mode toggle: a chip-shaped plate with the pacifier, filled with the soft role while on (ink pacifier) and
 * ink-2 while off (a mute pacifier, bone when hovered or focused); hovered or focused it is outlined.
 */
private fun DrawScope.drawRunModeToggle(text: CanvasTextMeasurer, bounds: Rect, easy: Boolean, active: Boolean) {
    val path = chipPlate(bounds.left, bounds.top, bounds.right, bounds.bottom, 8f * density)
    drawPath(path, if (easy) text.roles.soft else Kk.Ink2)
    if (active) drawPath(path, if (easy) Kk.Ink else Kk.Bone, style = kkStroke(1.5f * density))
    val pacifier = KkPacifierSigilPaths.of(bounds.height * 0.34f)
    translate(bounds.center.x, bounds.center.y) {
        drawPath(pacifier, if (easy) Kk.Ink else if (active) Kk.Bone else Kk.Mute)
    }
}

/** The stamp's size (px before the measurer's scale): 17 at the reference hero, never under 11. */
internal fun homeBabyStampSize(scene: HomeScene, density: Float): Float = (17f * scene.heroRadius / density / 260f).coerceIn(11f, 17f)

/**
 * The unrotated Baby mode stamp under the hero's singularity (px): centred on the hero, its top 0.34 hero radii below the
 * centre, under the dashed 0.27 R ring.
 */
internal fun homeBabyStampRect(text: CanvasTextMeasurer, scene: HomeScene, density: Float): Rect {
    val size = kkStampSize(text, text.language.text(SessionRedesignText.BABY_MODE), density, homeBabyStampSize(scene, density))
    val left = scene.heroCenter.x - size.width * 0.5f
    val top = scene.heroCenter.y + 0.34f * scene.heroRadius
    return Rect(left, top, left + size.width, top + size.height)
}

/** The soft "Baby mode" stamp under the hero while the next runs are Baby mode runs. */
internal fun DrawScope.drawHomeBabyStamp(text: CanvasTextMeasurer, scene: HomeScene) {
    val rect = homeBabyStampRect(text, scene, density)
    drawKkStamp(text, text.language.text(SessionRedesignText.BABY_MODE), rect.topLeft, KkStampVariant.YOU,
        fontSize = homeBabyStampSize(scene, density), color = text.roles.soft)
}

private fun chipValueStyle(text: CanvasTextMeasurer, scene: HomeScene) =
    text.typography.condStyle(if (scene.compactChips) 16f else 21f, trackingEm = 0.02f, tabular = true, lineHeightEm = 1f)

private fun chipLabelStyle(text: CanvasTextMeasurer) = text.typography.monoStyle(9f, trackingEm = 0.06f)

private fun DrawScope.drawMatterChip(
    text: CanvasTextMeasurer,
    scene: HomeScene,
    value: String,
    label: String?,
    headerLeft: Float,
) {
    val valueLayout = measureKkText(text, value, chipValueStyle(text, scene), uppercase = true)
    val labelLayout = label?.let { measureKkText(text, it, chipLabelStyle(text), uppercase = true) }
    val gap = 8f * density
    val gemSize = if (scene.compactChips) 10f else 14f
    val width = (24f + gemSize) * density + gap + valueLayout.size.width +
        (labelLayout?.let { gap + it.size.width } ?: 0f)
    val right = scene.chipsRight
    val left = right - width
    if (left < headerLeft) return

    val top = scene.chipsTop
    val bottom = top + scene.chipHeight
    val centerY = (top + bottom) * 0.5f
    drawPath(chipPlate(left, top, right, bottom, 8f * density), Kk.Ink2)
    val x = left + 10f * density
    drawKkGem(Offset(x + gemSize * density * 0.5f, centerY), text.roles.you, gemSize)
    val valueX = x + gemSize * density + gap
    drawKkText(valueLayout, valueX, centerY, Kk.Bone, valign = KkVAlign.CENTER)
    if (labelLayout != null) {
        drawKkText(labelLayout, valueX + valueLayout.size.width + gap, centerY, Kk.Mute, valign = KkVAlign.CENTER)
    }
}

/** The header chip's slanted plate (one reused path), drawn by every chip and the Baby mode toggle. */
private fun chipPlate(left: Float, top: Float, right: Float, bottom: Float, cut: Float): Path {
    val path = chipPath
    path.rewind()
    path.moveTo(left + cut, top)
    path.lineTo(right, top)
    path.lineTo(right - cut, bottom)
    path.lineTo(left, bottom)
    path.close()
    return path
}

private val chipPath = Path()
