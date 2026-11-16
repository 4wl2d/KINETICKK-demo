// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.interaction.canvas.world

import kinetickk.ball.gameplay.interaction.fx.PieceMotion
import kinetickk.foundation.design.*
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.gameplay.interaction.fx.PixelFxClock
import kinetickk.ball.gameplay.interaction.fx.PixelLooks
import kinetickk.ball.gameplay.interaction.fx.PixelStrip
import kinetickk.ball.gameplay.interaction.fx.VisualFxProjection
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.nucleus.render.TrailPointProjection
import kotlin.math.sqrt
import kinetickk.ball.gameplay.interaction.canvas.*
import kinetickk.ball.gameplay.interaction.canvas.fx.*
import kinetickk.ball.gameplay.interaction.canvas.look.*

/** Flux Wake's burning half-width and the fade after a trail point's life (mirrors of the simulation's). */
private const val FLUX_WAKE_WIDTH = 20f
private const val TRAIL_FADE_SECONDS = 0.45f

/** Trail dot frames by the share of a point's life left: hot, plain, broken, a dot, a dying dot. */
private val TrailDotLife = floatArrayOf(0.8f, 0.6f, 0.4f, 0.2f, 0f)

/** Frames of a Flux Wake cell: two fresh burning ones, two older ones, then two of dying embers after its life. */
private const val WAKE_FRESH = 0
private const val WAKE_OLDER = 2
private const val WAKE_DYING = 4
private const val WAKE_LOOP = 2

/** A wake point burns fresh for this share of its life; a turning point (a harder burn) stays fresh for all of it. */
private const val WAKE_FRESH_SHARE = 0.5f

/** Edge pips mark the burning edge as fresh, older, and over the last share of a point's life as about to stop. */
private const val EDGE_OLDER_SHARE = 0.5f
private const val EDGE_ENDING_SHARE = 0.75f

/** Echoes fainter than this are not drawn: a slow glide leaves no afterimage. */
private const val ECHO_MIN_INTENSITY = 0.12f

/**
 * Dash afterimages: Core-sized pixel rings in the you color that break into dots as they age. Each is one signature
 * operation of the Core's own pool, as the trail's cells are.
 */
internal fun DrawScope.drawMotionEchoes(
    engine: GameplayRenderModel,
    visualFx: VisualFxProjection,
    shakeX: Float,
    shakeY: Float,
    roles: KkRolePalette,
) {
    if (visualFx.motionEchoes.isEmpty()) return
    val scale = engine.worldScale
    val palette = PixelPalettes.of(roles.you)
    val strip = PixelStrip.ECHO
    for (index in visualFx.motionEchoes.indices) {
        val echo = visualFx.motionEchoes[index]
        val center = world(engine, echo.x, echo.y, shakeX, shakeY)
        if (!isOnScreen(center, 60f)) continue
        // A dash leaves the whole ring; a glide starts one or two frames into the fade, and a slow one leaves none.
        if (echo.intensity < ECHO_MIN_INTENSITY) continue
        val faded = if (echo.intensity < 0.25f) 2 else if (echo.intensity < 0.5f) 1 else 0
        val frame = PixelFxClock.spreadFrame(strip, engine.fxAge(echo.life, echo.maxLife), echo.maxLife) + faded
        FxDrawBudget.take(FxPool.TRAIL, 1, signature = true)
        drawPixelFrame(strip, frame.coerceAtMost(strip.frames - 1), center.x, center.y, scale, palette)
    }
}

/** Frame rate of a shockwave's ring by its colour: dashes and rams snap, damage taken and quakes land heavier. */
internal fun shockwaveFps(colorIndex: Int): Int = when {
    colorIndex == 0 || colorIndex == 3 -> 24
    colorIndex == 4 || colorIndex == APEX_GOLD_COLOR || isQuakeColor(colorIndex) -> 12
    else -> 16
}

/**
 * Expanding pixel rings of the plain shockwaves: kills (bone), the deaths of Elites, mini-bosses and secret bosses (you,
 * under their dismantled mark), dash (you), ram impacts (you), damage taken (threat), element and Apex (gold) waves.
 * Skills, reactions and Breaks show their own looks and send no ring here. A ring steps outward through the ring ladder
 * at its own frame rate and thins, breaks and scatters into dots as it ages; each is signature work of the transient
 * pool. A quake is not a ring: it slams a crater and throws rock and cracks out to its radius ([drawQuake]).
 */
