// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.interaction.canvas.fx

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import kinetickk.ball.gameplay.interaction.canvas.*
import kinetickk.ball.gameplay.interaction.canvas.world.*
import kinetickk.ball.gameplay.interaction.fx.CoreRibbonProjection
import kinetickk.ball.gameplay.interaction.fx.PixelFxClock
import kinetickk.ball.gameplay.interaction.fx.PixelStrip
import kinetickk.ball.gameplay.interaction.fx.RIBBON_MAX_AGE
import kinetickk.ball.gameplay.interaction.fx.RIBBON_MIN_SPEED
import kinetickk.ball.gameplay.interaction.fx.ribbonLength
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.profile.api.ParticleDensity
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkRolePalette
import kinetickk.foundation.design.isOnScreen
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/** Width at the Core (about the old wedge's 9.6 px base); ×1.25 in Overdrive. */
private const val RIBBON_HEAD_WIDTH = 10f

/** The width tapers as `(1 − u)^0.8` along the drawn length (u = 0 at the Core, 1 at the end). */
private const val RIBBON_WIDTH_EXPONENT = 0.8f

/** Samples recorded in a Dash burn this much hotter. */
private const val RIBBON_DASH_BOOST = 1.3f

/** The bone rails run over this share of the drawn length (the old lines' 60 + 180 r of 80 + 260 r). */
private const val RIBBON_RAIL_SHARE = 0.7f

/** The rails' distance either side of the path (world units). */
private const val RIBBON_RAIL_OFFSET = 13f

/** Rail pips stand this far apart along the path, the first this far behind the Core (world units). */
private const val RAIL_PIP_SPACING = 9f
private const val RAIL_PIP_FIRST = 4f

/** Rail B, right of the backward direction, carries this share of rail A's light. */
private const val RAIL_B_SHARE = 0.7f

/** How much of the ribbon's heat a fully straight course takes away: it glows while carving, cools on straights. */
private const val RIBBON_STRAIGHT_COOLING = 0.3f

/** The Dash phase (CharacterSystem's 0.24 s): the ghosts break up with what is left of it. */
private const val DASH_GHOST_PHASE_SECONDS = 0.24f

/** Each ghost further back starts this much weaker. */
private const val DASH_GHOST_STEP = 0.4f

/** Path distances of the three Dash ghosts (the old afterimages' 60 × (1 + 0.4 i)). */
private val DashGhostDistance = floatArrayOf(84f, 108f, 132f)

/** Beads from the Overdrive head to the blunt end, the widths (world units) from which each takes over, and their spacing. */
private val RibbonBeads = arrayOf(PixelStrip.CORE_RIBBON_XL, PixelStrip.CORE_RIBBON_L, PixelStrip.CORE_RIBBON_M, PixelStrip.CORE_RIBBON_S,
    PixelStrip.CORE_RIBBON_XS)
private val RibbonBeadWidth = floatArrayOf(11.5f, 8.5f, 6f, 3.5f, 0f)
private val RibbonBeadSpacing = floatArrayOf(7f, 5.5f, 4f, 3f, 3f)

/** Frame groups of a bead strip: three loops of three frames (hot, warm, cool), then two of embers. */
private const val HEAT_LOOP = 3
private const val EMBER_FIRST = 9
private const val EMBER_FRAMES = 2

/** The light a bead needs for each heat: hot, warm, cool, embers; less draws nothing. */
private val RibbonHeat = floatArrayOf(0.7f, 0.47f, 0.2f, 0.05f)

/** Rail pip frames by the light left on the rail: bright, plain, dim, a dot, a dying dot. */
private val RailLight = floatArrayOf(0.75f, 0.5f, 0.3f, 0.15f, 0.04f)

/** Ghost frames by the ghost's strength: solid, thin, torn, broken, dots. */
private val GhostStrength = floatArrayOf(0.75f, 0.5f, 0.3f, 0.15f, 0.04f)

/** Beads one ribbon stamps at most (the longest ribbon at its tightest spacing). */
private const val MAX_BEADS = 256

/**
 * The Core's ribbon: plasma beads stamped back along its recorded path (never along the velocity) at fixed distances
 * from the drawn Core, as far as [ribbonLength] at the current speed. The head bead sits on the interpolated Core; the
 * others lie on the sampled path and glide with it, only their frames stepping. They step down through the bead sizes
 * as the old width tapered, to a blunt end, and each loops its heat at its own rate: hot in a Dash or Overdrive (and on
 * samples a Dash recorded), warm while carving, cool on a straight, embers where the light runs out. Bone pips mark the
 * two rails over the first 70 %, and Core-sized ghosts stand on the path while a Dash runs. No segment joins a sample
 * that starts a chain to an older one. A sample's age and the Dash phase are held to the ticks of the strip they pick a
 * frame of, so heat, rail light and ghosts change only on those ticks. Every bead, pip and ghost is one signature operation
 * of [FxPool.TRAIL]. A pastel volt in Baby mode; drawn without allocating (the beads are kept in the draw thread's
 * scratch, so the head draws last, over the rest).
 */
