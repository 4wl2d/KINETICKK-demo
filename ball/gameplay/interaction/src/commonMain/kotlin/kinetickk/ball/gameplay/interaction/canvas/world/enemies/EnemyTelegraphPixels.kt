// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.interaction.canvas.world.enemies

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import kinetickk.ball.gameplay.interaction.canvas.fx.FxDrawBudget
import kinetickk.ball.gameplay.interaction.canvas.fx.FxPool
import kinetickk.ball.gameplay.interaction.canvas.fx.PixelSheet
import kinetickk.ball.gameplay.interaction.canvas.fx.drawPixelFrame
import kinetickk.ball.gameplay.interaction.canvas.fx.drawPixelLink
import kinetickk.ball.gameplay.interaction.canvas.renderElapsed
import kinetickk.ball.gameplay.interaction.canvas.worldScale
import kinetickk.ball.gameplay.interaction.fx.PixelFxClock
import kinetickk.ball.gameplay.interaction.fx.PixelStrip
import kinetickk.ball.gameplay.nucleus.render.EnemyProjection
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * Enemy telegraphs and burst warnings use six families of marks the player
 * learns once: wind-up claws closing onto a body (and a charge's line to the Core), a sight line with its muzzle
 * brackets, a lane with rails, a reach ring of dashes, a blast rim with teeth and embers, and the Aegis tether. Rings
 * are pips at exact places on their radius and lines are link tiles from end to end, so every reach, lane and blast
 * keeps the old drawing's geometry; parts move only on their strip's ticks. Telegraphs are signature work: they always
 * draw and record their operations.
 */

/**
 * The pool telegraphs record their operations in: they share the dungeon fight's signature pool, whose only
 * secondary layer (the motes) draws before the enemies, so telegraphs never crowd it out.
 */
internal val TELEGRAPH_POOL = FxPool.DUNGEON

/** Places round a ring a pip can take: sixteen, one every 22.5°, clockwise on screen from +x. */
private const val RING_PLACES = 16
private val PlaceCos = FloatArray(RING_PLACES) { cos(it * 2.0 * PI / RING_PLACES).toFloat() }
private val PlaceSin = FloatArray(RING_PLACES) { sin(it * 2.0 * PI / RING_PLACES).toFloat() }

/**
 * The cells of a family's ring pips: [plain] is drawn on the ring's right, [slant] at 22.5° and [diagonal] at 45°;
 * the other places are quarter turns of those, the slant mirrored across the diagonal for 67.5°.
 */
internal enum class TelegraphPips(val plain: PixelStrip, val slant: PixelStrip, val diagonal: PixelStrip) {
    CLAWS(PixelStrip.TELE_WINDUP, PixelStrip.TELE_WINDUP, PixelStrip.TELE_WINDUP_D),
    RETICLE(PixelStrip.TELE_RETICLE, PixelStrip.TELE_RETICLE, PixelStrip.TELE_RETICLE),
    REACH(PixelStrip.TELE_REACH, PixelStrip.TELE_REACH_S, PixelStrip.TELE_REACH_D),
    BLAST(PixelStrip.TELE_BLAST_TOOTH, PixelStrip.TELE_BLAST_RIM, PixelStrip.TELE_BLAST_TOOTH_D),
    EMBERS(PixelStrip.TELE_BLAST_FILL, PixelStrip.TELE_BLAST_FILL, PixelStrip.TELE_BLAST_FILL),
    FUSE(PixelStrip.TELE_FUSE, PixelStrip.TELE_FUSE, PixelStrip.TELE_FUSE),
    WARD(PixelStrip.TELE_WARD, PixelStrip.TELE_WARD, PixelStrip.TELE_WARD),
}

/** A pip this far past the view (units) is not drawn: half the largest pip cell. */
private const val PIP_VIEW_MARGIN = 18f

/** Whether the pip at [place] on the ring of [radius] round ([x], [y]) is in view. */
private fun DrawScope.pipInView(place: Int, x: Float, y: Float, radius: Float): Boolean {
    val px = x + PlaceCos[place] * radius
    val py = y + PlaceSin[place] * radius
    return px > -PIP_VIEW_MARGIN && py > -PIP_VIEW_MARGIN && px < size.width + PIP_VIEW_MARGIN && py < size.height + PIP_VIEW_MARGIN
}

/**
 * Pips of [pips] at [frame] on the ring of [radius] round ([x], [y]): every [step]th place from [first] (16 places
 * with step 1, the eight cardinal and diagonal ones with first 0 and step 2, the four diagonals with first 2 and step
 * 4). Each pip's centre lies exactly on the ring; pips out of view are skipped and only those drawn are recorded.
 */
internal fun DrawScope.drawPipRing(
    pips: TelegraphPips,
    frame: Int,
    x: Float,
    y: Float,
    radius: Float,
    first: Int,
    step: Int,
    scale: Float,
    palette: ColorFilter,
) {
    var visible = 0
    var place = first
    while (place < RING_PLACES) {
        if (pipInView(place, x, y, radius)) visible++
        place += step
    }
    if (visible == 0) return
    FxDrawBudget.take(TELEGRAPH_POOL, visible, signature = true)
    place = first
    while (place < RING_PLACES) {
        if (!pipInView(place, x, y, radius)) {
            place += step
            continue
        }
        val px = x + PlaceCos[place] * radius
        val py = y + PlaceSin[place] * radius
        val quarter = place shr 2
        when (place and 3) {
            0 -> drawPixelFrame(pips.plain, frame, px, py, scale, palette, turns = quarter)
            1 -> drawPixelFrame(pips.slant, frame, px, py, scale, palette, turns = quarter)
            2 -> drawPixelFrame(pips.diagonal, frame, px, py, scale, palette, turns = quarter)
            else -> drawPixelFrame(pips.slant, frame, px, py, scale, palette, flipY = true, turns = quarter + 1)
        }
        place += step
    }
}

/** Past the view a clipped telegraph line still draws this far (units). */
private const val LINK_VIEW_MARGIN = 40f

/** Tiles of one [drawPixelLink] call, its own cap: a longer line goes on in another call from a whole tile. */
private const val LINK_CHUNK_TILES = 48

/** The span of a line left after clipping (draw-thread confined scratch, like the renderers' other caches). */
private val clipSpan = FloatArray(2)

/** Clips [clipSpan] against one edge of the view (Liang–Barsky); false when the line misses it. */
private fun clipEdge(p: Float, q: Float): Boolean {
    if (p == 0f) return q >= 0f
    val r = q / p
    if (p < 0f) {
        if (r > clipSpan[1]) return false
        if (r > clipSpan[0]) clipSpan[0] = r
    } else {
        if (r < clipSpan[0]) return false
        if (r < clipSpan[1]) clipSpan[1] = r
    }
    return true
}

/**
 * Tiles [frame] of the link segment [strip] from ([fromX], [fromY]) to ([toX], [toY]) over the part of the line in
 * view: whole tiles before the view are skipped, so every tile keeps its place, and the line stops where it leaves the
 * view. A line longer than [LINK_CHUNK_TILES] tiles (a Comet's lane at a small texel) is drawn in several runs, each
 * from a whole tile. Records its tiles before drawing.
 */
internal fun DrawScope.drawTelegraphLink(
    strip: PixelStrip,
    frame: Int,
    fromX: Float,
    fromY: Float,
    toX: Float,
    toY: Float,
    scale: Float,
    palette: ColorFilter,
) {
    val dx = toX - fromX
    val dy = toY - fromY
    val length = sqrt(dx * dx + dy * dy)
    if (length < 1f) return
    clipSpan[0] = 0f
    clipSpan[1] = 1f
    val inView = clipEdge(-dx, fromX + LINK_VIEW_MARGIN) && clipEdge(dx, size.width + LINK_VIEW_MARGIN - fromX) &&
        clipEdge(-dy, fromY + LINK_VIEW_MARGIN) && clipEdge(dy, size.height + LINK_VIEW_MARGIN - fromY)
    if (!inView || clipSpan[0] >= clipSpan[1]) return
    val tile = strip.w * PixelSheet.step(scale) / scale
    var start = floor(clipSpan[0] * length / tile) * tile / length
    val end = clipSpan[1]
    FxDrawBudget.take(TELEGRAPH_POOL, ceil((end - start) * length / tile).toInt().coerceAtLeast(1), signature = true)
    val chunk = LINK_CHUNK_TILES * tile / length
    while (start < end) {
        val stop = min(end, start + chunk)
        drawPixelLink(strip, frame, fromX + dx * start, fromY + dy * start, fromX + dx * stop, fromY + dy * stop, scale, palette)
        start = stop
    }
}

/** The share of a telegraph [age] seconds into its [lead], held to the ticks of [fps]. */
internal fun heldShare(age: Float, lead: Float, fps: Int): Float =
    if (lead <= 0f) 1f else (PixelFxClock.stepped(age, fps) / lead).coerceIn(0f, 1f)

/** A telegraph's stage at [share] of its lead, at most [last]: faint, solid, hot, then snapping in its last 15 %. */
internal fun telegraphStage(share: Float, last: Int = 3): Int = min(last, when {
    share < 0.3f -> 0
    share < 0.6f -> 1
    share < 0.85f -> 2
    else -> 3
})

// -- wind-up ---------------------------------------------------------------------------------------------------------

/** Eight wind-up claws on the ring of [radius] round [center], at [stage] and on the strip's beat. */
private fun DrawScope.drawClaws(engine: GameplayRenderModel, center: Offset, radius: Float, stage: Int, palette: ColorFilter) {
    val beat = PixelFxClock.tick(engine.renderElapsed, PixelStrip.TELE_WINDUP.fps) and 1
    drawPipRing(TelegraphPips.CLAWS, stage * 2 + beat, center.x, center.y, radius, 0, 2, engine.worldScale, palette)
}

/**
 * A charge's wind-up [age] seconds into its [lead]: a line to [core] that firms through three weights, and claws
 * closing from [reach] beyond the body onto it.
 */
internal fun DrawScope.drawWindup(
    engine: GameplayRenderModel,
    enemy: EnemyProjection,
    center: Offset,
    core: Offset,
    age: Float,
    lead: Float,
    reach: Float,
    palette: ColorFilter,
) {
    val strip = PixelStrip.LINK_TELE_WINDUP
    val share = heldShare(age, lead, strip.fps)
    val weight = telegraphStage(share, 2)
    drawTelegraphLink(strip, weight * 4 + PixelFxClock.loopFrame(strip, engine.renderElapsed, 0, 4), center.x, center.y, core.x, core.y,
        engine.worldScale, palette)
    drawClaws(engine, center, enemy.radius + reach * (1f - share), telegraphStage(share), palette)
}

/**
 * A volley about to fire when [timer] runs out of its [lead]: claws closing from [gap] plus [rate] units a second of
 * the time left onto [gap] beyond the body.
 */
internal fun DrawScope.drawVolley(
    engine: GameplayRenderModel,
    enemy: EnemyProjection,
    center: Offset,
    timer: Float,
    lead: Float,
    gap: Float,
    rate: Float,
    palette: ColorFilter,
) {
    if (timer < 0f || timer > lead) return
    val held = PixelFxClock.stepped(lead - timer, PixelStrip.TELE_WINDUP.fps)
    drawClaws(engine, center, enemy.radius + gap + rate * (lead - held).coerceAtLeast(0f), telegraphStage(held / lead), palette)
}

// -- sight -----------------------------------------------------------------------------------------------------------

/** Frames of the sight line: dim and bright tracking (four each), locked (two) and the hairline. */
private const val SIGHT_BRIGHT = 4
private const val SIGHT_LOCKED = 8
internal const val SIGHT_HAIRLINE = 10

/** Reticle frames: three closing stages, then locked on two beats. */
private const val RETICLE_LOCKED = 3

/**
 * A sight line from [center] to [end] at [share] of its aim: dashes marching at the target, dim then bright, or a
 * ruled bar once [locked]; with muzzle brackets on the ring of [muzzle] round the body unless it is negative.
 */
internal fun DrawScope.drawSight(
    engine: GameplayRenderModel,
    center: Offset,
    end: Offset,
    share: Float,
    locked: Boolean,
    muzzle: Float,
    palette: ColorFilter,
) {
    val strip = PixelStrip.LINK_TELE_SIGHT
    val beat = PixelFxClock.tick(engine.renderElapsed, strip.fps) and 1
    val frame = if (locked) SIGHT_LOCKED + beat else (if (share < 0.5f) 0 else SIGHT_BRIGHT) + PixelFxClock.loopFrame(strip, engine.renderElapsed, 0, 4)
    val scale = engine.worldScale
    drawTelegraphLink(strip, frame, center.x, center.y, end.x, end.y, scale, palette)
    if (muzzle < 0f) return
    val bracket = if (locked) RETICLE_LOCKED + beat else telegraphStage(share, 2)
    drawPipRing(TelegraphPips.RETICLE, bracket, center.x, center.y, muzzle, 2, 4, scale, palette)
}

/** The Interceptor's mark on the point it means to cut the Core off at, turning on its own strip. */
internal fun DrawScope.drawInterceptMark(engine: GameplayRenderModel, at: Offset, palette: ColorFilter) {
    val strip = PixelStrip.TELE_TARGET
    FxDrawBudget.take(TELEGRAPH_POOL, 1, signature = true)
    drawPixelFrame(strip, PixelFxClock.loopFrame(strip, engine.renderElapsed), at.x, at.y, engine.worldScale, palette)
}

// -- lane ------------------------------------------------------------------------------------------------------------

/** A lane from ([x], [y]) along [angle] for [length] units at [weight] (0 faint to 3 about to strike). */
internal fun DrawScope.drawLane(engine: GameplayRenderModel, x: Float, y: Float, angle: Float, length: Float, weight: Int, palette: ColorFilter) {
    val strip = PixelStrip.LINK_TELE_LANE
    drawTelegraphLink(strip, weight * 4 + PixelFxClock.loopFrame(strip, engine.renderElapsed, 0, 4), x, y, x + cos(angle) * length,
        y + sin(angle) * length, engine.worldScale, palette)
}

/**
 * A coming volley of three parallel shots [spread] units apart along [angle] through ([x], [y]): three lanes from
 * [from] to [to] units along that line, firming through the lane's four weights over the warning's [lead] seconds as
 * its [remaining] seconds run out (held to the lane strip's ticks).
 */
internal fun DrawScope.drawVolleyLanes(
    engine: GameplayRenderModel,
    x: Float,
    y: Float,
    angle: Float,
    from: Float,
    to: Float,
    spread: Float,
    remaining: Float,
    lead: Float,
    palette: ColorFilter,
) {
    val weight = telegraphStage(heldShare(lead - remaining, lead, PixelStrip.LINK_TELE_LANE.fps))
    val cosine = cos(angle)
    val sine = sin(angle)
    for (lane in -1..1) {
        val lateral = lane * spread
        drawLane(engine, x - sine * lateral + cosine * from, y + cosine * lateral + sine * from, angle, to - from, weight, palette)
    }
}

// -- reach -----------------------------------------------------------------------------------------------------------

/** Reach frames: three breathing (dim to bright), then three of a glint running round a live reach. */
private const val REACH_BREATH = 3
private const val REACH_LIVE_END = 6

/** A reach ring of [radius] round [center] at [frame]: sixteen dashes, eight or four on a tight ring. */
internal fun DrawScope.drawReach(engine: GameplayRenderModel, center: Offset, radius: Float, frame: Int, palette: ColorFilter) {
    val step = if (radius >= 160f) 1 else if (radius >= 64f) 2 else 4
    drawPipRing(TelegraphPips.REACH, frame, center.x, center.y, radius, if (step == 4) 2 else 0, step, engine.worldScale, palette)
}

/** The breathing frame of an idle reach: the old pulse (2.2 radians a second, phased by the enemy) held to ticks. */
internal fun reachBreath(engine: GameplayRenderModel, enemy: EnemyProjection): Int {
    val time = PixelFxClock.stepped(engine.renderElapsed, PixelStrip.TELE_REACH.fps)
    return ((sin(time * 2.2f + enemy.id) + 1f) * 0.5f * (REACH_BREATH - 0.01f)).toInt()
}

/** The frame of a live reach: a glint running clockwise along every dash. */
internal fun reachLive(engine: GameplayRenderModel): Int =
    PixelFxClock.loopFrame(PixelStrip.TELE_REACH, engine.renderElapsed, REACH_BREATH, REACH_LIVE_END)

/** A reach ring that firms over a lead: dim, then bright, then live from two thirds of [share]. */
internal fun reachRamp(engine: GameplayRenderModel, share: Float): Int =
    if (share < 0.34f) 1 else if (share < 0.67f) 2 else reachLive(engine)

// -- blast -----------------------------------------------------------------------------------------------------------

/** Blast rim frames: four pulse frames on a thin rim, then four on a heavy one. */
private const val BLAST_HEAVY = 4

/** Fill embers: cool, warm and about to touch the rim. */
private const val EMBER_STAGES = 3

/**
 * A blast warning of [reach] round ([x], [y]) at [progress] of its fuse: the rim at [pulse] (0 dim to 3 flashing),
 * teeth on its eight cardinal and diagonal places and plain rim between, heavy from half way; and a ring of embers
 * stepping out from the heart to the rim, drawn once it clears [heart].
 */
internal fun DrawScope.drawBlastWarning(
    x: Float,
    y: Float,
    reach: Float,
    progress: Float,
    pulse: Int,
    heart: Float,
    scale: Float,
    palette: ColorFilter,
) {
    drawPipRing(TelegraphPips.BLAST, (if (progress >= 0.5f) BLAST_HEAVY else 0) + pulse, x, y, reach, 0, 1, scale, palette)
    val ember = reach * progress
    if (ember <= heart) return
    drawPipRing(TelegraphPips.EMBERS, min(EMBER_STAGES - 1, (progress * EMBER_STAGES).toInt()), x, y, ember, 1, 2, scale, palette)
}

/** The blast blink's last and quickest half, in ticks of the rim strip: a third of a second at 24 FPS. */
private const val BLINK_QUICK_TICKS = 8

/** Ticks a half of the blink gains for a whole fuse between it and the detonation. */
private const val BLINK_SLOWING_TICKS = 4

/**
 * The half of a blast warning's blink [since] seconds into a fuse of [life] seconds, counted back from the detonation
 * (0 the last). The blink is a square wave on the rim strip's ticks, lit in its even halves: the last
 * [BLINK_QUICK_TICKS] ticks before the detonation are lit, and each half before them lasts longer by its distance from
 * the detonation as a share of the fuse, so the blink quickens as the fuse burns yet never changes more often than
 * three times a second. The half the fuse starts in joins the next one when less than half a quick half of it would
 * show, so a warning never blinks just after it appears. It reads only the fuse's own time, so every cell of one
 * warning changes on the same tick.
 */
internal fun blastBlinkHalf(since: Float, life: Float): Int {
    val fps = PixelStrip.TELE_BLAST_TOOTH.fps
    val lifeTicks = ceil(life * fps).toInt().coerceAtLeast(1)
    val left = lifeTicks - PixelFxClock.tick(since.coerceAtLeast(0f), fps)
    var end = 0
    var half = 0
    while (true) {
        val length = BLINK_QUICK_TICKS + BLINK_SLOWING_TICKS * end / lifeTicks
        if (left <= end + length) {
            val first = lifeTicks <= end + length
            return if (first && half > 0 && lifeTicks - end < BLINK_QUICK_TICKS / 2) half - 1 else half
        }
        end += length
        half++
    }
}

/** The rim's pulse frame in blink [half]: dim in a dark half, lit in a lit one and flashing in the last. */
internal fun blastPulse(half: Int): Int = if (half % 2 == 1) 0 else if (half == 0) 3 else 2

/**
 * A primed fuse burning down from [fuseSeconds]: the blast warning at the blast's [reach], blinking faster as it
 * burns ([blastBlinkHalf]), and bone sparks on the ring just outside the body lit with the rim.
 */
internal fun DrawScope.drawFuse(
    engine: GameplayRenderModel,
    enemy: EnemyProjection,
    center: Offset,
    reach: Float,
    fuseSeconds: Float,
    palette: ColorFilter,
    bone: ColorFilter,
) {
    val fps = PixelStrip.TELE_BLAST_TOOTH.fps
    val since = (fuseSeconds - enemy.behaviorRemaining).coerceAtLeast(0f)
    val half = blastBlinkHalf(since, fuseSeconds)
    val progress = (PixelFxClock.stepped(since, fps) / fuseSeconds).coerceIn(0f, 1f)
    val scale = engine.worldScale
    drawBlastWarning(center.x, center.y, reach, progress, blastPulse(half), enemy.radius + 8f, scale, palette)
    drawPipRing(TelegraphPips.FUSE, if (half % 2 == 0) 1 else 0, center.x, center.y, enemy.radius + 5f, 2, 4, scale, bone)
}

// -- tether ----------------------------------------------------------------------------------------------------------

/** The ward studs glint every half second. */
private const val WARD_GLINT_TICKS = 6

/** An Aegis tether from [from] to its ward at [center]: the bone chain and four bone studs round the ward. */
internal fun DrawScope.drawTether(engine: GameplayRenderModel, enemy: EnemyProjection, from: Offset, center: Offset, bone: ColorFilter) {
    val chain = PixelStrip.LINK_TELE_TETHER
    val scale = engine.worldScale
    drawTelegraphLink(chain, PixelFxClock.loopFrame(chain, engine.renderElapsed), from.x, from.y, center.x, center.y, scale, bone)
    val glint = PixelFxClock.tick(engine.renderElapsed, PixelStrip.TELE_WARD.fps) / WARD_GLINT_TICKS and 1
    drawPipRing(TelegraphPips.WARD, glint, center.x, center.y, enemy.radius + 3f, 2, 4, scale, bone)
}
