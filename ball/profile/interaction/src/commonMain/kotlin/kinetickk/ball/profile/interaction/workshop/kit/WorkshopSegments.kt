// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.profile.interaction.workshop.kit

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import kinetickk.ball.profile.interaction.ProfileFrame
import kinetickk.ball.profile.interaction.dp
import kinetickk.ball.profile.interaction.drawProfileText
import kinetickk.ball.profile.interaction.localization.ProfileScreensRedesignText
import kinetickk.ball.profile.interaction.workshop.api.WorkshopTab
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.*
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/** The detail segments behind the Characters and Weapons tabs; [key] names their test tags. */
internal enum class WorkshopSegment(val key: String) {
    UPGRADE("upgrade"),
    TRAITS("traits"),
    MASTERY("mastery"),
    DETAILS("details"),
}

private val CharacterSegments = listOf(WorkshopSegment.UPGRADE, WorkshopSegment.TRAITS)
private val WeaponSegments = listOf(WorkshopSegment.UPGRADE, WorkshopSegment.MASTERY, WorkshopSegment.DETAILS)

/** The segments of [this] tab in strip order; the Core Matrix has none. UPGRADE is the default. */
internal val WorkshopTab.segments: List<WorkshopSegment>
    get() = when (this) {
        WorkshopTab.CHARACTERS -> CharacterSegments
        WorkshopTab.WEAPONS -> WeaponSegments
        WorkshopTab.MATRIX -> emptyList()
    }

/** Height of a detail's segmented switch: d(34) on the regular frame, d(32) on phones. */
internal fun workshopSegmentHeight(frame: ProfileFrame): Float = frame.d(if (frame.regular) 34f else 32f)

/** The upgrade block's slot under the Upgrade segment: d(168) on the regular frame, d(130) on phones. */
internal fun workshopUpgradeBlockHeight(frame: ProfileFrame): Float = frame.d(if (frame.regular) 168f else 130f)

/**
 * The segment cells inside [strip], from its left edge: each [naturalWidths] entry (px of a 34 dp
 * `kkSegmentWidth` cell) scaled to the strip's height, d(3) apart.
 */
internal fun workshopSegmentCells(frame: ProfileFrame, strip: Rect, naturalWidths: List<Float>): List<Rect> {
    val scale = strip.height / (frame.density * 34f)
    var x = strip.left
    return naturalWidths.map { width ->
        Rect(x, strip.top, x + width * scale, strip.bottom).also { x = it.right + frame.d(3f) }
    }
}

internal fun WorkshopSegment.label(language: AppLanguage): String = language.text(when (this) {
    WorkshopSegment.UPGRADE -> ProfileScreensRedesignText.SegmentUpgrade
    WorkshopSegment.TRAITS -> ProfileScreensRedesignText.SegmentTraits
    WorkshopSegment.MASTERY -> ProfileScreensRedesignText.Mastery
    WorkshopSegment.DETAILS -> ProfileScreensRedesignText.SegmentDetails
})

/** Test tag of a segment node (`kinetickk.workshop.segment.upgrade` …). */
internal fun WorkshopSegment.tag(): String = "kinetickk.workshop.segment.$key"

internal fun lerpF(a: Float, b: Float, t: Float): Float = a + (b - a) * t

internal fun DrawScope.drawWorkshopFocus(frame: ProfileFrame, bounds: Rect) {
    val inset = frame.density * 5f
    drawRect(Kk.Bone, Offset(bounds.left - inset, bounds.top - inset), Size(bounds.width + inset * 2f, bounds.height + inset * 2f),
        style = kkStroke(frame.density * 2f))
}

/**
 * A detail panel's segmented switch in [strip] (the parent's coordinates): Ink 3 cells with the
 * `you` face under the shown [selected] segment (moving with Snap over 90 ms), one radio node per
 * segment. Left/Right inside the group move the selection; Enter and Space activate a cell.
 */