internal fun DrawScope.drawCoreRibbon(
    engine: GameplayRenderModel,
    ribbon: CoreRibbonProjection,
    shakeX: Float,
    shakeY: Float,
    roles: KkRolePalette,
) {
    if (engine.phase == GamePhase.GAME_OVER || engine.speed <= RIBBON_MIN_SPEED || ribbon.size == 0) return
    val overdrive = engine.overdriveTime > 0f
    val dashing = engine.dashPhaseTime > 0f
    val palette = PixelPalettes.of(if (engine.easyMode) easyPastelYou(roles) else roles.you)
    val rail = PixelPalettes.of(if (engine.easyMode) easyPastelLilac(roles) else Kk.Bone)
    val scale = engine.worldScale
    val cooling = 1f - RIBBON_STRAIGHT_COOLING * engine.coreStraightness.coerceIn(0f, 1f)
    val base = (if (overdrive || dashing) 0.85f else 0.55f) * cooling
    val headWidth = RIBBON_HEAD_WIDTH * if (overdrive) 1.25f else 1f
    val budget = ribbonLength(engine.speed)
    val railBudget = budget * RIBBON_RAIL_SHARE
    val rails = engine.settings.particleDensity != ParticleDensity.LOW
    val head = world(engine, engine.renderCoreX, engine.renderCoreY, shakeX, shakeY)
    val scratch = CoreRibbonScratch
    var beads = 0
    var hx = head.x
    var hy = head.y
    var travelled = 0f
    var nextBead = 0f
    var nextPip = RAIL_PIP_FIRST
    for (index in 0 until ribbon.size) {
        val point = world(engine, ribbon.x(index), ribbon.y(index), shakeX, shakeY)
        val dx = point.x - hx
        val dy = point.y - hy
        val length = sqrt(dx * dx + dy * dy)
        if (length > MIN_SEGMENT) {
            val take = min(length, budget - travelled)
            val ux = dx / length
            val uy = dy / length
            val age = ribbon.age(index)
            val boost = if (ribbon.dash(index)) RIBBON_DASH_BOOST else 1f
            val end = travelled + take
            while (nextBead <= end && beads < MAX_BEADS) {
                val u = nextBead / budget
                val width = headWidth * (1f - u).coerceAtLeast(0f).pow(RIBBON_WIDTH_EXPONENT)
                var size = 0
                while (width < RibbonBeadWidth[size]) size++
                val heat = stageOf(base * (1f - u) * ageFade(age, RibbonBeads[size].fps) * boost, RibbonHeat)
                if (heat >= 0) {
                    scratch.x[beads] = hx + ux * (nextBead - travelled)
                    scratch.y[beads] = hy + uy * (nextBead - travelled)
                    scratch.look[beads] = size * RibbonHeat.size + heat
                    beads++
                }
                nextBead += RibbonBeadSpacing[size]
            }
            if (rails) {
                // Rail A (brighter) left of the backward direction, rail B right of it, at fixed distances along the path.
                while (nextPip <= end && nextPip < railBudget) {
                    val along = nextPip - travelled
                    val px = hx + ux * along
                    val py = hy + uy * along
                    val light = (1f - nextPip / railBudget) * ageFade(age, PixelStrip.CORE_RAIL.fps)
                    drawRailPip(px + uy * RIBBON_RAIL_OFFSET, py - ux * RIBBON_RAIL_OFFSET, light, scale, rail)
                    drawRailPip(px - uy * RIBBON_RAIL_OFFSET, py + ux * RIBBON_RAIL_OFFSET, light * RAIL_B_SHARE, scale, rail)
                    nextPip += RAIL_PIP_SPACING
                }
            }
            travelled = end
            hx += ux * take
            hy += uy * take
            if (travelled >= budget) break
        }
        if (ribbon.startsChain(index)) break
    }
    val time = engine.renderElapsed
    for (bead in beads - 1 downTo 0) {
        val x = scratch.x[bead]
        val y = scratch.y[bead]
        if (!isOnScreen(Offset(x, y), 40f)) continue
        FxDrawBudget.take(FxPool.TRAIL, 1, signature = true)
        val strip = RibbonBeads[scratch.look[bead] / RibbonHeat.size]
        val heat = scratch.look[bead] % RibbonHeat.size
        val frame = if (heat < RibbonHeat.lastIndex) heat * HEAT_LOOP + PixelFxClock.loopFrame(strip, time, 0, HEAT_LOOP, bead)
        else EMBER_FIRST + PixelFxClock.loopFrame(strip, time, 0, EMBER_FRAMES, bead)
        drawPixelFrame(strip, frame, x, y, scale, palette)
    }
    if (dashing) drawDashGhosts(engine, ribbon, shakeX, shakeY, palette, budget, scale)
}

