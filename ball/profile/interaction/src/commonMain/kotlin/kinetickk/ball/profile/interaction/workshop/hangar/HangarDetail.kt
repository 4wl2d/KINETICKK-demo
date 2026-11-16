// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.profile.interaction.workshop.hangar

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.selected
import kinetickk.ball.content.api.localizedContent
import kinetickk.ball.profile.interaction.PROFILE_DESCRIPTION_MAX_LINES
import kinetickk.ball.profile.interaction.ProfileFrame
import kinetickk.ball.profile.interaction.drawProfileText
import kinetickk.ball.profile.interaction.fitKkText
import kinetickk.ball.profile.interaction.workshop.kit.armoryWeaponIcon
import kinetickk.ball.profile.interaction.localization.ProfileScreensRedesignText
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.*

/** The weapon icon of the signature row (the Armory's cached `weapons.<id>` icon). */
private fun hangarWeaponIcon(weapon: HangarWeaponRenderModel): KkIcon? = armoryWeaponIcon(weapon.definition.id)

/** The Architect clear record at the right of the preview. */
private fun DrawScope.drawHangarClearRecord(
    measurer: CanvasTextMeasurer,
    frame: ProfileFrame,
    type: HangarType,
    preview: Rect,
    left: Float,
    shape: HangarShapeRenderModel,
    language: AppLanguage,
) {
    val labelText = hangarClearText(shape, language) ?: return
    val width = (preview.right - left - frame.d(12f)).coerceAtLeast(frame.d(40f))
    val label = fitKkText(measurer, labelText, type.mono * frame.k, width, minFactor = 0.5f) { measurer.typography.monoStyle(it) }
    drawProfileText(label, preview.right, preview.center.y, if (shape.victorious) Kk.Gold else Kk.Mute, "hangar.detail.clear",
        align = KkAlign.END, valign = KkVAlign.CENTER)
}

/** The inspected character's description wrapped to [width] (every line; the details scroll). */
internal fun hangarDescriptionLayout(
    measurer: CanvasTextMeasurer,
    frame: ProfileFrame,
    type: HangarType,
    shape: HangarShapeRenderModel,
    language: AppLanguage,
    width: Float,
) = measureKkText(measurer, shape.definition.mechanicDescription.localizedContent(language),
    measurer.typography.bodyStyle(type.body * frame.k), maxWidth = width, maxLines = PROFILE_DESCRIPTION_MAX_LINES)

