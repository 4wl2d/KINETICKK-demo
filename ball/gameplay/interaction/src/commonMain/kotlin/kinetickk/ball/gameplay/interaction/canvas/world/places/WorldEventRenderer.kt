// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.interaction.canvas.world.places

import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.WorldEventKind
import kinetickk.ball.content.api.obstacleSeedUnit
import kinetickk.ball.gameplay.interaction.canvas.*
import kinetickk.ball.gameplay.interaction.canvas.fx.*
import kinetickk.ball.gameplay.interaction.canvas.world.enemies.TELEGRAPH_POOL
import kinetickk.ball.gameplay.interaction.fx.PixelFxClock
import kinetickk.ball.gameplay.interaction.fx.PixelStrip
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.nucleus.render.WorldEventProjection
import kinetickk.ball.profile.api.ParticleDensity
import kinetickk.foundation.design.*
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

// Ambient world events: the storm's motes and the flare's sweep on the ground, then the telegraphed strikes, all as pixel
// cells. Colours come from the role palette only: the meteor's strike rings are danger (threat), the ion storm's rings take
// the Ion element's hue with a threat ring where the Core is hurt, the data storm is bone, the gravity storm polarity and
// the flare heat.

/** Side of an ambient cell (world units); a large screen grows it so no frame draws more than [MAX_EVENT_CELLS]. */
internal const val EVENT_CELL = 160f

/** Ambient cells one frame may draw (one shape each). */
internal const val MAX_EVENT_CELLS = 48

/** Growth of the cell side while a screen would need more than [MAX_EVENT_CELLS]. */
private const val EVENT_CELL_GROWTH = 1.25f

/** Gravity streaks drift down the wind at this speed (world units per second). */
private const val GRAVITY_DRIFT = 150f

/** Data motes rise at this speed (world units per second) and sway sideways. */
private const val DATA_RISE = 38f
private const val DATA_SWAY = 6f

/** Share of data-storm cells that hold a brighter glint instead of a mote. */
private const val DATA_GLINT_SHARE = 0.2f

/** The flare's band crosses the screen in this long after a sweep starts; its width in world units. */
internal const val SOLAR_SWEEP_CROSS_SECONDS = 1.2f
private const val SOLAR_SWEEP_BAND = 260f

/** How long the ground look and the edge band take to fade out at the event's end (seconds). */
internal const val WORLD_EVENT_FADE_SECONDS = 3f

/** Ground looks ramp to this share of their strength while the event is only telegraphed. */
private const val TELEGRAPH_AMBIENT_SHARE = 0.5f

/**
 * The event's ground look after the dimension marks: the gravity storm's polarity streaks drifting down its wind, the data
 * storm's bone motes rising, the solar flare's heat band crossing the screen on each sweep. The motes sit in world-anchored
 * cells (one stateless hash of the cell and the kind each, no state, at most [MAX_EVENT_CELLS] a frame), each one
 * shape stepping at its strip's rate; a Low particle density draws half of them and a crowd above the status-pattern limit
 * half, above the signature limit none, and the event's ramp thins them cell by cell. The sweep band is rows of tiled heat
 * segments crossing in steps of their own frame rate; it always draws, since the sweep heats enemies, as signature work
 * of the weather's pool.
 */
internal fun DrawScope.drawWorldEventAmbient(engine: GameplayRenderModel, shakeX: Float, shakeY: Float, roles: KkRolePalette) {
    val event = engine.worldEvent ?: return
    val now = engine.renderElapsed
    val share = worldEventAmbientShare(event, now)
    if (share <= 0f) return
    when (event.kind) {
        WorldEventKind.GRAVITY_STORM, WorldEventKind.DATA_STORM -> drawEventMotes(engine, event, now, share, shakeX, shakeY, roles)
        WorldEventKind.SOLAR_FLARE -> drawSolarSweep(event, now, share, shakeX, shakeY, PixelPalettes.of(roles.heat), engine.worldScale)
        // Meteors and ion zones show only as their strikes.
        WorldEventKind.METEOR_SHOWER, WorldEventKind.ION_STORM -> Unit
    }
}

