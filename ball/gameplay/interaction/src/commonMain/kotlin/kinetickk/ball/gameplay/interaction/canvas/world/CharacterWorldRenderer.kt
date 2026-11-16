// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.interaction.canvas.world

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.gameplay.interaction.canvas.WorldStrokes
import kinetickk.ball.gameplay.interaction.canvas.look.formIndex
import kinetickk.ball.gameplay.interaction.canvas.spin
import kinetickk.ball.gameplay.interaction.canvas.renderElapsed
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkFormPaths
import kinetickk.foundation.design.KkRolePalette
import kinetickk.foundation.design.KkStrokeWidth
import kinetickk.foundation.design.kkMix
import kinetickk.foundation.design.kkStroke
import kotlin.math.cos
import kotlin.math.sin

private fun DrawScope.drawCoreForm(engine: GameplayRenderModel, center: Offset, fill: Color, roles: KkRolePalette) {
    val path = KkFormPaths.of(engine.coreShape.formIndex(), engine.coreRadius)
    translate(center.x, center.y) {
        drawPath(path, Kk.Ink, style = kkStroke(6f))
        drawPath(path, fill)
    }
    if (engine.coreShape == CoreShape.PRISM) {
        val ability = engine.characterAbility
        drawFacetRing(ability.facets, ability.facetProgress, center, engine.renderElapsed, roles)
    }
}

/** Four facets orbiting the Core: intact facets filled, broken ones outlined, the next one filling in. */
private fun DrawScope.drawFacetRing(facets: Int, progress: Float, center: Offset, elapsed: Float, roles: KkRolePalette) {
    val base = elapsed * 0.6f
    for (index in 0 until 4) {
        val angle = base + index * 1.5707964f
        val point = Offset(center.x + cos(angle) * 27f, center.y + sin(angle) * 27f)
        val intact = index < facets
        rotate(45f + angle * 57.29578f, point) {
            val half = if (intact) 4.5f else 3.5f
            drawRect(Kk.Ink, Offset(point.x - half - 1.5f, point.y - half - 1.5f), Size(half * 2f + 3f, half * 2f + 3f))
            if (intact) {
                drawRect(roles.shield, Offset(point.x - half, point.y - half), Size(half * 2f, half * 2f))
            } else {
                drawRect(roles.shield.copy(alpha = 0.25f + 0.6f * if (index == facets) progress else 0f),
                    Offset(point.x - half, point.y - half), Size(half * 2f, half * 2f), style = kkStroke(KkStrokeWidth.Line2))
            }
        }
    }
}

/**
 * The Core: a bone form silhouette ([KkFormPaths]) with a hard ink rim and a soft glow; you-color
 * in overdrive and heat-tinted while overheated, paled toward ice by Core Freeze.
 * Integrity/heat/shield arcs are the HUD's Core halo. In Baby mode the form wears a diaper ([drawDiaper]).
 */
internal fun DrawScope.drawCore(engine: GameplayRenderModel, center: Offset, roles: KkRolePalette) {
    val overdrive = engine.overdriveTime > 0f
    var fill = when {
        overdrive -> roles.you
        engine.overheated -> kkMix(Kk.Bone, roles.heat, 0.35f)
        else -> Kk.Bone
    }
    var glow = when {
        overdrive -> roles.you
        engine.overheated -> roles.heat
        else -> Kk.Bone
    }
    var glowAlpha = if (overdrive || engine.overheated) 0.26f else 0.12f
    // Core Freeze pales the form toward ice and dims its glow as the freeze grows; overdrive and overheat show
    // through until the Core is nearly solid.
    val freeze = engine.coreStasis
    if (freeze > 0f) {
        val cryo = roles.element(StatusElement.CRYO.ordinal)
        fill = kkMix(fill, kkMix(Kk.Bone, cryo, 0.45f), 0.8f * freeze)
        glow = kkMix(glow, cryo, 0.65f * freeze)
        glowAlpha *= 1f - 0.5f * freeze
    }
    drawCircle(glow.copy(alpha = glowAlpha), 24f, center)
    drawCircle(glow.copy(alpha = glowAlpha * 0.5f), 32f, center)
    if (overdrive) drawCircle(glow.copy(alpha = 0.1f), 44f, center)
    // Phased by Void Step: the Core shows at half strength.
    drawCoreForm(engine, center, if (engine.coreState.phased) fill.copy(alpha = 0.5f) else fill, roles)
    if (engine.easyMode) drawDiaper(engine, center, roles, if (engine.coreState.phased) 0.5f else 1f)
    val ability = engine.characterAbility
    if (ability.charge > 0f && engine.coreShape != CoreShape.PRISM) {
        drawArc(roles.you, -90f, 360f * ability.charge.coerceIn(0f, 1f), false,
            Offset(center.x - 25f, center.y - 25f), Size(50f, 50f), style = kkStroke(KkStrokeWidth.Icon))
    }
    // The state marks stay inside the HUD's Core halo (radius 38).
    drawCoreMarks(engine, center, roles)
}

/**
 * The singularity (cursor): a threat ring with a soft halo, a slowly turning dashed outer ring, a
 * bone dot and four crosshair ticks. [danger] (contact is close) brightens the halo.
 * In Baby mode ([easy]) the cursor is harmless: the soft role instead of threat, no danger pulse, and
 * the pacifier guard's wings left and right of the ring.
 */
internal fun DrawScope.drawSingularity(center: Offset, time: Float, danger: Boolean, roles: KkRolePalette, easy: Boolean = false) {
    val tone = if (easy) roles.soft else roles.threat
    val pulse = if (danger && !easy) (sin(time * 10f) + 1f) * 0.5f else 0f
    drawCircle(tone.copy(alpha = 0.10f + pulse * 0.10f), 24f, center, style = kkStroke(8f))
    drawCircle(tone.copy(alpha = 0.05f + pulse * 0.06f), 28f, center, style = kkStroke(10f))
    rotate(time * spin(3.2f), center) {
        drawCircle(tone.copy(alpha = 0.7f), 27.25f, center, style = WorldStrokes.dashedThin)
    }
    if (easy) drawPacifierWings(center, tone)
    // Leave the cursor's center transparent to keep the arena visible.
    drawCircle(tone, 13.5f, center, style = kkStroke(KkStrokeWidth.Heavy))
    drawCircle(Kk.Bone, 4f, center)
    drawRect(tone, Offset(center.x - 1f, center.y - 40f), Size(2f, 8f))
    drawRect(tone, Offset(center.x - 1f, center.y + 32f), Size(2f, 8f))
    drawRect(tone, Offset(center.x - 40f, center.y - 1f), Size(8f, 2f))
    drawRect(tone, Offset(center.x + 32f, center.y - 1f), Size(8f, 2f))
}