/** The light a sample of [age] seconds keeps, its age held to the ticks of the [fps] strip it lights. */
private fun ageFade(age: Float, fps: Int): Float = (1f - PixelFxClock.stepped(age, fps) / RIBBON_MAX_AGE).coerceIn(0f, 1f)

/** The first index of [thresholds] that [value] reaches, or −1 below them all. */
private fun stageOf(value: Float, thresholds: FloatArray): Int {
    for (stage in thresholds.indices) if (value >= thresholds[stage]) return stage
    return -1
}

private fun DrawScope.drawRailPip(x: Float, y: Float, light: Float, scale: Float, palette: ColorFilter) {
    val frame = stageOf(light, RailLight)
    if (frame < 0 || !isOnScreen(Offset(x, y), 8f)) return
    FxDrawBudget.take(FxPool.TRAIL, 1, signature = true)
    drawPixelFrame(PixelStrip.CORE_RAIL, frame, x, y, scale, palette)
}

/**
 * The three Dash ghosts (Core-sized) at their distances along the drawn ribbon, breaking up with the Dash phase: how far
 * it has run, held to the ghost strip's ticks.
 */
private fun DrawScope.drawDashGhosts(
    engine: GameplayRenderModel,
    ribbon: CoreRibbonProjection,
    shakeX: Float,
    shakeY: Float,
    palette: ColorFilter,
    budget: Float,
    scale: Float,
) {
    val phase = 1f - steppedProgress(engine.stepAge(DASH_GHOST_PHASE_SECONDS - engine.dashPhaseTime), DASH_GHOST_PHASE_SECONDS, PixelStrip.CORE_GHOST.fps)
    val point = CoreRibbonScratch.point
    for (ghost in DashGhostDistance.indices) {
        val distance = DashGhostDistance[ghost]
        if (distance > budget || !CoreRibbonGeometry.pointAlong(ribbon, engine.renderCoreX, engine.renderCoreY, distance, point)) return
        val frame = stageOf((1f - DASH_GHOST_STEP * ghost) * phase, GhostStrength)
        if (frame < 0) continue
        val center = world(engine, point[0], point[1], shakeX, shakeY)
        FxDrawBudget.take(FxPool.TRAIL, 1, signature = true)
        drawPixelFrame(PixelStrip.CORE_GHOST, frame, center.x, center.y, scale, palette)
    }
}

/** Shorter segments (repeated positions) join nothing. */
private const val MIN_SEGMENT = 0.01f

/** Pure ribbon geometry in world units. */
internal object CoreRibbonGeometry {
    /**
     * The point [distance] world units along the ribbon from the head ([headX], [headY]) through the samples newest
     * first, written to [out] (x, y). False when the chain (up to its first [CoreRibbonProjection.startsChain] sample)
     * is shorter, or for a negative distance. Repeated positions add no length.
     */
    fun pointAlong(ribbon: CoreRibbonProjection, headX: Float, headY: Float, distance: Float, out: FloatArray): Boolean {
        if (distance < 0f) return false
        var hx = headX
        var hy = headY
        var travelled = 0f
        for (index in 0 until ribbon.size) {
            val dx = ribbon.x(index) - hx
            val dy = ribbon.y(index) - hy
            val length = sqrt(dx * dx + dy * dy)
            if (length > MIN_SEGMENT) {
                if (travelled + length >= distance) {
                    val t = (distance - travelled) / length
                    out[0] = hx + dx * t
                    out[1] = hy + dy * t
                    return true
                }
                travelled += length
                hx = ribbon.x(index)
                hy = ribbon.y(index)
            }
            if (ribbon.startsChain(index)) return false
        }
        return false
    }
}

/** The draw thread's reused ghost position and the beads of the ribbon being drawn: position, and size × heats + heat. */
private object CoreRibbonScratch {
    val point = FloatArray(2)
    val x = FloatArray(MAX_BEADS)
    val y = FloatArray(MAX_BEADS)
    val look = IntArray(MAX_BEADS)
}