/**
 * Telegraphed strikes after fire-trap bursts, each filling toward its landing on its pips' ticks: a meteor's
 * ring of threat pips at the landing's exact radius over an ink halftone shadow that grows and darkens as it falls and a
 * threat halftone reaching out; an ion zone's ring of pips in the Ion hue over its halftone, with a ring of threat pips
 * where it hurts the Core. The pips pulse twice as fast as the landing nears, on their strip's own ticks either way.
 * Rings and discs are capped ([MAX_PIPS], [MAX_DISC_ROWS]) and every strike is signature work of the telegraphs' pool.
 */
internal fun DrawScope.drawWorldEventStrikes(engine: GameplayRenderModel, shakeX: Float, shakeY: Float, roles: KkRolePalette) {
    val event = engine.worldEvent ?: return
    val strikes = event.strikes
    val meteor = event.kind == WorldEventKind.METEOR_SHOWER
    if (strikes.isEmpty() || !meteor && event.kind != WorldEventKind.ION_STORM) return
    val now = engine.renderElapsed
    val scale = engine.worldScale
    val threat = PixelPalettes.of(roles.threat)
    val ring = if (meteor) threat else PixelPalettes.of(roles.element(StatusElement.ION.ordinal))
    val coreReach = engine.content.variety.worldEvents.ion.coreRadius
    val strip = PixelStrip.EVENT_PIP
    for (index in strikes.indices) {
        val strike = strikes[index]
        val center = world(engine, strike.x, strike.y, shakeX, shakeY)
        if (!isOnScreen(center, strike.radius + 8f)) continue
        val span = (strike.strikesAt - strike.armedAt).coerceAtLeast(0.01f)
        val armed = now - strike.armedAt
        val progress = (PixelFxClock.stepped(armed, strip.fps) / span).coerceIn(0f, 1f)
        // The pulse steps a frame every other tick, then every tick for the last stretch; neighbouring pips run half a
        // pulse apart, so the ring shimmers.
        val tick = PixelFxClock.tick(armed, strip.fps)
        val quicken = ceil(STRIKE_QUICKEN * span * strip.fps).toInt()
        val pulse = if (tick < quicken) tick / 2 else quicken / 2 + tick - quicken
        var ops = 0
        if (meteor) {
            // The meteor's shadow grows and darkens as it falls; the ring is where it lands.
            ops += drawPixelDisc(center.x, center.y, strike.radius * (0.2f + 0.6f * progress), 1 + (progress * 2.99f).toInt(), scale,
                PixelPalettes.of(Kk.Ink))
        }
        ops += drawPixelDisc(center.x, center.y, strike.radius * progress, if (progress < STRIKE_DENSER) 0 else 1, scale, ring)
        ops += drawPipRing(strip, center.x, center.y, strike.radius, STRIKE_PIP_SPACING, scale, ring) { pip -> (pulse + (pip and 1) * 2) % strip.frames }
        if (!meteor) {
            ops += drawPipRing(strip, center.x, center.y, coreReach, STRIKE_PIP_SPACING, scale, threat) { pip -> (pulse + (pip and 1) * 2) % strip.frames }
        }
        FxDrawBudget.take(TELEGRAPH_POOL, ops, signature = true)
    }
}

/** A strike's pips pulse twice as fast from this share of its telegraph; its fill is denser past [STRIKE_DENSER]. */
private const val STRIKE_QUICKEN = 0.6f
private const val STRIKE_DENSER = 0.6f

/** A strike ring's pips sit about this many world units apart. */
private const val STRIKE_PIP_SPACING = 12f

