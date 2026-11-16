// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.interaction.canvas.fx

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import kinetickk.ball.gameplay.interaction.canvas.CanvasMath
import kinetickk.foundation.design.isOnScreen
import kinetickk.ball.gameplay.interaction.canvas.renderCoreX
import kinetickk.ball.gameplay.interaction.canvas.renderCoreY
import kinetickk.ball.gameplay.interaction.canvas.fxAge
import kinetickk.ball.gameplay.interaction.canvas.world
import kinetickk.ball.gameplay.interaction.canvas.worldScale
import kinetickk.ball.gameplay.interaction.fx.PixelFxClock
import kinetickk.ball.gameplay.interaction.fx.PixelLooks
import kinetickk.ball.gameplay.interaction.fx.PixelStrip
import kinetickk.ball.gameplay.interaction.fx.VisualFxProjection
import kinetickk.ball.gameplay.nucleus.protocol.ReactionCueFlags
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.profile.api.ReactionEffectsLevel
import kinetickk.foundation.design.KkEase
import kinetickk.foundation.design.KkRolePalette
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private const val TAU = CanvasMath.TAU

/** Live transient looks past which older ones lose their satellites. */
private const val CROWDED_BURSTS = 16

/** A look older than this (seconds) is "older" for [CROWDED_BURSTS]. */
private const val OLD_BURST_SECONDS = 0.2f

/** Frame rate of a burst's ring and how long it takes to reach the burst's edge. */
private const val BURST_RING_FPS = 16
private const val BURST_RING_SECONDS = 0.5f

/** A burst scatters one mote per this many units of reach past [SATELLITE_FREE_REACH] ([PixelLooks.MAX_SATELLITES]). */
private const val SATELLITE_FREE_REACH = 50f
private const val SATELLITE_REACH_STEP = 30f

/** A cascade (a reaction set off by another) plays at this share of the frame's alpha. */
private const val CASCADE_ALPHA = 0.7f

/** Pieces of a reaction that always draw, whatever the budget, the setting and the crowd: enough to read its reach. */
private const val SIGNATURE_PIECES = 6

/** Whether this frame's bursts keep only their signature (the Minimal setting, or a crowd past the signature limit). */
internal fun signatureOnly(): Boolean = FxFrame.level == ReactionEffectsLevel.MINIMAL || FxFrame.crowd > FxFrame.CROWD_SIG_ONLY

/** Whether this frame's bursts scatter satellites (the Full setting and no crowd). */
internal fun satellitesOn(): Boolean = FxFrame.level == ReactionEffectsLevel.FULL && FxFrame.crowd <= FxFrame.CROWD_SECONDARY_OFF

internal fun satelliteCount(reach: Float, extra: Int): Int =
    ((reach - SATELLITE_FREE_REACH) / SATELLITE_REACH_STEP).toInt().coerceIn(0, PixelLooks.MAX_SATELLITES) +
        extra.coerceIn(0, PixelLooks.EXTRA_SATELLITES)

/** A strip playing once from [age] at a layer point; a signature piece always draws and is recorded. */
internal fun DrawScope.playStrip(strip: PixelStrip, age: Float, x: Float, y: Float, scale: Float, palette: ColorFilter, alpha: Float,
    signature: Boolean, flip: Boolean = false): Boolean {
    val frame = PixelFxClock.frame(strip, age)
    if (frame < 0) return false
    if (!FxDrawBudget.take(FxPool.TRANSIENT, 1, signature)) return false
    drawPixelFrame(strip, frame, x, y, scale, palette, alpha = alpha, flipX = flip)
    return true
}

/** A Break's ring stepping out to [reach] over [BURST_RING_SECONDS]; a signature piece. */
private fun DrawScope.playRing(center: Offset, reach: Float, age: Float, scale: Float, palette: ColorFilter, alpha: Float) {
    if (age < 0f || age >= BURST_RING_SECONDS) return
    val progress = steppedProgress(age, BURST_RING_SECONDS, BURST_RING_FPS)
    FxDrawBudget.take(FxPool.TRANSIENT, PIXEL_RING_OPS, signature = true)
    drawPixelRing(center.x, center.y, reach * KkEase.Out.transform(progress), ringStage(progress), scale, palette, alpha = alpha)
}