internal fun DrawScope.drawShockwaves(
    engine: GameplayRenderModel,
    visualFx: VisualFxProjection,
    shakeX: Float,
    shakeY: Float,
    roles: KkRolePalette,
) {
    if (visualFx.shockwaves.isEmpty()) return
    val scale = engine.worldScale
    for (index in visualFx.shockwaves.indices) {
        val wave = visualFx.shockwaves[index]
        val center = world(engine, wave.x, wave.y, shakeX, shakeY)
        if (!isOnScreen(center, wave.maxRadius)) continue
        val age = wave.maxLife - engine.fxLife(wave.life, wave.maxLife)
        val palette = PixelPalettes.of(fxColor(wave.colorIndex, roles))
        if (isQuakeColor(wave.colorIndex)) {
            drawQuake(wave.x, wave.y, center.x, center.y, wave.maxRadius, age, wave.maxLife, scale, palette)
            continue
        }
        val progress = steppedProgress(age, wave.maxLife, shockwaveFps(wave.colorIndex))
        FxDrawBudget.take(FxPool.TRANSIENT, PIXEL_RING_OPS, signature = true)
        drawPixelRing(center.x, center.y, wave.maxRadius * KkEase.Out.transform(progress), ringStage(progress), scale, palette)
    }
}

/**
 * A quake at ([x], [y]) on the layer, [age] seconds into its [life]: its crater slams down in the middle (the centre
 * strip spread over the quake's life) while rocks tumble out to its [radius], a sparser set of rocks lands short of it
 * and cracks run most of the way, every part on the quake strips' 12 FPS ticks. The outer rocks are signature: they show
 * how far the quake reached. [worldX], [worldY] seed the sets.
 */
private fun DrawScope.drawQuake(worldX: Float, worldY: Float, x: Float, y: Float, radius: Float, age: Float, life: Float, scale: Float,
    palette: ColorFilter) {
    FxDrawBudget.take(FxPool.TRANSIENT, 1, signature = true)
    drawPixelFrame(PixelStrip.QUAKE_CORE, PixelFxClock.spreadFrame(PixelStrip.QUAKE_CORE, age, life), x, y, scale, palette)
    val seed = worldX.toRawBits() * 31 + worldY.toRawBits()
    val rocks = pieceCount(radius)
    drawPixelPieces(PixelStrip.QUAKE_ROCK, null, PieceMotion.OUT, x, y, radius, age, seed, rocks, scale, palette)
    drawPixelPieces(PixelStrip.QUAKE_ROCK, null, PieceMotion.OUT, x, y, radius * QUAKE_SHORT_REACH, age, seed + 2, rocks / 2, scale, palette,
        signatures = 0)
    drawPixelPieces(PixelStrip.QUAKE_CRACK, PixelStrip.QUAKE_CRACK_D, PieceMotion.OUT, x, y, radius * QUAKE_CRACK_REACH, age, seed + 1,
        (rocks + 1) / 2, scale, palette, signatures = 0)
}

/** The cracks run this share of the quake's radius and the short rocks land at this share; the outer rocks fly all of it. */
private const val QUAKE_CRACK_REACH = 0.8f
private const val QUAKE_SHORT_REACH = 0.55f

/**
 * Impact accents drawn over the enemies ([kinetickk.ball.gameplay.interaction.fx.ImpactMarkProjection]), by the
 * shockwave's kind: where an enemy died its bone pop (a large one caving in and bursting), where an Elite, a mini-boss
 * or a secret boss died the bone dismantled mark (a cut on the slash axis, the halves parting across it and breaking
 * into plates), and for ram impacts the bone slash across the target. Each plays its own strip to the end, however
 * short its shockwave was, as one signature operation.
 */
internal fun DrawScope.drawImpactMarks(engine: GameplayRenderModel, visualFx: VisualFxProjection, shakeX: Float, shakeY: Float) {
    val marks = visualFx.impactMarks
    if (marks.isEmpty()) return
    val scale = engine.worldScale
    val bone = PixelPalettes.of(Kk.Bone)
    for (index in marks.indices) {
        val mark = marks[index]
        val strip = PixelLooks.impactStrip(mark.kind, mark.maxRadius) ?: continue
        val frame = PixelFxClock.frame(strip, engine.fxAge(mark.life, mark.maxLife))
        if (frame < 0) continue
        val center = world(engine, mark.x, mark.y, shakeX, shakeY)
        if (!isOnScreen(center, strip.w * PIXEL_FX_TEXEL)) continue
        FxDrawBudget.take(FxPool.TRANSIENT, 1, signature = true)
        drawPixelFrame(strip, frame, center.x, center.y, scale, bone)
    }
}

