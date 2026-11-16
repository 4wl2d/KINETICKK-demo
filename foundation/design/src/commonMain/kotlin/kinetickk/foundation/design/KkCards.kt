// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.foundation.design

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.isUnspecified
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import kotlin.math.max

// Card frame (rarity) and face (type), icon plate, relic slot + synergy, weapon slot.

private const val CARD_CUT_DP = 22f

/**
 * Lift of a selected card (`.card.on`: up 22 dp, −1.5°). Wrap the card frame and its content so
 * both move together. [selected] 0..1 (Pull). Allocation-free (inline).
 */
inline fun DrawScope.withKkCardLift(bounds: Rect, selected: Float, block: DrawScope.() -> Unit) {
    val s = selected.coerceIn(0f, 1.2f)
    translate(0f, -22f * density * s) {
        rotate(-1.5f * s, bounds.center) { block() }
    }
}

/**
 * Runs [block] clipped to the card face slab of [bounds] (22 dp cut), for art painted into the face.
 * The slab is a shared cached path, so it is only ever used as this clip, never handed out.
 */
inline fun DrawScope.clipKkCardFace(bounds: Rect, block: DrawScope.() -> Unit) {
    clipPath(kkCardFaceSlab(bounds), block = block)
}

/** The cached face slab behind [clipKkCardFace]; callers must not change it. */
@PublishedApi
internal fun DrawScope.kkCardFaceSlab(bounds: Rect): Path = KkPathMemo.slab(bounds, d(CARD_CUT_DP))

/**
 * The material's halo and echo, the face and,
 * inside it, the hue's pattern, the watermark and the material's rising halftone. [textBottom] (px,
 * NaN when unknown) is where the card's text ends: the watermark keeps below it. [haloImages] draws the halo from an image.
 */
fun DrawScope.drawKkCardBack(bounds: Rect, time: Float, selected: Float, style: KkCardStyle, textBottom: Float = Float.NaN, haloImages: KkCardHaloImages? = null) {
    val material = style.material
    val frame = style.frame
    val s = selected.coerceIn(0f, 1.2f)
    val cut = d(CARD_CUT_DP)
    if (haloImages?.draw(this, bounds, cut, frame) != true) drawCardHalo(KkPathMemo.slab(bounds, cut), bounds, frame)
    // Echo slab behind the face.
    val echo = d(kkLerp(material.restingEchoDp(), material.selectedEchoDp(), s.coerceAtMost(1f)))
    drawCardEcho(bounds, cut, echo, frame, time)
    val face = KkPathMemo.slab(bounds, cut)
    drawPath(face, if (s > 0.5f) Kk.Ink3 else Kk.Ink2)
    clipPath(face) { drawCardBody(bounds, style, time, textBottom) }
}

/**
 * The material's inner glow, sparks and facets, the hue's frame
 * ornament, the sheen and the band with its labels. [textLeft] and [textBottom] (px, NaN when
 * unknown) bound the card's text column: the ornaments beside it and the prism facets keep clear of
 * it. [frame] true also draws [drawKkCardFrame] (callers that lay plates over the face pass false
 * and draw it after them).
 */
fun DrawScope.drawKkCardFront(
    measurer: CanvasTextMeasurer,
    bounds: Rect,
    time: Float,
    bandStart: String?,
    bandEnd: String?,
    bandHeightDp: Float,
    style: KkCardStyle,
    textLeft: Float = Float.NaN,
    textBottom: Float = Float.NaN,
    frame: Boolean = false,
) {
    val material = style.material
    val rarity = style.frame
    val cut = d(CARD_CUT_DP)
    val face = KkPathMemo.slab(bounds, cut)
    val bandBottom = bounds.top + d(bandHeightDp)
    clipPath(face) {
        drawCardGlow(face, bounds, rarity)
        if (material == KkCardMaterial.PRISMATIC) {
            drawPrismFacets(bounds, bandBottom, style.seed, time, textBottom)
            drawCardSparks(bounds, rarity, time, style.seed)
        }
        drawCardOrnament(bounds, cut, bandBottom, style, textLeft)
    }
    // The face sheen of chrome: the frame's own white (its model lists it).
    if (rarity.sheen.isSpecified) {
        drawKkSheen(face, bounds, time, 3.4f, rarity.sheen.copy(alpha = 0.17f), bandFraction = 0.45f, startFraction = -0.55f)
    }
    drawCardBand(measurer, bounds, style, time, bandStart, bandEnd, bandHeightDp)
    if (frame) drawKkCardFrame(bounds, time, style)
}

