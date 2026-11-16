// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.interaction.canvas.world.places.dungeons

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import kinetickk.ball.content.api.DungeonHazardKind
import kinetickk.ball.gameplay.interaction.canvas.world.places.drawPipRing
import kinetickk.ball.gameplay.interaction.canvas.world.places.drawPixelDisc
import kinetickk.ball.gameplay.interaction.canvas.world.places.ROOM_LINK_TILES
import kinetickk.ball.gameplay.interaction.canvas.world.places.holdLoopFrame
import kinetickk.ball.gameplay.interaction.fx.PixelFxClock
import kinetickk.ball.gameplay.interaction.fx.PixelStrip
import kinetickk.ball.gameplay.nucleus.render.DungeonHazardProjection
import kinetickk.ball.gameplay.nucleus.render.DungeonProjection
import kinetickk.ball.gameplay.nucleus.render.EnemyProjection
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkRolePalette
import kinetickk.foundation.design.kkMix
import kinetickk.foundation.design.kkStroke
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kinetickk.ball.gameplay.interaction.canvas.*
import kinetickk.ball.gameplay.interaction.canvas.fx.*

/*
 * The sealed room's fight on the ground, between the floor and everything that moves, as pixel cells at the
 * projection's exact places: the incoming wave's breach portals (a rift opening at each landing point inside a ring of
 * pips closing on it, cracks turning in; the guardian's larger, in the element's hue), each hazard's telegraph (a vent's
 * halftone disc filling inside its turning dashed rim of pips round a heating grate; a lance's halftone band with its
 * dashed edges narrowing to the hot line between its emitters; both blinking in their last quarter) and its strike (a
 * white-hot halftone disc or band that breaks up level by level over the strike time, under the vent's eruption or the
 * lance's beam), and the living guardian's sigil turning under it inside its ring of pips. All of it is gameplay-critical,
 * so it draws as signature work of FxPool.DUNGEON; positions, radii and times are the projection's, the clock the run's
 * render time held to each look's own frame rate.
 */

/** A breach portal starts this many times its member's radius wide and closes to [BREACH_END]. */
private const val BREACH_START = 3.4f
private const val BREACH_END = 1.15f
private const val GUARDIAN_BREACH = 1.8f

/** Rim pips sit about this many world units apart. */
private const val PIP_SPACING = 14f

/** Dashes of a vent's rim and of the guardian's sigil, and how fast they turn (rad/s, at most a turn per 24 s here). */
private const val RIM_DASHES = 12
private const val RIM_TURN = 0.9f
private const val SIGIL_DASHES = 8
private const val SIGIL_TURN = 0.26f

/** The breaches' cracks turn this fast (rad/s). */
private const val CRACK_TURN = 1.6f

/** Frames of [PixelStrip.HAZARD_PIP]: dim, lit, hot (a blink's flash) and faint. */
private const val PIP_DIM = 0
private const val PIP_LIT = 1
private const val PIP_HOT = 2
private const val PIP_FAINT = 3

/** A telegraph blinks over its last quarter: dark on every other tick of its frame rate. */
private const val BLINK_FROM = 0.75f

/** Draws the held dungeon's breaches, hazards and guardian sigil when a room of it is sealed. */
internal fun DrawScope.drawDungeonFight(engine: GameplayRenderModel, shakeX: Float, shakeY: Float, roles: KkRolePalette) {
    val dungeon = engine.structures.dungeon ?: return
    if (dungeon.engagedRoom < 0) return
    val structure = heldStructure(engine) ?: return
    val template = engine.content.structures.template(structure.template)
    val accent = structureAccent(template, roles)
    val time = engine.renderElapsed
    drawGuardianSigil(engine, dungeon, shakeX, shakeY, time, accent, roles)
    drawBreaches(engine, dungeon, shakeX, shakeY, time, accent, roles)
    val hazards = dungeon.hazards
    val strike = engine.content.structures.hazards.strikeSeconds
    for (index in hazards.indices) drawHazard(engine, hazards[index], shakeX, shakeY, time, strike, accent, roles)
}

/**
 * The incoming wave's portals while its telegraph runs: a rift opening at each landing point (its frames spread over
 * the telegraph) inside a ring of pips closing on it, with four cracks turning in, all on the rift strip's ticks.
 */
