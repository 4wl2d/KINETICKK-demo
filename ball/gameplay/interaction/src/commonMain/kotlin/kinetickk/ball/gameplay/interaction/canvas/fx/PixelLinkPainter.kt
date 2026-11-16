// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.interaction.canvas.fx

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.SkillId
import kinetickk.ball.gameplay.interaction.canvas.fxAge
import kinetickk.ball.gameplay.interaction.canvas.world
import kinetickk.ball.gameplay.interaction.canvas.worldScale
import kinetickk.ball.gameplay.interaction.fx.LinkKind
import kinetickk.ball.gameplay.interaction.fx.LinkLook
import kinetickk.ball.gameplay.interaction.fx.LinkLooks
import kinetickk.ball.gameplay.interaction.fx.LinkTone
import kinetickk.ball.gameplay.interaction.fx.PixelFxClock
import kinetickk.ball.gameplay.interaction.fx.PixelStrip
import kinetickk.ball.gameplay.interaction.fx.VisualFxProjection
import kinetickk.ball.gameplay.interaction.fx.WeaponArcProjection
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkRolePalette
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.sqrt

/** A link is drawn while either end lies within this many units of the view. */
private const val LINK_MARGIN = 24f

/** Over its last share of life a link shows only on every other tick: it flickers out instead of fading. */
private const val LINK_FLICKER_FROM = 0.7f

/** The segment strip of a link look: the weapon's coil for style 0, else by the look's kind. */
internal fun linkStrip(kind: LinkKind): PixelStrip = when (kind) {
    LinkKind.WEAPON -> PixelStrip.LINK_COIL
    LinkKind.EMBER -> PixelStrip.LINK_EMBER
    LinkKind.LIGHTNING -> PixelStrip.LINK_BOLT
    LinkKind.FROST -> PixelStrip.LINK_FROST
    LinkKind.BEAM -> PixelStrip.LINK_BEAM
    LinkKind.TAUT -> PixelStrip.LINK_TAUT
}

/** The strip a link of [kind] plays where it lands when its look has an end mark; null for kinds that have none. */
internal fun linkEndStrip(kind: LinkKind): PixelStrip? = when (kind) {
    LinkKind.LIGHTNING -> PixelStrip.LINKEND_BOLT
    LinkKind.FROST -> PixelStrip.LINKEND_FROST
    LinkKind.BEAM -> PixelStrip.LINKEND_BEAM
    LinkKind.TAUT -> PixelStrip.LINKEND_TAUT
    LinkKind.WEAPON, LinkKind.EMBER -> null
}

/**
 * Transient links ([kinetickk.ball.gameplay.interaction.fx.WeaponArcProjection]) as pixel-art segments. A link's
 * look is its kind: the Arc Coil's zigzag for weapons and items (style 0), and for a reaction, skill, Break or Apex
 * one of six kinds (ember, bolt, frost, beam, taut, sound: [LinkLooks]) in its source's colours, tiled along the link
 * and looping at the strip's frame rate. A look with an end mark
 * plays its kind's end strip ([linkEndStrip]) where it lands, in its second colour. A link flickers out over the end of
 * its life. Over the link budget, or at the minimal reaction effects setting, a link is one cell of
 * the taut line (the coil for the weapon) stretched from end to end.
 */