/** The material's border round the whole card. */
fun DrawScope.drawKkCardFrame(bounds: Rect, time: Float, style: KkCardStyle) {
    drawCardBorder(bounds, d(CARD_CUT_DP), style.frame, time)
}

/**
 * The band's leading label [text] in [color] on a band [bandWidth] px wide: up to 0.6 of the band beside a trailing label
 * ([hasEnd]), else up to where that label would end (a narrow card moves it into the body). A label still too wide
 * shrinks to fit (to 9 px) before it is cut. Painted layouts: the same label in another colour on the next card keeps
 * its own paragraph.
 */
internal fun DrawScope.kkCardBandStartLabel(
    measurer: CanvasTextMeasurer,
    text: String,
    color: Color,
    bandWidth: Float,
    hasEnd: Boolean,
): TextLayoutResult {
    val room = if (hasEnd) bandWidth * 0.6f else bandWidth - d(14f) - d(22f)
    val natural = measureKkTextPainted(measurer, text, measurer.typography.labelStyle(12f), color, uppercase = true)
    if (natural.size.width <= room) return natural
    val fitted = (12f * room / natural.size.width).coerceAtLeast(9f)
    return measureKkTextPainted(measurer, text, measurer.typography.labelStyle(fitted), color, uppercase = true, maxWidth = room)
}

private fun DrawScope.drawCardBand(
    measurer: CanvasTextMeasurer,
    bounds: Rect,
    style: KkCardStyle,
    time: Float,
    start: String?,
    end: String?,
    heightDp: Float,
) {
    val band = KkRects.of(bounds.left + d(18f), bounds.top, bounds.right, bounds.top + d(heightDp))
    val path = KkPathMemo.band(band.left, band.top, band.right, band.bottom, d(4f), d(9f))
    drawCardBandMaterial(path, band, style.frame, time)
    val prismatic = style.material == KkCardMaterial.PRISMATIC
    if (start != null) {
        val color = if (prismatic) Kk.Bone else style.startLabelColor()
        val layout = kkCardBandStartLabel(measurer, start, color, band.width, end != null)
        val x = band.left + d(14f)
        if (prismatic) {
            // The word carries the spectrum, sliding with the edge: plain glyphs tinted in a layer, so
            // the shared text layout never changes paint. Its own short tile puts several stops across the word.
            val tile = prismLabelTile(layout.size.width.toFloat())
            val shift = -kkLoop(time, 6f) * tile
            val brush = prismLabelBrush(tile, style.frame.prism ?: KkSpectrum.Full)
            val canvas = drawContext.canvas
            canvas.saveLayer(band, KkLayers.paint)
            drawKkText(layout, x, band.center.y, color, valign = KkVAlign.CENTER)
            translate(x + shift, 0f) {
                drawRect(brush, Offset(band.left - x - shift, band.top), band.size, blendMode = BlendMode.SrcIn)
            }
            canvas.restore()
        } else {
            drawKkText(layout, x, band.center.y, color, valign = KkVAlign.CENTER)
        }
    }
    if (end != null) {
        val color = style.endLabelColor()
        val layout = measureKkTextPainted(measurer, end, measurer.typography.monoStyle(9f, weight = FontWeight.Bold), color, uppercase = true)
        drawKkText(layout, band.right - d(22f), band.center.y, color, align = KkAlign.END, valign = KkVAlign.CENTER)
    }
}

/**
 * Icon plate (card/offer icon): a slab in [fill] (ink-3 unless the caller tints it) with [cutDp] cut
 * and a centered [icon] in [color]. A specified [rim] edges the plate (its right and bottom edges in
 * [rimSecond] when that is specified too). Allocation-free.
 */