@Composable
internal fun WorkshopSegmentStrip(
    frame: ProfileFrame,
    strip: Rect,
    segments: List<WorkshopSegment>,
    selected: WorkshopSegment,
    textScale: Float,
    onAction: (WorkshopAction) -> Unit,
) {
    if (segments.isEmpty() || strip.height <= 0f) return
    val language = LocalAppLanguage.current
    val measurer = rememberKkCanvasMeasurer(textScale)
    val labels = segments.map { it.label(language) }
    val cells = workshopSegmentCells(frame, strip, labels.map { kkSegmentWidth(measurer, it, frame.density) })
        .map { it.translate(-strip.left, -strip.top) }
    val selectedIndex = segments.indexOf(selected).coerceAtLeast(0)
    val position = remember { Animatable(selectedIndex.toFloat()) }
    LaunchedEffect(selectedIndex) { position.animateTo(selectedIndex.toFloat(), tween(KkTime.Snap, easing = KkEase.Snap)) }
    val sources = remember(segments) { List(segments.size) { MutableInteractionSource() } }
    val requesters = remember(segments) { List(segments.size) { FocusRequester() } }
    val hovered = sources.map { it.collectIsHoveredAsState().value }
    val focused = sources.map { it.collectIsFocusedAsState().value }
    val paths = remember { KkPathCache(slots = 1 + segments.size) }
    val labelSize = 16f * strip.height / (frame.density * 34f)
    Box(
        Modifier.offset { IntOffset(strip.left.roundToInt(), strip.top.roundToInt()) }
            .size(frame.dp(strip.width), frame.dp(strip.height))
            .drawBehind {
                val cut = frame.d(7f)
                cells.forEachIndexed { index, cell -> drawPath(paths.slab(1 + index, cell, cut), Kk.Ink3) }
                val p = position.value
                val low = floor(p).toInt().coerceIn(0, cells.lastIndex)
                val high = ceil(p).toInt().coerceIn(0, cells.lastIndex)
                val t = p - low
                val face = Rect(lerpF(cells[low].left, cells[high].left, t), 0f, lerpF(cells[low].right, cells[high].right, t), size.height)
                drawPath(paths.slab(0, face, cut), measurer.roles.you)
                val style = measurer.typography.labelStyle(labelSize, trackingEm = 0.07f)
                cells.forEachIndexed { index, cell ->
                    val cover = (1f - abs(p - index)).coerceIn(0f, 1f)
                    val base = if (hovered[index]) Kk.Bone else Kk.Mute
                    val text = measureKkText(measurer, labels[index], style, uppercase = true)
                    drawProfileText(text, cell.center.x, cell.center.y, lerp(base, Kk.Ink, cover), "workshop.segment", segments[index],
                        align = KkAlign.CENTER, valign = KkVAlign.CENTER)
                    if (focused[index]) drawWorkshopFocus(frame, cell)
                }
            }
            .selectableGroup(),
    ) {
        segments.forEachIndexed { index, segment ->
            val cell = cells[index]
            Box(
                Modifier.offset { IntOffset(cell.left.roundToInt(), cell.top.roundToInt()) }
                    .size(frame.dp(cell.width), frame.dp(cell.height))
                    .testTag(segment.tag())
                    .focusRequester(requesters[index])
                    .onKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                        when (event.key) {
                            Key.DirectionRight, Key.DirectionLeft -> {
                                val forward = event.key == Key.DirectionRight
                                onAction(WorkshopAction.StepSegment(forward))
                                requesters[(index + if (forward) 1 else segments.size - 1) % segments.size].requestFocus()
                                true
                            }
                            Key.Spacebar -> {
                                onAction(WorkshopAction.SelectSegment(segment))
                                true
                            }
                            else -> false
                        }
                    }
                    .semantics { contentDescription = labels[index] }
                    .selectable(selected = segment == selected, interactionSource = sources[index], indication = null,
                        role = Role.RadioButton) { onAction(WorkshopAction.SelectSegment(segment)) },
            )
        }
    }
}