internal fun DrawScope.drawHangarDetail(
    measurer: CanvasTextMeasurer,
    frame: ProfileFrame,
    type: HangarType,
    layout: HangarLayout,
    shape: HangarShapeRenderModel,
    model: HangarRenderModel,
    signatureWeapons: List<HangarWeaponRenderModel>,
    time: Float,
    language: AppLanguage,
) {
    val k = frame.k
    val roles = measurer.roles
    val definition = shape.definition
    // Large animated preview: the form spinning in a faint ring.
    val preview = layout.preview
    val radius = preview.height * 0.36f
    val center = Offset(preview.left + radius * 1.4f, preview.center.y)
    drawCircle(roles.you.copy(alpha = 0.12f), radius * 1.35f, center, style = kkStroke(frame.d(2f)))
    drawHangarForm(definition.id.hangarFormIndex(), center, radius, if (shape.unlocked) Kk.Bone else Kk.Ink3, Kk.Ink,
        time / HANGAR_SPIN_SECONDS * 360f)
    if (!shape.unlocked) drawKkIcon(KkIcon.SYSTEM_LOCKED, center, radius * 0.8f, Kk.Mute)
    drawHangarClearRecord(measurer, frame, type, preview, center.x + radius * 1.35f, shape, language)
    val tagText = definition.mechanicName.localizedContent(language) + " / " + definition.triggerKind.displayLabel.localizedContent(language)
    val tag = fitKkText(measurer, tagText, type.mono * k, layout.tag.width, minFactor = 0.5f) { measurer.typography.monoStyle(it) }
    drawProfileText(tag, layout.tag.left, layout.tag.top, roles.you, "hangar.detail.tag")
    val name = fitKkText(measurer, definition.displayName.localizedContent(language), type.name * k, layout.name.width, minFactor = 0.4f) {
        measurer.typography.wideStyle(it, lineHeightEm = 1.05f)
    }
    drawProfileText(name, layout.name.left, layout.name.center.y, if (shape.unlocked) Kk.Bone else Kk.Mute, "hangar.detail.name",
        valign = KkVAlign.CENTER)
    drawProfileText(hangarDescriptionLayout(measurer, frame, type, shape, language, layout.description.width),
        layout.description.left, layout.description.top, Kk.Bone, "hangar.detail.description")
    if (layout.unlock.height > 0f) {
        val unlock = fitKkText(measurer, hangarUnlockText(shape, language), type.mono * k, layout.unlock.width, maxLines = 2,
            minFactor = 0.5f) { measurer.typography.monoStyle(it) }
        drawProfileText(unlock, layout.unlock.left, layout.unlock.top, if (shape.unlocked) Kk.Bone2 else roles.threat, "hangar.detail.unlock")
    }
    if (shape.shape == model.selected) {
        drawKkGem(Offset(layout.name.right - frame.d(10f), layout.tag.center.y), roles.you, frame.d(9f) / density)
    }
    // Traits, difficulty and the signature weapons show on the Traits segment only.
    if (layout.signature.height <= 0f) return
    val labels = listOf(
        ProfileScreensRedesignText.HangarMass to shape.massBar,
        ProfileScreensRedesignText.HangarIntegrity to shape.integrityBar,
        ProfileScreensRedesignText.HangarHandling to shape.handlingBar,
        ProfileScreensRedesignText.HangarHeat to shape.heatBar,
    )
    val labelWidth = layout.traits.first().width * 0.36f
    labels.forEachIndexed { index, (label, value) ->
        val row = layout.traits[index]
        val text = fitKkText(measurer, language.text(label), type.mono * k, labelWidth - frame.d(8f), minFactor = 0.5f) {
            measurer.typography.monoStyle(it)
        }
        drawProfileText(text, row.left, row.center.y, Kk.Mute, "hangar.detail.trait", index, valign = KkVAlign.CENTER)
        val barLeft = row.left + labelWidth
        val barHeight = row.height * 0.36f
        val bar = Rect(barLeft, row.center.y - barHeight * 0.5f, row.right, row.center.y + barHeight * 0.5f)
        drawRect(Kk.Line2, bar.topLeft, bar.size)
        drawRect(roles.you, bar.topLeft, Size(bar.width * value.coerceIn(0f, 1f), bar.height))
    }
    val difficultyLabel = fitKkText(measurer, language.text(ProfileScreensRedesignText.HangarDifficulty), type.mono * k,
        labelWidth - frame.d(8f), minFactor = 0.5f) { measurer.typography.monoStyle(it) }
    val difficulty = layout.difficulty
    drawProfileText(difficultyLabel, difficulty.left, difficulty.center.y, Kk.Mute, "hangar.detail.difficulty", valign = KkVAlign.CENTER)
    val pip = difficulty.height * 0.5f
    drawKkPips(Offset(difficulty.left + labelWidth, difficulty.center.y - pip * 0.5f), 3, definition.difficulty, roles.threat,
        sizeDp = pip / density, widthDp = pip * 1.6f / density, emptyOutlined = true)
    // Each signature weapon shows its icon, name and status.
    val signature = layout.signature
    val firstRow = layout.signatureRows.firstOrNull() ?: signature
    val signatureLabel = fitKkText(measurer, language.text(ProfileScreensRedesignText.HangarSignature), type.mono * k,
        labelWidth - frame.d(8f), minFactor = 0.5f) { measurer.typography.monoStyle(it) }
    drawProfileText(signatureLabel, signature.left, firstRow.top + layout.signatureLine * 0.5f, Kk.Mute, "hangar.detail.signature",
        valign = KkVAlign.CENTER)
    signatureWeapons.forEachIndexed { index, weapon ->
        val row = layout.signatureRows.getOrNull(index) ?: return@forEachIndexed
        drawHangarSignatureWeapon(measurer, frame, type, Rect(row.left + labelWidth, row.top, row.right, row.bottom), layout.signatureLine,
            weapon, language)
    }
}

/**
 * One signature weapon in [row] with its icon, name and status.
 */
private fun DrawScope.drawHangarSignatureWeapon(
    measurer: CanvasTextMeasurer,
    frame: ProfileFrame,
    type: HangarType,
    row: Rect,
    lineHeight: Float,
    weapon: HangarWeaponRenderModel,
    language: AppLanguage,
) {
    val k = frame.k
    val roles = measurer.roles
    val centerY = row.top + lineHeight * 0.5f
    val owned = weapon.owned || weapon.starter
    val icon = lineHeight * 0.72f
    hangarWeaponIcon(weapon)?.let { drawKkIcon(it, Offset(row.left + icon * 0.5f, centerY), icon, if (owned) roles.you else Kk.Mute) }
    val status = fitKkText(measurer, hangarWeaponStatus(weapon, language), type.mono * k, row.width * 0.4f, minFactor = 0.5f) {
        measurer.typography.monoStyle(it)
    }
    val statusColor = if (owned) roles.you else Kk.Mute
    drawProfileText(status, row.right, centerY, statusColor, "hangar.detail.signature.state", weapon.definition.id, align = KkAlign.END,
        valign = KkVAlign.CENTER)
    val nameLeft = row.left + icon + frame.d(8f)
    val name = fitKkText(measurer, weapon.definition.name.localizedContent(language), type.mono * 1.45f * k,
        (row.right - status.size.width - frame.d(10f) - nameLeft).coerceAtLeast(frame.d(30f)), minFactor = 0.5f) {
        measurer.typography.condStyle(it, lineHeightEm = 1f)
    }
    drawProfileText(name, nameLeft, centerY, if (owned) Kk.Bone else Kk.Bone2, "hangar.detail.signature.value", weapon.definition.id,
        valign = KkVAlign.CENTER)
}