fun DrawScope.drawKkIconPlate(
    bounds: Rect,
    icon: KkIcon?,
    color: Color,
    cutDp: Float = 8f,
    iconSizeDp: Float = 30f,
    rim: Color = Color.Unspecified,
    rimSecond: Color = Color.Unspecified,
    fill: Color = Kk.Ink3,
) {
    val cut = d(cutDp)
    drawPath(KkPathMemo.slab(bounds, cut), fill)
    if (rim.isSpecified) {
        val w = d(1.5f)
        val i = w * 0.5f
        val c = (cut - i * 0.4f).coerceAtLeast(0f)
        drawPath(KkPathMemo.slab(bounds.left + i, bounds.top + i, bounds.right - i, bounds.bottom - i, c), rim, alpha = PLATE_RIM_ALPHA,
            style = kkStroke(w))
        if (rimSecond.isSpecified) {
            // The second hue takes the right and bottom edges.
            drawLine(rimSecond, Offset(bounds.right - i, bounds.top + i), Offset(bounds.right - i - c, bounds.bottom - i), w, alpha = PLATE_RIM_ALPHA)
            drawLine(rimSecond, Offset(bounds.right - i - c, bounds.bottom - i), Offset(bounds.left + i + w, bounds.bottom - i), w, alpha = PLATE_RIM_ALPHA)
        }
    }
    if (icon != null) drawKkIcon(icon, bounds.center, d(iconSizeDp), color)
}

/** The icon plate rim's strength. */
private const val PLATE_RIM_ALPHA = 0.85f

/**
 * Relic slot (`.rslot`, 44 dp): diamond with an aspect-colored rim, ink-2 inside and the aspect
 * [icon]; [color] `Color.Unspecified` draws the empty slot (line-2 rim + hatch). [ring] (when
 * specified) draws the selection ring (`you` to bind, threat to replace). Allocation-free.
 */
fun DrawScope.drawKkRelicSlot(
    center: Offset,
    color: Color,
    icon: KkIcon? = null,
    sizeDp: Float = 44f,
    iconSizeDp: Float = sizeDp * 16f / 44f,
    ring: Color = Color.Unspecified,
    alpha: Float = 1f,
) {
    val half = d(sizeDp) * 0.5f
    val empty = color.isUnspecified
    if (ring.isSpecified) {
        drawPath(KkPathMemo.diamond(center.x, center.y, half + d(6f), half + d(6f)), ring, alpha, kkStroke(d(2f)))
    }
    drawPath(KkPathMemo.diamond(center.x, center.y, half, half), if (empty) Kk.Line2 else color, alpha)
    // `inset: 2.5px` on a diamond moves each vertex 2.5 dp inward along both axes.
    val inner = KkPathMemo.diamond(center.x, center.y, half - d(2.5f) * 1.414f, half - d(2.5f) * 1.414f)
    drawPath(inner, Kk.Ink2, alpha)
    if (empty) {
        drawKkHatch(inner)
    } else if (icon != null) {
        drawKkIcon(icon, center, d(iconSizeDp), color, alpha)
    }
}

/**
 * Synergy link bar between two adjacent relic slots (12 × 3 dp with a soft glow, stacked
 * strokes). [from]/[to] are the facing slot tips. Allocation-free.
 */
fun DrawScope.drawKkSynergyLink(from: Offset, to: Offset, color: Color) {
    drawLine(color.copy(alpha = color.alpha * 0.18f), from, to, d(9f))
    drawLine(color.copy(alpha = color.alpha * 0.3f), from, to, d(6f))
    drawLine(color, from, to, d(3f))
}

/**
 * Synergy bracket above linked slots: a straight line from [left] to [right] at [y] with 8 dp
 * legs pointing down; dashed for a preview synergy. Optional [tag] (plate in [color], ink text)
 * sits centered above the line. Returns the tag rect or null.
 */
fun DrawScope.drawKkSynergyBracket(
    measurer: CanvasTextMeasurer,
    left: Float,
    right: Float,
    y: Float,
    color: Color,
    dashed: Boolean = false,
    tag: String? = null,
): Rect? {
    val stroke = if (dashed) KkBracketDash.stroke(d(2f), density) else kkStroke(d(2f))
    drawPath(KkPathMemo.bracket(left, right, y, d(8f)), color, style = stroke)
    if (tag == null) return null
    val size = kkTagSize(measurer, tag, density, 20f, 12f)
    val topLeft = Offset((left + right) * 0.5f - size.width * 0.5f, y - d(6f) - size.height)
    return drawKkTag(measurer, tag, topLeft, heightDp = 20f, fontSize = 12f, background = if (dashed) Kk.Ink else color,
        foreground = if (dashed) color else Kk.Ink)
}

