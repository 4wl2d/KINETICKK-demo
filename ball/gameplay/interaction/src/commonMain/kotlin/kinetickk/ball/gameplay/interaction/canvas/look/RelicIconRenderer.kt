// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.interaction.canvas.look

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import kinetickk.ball.content.api.RelicAspect
import kinetickk.ball.content.api.RelicDefinition
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.RelicPolicy
import kinetickk.foundation.design.CanvasRuneStyle
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.drawRuneMedallion
import kinetickk.foundation.design.kkStroke
import kotlin.math.PI

/** Aspect colors come from the redesign aspect tokens, in RelicAspect order. */
internal fun relicAspectColor(aspect: RelicAspect): Color = Kk.aspect(aspect.ordinal)

/**
 * Interaction maps semantic identity, aspect and rank into a shared geometric mark. [accent] is
 * the aspect hue, by default the aspect token; overlays and the Codex pass it resolved under the
 * player's color-vision preset so the rune matches the plate or slot it sits on.
 */
fun DrawScope.drawRelicIcon(
    definition: RelicDefinition,
    policy: RelicPolicy,
    center: Offset,
    radius: Float,
    rank: Int? = null,
    alpha: Float = 1f,
    accent: Color = relicAspectColor(definition.aspect),
) {
    drawRuneMedallion(
        style = definition.id.toCanvasRuneStyle(),
        center = center,
        radius = radius,
        accent = accent.copy(alpha = accent.alpha * alpha.coerceIn(0f, 1f)),
        frameSides = relicFrameSides(definition.aspect),
        frameRotation = -PI.toFloat() * 0.5f,
        markerCount = if (rank == null) 0 else policy.maxRank,
        filledMarkerCount = rank?.coerceIn(1, policy.maxRank) ?: 0,
    )
}

/**
 * Generic signal used before an elite pickup resolves into a catalog relic: an empty relic slot
 * (ink diamond, bone outline) holding a small bright diamond that slowly turns.
 */
internal fun DrawScope.drawUnresolvedRelicIcon(center: Offset, radius: Float, time: Float, accent: Color) {
    val stroke = (radius * 0.14f).coerceAtLeast(1f)
    val half = radius * 0.72f
    rotate(45f, center) {
        drawRect(accent.copy(alpha = 0.12f), Offset(center.x - half * 1.35f, center.y - half * 1.35f), Size(half * 2.7f, half * 2.7f))
        drawRect(Kk.Ink2, Offset(center.x - half, center.y - half), Size(half * 2f, half * 2f))
        drawRect(Kk.Bone, Offset(center.x - half, center.y - half), Size(half * 2f, half * 2f), style = kkStroke(stroke))
    }
    val inner = radius * 0.3f
    rotate(45f + time * 20f, center) {
        drawRect(accent, Offset(center.x - inner, center.y - inner), Size(inner * 2f, inner * 2f))
    }
}

/** One distinct rune per relic. */
fun RelicId.toCanvasRuneStyle(): CanvasRuneStyle = when (this) {
    RelicId.PERPETUAL_VECTOR -> CanvasRuneStyle.RADIAL_WHEEL
    RelicId.GYRE_ENGINE -> CanvasRuneStyle.FOUR_SWIRL_ARMS
    RelicId.GLASS_COMET -> CanvasRuneStyle.CRACKED_RING
    RelicId.ECHO_CHAMBER -> CanvasRuneStyle.NESTED_DIAMONDS
    RelicId.FURNACE_COVENANT -> CanvasRuneStyle.DIAMOND_FLAME
    RelicId.ABSOLUTE_ZERO -> CanvasRuneStyle.SIX_PETAL_ROSETTE
    RelicId.STORMHEART_CAPACITOR -> CanvasRuneStyle.BOLT_AND_DOT
    RelicId.AEGIS_INVERSION -> CanvasRuneStyle.CROSSED_DIAMOND
    RelicId.NEUTRON_HEART -> CanvasRuneStyle.OFFSET_RINGS
    RelicId.VERDANT_ROT -> CanvasRuneStyle.STITCHED_DIAGONAL
}