private fun DrawScope.drawBreaches(
    engine: GameplayRenderModel,
    dungeon: DungeonProjection,
    shakeX: Float,
    shakeY: Float,
    time: Float,
    accent: Color,
    roles: KkRolePalette,
) {
    if (dungeon.incomingAt < 0f || dungeon.breaches.isEmpty()) return
    val telegraph = engine.content.structures.waveTelegraphSeconds
    val strip = PixelStrip.HAZARD_BREACH
    val since = time - (dungeon.incomingAt - telegraph)
    val held = PixelFxClock.stepped(since, strip.fps)
    val progress = (held / telegraph).coerceIn(0f, 1f)
    val frame = PixelFxClock.spreadFrame(strip, since, telegraph)
    val pip = if (progress > BLINK_FROM) PIP_HOT else PIP_LIT
    val scale = engine.worldScale
    val breaches = dungeon.breaches
    for (index in breaches.indices) {
        val breach = breaches[index]
        val at = world(engine, breach.x, breach.y, shakeX, shakeY)
        val palette = PixelPalettes.of(if (breach.guardian) kkMix(accent, Kk.Bone, 0.2f) else roles.threat)
        val radius = breach.radius * if (breach.guardian) GUARDIAN_BREACH else 1f
        val ring = radius * (BREACH_START + (BREACH_END - BREACH_START) * progress)
        var ops = drawPipRing(PixelStrip.HAZARD_PIP, at.x, at.y, ring, PIP_SPACING, scale, palette) { pip }
        // The guardian's portal also marks the disc it lands on.
        if (breach.guardian) ops += drawPipRing(PixelStrip.HAZARD_PIP, at.x, at.y, radius * BREACH_END, PIP_SPACING, scale, palette) { PIP_DIM }
        // Four cracks turning in toward the point, just outside the closing ring.
        val turn = held * CRACK_TURN + index
        val crack = PixelFxClock.loopFrame(PixelStrip.HAZARD_CRACK, since, phase = index)
        for (arm in 0 until 4) {
            val angle = turn + arm * (CanvasMath.TAU / 4f)
            drawPixelAimed(PixelStrip.HAZARD_CRACK, PixelStrip.HAZARD_CRACK_D, crack, at.x + cos(angle) * ring * 1.12f,
                at.y + sin(angle) * ring * 1.12f, angle + CanvasMath.TAU * 0.5f, scale, palette)
        }
        drawPixelFrame(strip, frame, at.x, at.y, scale, palette)
        FxDrawBudget.take(FxPool.DUNGEON, ops + 5, signature = true)
    }
}

/** One hazard: its telegraph until it strikes, then its strike for [strike] seconds. */
private fun DrawScope.drawHazard(
    engine: GameplayRenderModel,
    hazard: DungeonHazardProjection,
    shakeX: Float,
    shakeY: Float,
    time: Float,
    strike: Float,
    accent: Color,
    roles: KkRolePalette,
) {
    if (time < hazard.armedAt || time > hazard.strikesAt + strike) return
    val a = world(engine, hazard.ax, hazard.ay, shakeX, shakeY)
    val b = world(engine, hazard.bx, hazard.by, shakeX, shakeY)
    val reach = hazard.radius + abs(b.x - a.x) + abs(b.y - a.y)
    val mid = Offset((a.x + b.x) * 0.5f, (a.y + b.y) * 0.5f)
    if (mid.x < -reach || mid.y < -reach || mid.x > size.width + reach || mid.y > size.height + reach) return
    val threat = PixelPalettes.of(roles.threat)
    val hot = PixelPalettes.of(kkMix(roles.threat, Kk.Bone, 0.65f))
    val tint = PixelPalettes.of(kkMix(roles.threat, accent, 0.2f))
    val ops = when (hazard.kind) {
        DungeonHazardKind.VENT ->
            if (time >= hazard.strikesAt) drawVentStrike(engine, hazard, a, time - hazard.strikesAt, strike, threat, hot)
            else drawVentTelegraph(engine, hazard, a, time, tint, threat, hot)
        DungeonHazardKind.LANCE ->
            if (time >= hazard.strikesAt) drawLanceStrike(engine, hazard, a, b, time - hazard.strikesAt, strike, threat, hot)
            else drawLanceTelegraph(engine, hazard, a, b, time, tint, threat, hot)
    }
    FxDrawBudget.take(FxPool.DUNGEON, ops, signature = true)
}

/**
 * A vent's telegraph on the vent core strip's ticks: a faint halftone of the whole disc, a denser one filling out to
 * the telegraph's share of the radius, a dashed rim of pips at the exact radius turning, and the grate at the centre
 * heating; over the last quarter the rim flashes hot and dark and the grate blinks with it. Returns its draw operations.
 */