/** A butt-capped dashed stroke ([on] and [off] dp), cached for its one width (draw-thread confined). */
private class KkDashedStroke(private val on: Float, private val off: Float) {
    private var key = -1f
    private var dashed: Stroke? = null

    fun stroke(width: Float, density: Float): Stroke {
        if (key == width && dashed != null) return dashed!!
        key = width
        return Stroke(width, cap = StrokeCap.Butt, pathEffect = PathEffect.dashPathEffect(floatArrayOf(on * density, off * density)))
            .also { dashed = it }
    }
}

private val KkBracketDash = KkDashedStroke(6f, 4f)

internal val SlotGlowWidths = floatArrayOf(2f, 4f, 7f, 10f, 14f)
internal val SlotGlowAlphas = floatArrayOf(0.16f, 0.12f, 0.08f, 0.05f, 0.03f)

/** Measures the weapon-slot level text ("Lvl N", mono 700 10 px) once for [drawKkWeaponSlot]. */
fun measureKkWeaponLevel(measurer: CanvasTextMeasurer, text: String): TextLayoutResult =
    measureKkText(measurer, text, measurer.typography.monoStyle(10f, weight = FontWeight.Bold, trackingEm = 0f, lineHeightEm = 1f))

/**
 * Weapon slot (`.wslot`, 62 dp): sheared square, [icon] in `you` (gold when [maxLevel]),
 * [cooldown] 0..1 remaining as an ink 80 % conic sweep from 12 o'clock, `you` glow when [ready]
 * (stacked strokes), [levelText] ("Lvl N", mono) at the bottom right. `icon == null` with
 * [empty] draws the hatch + plus. Pass a pre-measured [levelLayout] on hot paths.
 */
fun DrawScope.drawKkWeaponSlot(
    measurer: CanvasTextMeasurer,
    bounds: Rect,
    icon: KkIcon?,
    levelText: String? = null,
    cooldown: Float = 0f,
    ready: Boolean = false,
    maxLevel: Boolean = false,
    empty: Boolean = icon == null,
    levelLayout: TextLayoutResult? = null,
    iconSizeDp: Float = 32f,
) {
    val cut = d(9f)
    val face = KkPathMemo.slab(bounds, cut)
    if (empty) {
        drawKkHatch(face)
        drawKkIcon(KkIcon.UI_PLUS, bounds.center, d(16f), Kk.Mute2)
        return
    }
    val accent = if (maxLevel) Kk.RLegend else measurer.roles.you
    if (ready || maxLevel) {
        // drop-shadow(0 0 5px accent @55 %) as stacked strokes; the face covers their inner half.
        for (index in 0 until SlotGlowWidths.size) {
            drawPath(face, accent, alpha = SlotGlowAlphas[index], style = kkStroke(d(SlotGlowWidths[index])))
        }
    }
    drawPath(face, if (ready) Kk.Ink3 else Kk.Ink2)
    if (icon != null) drawKkIcon(icon, bounds.center, d(iconSizeDp), accent)
    val cd = cooldown.coerceIn(0f, 1f)
    if (cd > 0f) {
        clipPath(face) {
            val radius = max(bounds.width, bounds.height)
            drawArc(Kk.Ink.copy(alpha = 0.8f), -90f, 360f * cd, true,
                Offset(bounds.center.x - radius, bounds.center.y - radius), Size(radius * 2f, radius * 2f))
        }
    }
    val layout = levelLayout ?: levelText?.let { measureKkWeaponLevel(measurer, it) }
    if (layout != null) {
        drawKkText(layout, bounds.right - d(9f), bounds.bottom - d(5f), if (maxLevel) Kk.RLegend else Kk.Bone,
            align = KkAlign.END, valign = KkVAlign.BASELINE)
    }
}

/** Measures a skill slot's key-cap label (mono 700, [size] sp) for [drawKkSkillSlot] and the Apex key cap. */
fun measureKkKeyCap(measurer: CanvasTextMeasurer, text: String, size: Float): TextLayoutResult =
    measureKkText(measurer, text, kkKeyCapStyle(measurer, size), uppercase = true)

