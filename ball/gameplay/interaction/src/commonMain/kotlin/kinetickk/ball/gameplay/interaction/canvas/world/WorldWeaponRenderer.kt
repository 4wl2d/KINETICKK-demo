// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.interaction.canvas.world

import kinetickk.foundation.design.*

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.nucleus.render.WeaponEntityKind
import kinetickk.ball.gameplay.nucleus.render.WeaponEntityProjection
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kinetickk.ball.gameplay.interaction.canvas.*

/** Mirrors of the simulation's geometry the world draws (the nucleus keeps the authoritative values). */
private const val PHASE_LATTICE_CENTER = 110f
private const val PHASE_LATTICE_HALF_WIDTH = 42f
private const val PHASE_LATTICE_OUTER_OFFSET = 100f
private const val MORNINGSTAR_SECOND_ORBIT = 0.72f
private const val MORNINGSTAR_SECOND_SIZE = 0.45f

/** A Gyre disc on its way back. */
private const val DISC_RETURNING = 1

private fun KkRolePalette.element(element: StatusElement): Color = elements.getOrElse(element.ordinal) { you }

/** Phase Lattice: the ring band, wider at the first form, an outer ring at the second. */
internal fun DrawScope.drawPhaseLattice(engine: GameplayRenderModel, core: Offset, roles: KkRolePalette) {
    val center = PHASE_LATTICE_CENTER
    val half = PHASE_LATTICE_HALF_WIDTH * if (engine.weaponLevel >= 3) 1.35f else 1f
    val pulse = (sin(engine.renderElapsed * 4f) + 1f) * 0.5f
    val cryo = roles.element(StatusElement.CRYO)
    drawCircle(cryo.copy(alpha = 0.05f), center, core, style = kkStroke(half * 2f))
    drawCircle(roles.you.copy(alpha = 0.4f), center - half * 0.5f + pulse * half, core, style = kkStroke(KkStrokeWidth.Line2))
    drawCircle(Kk.Bone.copy(alpha = 0.22f), center + half, core, style = WorldStrokes.dashedHair)
    if (engine.weaponLevel >= 6) {
        drawCircle(cryo.copy(alpha = 0.28f), center + PHASE_LATTICE_OUTER_OFFSET, core, style = WorldStrokes.dashedThin)
    }
}

/** Morningstar's second form: a smaller star opposite the first on a shorter chain. */
internal fun DrawScope.drawSecondMorningstar(engine: GameplayRenderModel, core: Offset, ball: Offset, roles: KkRolePalette) {
    if (engine.weaponLevel < 6) return
    val second = Offset(core.x - (ball.x - core.x) * MORNINGSTAR_SECOND_ORBIT, core.y - (ball.y - core.y) * MORNINGSTAR_SECOND_ORBIT)
    drawLine(roles.you.copy(alpha = 0.12f), core, second, 4f)
    drawLine(Kk.Bone.copy(alpha = 0.5f), core, second, 1f, pathEffect = WorldStrokes.dashEffect)
    val radius = 20f * MORNINGSTAR_SECOND_SIZE + 3f
    drawCircle(roles.you.copy(alpha = 0.12f), radius + 8f, second)
    drawCircle(Kk.Bone, radius, second)
    drawCircle(roles.you, radius - 1f, second, style = kkStroke(KkStrokeWidth.Icon))
}

/** Draw mines' gravity wells beneath enemies, and Gyre discs above them. */
internal fun DrawScope.drawWeaponEntities(
    engine: GameplayRenderModel,
    shakeX: Float,
    shakeY: Float,
    roles: KkRolePalette,
    airborne: Boolean,
) {
    val entities = engine.weaponEntities
    for (index in entities.indices) {
        val entity = entities[index]
        val fliesOverEnemies = entity.kind == WeaponEntityKind.DISC
        if (fliesOverEnemies != airborne) continue
        val center = world(engine, engine.interpolated(entity.previousX, entity.x), engine.interpolated(entity.previousY, entity.y), shakeX, shakeY)
        when (entity.kind) {
            WeaponEntityKind.DISC -> if (isOnScreen(center, entity.radius + 20f)) drawDisc(engine, entity, center, roles)
            WeaponEntityKind.WELL -> if (isOnScreen(center, entity.radius)) drawWell(engine, entity, center, roles)
        }
    }
}

/** A Gyre disc: a bone disc with a you rim and a notch that spins; brighter on the way back. */
private fun DrawScope.drawDisc(engine: GameplayRenderModel, disc: WeaponEntityProjection, center: Offset, roles: KkRolePalette) {
    val radius = disc.radius
    val returning = disc.phase == DISC_RETURNING
    drawCircle(roles.you.copy(alpha = if (returning) 0.22f else 0.12f), radius + 8f, center)
    drawCircle(Kk.Bone, radius, center)
    drawCircle(roles.you, radius - 1.25f, center, style = kkStroke(2.5f))
    rotate(engine.renderElapsed * 720f, center) {
        drawRect(Kk.Ink, Offset(center.x - radius * 0.15f, center.y - radius + 2f), Size(radius * 0.3f, radius * 0.6f))
    }
}

/** A gravity mine's last form: dashed rings turning inward, fading with its life. */
private fun DrawScope.drawWell(engine: GameplayRenderModel, well: WeaponEntityProjection, center: Offset, roles: KkRolePalette) {
    val gravitic = roles.element(StatusElement.GRAVITIC)
    val life = (well.life / max(0.01f, well.maxLife)).coerceIn(0f, 1f)
    val alpha = min(1f, life * 2.5f)
    val inward = (engine.renderElapsed * 0.9f) % 1f
    drawCircle(gravitic.copy(alpha = 0.07f * alpha), well.radius, center)
    drawCircle(gravitic.copy(alpha = 0.5f * alpha), well.radius, center, style = WorldStrokes.dashedThin)
    drawCircle(gravitic.copy(alpha = 0.35f * alpha), well.radius * (1f - inward * 0.8f), center, style = WorldStrokes.dashedHair)
    drawCircle(Kk.Ink, 6f, center)
    drawCircle(gravitic.copy(alpha = alpha), 4f, center)
}