private fun DrawScope.drawVentTelegraph(
    engine: GameplayRenderModel,
    hazard: DungeonHazardProjection,
    at: Offset,
    time: Float,
    tint: ColorFilter,
    threat: ColorFilter,
    hot: ColorFilter,
): Int {
    val strip = PixelStrip.HAZARD_VENT_CORE
    val scale = engine.worldScale
    val armed = time - hazard.armedAt
    val tick = PixelFxClock.tick(armed, strip.fps)
    val progress = (PixelFxClock.stepped(armed, strip.fps) / (hazard.strikesAt - hazard.armedAt)).coerceIn(0f, 1f)
    val blinking = progress > BLINK_FROM
    val dark = blinking && tick % 2 == 1
    var ops = drawPixelDisc(at.x, at.y, hazard.radius, 0, scale, tint)
    ops += drawPixelDisc(at.x, at.y, hazard.radius * progress, if (progress < 0.5f) 1 else 2, scale, tint)
    val pip = if (dark) PIP_DIM else if (blinking) PIP_HOT else PIP_LIT
    ops += drawPipRing(PixelStrip.HAZARD_PIP, at.x, at.y, hazard.radius, PIP_SPACING * 0.7f, scale, threat,
        turn = PixelFxClock.stepped(time, strip.fps) * RIM_TURN, dashes = RIM_DASHES) { pip }
    val grate = if (dark) strip.frames - 1 else min(strip.frames - 2, (progress * (strip.frames - 1)).toInt())
    drawPixelFrame(strip, grate, at.x, at.y, scale, hot)
    return ops + 1
}

/**
 * A vent's strike on the eruption strip's ticks: the disc white-hot, breaking up from solid level by level over the
 * strike time, its rim of hot pips pushed out to 1.35 radii, and the eruption at the centre. Returns its draw operations.
 */
private fun DrawScope.drawVentStrike(
    engine: GameplayRenderModel,
    hazard: DungeonHazardProjection,
    at: Offset,
    since: Float,
    strike: Float,
    threat: ColorFilter,
    hot: ColorFilter,
): Int {
    val strip = PixelStrip.HAZARD_VENT_STRIKE
    val scale = engine.worldScale
    val share = (PixelFxClock.stepped(since, strip.fps) / strike).coerceIn(0f, 1f)
    val level = PIXEL_FILL_LEVELS - 1 - (share * PIXEL_FILL_LEVELS).toInt()
    var ops = if (level >= 0) drawPixelDisc(at.x, at.y, hazard.radius, level, scale, hot) else 0
    val pip = if (share < 0.5f) PIP_HOT else PIP_LIT
    ops += drawPipRing(PixelStrip.HAZARD_PIP, at.x, at.y, hazard.radius * (1f + 0.35f * share), PIP_SPACING, scale, threat) { pip }
    drawPixelFrame(strip, PixelFxClock.frame(strip, since), at.x, at.y, scale, hot)
    return ops + 1
}

/**
 * A lance's telegraph on its edge strip's ticks: a halftone of the strike's band, its two dashed edges closing from
 * twice the strike's width to it, the hot line between the emitters and the emitters pulsing at both walls; over the
 * last quarter the edges and the line go dark on every other tick and the emitters flash. Returns its draw operations.
 */
private fun DrawScope.drawLanceTelegraph(
    engine: GameplayRenderModel,
    hazard: DungeonHazardProjection,
    a: Offset,
    b: Offset,
    time: Float,
    tint: ColorFilter,
    threat: ColorFilter,
    hot: ColorFilter,
): Int {
    val strip = PixelStrip.LINK_PLACE_LANCE_EDGE
    val scale = engine.worldScale
    val armed = time - hazard.armedAt
    val tick = PixelFxClock.tick(armed, strip.fps)
    val progress = (PixelFxClock.stepped(armed, strip.fps) / (hazard.strikesAt - hazard.armedAt)).coerceIn(0f, 1f)
    val blinking = progress > BLINK_FROM
    val dark = blinking && tick % 2 == 1
    var ops = drawLanceBand(a, b, hazard.radius, if (progress < 0.5f) 0 else 1, scale, tint)
    if (!dark) {
        // The band's edges close from twice the strike's half width to it.
        val length = hypot(b.x - a.x, b.y - a.y).coerceAtLeast(1f)
        val edge = hazard.radius * (2f - progress)
        val nx = -(b.y - a.y) / length * edge
        val ny = (b.x - a.x) / length * edge
        val frame = PixelFxClock.loopFrame(strip, armed)
        ops += drawPixelLink(strip, frame, a.x + nx, a.y + ny, b.x + nx, b.y + ny, scale, threat, maxTiles = ROOM_LINK_TILES)
        ops += drawPixelLink(strip, frame, a.x - nx, a.y - ny, b.x - nx, b.y - ny, scale, threat, maxTiles = ROOM_LINK_TILES)
        ops += drawPixelLink(PixelStrip.LINK_PLACE_LANCE_CORE, PixelFxClock.loopFrame(PixelStrip.LINK_PLACE_LANCE_CORE, armed), a.x, a.y, b.x, b.y,
            scale, hot, maxTiles = ROOM_LINK_TILES)
    }
    val emitter = PixelStrip.HAZARD_EMITTER
    val pulse = if (blinking) (if (dark) 0 else 2) else holdLoopFrame(emitter, armed)
    drawPixelFrame(emitter, pulse, a.x, a.y, scale, hot)
    drawPixelFrame(emitter, pulse, b.x, b.y, scale, hot, flipX = true)
    return ops + 2
}

