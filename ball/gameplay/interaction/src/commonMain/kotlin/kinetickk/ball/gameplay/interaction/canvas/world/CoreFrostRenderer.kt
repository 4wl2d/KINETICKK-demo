// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.interaction.canvas.world

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.gameplay.interaction.canvas.CanvasMath
import kinetickk.ball.gameplay.interaction.canvas.renderElapsed
import kinetickk.ball.gameplay.interaction.canvas.worldScale
import kinetickk.ball.gameplay.interaction.canvas.fx.FxDrawBudget
import kinetickk.ball.gameplay.interaction.canvas.fx.FxPool
import kinetickk.ball.gameplay.interaction.canvas.fx.PIXEL_FX_TEXEL
import kinetickk.ball.gameplay.interaction.canvas.fx.PixelPalettes
import kinetickk.ball.gameplay.interaction.canvas.fx.drawPixelFrame
import kinetickk.ball.gameplay.interaction.fx.PixelFxClock
import kinetickk.ball.gameplay.interaction.fx.PixelStrip
import kinetickk.ball.gameplay.nucleus.render.CoreStasisMotion
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkRolePalette
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

/**
 * Core Freeze frost over the Core (world units), grown in steps with the freeze: a frost rim and film that set,
 * [FROST_CRYSTALS] ice crystals that grow round it in a spread order, each through its growth stages, and cracks that
 * run across the Core past [CRACK_START]. While the Core thaws, meltwater drips off the rim. Frost is gameplay
 * feedback, so it is a signature layer that always draws; nothing in it changes faster than 1.1 Hz and nothing flashes.
 */
internal fun DrawScope.drawCoreFrost(engine: GameplayRenderModel, core: Offset, roles: KkRolePalette) {
    val freeze = engine.coreStasis
    if (freeze <= 0f) return
    drawCoreFrostAt(core, engine.coreRadius, freeze, engine.renderElapsed, roles, engine.coreStasisMotion == CoreStasisMotion.THAWING,
        engine.worldScale)
}

/** The frost of [freeze] (0..1) round a Core of [radius] at [core] on a layer of [scale]; [thawing] adds the meltwater. */
internal fun DrawScope.drawCoreFrostAt(
    core: Offset,
    radius: Float,
    freeze: Float,
    time: Float,
    roles: KkRolePalette,
    thawing: Boolean,
    scale: Float,
) {
    val cryo = PixelPalettes.of(roles.element(CRYO))
    val grown = ceil(freeze * FROST_CRYSTALS).toInt().coerceIn(0, FROST_CRYSTALS)
    val beads = if (thawing) meltBeads(freeze) else 0
    FxDrawBudget.take(FxPool.CORE_STATES, 1 + grown + beads, signature = true)
    val overlay = if (radius >= LARGE_CORE_RADIUS) PixelStrip.CORE_FROST_L else PixelStrip.CORE_FROST
    drawPixelFrame(overlay, frostOverlayFrame(freeze, time), core.x, core.y, scale, cryo)
    // Each crystal's root sits just inside the rim; its cell's centre lies [CRYSTAL_ROOT] further out along it.
    val stepped = PixelFxClock.stepped(time, PixelStrip.CORE_FROST_CRYSTAL.fps)
    for (rank in 0 until grown) {
        val index = FrostOrder[rank]
        val grow = (freeze * FROST_CRYSTALS - rank).coerceIn(0f, 1f)
        val last = if (FrostSmall[index]) CRYSTAL_GROWN - 1 else CRYSTAL_GROWN
        var frame = (grow * (CRYSTAL_GROWN + 1)).toInt().coerceAtMost(last)
        // A grown crystal catches the light for a moment every few seconds, each on its own beat.
        if (frame == CRYSTAL_GROWN && (stepped * GLINT_RATE + index * GLINT_SPREAD) % 1f < GLINT_SHARE) frame = CRYSTAL_GLINT
        val angle = index * CanvasMath.TAU / FROST_CRYSTALS + FrostJitter[index]
        val distance = radius - CRYSTAL_INSET + CRYSTAL_ROOT
        drawCrystal(index, frame, core.x + cos(angle) * distance, core.y + sin(angle) * distance, scale, cryo)
    }
    if (beads > 0) drawMeltwater(core, radius, beads, time, scale)
}

/**
 * Crystal [index] of the twelve, pointing outward along its own twelfth of the turn: the 0° and 30° cells, the 60° one
 * as the 30° cell mirrored, each turned by quarter turns, so the twelve headings stay on the texel grid.
 */
private fun DrawScope.drawCrystal(index: Int, frame: Int, x: Float, y: Float, scale: Float, palette: ColorFilter) {
    val quarter = index / 3
    when (index % 3) {
        0 -> drawPixelFrame(PixelStrip.CORE_FROST_CRYSTAL, frame, x, y, scale, palette, turns = quarter)
        1 -> drawPixelFrame(PixelStrip.CORE_FROST_CRYSTAL_30, frame, x, y, scale, palette, turns = quarter)
        // Mirrored, the 30° cell points to 150°; three more quarter turns bring it to 60°.
        else -> drawPixelFrame(PixelStrip.CORE_FROST_CRYSTAL_30, frame, x, y, scale, palette, flipX = true, turns = quarter + 3)
    }
}