/**
 * The Core's path history in the you color: a dot on every trail point that breaks up in steps as the point ages. Where
 * Flux Wake burns, each point also burns as a wake cell as wide as the band (the first form's or the doubled one), the
 * cells overlapping into a burning strip that dies down to embers once a point stops burning, and pips stand on both
 * edges at the exact burning half-width. A point's age is held to its strip's
 * ticks before any frame is picked, and every cell is one signature operation of the Core's own pool. The ribbon that
 * trails the Core is [drawCoreRibbon]'s.
 */
internal fun DrawScope.drawTrail(engine: GameplayRenderModel, shakeX: Float, shakeY: Float, roles: KkRolePalette) {
    val trail = engine.trail
    if (trail.isEmpty()) return
    val scale = engine.worldScale
    val palette = PixelPalettes.of(roles.you)
    if (engine.weapon == WeaponId.FLUX_WAKE) {
        val wide = engine.weaponLevel >= 3
        // The exact burning half-width, doubled once the wide form unlocks.
        val half = FLUX_WAKE_WIDTH * (if (wide) 2f else 1f)
        drawWake(engine, trail, if (wide) PixelStrip.TRAIL_WAKE_WIDE else PixelStrip.TRAIL_WAKE, half, shakeX, shakeY, scale, palette)
    }
    for (index in trail.indices) {
        val point = trail[index]
        val life = 1f - trailAge(engine, point, PixelStrip.TRAIL_DOT) / (point.life + TRAIL_FADE_SECONDS)
        if (life <= 0f) continue
        val location = world(engine, point.x, point.y, shakeX, shakeY)
        if (!isOnScreen(location, 8f)) continue
        var frame = 0
        while (life <= TrailDotLife[frame]) frame++
        FxDrawBudget.take(FxPool.TRAIL, 1, signature = true)
        drawPixelFrame(PixelStrip.TRAIL_DOT, frame, location.x, location.y, scale, palette)
    }
}

/** [point]'s age at the interpolated time, held to the start of its tick of [strip]: the age its frames step on. */
private fun trailAge(engine: GameplayRenderModel, point: TrailPointProjection, strip: PixelStrip): Float =
    PixelFxClock.stepped(engine.stepAge(point.age), strip.fps)

/** The Flux Wake cells of [trail] in [cell], then the pips on both burning edges [half] world units from the path. */
private fun DrawScope.drawWake(
    engine: GameplayRenderModel,
    trail: List<TrailPointProjection>,
    cell: PixelStrip,
    half: Float,
    shakeX: Float,
    shakeY: Float,
    scale: Float,
    palette: ColorFilter,
) {
    val time = engine.renderElapsed
    val reach = cell.w * PIXEL_FX_TEXEL
    for (index in trail.indices) {
        val point = trail[index]
        val age = trailAge(engine, point, cell)
        if (age >= point.life + TRAIL_FADE_SECONDS) continue
        val location = world(engine, point.x, point.y, shakeX, shakeY)
        if (!isOnScreen(location, reach)) continue
        val frame = when {
            age >= point.life -> WAKE_DYING + if (age - point.life < TRAIL_FADE_SECONDS * 0.5f) 0 else 1
            point.scale > 1f || age < point.life * WAKE_FRESH_SHARE -> WAKE_FRESH + PixelFxClock.loopFrame(cell, time, 0, WAKE_LOOP, index)
            else -> WAKE_OLDER + PixelFxClock.loopFrame(cell, time, 0, WAKE_LOOP, index)
        }
        FxDrawBudget.take(FxPool.TRAIL, 1, signature = true)
        drawPixelFrame(cell, frame, location.x, location.y, scale, palette)
    }
    // The edges run across the path at each burning point, square to the line through its neighbours.
    for (index in trail.indices) {
        val point = trail[index]
        val age = trailAge(engine, point, PixelStrip.TRAIL_EDGE)
        if (age >= point.life) continue
        val before = trail[if (index > 0) index - 1 else index]
        val after = trail[if (index < trail.lastIndex) index + 1 else index]
        val dx = after.x - before.x
        val dy = after.y - before.y
        val length = sqrt(dx * dx + dy * dy)
        if (length < 0.01f) continue
        val location = world(engine, point.x, point.y, shakeX, shakeY)
        if (!isOnScreen(location, half + 8f)) continue
        val share = age / point.life
        val frame = if (share >= EDGE_ENDING_SHARE) 2 else if (share >= EDGE_OLDER_SHARE && point.scale <= 1f) 1 else 0
        val nx = -dy / length * half
        val ny = dx / length * half
        FxDrawBudget.take(FxPool.TRAIL, 2, signature = true)
        drawPixelFrame(PixelStrip.TRAIL_EDGE, frame, location.x + nx, location.y + ny, scale, palette)
        drawPixelFrame(PixelStrip.TRAIL_EDGE, frame, location.x - nx, location.y - ny, scale, palette)
    }
}
