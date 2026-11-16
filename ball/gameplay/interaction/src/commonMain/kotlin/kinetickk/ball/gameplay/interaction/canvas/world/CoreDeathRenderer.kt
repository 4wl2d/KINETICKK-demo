// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.interaction.canvas.world

import kinetickk.ball.gameplay.interaction.fx.PieceMotion
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import kinetickk.ball.gameplay.interaction.canvas.fx.*
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.gameplay.interaction.canvas.renderElapsed
import kinetickk.ball.gameplay.interaction.canvas.worldScale
import kinetickk.ball.gameplay.interaction.fx.PixelFxClock
import kinetickk.ball.gameplay.interaction.fx.PixelStrip
import kinetickk.ball.gameplay.nucleus.render.CoreStasisMotion
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.profile.api.ParticleDensity
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkRolePalette

/** Seconds after which the in-world death is over (the report covers the world). */
internal const val CoreDeathDuration = 1.1f

/** The Core swells over this many ticks of its death strip before the strip plays, growing by [SwellRate] of its size a second. */
private const val SwellTicks = 2
private const val SwellRate = 2.5f

/** A Core frozen solid stays encased this long before the ice bursts. */
private const val EncaseSeconds = 0.38f

/** How far the shattered Core's wedges and the frozen Core's prisms fly (the old flash rings' reach, world units). */
private const val SHARD_REACH = 150f
private const val ICE_REACH = 170f

/** The frozen Core's bone debris flies this share of the prisms' reach. */
private const val DEBRIS_REACH_SHARE = 0.65f

/**
 * Bounded presentation only: the accepted run is already finished and cannot gain rewards. The vector Core swells over
 * the first [SwellTicks] ticks of its death strip's rate, each tick at the size it reaches by the tick's end (about 1.16
 * and then 1.31 of its own), then the strip plays on the ticks that follow: it bursts white, hollows into a torn threat
 * ring round a black singularity, throws wedges and collapses to embers, while wedge pieces fly out to [SHARD_REACH];
 * all of it is over before [CoreDeathDuration]. A Core frozen solid by Core Freeze dies its own way instead
 * ([drawCoreFreezeDeath]).
 */
internal fun DrawScope.drawCoreDeath(
    engine: GameplayRenderModel,
    elapsed: Float,
    roles: KkRolePalette,
) {
    val core = Offset(engine.coreX - engine.cameraX + size.width * 0.5f,
        engine.coreY - engine.cameraY + size.height * 0.5f)
    if (engine.coreStasisMotion == CoreStasisMotion.FROZEN) {
        drawCoreFreezeDeath(engine, core, elapsed, roles)
        return
    }
    val fps = PixelStrip.CORE_DEATH.fps
    val tick = PixelFxClock.tick(elapsed, fps)
    if (tick < SwellTicks) {
        scale(1f + (tick + 1) / fps.toFloat() * SwellRate, pivot = core) { drawCore(engine, core, roles) }
        return
    }
    if (elapsed >= CoreDeathDuration) return
    val t = elapsed - SwellTicks / fps.toFloat()
    val scale = engine.worldScale
    val threat = PixelPalettes.of(roles.threat)
    drawPixelPieces(PixelStrip.CORE_SHARD, PixelStrip.CORE_SHARD_D, PieceMotion.OUT, core.x, core.y, SHARD_REACH, t, DEATH_SEED,
        deathPieces(engine), scale, threat, pool = FxPool.CORE_STATES)
    drawPixelFrame(PixelStrip.CORE_DEATH, PixelFxClock.frame(PixelStrip.CORE_DEATH, t), core.x, core.y, scale, threat)
}

/** Pieces a death throws by the particle density setting (the old debris counts). */
private fun deathPieces(engine: GameplayRenderModel): Int = when (engine.settings.particleDensity) {
    ParticleDensity.LOW -> 6
    ParticleDensity.NORMAL -> 12
    ParticleDensity.HIGH -> 18
}

/** Turns the death's piece sets: a fixed seed, so a death replays the same way. */
private const val DEATH_SEED = 0x2C0DE

/**
 * A Core frozen solid: the iced Core stays in the middle while its ice block clouds over for [EncaseSeconds], then the
 * block's burst plays on its own ticks (it blazes, splits into prisms, a frost hexagon races out and frost hangs) while
 * ice prisms fly out to [ICE_REACH] over a shorter throw of bone debris; all of it is over before [CoreDeathDuration].
 */
private fun DrawScope.drawCoreFreezeDeath(engine: GameplayRenderModel, core: Offset, elapsed: Float, roles: KkRolePalette) {
    if (elapsed >= CoreDeathDuration) return
    val radius = engine.coreRadius
    val scale = engine.worldScale
    drawCore(engine, core, roles)
    drawCoreFrostAt(core, radius, 1f, engine.renderElapsed, roles, thawing = false, scale = scale)
    if (elapsed < EncaseSeconds) {
        drawCoreEncased(core, radius, elapsed, EncaseSeconds, roles, scale)
        return
    }
    val t = elapsed - EncaseSeconds
    val cryo = PixelPalettes.of(roles.element(StatusElement.CRYO.ordinal))
    val pieces = deathPieces(engine)
    drawPixelPieces(PixelStrip.CORE_ICE, PixelStrip.CORE_ICE_D, PieceMotion.OUT, core.x, core.y, ICE_REACH * DEBRIS_REACH_SHARE, t,
        DEATH_SEED + 1, pieces / 2, scale, PixelPalettes.of(Kk.Bone), pool = FxPool.CORE_STATES)
    drawPixelPieces(PixelStrip.CORE_ICE, PixelStrip.CORE_ICE_D, PieceMotion.OUT, core.x, core.y, ICE_REACH, t, DEATH_SEED, pieces, scale,
        cryo, pool = FxPool.CORE_STATES)
    drawPixelFrame(PixelStrip.CORE_DEATH_FROZEN, PixelFxClock.frame(PixelStrip.CORE_DEATH_FROZEN, t), core.x, core.y, scale, cryo)
}
