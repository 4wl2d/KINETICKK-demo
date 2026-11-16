// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.interaction.canvas.world.enemies

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import kinetickk.ball.content.api.EnemyRank
import kinetickk.ball.gameplay.interaction.canvas.CanvasMath
import kinetickk.ball.gameplay.interaction.canvas.fx.FxDrawBudget
import kinetickk.ball.gameplay.interaction.canvas.fx.FxPool
import kinetickk.ball.gameplay.interaction.canvas.fx.PixelPalettes
import kinetickk.ball.gameplay.interaction.canvas.fx.drawPixelAimed
import kinetickk.ball.gameplay.interaction.canvas.renderElapsed
import kinetickk.ball.gameplay.interaction.canvas.world.heldLoopFrame
import kinetickk.ball.gameplay.interaction.canvas.worldScale
import kinetickk.ball.gameplay.interaction.fx.PixelStrip
import kinetickk.ball.gameplay.nucleus.render.EnemyProjection
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.foundation.design.Kk
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * Shields that only an impact (or the end of a shift) breaks, as bone plates standing on the
 * shield's circle and facing outward. A plate's loop lasts half a second, the old ring's two beats a second: it hardens
 * bright, a glint crosses it and it settles dim. Every plate of one shield shows the same frame, and each enemy keeps
 * its own beat from its id, so a shielded crowd never flashes in step. The plates are signature work of FxPool.STATUS.
 */

/**
 * A shield's look: its plate strip drawn facing right ([plate]) and down-right ([diagonal]), the circle it stands on
 * in enemy radii ([ring]), and a plate every [spacing] units of that circle, at least [minPlates] and at most
 * [maxPlates].
 */
internal enum class ShieldLook(
    val plate: PixelStrip,
    val diagonal: PixelStrip,
    val ring: Float,
    val spacing: Float,
    val minPlates: Int,
    val maxPlates: Int,
) {
    /** An ordinary or Elite enemy's (a Seraph's mend, a Shielded Elite): thin plates, smaller and dimmer than a boss's. */
    ORDINARY(PixelStrip.SHIELD_PLATE, PixelStrip.SHIELD_PLATE_D, 1.35f, 18f, 6, 24),

    /** A boss shield: thick riveted plates with a bright rim. */
    BOSS(PixelStrip.SHIELD_PLATE_BOSS, PixelStrip.SHIELD_PLATE_BOSS_D, 1.55f, 22f, 8, 40),

    /** The Architect's while it shifts between phases: sheared plates just inside its frames' corners. */
    SHIFT(PixelStrip.SHIELD_PLATE_SHIFT, PixelStrip.SHIELD_PLATE_SHIFT_D, 1.9f, 24f, 8, 40);

    /** Plates round an enemy of [radius]. */
    fun plates(radius: Float): Int = (CanvasMath.TAU * radius * ring / spacing).roundToInt().coerceIn(minPlates, maxPlates)
}

/** The impact shield of an enemy of [rank]: boss ranks the heavy plates, ordinary and Elite enemies the light ones. */
internal fun DrawScope.drawImpactShield(engine: GameplayRenderModel, enemy: EnemyProjection, center: Offset, rank: EnemyRank) {
    val look = when (rank) {
        EnemyRank.MINI_BOSS, EnemyRank.BOSS, EnemyRank.SECRET_BOSS -> ShieldLook.BOSS
        EnemyRank.ORDINARY, EnemyRank.ELITE -> ShieldLook.ORDINARY
    }
    drawShieldPlates(engine, enemy, center, look)
}

/**
 * The plates of [look] on the circle of [ShieldLook.ring] radii round [center], the first at the top and the rest
 * evenly round it, each facing outward, all on the frame of the enemy's own beat. Books a signature operation of
 * [FxPool.STATUS] per plate.
 */
internal fun DrawScope.drawShieldPlates(engine: GameplayRenderModel, enemy: EnemyProjection, center: Offset, look: ShieldLook) {
    val count = look.plates(enemy.radius)
    val radius = enemy.radius * look.ring
    FxDrawBudget.take(FxPool.STATUS, count, signature = true)
    val frame = heldLoopFrame(look.plate, engine.renderElapsed, phase = enemy.id)
    val palette = PixelPalettes.of(Kk.Bone)
    val scale = engine.worldScale
    for (plate in 0 until count) {
        val angle = -QUARTER_TURN + plate * CanvasMath.TAU / count
        drawPixelAimed(look.plate, look.diagonal, frame, center.x + cos(angle) * radius, center.y + sin(angle) * radius, angle, scale, palette)
    }
}

private const val QUARTER_TURN = CanvasMath.TAU / 4f