/** The key-cap label style (mono 700, no tracking, tight line). */
fun kkKeyCapStyle(measurer: CanvasTextMeasurer, size: Float) =
    measurer.typography.monoStyle(size, weight = FontWeight.Bold, trackingEm = 0f, lineHeightEm = 1f)

/**
 * Skill slot (Z/X/C, beside [drawKkWeaponSlot]): the weapon slot's sheared face (Ink2 cooling, Ink3 and
 * the `you` glow when [ready]) with the skill's [icon] in [iconColor], [cooldown] 0..1 remaining as the
 * ink 80 % conic sweep from 12 o'clock, [rank] of [maxRank] sheared pips top-left (owned `you`, missing
 * Ink4, all gold at the top rank; [rankFill] 0..1 fills the newest owned pip), the [active] share as a bar in [activeColor]
 * near the bottom ([pending] in Bone: a skill in flight), a Bone face while [pressed] or on the
 * [readyFlash], the sweep's leading edge in [refundColor] at [refundFlash] (a refund or a refusal), and
 * [keyLayout] on an Ink3 key cap centred below the slot. [empty] is a slot nothing is bound to yet (it is usable, not
 * locked): a darker Ink1 face inside a dashed Line2 outline with its [maxRank] pips unlit and no glyph, sweep or bar.
 * [locked] draws the hatch and the lock only. [shakeX] moves the whole slot. [unit] is px per reference dp for the
 * pips and the key cap (0 = dp). Allocation-free: paths come from the shared memo, the dashed outline from a
 * one-width stroke cache; the shake is a translation.
 */
fun DrawScope.drawKkSkillSlot(
    measurer: CanvasTextMeasurer,
    bounds: Rect,
    icon: KkIcon?,
    iconColor: Color,
    cooldown: Float,
    ready: Boolean,
    rank: Int,
    maxRank: Int = 3,
    active: Float = 0f,
    activeColor: Color = Kk.Bone,
    pending: Float = 0f,
    locked: Boolean = false,
    pressed: Float = 0f,
    readyFlash: Float = 0f,
    refundFlash: Float = 0f,
    refundColor: Color = Kk.Bone,
    shakeX: Float = 0f,
    keyLayout: TextLayoutResult? = null,
    iconSizeDp: Float = 26f,
    rankFill: Float = 1f,
    unit: Float = 0f,
    empty: Boolean = false,
) {
    translate(shakeX, 0f) {
        drawSkillSlotBody(measurer, bounds, icon, iconColor, cooldown, ready, rank, maxRank, active, activeColor,
            pending, locked, empty, pressed, readyFlash, refundFlash, refundColor, keyLayout, iconSizeDp, rankFill,
            if (unit > 0f) unit else density)
    }
}