/**
 * A lance's strike on its beam strip's ticks: a wider threat halftone round the band, the band white-hot breaking up
 * from solid level by level over the strike time, and the beam along its line breaking into sparks. Returns its draw
 * operations.
 */
private fun DrawScope.drawLanceStrike(
    engine: GameplayRenderModel,
    hazard: DungeonHazardProjection,
    a: Offset,
    b: Offset,
    since: Float,
    strike: Float,
    threat: ColorFilter,
    hot: ColorFilter,
): Int {
    val strip = PixelStrip.LINK_PLACE_LANCE_BEAM
    val scale = engine.worldScale
    val share = (PixelFxClock.stepped(since, strip.fps) / strike).coerceIn(0f, 1f)
    var ops = drawLanceBand(a, b, hazard.radius + LANCE_GLOW, if (share < 0.5f) 1 else 0, scale, threat)
    val level = PIXEL_FILL_LEVELS - 1 - (share * PIXEL_FILL_LEVELS).toInt()
    if (level >= 0) ops += drawLanceBand(a, b, hazard.radius, level, scale, hot)
    ops += drawPixelLink(strip, PixelFxClock.spreadFrame(strip, since, strike), a.x, a.y, b.x, b.y, scale, hot, maxTiles = ROOM_LINK_TILES)
    return ops
}

/** The strike's threat glow reaches this far past the band on each side (world units). */
private const val LANCE_GLOW = 17f

/** The band of [halfWidth] round the axis-aligned line from [a] to [b] in the halftone [level]; returns its pieces. */
private fun DrawScope.drawLanceBand(a: Offset, b: Offset, halfWidth: Float, level: Int, scale: Float, palette: ColorFilter): Int {
    val horizontal = abs(b.x - a.x) >= abs(b.y - a.y)
    val left = if (horizontal) min(a.x, b.x) else a.x - halfWidth
    val right = if (horizontal) max(a.x, b.x) else a.x + halfWidth
    val top = if (horizontal) a.y - halfWidth else min(a.y, b.y)
    val bottom = if (horizontal) a.y + halfWidth else max(a.y, b.y)
    return drawPixelFill(left, top, right, bottom, level, scale, palette)
}

/**
 * The living guardian's sigil: the rune circle turning under it on its strip's ticks, a dashed ring of pips at the
 * sigil's radius turning with it, and faint pips on a wider ring round it.
 */
private fun DrawScope.drawGuardianSigil(
    engine: GameplayRenderModel,
    dungeon: DungeonProjection,
    shakeX: Float,
    shakeY: Float,
    time: Float,
    accent: Color,
    roles: KkRolePalette,
) {
    if (dungeon.guardianId < 0) return
    val guardian = enemyWithId(engine.enemies, dungeon.guardianId) ?: return
    val at = world(engine, engine.interpolated(guardian.previousX, guardian.x), engine.interpolated(guardian.previousY, guardian.y), shakeX, shakeY)
    val strip = PixelStrip.HAZARD_SIGIL
    val scale = engine.worldScale
    val palette = PixelPalettes.of(kkMix(roles.threat, accent, 0.35f))
    val radius = guardian.radius + 34f
    drawPixelFrame(strip, holdLoopFrame(strip, time), at.x, at.y, scale, palette)
    var ops = drawPipRing(PixelStrip.HAZARD_PIP, at.x, at.y, radius, PIP_SPACING * 0.7f, scale, palette,
        turn = PixelFxClock.stepped(time, strip.fps) * SIGIL_TURN, dashes = SIGIL_DASHES) { PIP_LIT }
    ops += drawPipRing(PixelStrip.HAZARD_PIP, at.x, at.y, radius * 1.18f, PIP_SPACING * 2f, scale, palette) { PIP_FAINT }
    FxDrawBudget.take(FxPool.DUNGEON, ops + 1, signature = true)
}

/** The enemy with [id] that is not dead, or null. */
private fun enemyWithId(enemies: List<EnemyProjection>, id: Int): EnemyProjection? {
    for (index in enemies.indices) {
        val enemy = enemies[index]
        if (enemy.id == id && !enemy.dead) return enemy
    }
    return null
}