/** Motes scattered between half and most of the [reach], alternating [stripA] and [stripB], each a little after the last. */
internal fun DrawScope.playSatellites(center: Offset, reach: Float, age: Float, seed: Int, count: Int, scale: Float, stripA: PixelStrip,
    a: ColorFilter, stripB: PixelStrip, b: ColorFilter, alpha: Float, near: Float = 0.5f, far: Float = 0.82f) {
    for (index in 0 until count) {
        val first = index % 2 == 0
        val strip = if (first) stripA else stripB
        val frame = PixelFxClock.frame(strip, age - (index + 1) * PixelLooks.SATELLITE_STAGGER)
        if (frame < 0) continue
        if (!FxDrawBudget.take(FxPool.TRANSIENT, 1, signature = false)) return
        val angle = (index + fxHash(seed, 2 * index + 1)) * TAU / count
        val distance = reach * (near + (far - near) * fxHash(seed, 2 * index + 2))
        drawPixelFrame(strip, frame, center.x + cos(angle) * distance, center.y + sin(angle) * distance, scale, if (first) a else b,
            alpha = alpha, flipX = index % 4 >= 2)
    }
}

/**
 * Breaks and reactions play short geometric bursts. The ring or scattering pieces show the effect's reach.
 * Cascades use less opacity, and crowded scenes keep the main shape while dropping some particles.
 */
internal fun DrawScope.drawElementBursts(
    engine: GameplayRenderModel,
    visualFx: VisualFxProjection,
    shakeX: Float,
    shakeY: Float,
    roles: KkRolePalette,
) {
    val bursts = visualFx.elementBursts
    if (bursts.isEmpty()) return
    val scale = engine.worldScale
    val peak = FxFrame.peakAlpha
    val minimal = signatureOnly()
    val scatter = satellitesOn()
    val crowded = bursts.size > CROWDED_BURSTS
    for (index in bursts.indices) {
        val burst = bursts[index]
        val center = world(engine, burst.x, burst.y, shakeX, shakeY)
        val reach = burst.radius * (1f + 0.12f * (burst.weight - 1))
        val verb = burst.flags and ReactionCueFlags.VERB != 0
        // A burst whose look also plays on the Core stays drawn while the Core is on screen.
        if (!verb && !isOnScreen(center, reach * 1.3f + 24f)) continue
        val age = engine.fxAge(burst.life, burst.maxLife)
        val seed = burst.seed
        val alpha = if (burst.flags and ReactionCueFlags.CASCADE != 0) peak * CASCADE_ALPHA else peak
        val full = scatter && !(crowded && age > OLD_BURST_SECONDS)
        if (burst.secondary < 0) {
            val a = PixelPalettes.of(roles.element(burst.primary))
            val mote = PixelLooks.moteStrip(burst.primary)
            playRing(center, reach, age, scale, a, alpha)
            playStrip(PixelLooks.breakStrip(burst.primary), age, center.x, center.y, scale, a, alpha, signature = true)
            if (full) playSatellites(center, reach, age, seed, satelliteCount(reach, burst.weight - 1), scale, mote, a, mote, a, alpha)
            continue
        }
        val look = PixelLooks.reaction(burst.reaction)
        val first = roles.element(burst.primary)
        val palette = PixelPalettes.duo(first, roles.element(burst.secondary))
        playStrip(look.strip, age, center.x, center.y, scale, palette, alpha, signature = true)
        // A cascade, the quieter settings and a crowd throw half the pieces.
        val quiet = minimal || !full || burst.flags and ReactionCueFlags.CASCADE != 0
        val signatures = min(SIGNATURE_PIECES, pieceCount(reach, look.fan))
        val pieces = pieceCount(reach, look.fan).let { if (quiet) max(signatures, it / 2) else it }
        // A reaction with a heading throws its fan along it; one without takes a heading from its seed.
        val heading = if (burst.direction.isNaN()) fxHash(seed, 1) * TAU else burst.direction
        drawPixelPieces(look.piece, look.pieceDiagonal, look.motion, center.x, center.y, reach, age, seed, pieces, scale, palette, alpha,
            signatures, cos(heading), sin(heading), if (look.fan > 0f) look.fan else TAU)
        // A keystone rewrote the reaction: its gold wedge over the figure.
        if (burst.flags and ReactionCueFlags.KEYSTONE != 0) {
            playStrip(PixelStrip.REACT_KEYSTONE, age, center.x, center.y, scale, PixelPalettes.of(roles.gold.base), alpha, signature = true)
        }
        if (verb) {
            val core = world(engine, engine.renderCoreX, engine.renderCoreY, shakeX, shakeY)
            playStrip(PixelStrip.REACT_VERB, age, core.x, core.y, scale, PixelPalettes.of(first), alpha, signature = true)
        }
    }
}