/**
 * The encased Core of a full freeze (world units): the ice block round the Core of [radius], clouding over in its
 * stages across [life] seconds, at [age] seconds.
 */
internal fun DrawScope.drawCoreEncased(core: Offset, radius: Float, age: Float, life: Float, roles: KkRolePalette, scale: Float) {
    val strip = if (radius >= LARGE_CORE_RADIUS) PixelStrip.CORE_ENCASED_L else PixelStrip.CORE_ENCASED
    FxDrawBudget.take(FxPool.CORE_STATES, 1, signature = true)
    drawPixelFrame(strip, PixelFxClock.spreadFrame(strip, age, life), core.x, core.y, scale, PixelPalettes.of(roles.element(CRYO)))
}

/**
 * The frost overlay's frame at [freeze]: the rim setting (two stages up to [CRACK_START]), then the cracks running out
 * in [CRACK_STAGES] stages to the Core's edge at a full freeze. Past [URGENT_FREEZE] the last two crack stages pulse
 * between their plain frame and their lit one, half of [CRACK_PERIOD] each.
 */
private fun frostOverlayFrame(freeze: Float, time: Float): Int {
    if (freeze <= CRACK_START) return if (freeze < CRACK_START * 0.5f) 0 else 1
    val stage = ceil((freeze - CRACK_START) / (1f - CRACK_START) * CRACK_STAGES).toInt().coerceIn(1, CRACK_STAGES)
    val urgent = freeze >= URGENT_FREEZE && freeze < 1f && stage >= CRACK_STAGES - 1
    if (urgent && PixelFxClock.stepped(time, PixelStrip.CORE_FROST.fps) % CRACK_PERIOD >= CRACK_PERIOD * 0.5f) {
        return OVERLAY_URGENT + stage - (CRACK_STAGES - 1)
    }
    return 1 + stage
}

/** Meltwater beads while thawing: fewer as the frost goes. */
private fun meltBeads(freeze: Float): Int = if (freeze > 0.66f) 3 else if (freeze > 0.33f) 2 else 1

/** Meltwater drops gathering on the lower rim of a thawing Core, each dropping on its own beat of the drop's loop. */
private fun DrawScope.drawMeltwater(core: Offset, radius: Float, beads: Int, time: Float, scale: Float) {
    val strip = PixelStrip.CORE_FROST_DROP
    val loop = PixelFxClock.seconds(strip)
    val bone = PixelPalettes.of(Kk.Bone)
    for (bead in 0 until beads) {
        val angle = MeltAngles[bead]
        val frame = PixelFxClock.frame(strip, (time + bead * MELT_STAGGER * loop) % loop)
        // The drop's cell hangs from the rim: its bead's top row lies DROP_HANG above the cell's centre.
        drawPixelFrame(strip, frame, core.x + cos(angle) * (radius + 1f), core.y + sin(angle) * (radius + 1f) + DROP_HANG, scale, bone)
    }
}

private val CRYO = StatusElement.CRYO.ordinal

private const val FROST_CRYSTALS = 12

/** A Core at least this large takes the large frost and ice block cells. */
private const val LARGE_CORE_RADIUS = 18f

/** Crystal frames: growth stages up to [CRYSTAL_GROWN], then the grown crystal's glint. */
private const val CRYSTAL_GROWN = 4
private const val CRYSTAL_GLINT = 5

/** A crystal's root sits this far inside the Core's rim, and its cell's centre this far out from the root (world units). */
private const val CRYSTAL_INSET = 3f
private const val CRYSTAL_ROOT = 4f * PIXEL_FX_TEXEL

/** Glints: 0.3 a second per crystal, each lasting this share of its beat, the crystals' beats spread apart. */
private const val GLINT_RATE = 0.3f
private const val GLINT_SHARE = 0.08f
private const val GLINT_SPREAD = 0.29f

/** Cracks start to run at this freeze and reach the Core's edge at a full freeze. */
private const val CRACK_START = 0.45f
private const val CRACK_STAGES = 5

/** Past this freeze the cracks pulse (0.9 s, 1.1 Hz): the last second or so. */
private const val URGENT_FREEZE = 0.78f
private const val CRACK_PERIOD = 0.9f

/** The overlay's lit frames of the last two crack stages, the urgent pulse's second frame. */
private const val OVERLAY_URGENT = 7

/** Meltwater: each bead starts this share of the loop after the one before; the drop cell hangs this far below the rim. */
private const val MELT_STAGGER = 0.37f
private const val DROP_HANG = 4.5f * PIXEL_FX_TEXEL
private val MeltAngles = floatArrayOf(1.2f, 1.75f, 2.3f)

/** Crystal growth order: each new crystal lands opposite and between the grown ones. */
private val FrostOrder = intArrayOf(0, 6, 3, 9, 1, 7, 4, 10, 2, 8, 5, 11)

/** Per-crystal angle jitter (radians), so the ring reads as ice rather than a gear. */
private val FrostJitter = floatArrayOf(0f, 0.21f, -0.14f, 0.09f, -0.23f, 0.17f, -0.05f, 0.26f, -0.18f, 0.12f, -0.09f, 0.19f)

/** The short crystals stop one growth stage early, so the ring stays uneven. */
private val FrostSmall = booleanArrayOf(false, true, false, false, false, true, false, false, false, true, false, false)