private fun DrawScope.drawSkillSlotBody(
    measurer: CanvasTextMeasurer,
    bounds: Rect,
    icon: KkIcon?,
    iconColor: Color,
    cooldown: Float,
    ready: Boolean,
    rank: Int,
    maxRank: Int,
    active: Float,
    activeColor: Color,
    pending: Float,
    locked: Boolean,
    empty: Boolean,
    pressed: Float,
    readyFlash: Float,
    refundFlash: Float,
    refundColor: Color,
    keyLayout: TextLayoutResult?,
    iconSizeDp: Float,
    rankFill: Float,
    u: Float,
) {
    val roles = measurer.roles
    val cut = d(9f)
    val face = KkPathMemo.slab(bounds, cut)
    if (locked) {
        drawKkHatch(face)
        drawKkIcon(KkIcon.SYSTEM_LOCKED, bounds.center, d(16f), Kk.Mute2)
        return
    }
    if (empty) {
        // Room for an active item: a recessed face inside a dashed edge (the "not yet" outline of an unmet pairing chip),
        // its rank pips unlit below; a press still flashes it, faintly.
        drawPath(face, Kk.Ink1)
        drawPath(face, Kk.Line2, style = KkSkillSlotDash.stroke(d(EMPTY_SLOT_STROKE_DP), density))
        val flash = pressed.coerceIn(0f, 1f)
        if (flash > 0f) drawPath(face, Kk.Bone, alpha = flash * EMPTY_SLOT_PRESS_ALPHA)
    } else {
        if (ready) {
            // drop-shadow(0 0 5px you @55 %) as stacked strokes; the face covers their inner half.
            for (index in 0 until SlotGlowWidths.size) {
                drawPath(face, roles.you, alpha = SlotGlowAlphas[index], style = kkStroke(d(SlotGlowWidths[index])))
            }
        }
        drawPath(face, if (ready) Kk.Ink3 else Kk.Ink2)
        val flash = max(pressed, readyFlash).coerceIn(0f, 1f)
        if (flash > 0f) drawPath(face, Kk.Bone, alpha = flash)
        if (icon != null) drawKkIcon(icon, bounds.center, d(iconSizeDp), iconColor)
        val cd = cooldown.coerceIn(0f, 1f)
        val edge = refundFlash.coerceIn(0f, 1f)
        if (cd > 0f || edge > 0f) {
            clipPath(face) {
                val radius = max(bounds.width, bounds.height)
                val center = bounds.center
                if (cd > 0f) {
                    drawArc(Kk.Ink.copy(alpha = 0.8f), -90f, 360f * cd, true,
                        Offset(center.x - radius, center.y - radius), Size(radius * 2f, radius * 2f))
                }
                if (edge > 0f) {
                    // The sweep's leading radius (where the remaining cooldown ends).
                    val angle = (-90f + 360f * cd) * DEGREES_TO_RADIANS
                    drawLine(refundColor, center, Offset(center.x + kotlin.math.cos(angle) * radius, center.y + kotlin.math.sin(angle) * radius),
                        d(3f), alpha = edge)
                }
            }
        }
    }
    // Rank pips: sheared 6 × 3 inside the top-left; every pip gold at the top rank.
    val pipWidth = 6f * u
    val pipHeight = 3f * u
    val pipGap = d(2f)
    val pipTop = bounds.top + d(4f)
    var pipLeft = bounds.left + cut + d(2f)
    val owned = if (empty) 0 else rank.coerceIn(0, maxRank)
    val top = owned >= maxRank
    for (index in 0 until maxRank) {
        val path = KkPathMemo.sheared(pipLeft, pipTop, pipLeft + pipWidth, pipTop + pipHeight)
        if (index < owned) {
            val color = if (top) roles.gold.base else roles.you
            val fill = if (index == owned - 1) rankFill.coerceIn(0f, 1f) else 1f
            if (fill < 1f) {
                drawPath(path, Kk.Ink4)
                clipRect(pipLeft - pipHeight, pipTop, pipLeft + pipWidth * fill, pipTop + pipHeight) { drawPath(path, color) }
            } else {
                drawPath(path, color)
            }
        } else {
            drawPath(path, Kk.Ink4)
        }
        pipLeft += pipWidth + pipGap
    }
    if (empty) return
    val share = active.coerceIn(0f, 1f)
    val flight = pending.coerceIn(0f, 1f)
    if (share > 0f || flight > 0f) {
        val barLeft = bounds.left + cut
        val barWidth = bounds.width - cut * 2f
        val barBottom = bounds.bottom - d(5f)
        val barHeight = d(3f)
        if (share > 0f) drawRect(activeColor, Offset(barLeft, barBottom - barHeight), Size(barWidth * share, barHeight))
        if (flight > 0f) drawRect(Kk.Bone, Offset(barLeft, barBottom - barHeight), Size(barWidth * flight, barHeight))
    }
    if (keyLayout != null) {
        // Ink3 sheared key cap, 12 u high with 3 u padding, 3 u below the slot.
        val capHeight = 12f * u
        val capWidth = keyLayout.size.width + 6f * u
        val capTop = bounds.bottom + 3f * u
        val capLeft = bounds.center.x - capWidth * 0.5f
        drawPath(KkPathMemo.sheared(capLeft, capTop, capLeft + capWidth, capTop + capHeight), Kk.Ink3)
        drawKkText(keyLayout, bounds.center.x, capTop + capHeight * 0.5f, Kk.Bone, align = KkAlign.CENTER, valign = KkVAlign.CENTER)
    }
}

/** The empty slot's dashed edge. */
private val KkSkillSlotDash = KkDashedStroke(5f, 4f)

/** The empty slot's dashed edge (dp) and how strongly a press lights its face. */
private const val EMPTY_SLOT_STROKE_DP = 1.5f
private const val EMPTY_SLOT_PRESS_ALPHA = 0.35f

private const val DEGREES_TO_RADIANS = (kotlin.math.PI / 180.0).toFloat()