internal fun DrawScope.drawWeaponArcs(
    engine: GameplayRenderModel,
    visualFx: VisualFxProjection,
    shakeX: Float,
    shakeY: Float,
    roles: KkRolePalette,
) {
    val arcs = visualFx.weaponArcs
    if (arcs.isEmpty()) return
    val scale = engine.worldScale
    val alpha = FxFrame.peakAlpha
    for (index in arcs.indices) {
        val arc = arcs[index]
        val start = world(engine, arc.fromX, arc.fromY, shakeX, shakeY)
        val end = world(engine, arc.toX, arc.toY, shakeX, shakeY)
        if ((start.x < -LINK_MARGIN && end.x < -LINK_MARGIN) || (start.y < -LINK_MARGIN && end.y < -LINK_MARGIN) ||
            (start.x > size.width + LINK_MARGIN && end.x > size.width + LINK_MARGIN) ||
            (start.y > size.height + LINK_MARGIN && end.y > size.height + LINK_MARGIN)
        ) continue
        val look = LinkLooks[arc.style]
        var strip = linkStrip(look.kind)
        val age = engine.fxAge(arc.life, arc.maxLife)
        val tick = PixelFxClock.tick(age, strip.fps)
        if (age > arc.maxLife * LINK_FLICKER_FROM && tick % 2 == 1) continue
        linkColors(engine, look, arc.style, roles)
        val dx = end.x - start.x
        val dy = end.y - start.y
        // Tiles as drawPixelLink lays them: a texel is a whole number of device pixels.
        val tiles = max(1, ceil(sqrt(dx * dx + dy * dy) * scale / (strip.w * PixelSheet.step(scale))).toInt())
        // Over the budget or at the minimal setting a link is one cell stretched along it: a single operation.
        val plain = FxFrame.plainLinks || !FxDrawBudget.take(FxPool.LINKS, tiles, signature = false)
        if (plain) {
            if (strip != PixelStrip.LINK_COIL) strip = PixelStrip.LINK_TAUT
            FxDrawBudget.take(FxPool.LINKS, 1, signature = true)
        }
        drawPixelLink(strip, PixelFxClock.loopFrame(strip, age, phase = linkPhase(arc)), start.x, start.y, end.x, end.y, scale,
            PixelPalettes.of(LinkScratch.first), alpha, stretched = plain)
        val mark = if (look.endMark && !plain) linkEndStrip(look.kind) else null
        if (mark != null && FxDrawBudget.take(FxPool.LINKS, 1, signature = false)) {
            // The end strip plays through over the link's own life.
            drawPixelFrame(mark, PixelFxClock.spreadFrame(mark, age, arc.maxLife), end.x, end.y, scale, PixelPalettes.of(LinkScratch.second),
                alpha = alpha)
        }
    }
}

/** A link's place in its strip's loop: taken from where it runs, so it never changes when older links leave the list. */
internal fun linkPhase(arc: WeaponArcProjection): Int = ((arc.fromX.toRawBits() * 31) xor arc.toY.toRawBits() xor arc.style) and 0xFF

/** Draw-thread scratch for a link's two colours. */
private object LinkScratch {
    var first: Color = Color.White
    var second: Color = Color.White
}

private fun linkColors(engine: GameplayRenderModel, look: LinkLook, style: Int, roles: KkRolePalette) {
    when (look.tone) {
        LinkTone.REACTION -> {
            val reaction = ReactionId.entries.getOrNull(LinkLooks.reaction(style))
            // Style 0 is the weapon's own link, in the player's colour.
            LinkScratch.first = if (reaction == null) roles.you else roles.element(reaction.first.ordinal)
            LinkScratch.second = if (reaction == null) Kk.Bone else roles.element(reaction.second.ordinal)
        }
        LinkTone.SKILL -> {
            val hue = skillLinkHue(engine, LinkLooks.skill(style), roles)
            LinkScratch.first = hue
            LinkScratch.second = hue
        }
        LinkTone.ELEMENT -> {
            val hue = roles.element(LinkLooks.element(style).coerceAtLeast(0))
            LinkScratch.first = hue
            LinkScratch.second = hue
        }
        LinkTone.GOLD -> {
            LinkScratch.first = roles.gold.base
            LinkScratch.second = roles.gold.light
        }
        LinkTone.BONE -> {
            LinkScratch.first = Kk.Bone
            LinkScratch.second = Kk.Bone
        }
    }
}

/** The hue of a skill's links: its element, or the preset's Kinetic without one. */
private fun skillLinkHue(engine: GameplayRenderModel, skill: Int, roles: KkRolePalette): Color {
    if (skill !in SkillId.entries.indices) return Kk.Bone
    val element = engine.content.skills.getOrNull(skill)?.element
    return if (element == null) roles.kinetic else roles.element(element.ordinal)
}