/**
 * Share (0..1) of an event's ground look at [now]: up to [TELEGRAPH_AMBIENT_SHARE] over the telegraph, whole while it
 * runs, fading to nothing over its last [WORLD_EVENT_FADE_SECONDS].
 */
internal fun worldEventAmbientShare(event: WorldEventProjection, now: Float): Float {
    if (now < event.telegraphAt || now >= event.endsAt) return 0f
    if (now < event.startsAt) {
        val telegraph = (event.startsAt - event.telegraphAt).coerceAtLeast(0.01f)
        return TELEGRAPH_AMBIENT_SHARE * ((now - event.telegraphAt) / telegraph).coerceIn(0f, 1f)
    }
    return ((event.endsAt - now) / WORLD_EVENT_FADE_SECONDS).coerceIn(0f, 1f)
}

/** The ambient cell side for a [width] × [height] view (world units): [EVENT_CELL], grown until the view fits the cap. */
internal fun eventCellSize(width: Float, height: Float): Float {
    var cell = EVENT_CELL
    while (eventCellCount(width, height, cell) > MAX_EVENT_CELLS) cell *= EVENT_CELL_GROWTH
    return cell
}

/** The most world-anchored cells of side [cell] a [width] × [height] view can touch. */
internal fun eventCellCount(width: Float, height: Float, cell: Float): Int =
    ((width / cell).toInt() + 2) * ((height / cell).toInt() + 2)

private fun DrawScope.drawEventMotes(
    engine: GameplayRenderModel,
    event: WorldEventProjection,
    now: Float,
    share: Float,
    shakeX: Float,
    shakeY: Float,
    roles: KkRolePalette,
) {
    val crowd = FxFrame.crowd
    if (crowd > FxFrame.CROWD_SIG_ONLY) return
    // A low density or a crowd keeps every other cell (by the cell's own hash, so the kept ones never change).
    val halved = engine.settings.particleDensity == ParticleDensity.LOW || crowd > FxFrame.CROWD_SECONDARY_OFF
    val cell = eventCellSize(size.width, size.height)
    val left = engine.renderCameraX - size.width * 0.5f
    val top = engine.renderCameraY - size.height * 0.5f
    val firstX = floor(left / cell).toInt()
    val firstY = floor(top / cell).toInt()
    val lastX = floor((left + size.width) / cell).toInt()
    val lastY = floor((top + size.height) / cell).toInt()
    val kind = event.kind.ordinal
    val gravity = event.kind == WorldEventKind.GRAVITY_STORM
    val windX = cos(event.windAngle)
    val windY = sin(event.windAngle)
    val strength = 0.4f + 0.6f * event.windStrength.coerceIn(0f, 1f)
    val scale = engine.worldScale
    val pol = PixelPalettes.of(roles.pol)
    val bone = PixelPalettes.of(Kk.Bone)
    for (cellY in firstY..lastY) for (cellX in firstX..lastX) {
        val hash = dimensionCellHash(cellX, cellY, kind)
        if (halved && (hash and 1) == 0) continue
        // A cell shows once the event's share (and a gravity storm's wind) passes its own threshold, so the storm
        // thickens and thins mote by mote.
        if (obstacleSeedUnit(hash, 31) >= if (gravity) share * strength else share) continue
        val baseX = (cellX + obstacleSeedUnit(hash, 3)) * cell
        val baseY = (cellY + obstacleSeedUnit(hash, 11)) * cell
        val phase = obstacleSeedUnit(hash, 19)
        if (gravity) {
            val strip = PixelStrip.EVENT_GRAVITY_STREAK
            val travel = fraction(PixelFxClock.stepped(now, strip.fps) * GRAVITY_DRIFT / cell + phase)
            val along = (travel - 0.5f) * cell
            val head = world(engine, baseX + windX * along, baseY + windY * along, shakeX, shakeY)
            // The streak gathers, stretches and breaks up over its drift across the cell.
            drawPixelAimed(strip, PixelStrip.EVENT_GRAVITY_STREAK_D, (travel * strip.frames).toInt().coerceAtMost(strip.frames - 1), head.x, head.y,
                event.windAngle, scale, pol)
        } else {
            val strip = PixelStrip.EVENT_DATA_MOTE
            val held = PixelFxClock.stepped(now, strip.fps)
            val travel = fraction(held * DATA_RISE / cell + phase)
            val sway = sin(held * 1.3f + phase * 6.2832f) * DATA_SWAY
            val center = world(engine, baseX + sway, baseY + (0.5f - travel) * cell, shakeX, shakeY)
            if (obstacleSeedUnit(hash, 27) < DATA_GLINT_SHARE) {
                drawPixelFrame(PixelStrip.EVENT_DATA_GLINT, holdLoopFrame(PixelStrip.EVENT_DATA_GLINT, now + phase * 4f), center.x, center.y, scale, bone)
            } else {
                drawPixelFrame(strip, (travel * strip.frames).toInt().coerceAtMost(strip.frames - 1), center.x, center.y, scale, bone)
            }
        }
    }
}

