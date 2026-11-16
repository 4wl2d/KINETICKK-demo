// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.interaction.canvas.fx

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kinetickk.ball.content.api.SkillId
import kinetickk.ball.gameplay.interaction.canvas.CanvasMath
import kinetickk.ball.gameplay.interaction.canvas.fxAge
import kinetickk.ball.gameplay.interaction.canvas.world
import kinetickk.ball.gameplay.interaction.canvas.worldScale
import kinetickk.ball.gameplay.interaction.fx.PieceMotion
import kinetickk.ball.gameplay.interaction.fx.PixelFxClock
import kinetickk.ball.gameplay.interaction.fx.PixelSkillLooks
import kinetickk.ball.gameplay.interaction.fx.VisualFxProjection
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkRolePalette
import kinetickk.foundation.design.KkShape
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private const val TAU = CanvasMath.TAU

/** The interface's slash axis in radians, and quarter turns per radian. */
private val SLASH = KkShape.Slash * CanvasMath.DEG_TO_RAD
private const val QUARTERS_PER_RADIAN = 0.63661975f

/** Pieces of a skill look that always draw, whatever the budget, the setting and the crowd. */
private const val SIGNATURE_PIECES = 6

/** Draw-thread scratch for a skill's two hues. */
private object SkillHues {
    var a: Color = Color.White
    var b: Color = Color.White
}

/** Uses the skill's element colour, or the player and bone colours for Catalyze. */
private fun skillHues(engine: GameplayRenderModel, skill: SkillId, roles: KkRolePalette) {
    val hues = SkillHues
    when (skill) {
        SkillId.CATALYZE -> {
            hues.a = Kk.Bone
            hues.b = roles.you
        }
        else -> {
            val element = engine.content.skills.getOrNull(skill.ordinal)?.element
            val hue = if (element == null) roles.kinetic else roles.element(element.ordinal)
            hues.a = hue
            hues.b = hue
        }
    }
}

/**
 * Skill casts and delayed impacts, each as the look drawn for that skill ([PixelSkillLooks]): its figure at the cast
 * point (turned to the cast direction when it has one) and, for a look with a reach, its own pieces thrown out to
 * it, pulled in from it, standing on it or stepping along the cast. A higher rank throws more pieces; the Reduced and
 * Minimal settings and a crowd throw half. The figure and the first [SIGNATURE_PIECES] always draw.
 */
internal fun DrawScope.drawSkillBursts(
    engine: GameplayRenderModel,
    visualFx: VisualFxProjection,
    shakeX: Float,
    shakeY: Float,
    roles: KkRolePalette,
) {
    val bursts = visualFx.skillBursts
    if (bursts.isEmpty()) return
    val scale = engine.worldScale
    val alpha = FxFrame.peakAlpha
    val scatter = satellitesOn()
    for (index in bursts.indices) {
        val burst = bursts[index]
        val look = PixelSkillLooks.of(burst.skill, burst.phase) ?: continue
        skillHues(engine, SkillId.entries[burst.skill], roles)
        val center = world(engine, burst.x, burst.y, shakeX, shakeY)
        val age = engine.fxAge(burst.life, burst.maxLife)
        val palette = if (look.duo) PixelPalettes.duo(SkillHues.a, SkillHues.b) else PixelPalettes.of(SkillHues.a)
        // A cast without a direction takes one from its seed, so two of them never line up.
        val heading = if (burst.dirX != 0f || burst.dirY != 0f) atan2(burst.dirY, burst.dirX) else fxHash(burst.seed, 1) * TAU
        val frame = PixelFxClock.frame(look.strip, age)
        if (frame >= 0) {
            FxDrawBudget.take(FxPool.TRANSIENT, 1, signature = true)
            val diagonal = look.diagonal
            // The seed of a run is drawn on the slash axis: quarter turns bring it to within an eighth of the cast's line.
            val turns = if (look.motion == PieceMotion.ALONG) ((heading - SLASH) * QUARTERS_PER_RADIAN).roundToInt() and 3 else 0
            if (diagonal == null) drawPixelFrame(look.strip, frame, center.x, center.y, scale, palette, alpha = alpha, turns = turns)
            else drawPixelAimed(look.strip, diagonal, frame, center.x, center.y, heading, scale, palette, alpha)
        }
        val piece = look.piece
        if (piece != null) {
            // A run's length sets its pieces; a higher rank adds to a thrown set.
            val wanted = if (look.motion == PieceMotion.ALONG) runCount(burst.radius)
            else min(MAX_PIECES, pieceCount(burst.radius, look.fan) + 2 * (burst.rank - 1))
            val signatures = min(SIGNATURE_PIECES, wanted)
            drawPixelPieces(piece, look.pieceDiagonal, look.motion, center.x, center.y, burst.radius, age, burst.seed,
                if (scatter) wanted else max(signatures, wanted / 2), scale, palette, alpha, signatures, cos(heading), sin(heading),
                if (look.fan > 0f) look.fan else TAU)
        }
    }
}