/**
 * The flare's sweep: a heat band crossing the screen through its centre along the sweep's angle for
 * [SOLAR_SWEEP_CROSS_SECONDS] after it starts, in steps of its segments' frame rate. The band is rows of tiled heat
 * segments along its length: dense shimmer in its middle rows, sparse at its rims (sparse throughout while the event's
 * share is under half).
 */
private fun DrawScope.drawSolarSweep(event: WorldEventProjection, now: Float, share: Float, shakeX: Float, shakeY: Float, heat: ColorFilter,
    scale: Float) {
    val since = now - event.sweepStartedAt
    if (!(since >= 0f && since < SOLAR_SWEEP_CROSS_SECONDS)) return
    val strip = PixelStrip.LINK_PLACE_FLARE
    val progress = PixelFxClock.stepped(since, strip.fps) / SOLAR_SWEEP_CROSS_SECONDS
    val reach = sqrt(size.width * size.width + size.height * size.height) * 0.5f + SOLAR_SWEEP_BAND
    val offset = -reach + 2f * reach * progress
    val normalX = cos(event.sweepAngle)
    val normalY = sin(event.sweepAngle)
    val centerX = size.width * 0.5f + normalX * offset + shakeX
    val centerY = size.height * 0.5f + normalY * offset + shakeY
    val unit = PixelSheet.step(scale) / scale
    val row = strip.h * unit
    val rows = (SOLAR_SWEEP_BAND / row).toInt().coerceAtLeast(1)
    // Each half of a row is whole tiles long, so the two halves meet on a tile's edge, and draws every one of them
    // however large the view.
    val tiles = ceil(reach / (strip.w * unit)).toInt()
    val half = tiles * strip.w * unit
    val loop = PixelFxClock.loopFrame(strip, since, 0, SWEEP_DENSE_FRAMES)
    var ops = 0
    for (index in 0 until rows) {
        val across = (index - (rows - 1) * 0.5f) * row
        val dense = share >= 0.5f && kotlin.math.abs(index - (rows - 1) * 0.5f) <= 1f
        val frame = if (dense) loop else loop + SWEEP_DENSE_FRAMES
        val x = centerX + normalX * across
        val y = centerY + normalY * across
        ops += drawPixelLink(strip, frame, x + normalY * half, y - normalX * half, x, y, scale, heat, maxTiles = tiles)
        ops += drawPixelLink(strip, frame, x, y, x - normalY * half, y + normalX * half, scale, heat, maxTiles = tiles)
    }
    FxDrawBudget.take(FxPool.ATMOSPHERE, ops, signature = true)
}

/** The flare's segment strip holds its dense loop in its first four frames and its sparse loop in the next four. */
private const val SWEEP_DENSE_FRAMES = 4

private fun fraction(value: Float): Float = value - floor(value)
