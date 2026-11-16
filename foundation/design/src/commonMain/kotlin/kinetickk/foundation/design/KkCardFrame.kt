// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.foundation.design

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.Density
import kotlin.math.ceil
import kotlin.math.min

// The rarity channel of a card: its material frame. Nothing here reads a card's hue (the type
// channel); every colour comes from the material's [KkCardFrame].

/**
 * Card material: the rarity channel. It owns the card frame (the border, the band and its label,
 * the echo slabs, the inner glow and outer halo, the sheen, halftone and sparks)
 * and paints it in its own metal from [frame], never in the card's [KkCardStyle.hue]: the hue
 * owns the face. The ladder rises in brightness and richness: iron, steel, chrome and prismatic.
 */
enum class KkCardMaterial(val rank: Int) {
    /** A deliberately calm card (a decline): an ink band, a muted label, no frame and no effects. */
    CALM(1),

    /** Common: a dark iron frame (a 2 dp mid-grey border), a matte iron band, no glow. */
    IRON(1),

    /** Uncommon: a lighter steel frame, a brushed steel band with a lit bevel, a soft cool-white inner glow. */
    STEEL(2),

    /**
     * Rare: a bright chrome frame with a glint running round it and a soft white halo, a polished
     * chrome band with a specular line, the sheen sweep over band and face, one chrome echo.
     */
    CHROME(3),

    /**
     * Epic: a prismatic frame whose spectrum (the preset's own, [KkRolePalette.spectrum], never led
     * by a hue) turns slowly round the border with a bright bone rim inside it, a spectrum halo and
     * inner glow, a black glass band with a sliding prismatic edge and label,
     * facets a glint travels along, a rising spectrum halftone with spectrum sparks and a double echo
     * (spectrum and a bone rim). Its border, rim and echoes outshine chrome in lightness as well as
     * in colour.
     */
    PRISMATIC(4),

    ;

    /** Everything this material paints in the Default preset (full spectrum): the same for every card of the rank. */
    val frame: KkCardFrame get() = KkCardFrames.all[ordinal]

    /**
     * Whether the frame changes with time: chrome's sheen and glint, and the turning and sliding spectrum. Calm, iron and steel draw the same at every time (a caller may draw them once and keep the pixels).
     */
    val moves: Boolean get() = this == CHROME || this == PRISMATIC

    /** The frame with the current palette's prismatic spectrum. */
    fun frame(spectrum: KkSpectrum): KkCardFrame =
        if (this == PRISMATIC) KkCardFrames.prisms[spectrum.id] else frame

    companion object {
        /** The material of rank 1 (common) .. 4 (epic); out-of-range ranks are clamped. */
        fun ofRank(rank: Int): KkCardMaterial = when (rank.coerceIn(1, 4)) {
            1 -> IRON
            2 -> STEEL
            3 -> CHROME
            else -> PRISMATIC
        }
    }
}

/** The effect layers a [KkCardFrame] paints (its [KkCardFrame.richness] is how many). */
enum class KkCardFrameLayer {
    /** A border round the card in the material. */
    BORDER,

    /** A finish on the band: matte hairlines, brushing, polish or glass. */
    BAND_FINISH,

    /** A lit lip along the band's top edge and an edge line between band and face. */
    LIP,

    /** A border that catches the light: a gradient along it, lighter at the top left. */
    BEVEL,

    /** A soft glow inside the frame. */
    GLOW,

    /** A specular line across the band. */
    SPECULAR,

    /** A soft halo outside the frame. */
    HALO,

    /** An echo slab resting behind the card. */
    ECHO,

    /** A second echo behind the first. */
    DOUBLE_ECHO,

    /** A highlight sweeping across band and face. */
    SHEEN,

    /** A glint running round the border. */
    GLINT,

    /** The full spectrum, turning round the border and sliding along the band. */
    SPECTRUM,

    /** Facet lines in the face with a glint travelling along them. */
    FACETS,

    /** A halftone rising from the bottom of the face. */
    HALFTONE,

    /** Sparks rising through the face. */
    SPARKS,
}

/**
 * Everything the rarity channel of one [KkCardMaterial] paints, fixed per material (no card's hue
 * reaches it): the [border] stops ([borderDp] wide on a board card), the [band] tones (its bevel,
 * label zone and horizon) and the [labelZone] tones a band label sits on, the leading [label]
 * colours (stops of a gradient when several) and the trailing [labelEnd], the [echo] colours, how
 * many [echoes] rest behind the card, the inner [glow] and outer [halo] colours with their
 * strengths, the [effects] colours (sheen, glints, halftone and sparks) and the [layers]
 * it draws. Chrome sweeps a [sheen] over the face.
 * The prismatic material paints its [prism] (the preset's spectrum); [spectrum] holds every colour
 * it paints from it at once (its stops and its label's), never one hue.
 */
class KkCardFrame internal constructor(
    val material: KkCardMaterial,
    val prism: KkSpectrum?,
    val sheen: Color,
    val spectrum: Set<Color>,
    val border: List<Color>,
    val borderDp: Float,
    val band: List<Color>,
    val labelZone: List<Color>,
    val label: List<Color>,
    val labelEnd: Color,
    val echo: List<Color>,
    val echoes: Int,
    val glow: List<Color>,
    val glowStrength: Float,
    val halo: List<Color>,
    val haloStrength: Float,
    val effects: List<Color>,
    val layers: Set<KkCardFrameLayer>,
) {
    /** Every colour the frame paints (border, band, labels, echoes, glow, halo, effects and the face sheen). */
    val colors: List<Color> =
        (border + band + labelZone + label + labelEnd + echo + glow + halo + effects + listOfNotNull(sheen.takeIf { it.isSpecified })).distinct()

    /** How many effect layers the material draws: it rises strictly with the rank. */
    val richness: Int get() = layers.size
}

/** The metals' white: rims, lips, glints and the sheen. */
internal val FrameWhite = Color.White

/** The prismatic frame's bright inner rim (bone, inside the spectrum border): it lifts the epic frame above chrome in lightness too. */
internal val PrismRim = Kk.Bone

/** The inner rim's width (board dp). */
internal const val PRISM_RIM_DP = 3f

// Iron: matte dark metal with a mid-grey edge.
internal val IronBorderTop = Color(0xFF85847C)
internal val IronBorderBottom = Color(0xFF575650)
internal val IronBand = listOf(Color(0xFF3B3C37), Color(0xFF32332F), Color(0xFF2E2F2B), Color(0xFF2A2B27), Color(0xFF262723), Color(0xFF1F201D))
internal val IronLip = Color(0xFF8E8D86)
internal val IronEdge = Color(0xFF6E6D66)
private val IronLabel = Color(0xFFBDBCB3)
private val IronEcho = Color(0xFF2C2D29)

// Steel: cool brushed metal in the middle of the lightness range, lit at the top, an ink label.
private val SteelBorder = listOf(Color(0xFFF2F5F7), Color(0xFFBAC2C8), Color(0xFF8A9299))
internal val SteelBand = listOf(Color(0xFFD6DADE), Color(0xFFA9B1B8), Color(0xFF959DA4), Color(0xFF868E95), Color(0xFF7A8289), Color(0xFF4A5157))
internal val SteelEdge = Color(0xFF3E4449)
private val SteelEcho = Color(0xFF3B4146)
private val SteelGlow = Color(0xFFDADEE2)

// Chrome: polished, bright, with a dark horizon below the label.
private val ChromeBorder = listOf(FrameWhite, Color(0xFFB8C0C6), Color(0xFF6B747B), Color(0xFFF4F7F9), Color(0xFF8E979F), FrameWhite)
internal val ChromeBand = listOf(FrameWhite, Color(0xFFE7EBEE), Color(0xFFD4D9DD), Color(0xFFC1C7CC), Color(0xFFA9B0B6), Color(0xFF59616A), Color(0xFFB6BDC3))
internal val ChromeEcho = listOf(Color(0xFFE0E3E6), Color(0xFF858E95), Color(0xFFC4CACF))
private val ChromeHalo = Color(0xFFEAEDF0)

/** Bone hairlines over iron and brushed steel bands (drawn and modelled from these values). */
internal const val IRON_HAIRLINE = 0.05f
internal const val STEEL_HAIRLINE = 0.12f

/** The sheen's highlight over a chrome band. */
internal const val CHROME_SHEEN = 0.22f

/** The glass of the prismatic band: its reflection and the refraction streak crossing it. */
internal const val PRISM_GLASS_ALPHA = 0.035f
internal const val PRISM_STREAK_ALPHA = 0.13f
internal const val PRISM_STREAK_DEGREES = 24f

/** Minimum contrast of a band label against the band under it. */
const val KK_CARD_LABEL_CONTRAST = 4.5f

/** Share of the band width the leading label may cover (from the leading edge). */
const val KK_CARD_START_LABEL_END = 0.62f

/** Share of the band width where the trailing label (and the element pips) start. */
const val KK_CARD_END_LABEL_START = 0.7f

/** [preferred] when it reads at the label contrast over every one of [backgrounds], else lifted toward bone or ink until it does. */
private fun readableOver(preferred: Color, backgrounds: List<Color>): Color {
    fun worst(color: Color): Float {
        var low = Float.MAX_VALUE
        for (background in backgrounds) low = min(low, kkContrast(color, background))
        return low
    }
    val target = KK_CARD_LABEL_CONTRAST + 0.2f
    if (worst(preferred) >= target) return preferred
    for (step in 1..20) {
        for (toward in arrayOf(Kk.Bone, Kk.Ink)) {
            val candidate = kkMix(preferred, toward, step / 20f)
            if (worst(candidate) >= target) return candidate
        }
    }
    return if (worst(Kk.Bone) >= worst(Kk.Ink)) Kk.Bone else Kk.Ink
}

/** The tones of a band zone [tones] and each under the bone hairlines at [hairline]. */
private fun withHairlines(tones: List<Color>, hairline: Float): List<Color> =
    tones + tones.map { kkMix(it, Kk.Bone, hairline) }

/**
 * The frames of every material in the Default preset (full spectrum), in
 * [KkCardMaterial] order, plus the prismatic frame of every spectrum (by id).
 */
internal object KkCardFrames {
    val all: List<KkCardFrame> = KkCardMaterial.entries.map { frameOf(it, KkSpectrum.Full) }
    val prisms: List<KkCardFrame> = KkSpectrum.all.map { if (it === KkSpectrum.Full) all[KkCardMaterial.PRISMATIC.ordinal] else frameOf(KkCardMaterial.PRISMATIC, it) }
}

private fun frameOf(material: KkCardMaterial, spectrum: KkSpectrum): KkCardFrame = when (material) {
    KkCardMaterial.CALM -> KkCardFrame(
        material, null, Color.Unspecified, emptySet(), border = emptyList(), borderDp = 0f, band = listOf(Kk.Ink3, Kk.Line2), labelZone = listOf(Kk.Ink3),
        label = listOf(Kk.Mute), labelEnd = Kk.Mute, echo = emptyList(), echoes = 0, glow = emptyList(), glowStrength = 0f,
        halo = emptyList(), haloStrength = 0f, effects = emptyList(), layers = emptySet(),
    )
    KkCardMaterial.IRON -> {
        val zone = withHairlines(IronBand.subList(2, 5), IRON_HAIRLINE)
        val label = readableOver(IronLabel, zone)
        KkCardFrame(
            material, null, Color.Unspecified, emptySet(), border = listOf(IronBorderTop, IronBorderBottom), borderDp = 2f, band = IronBand + IronLip + IronEdge,
            labelZone = zone, label = listOf(label), labelEnd = label, echo = listOf(IronEcho, IronEdge), echoes = 0,
            glow = emptyList(), glowStrength = 0f, halo = emptyList(), haloStrength = 0f, effects = emptyList(),
            layers = setOf(KkCardFrameLayer.BORDER, KkCardFrameLayer.BAND_FINISH, KkCardFrameLayer.LIP),
        )
    }
    KkCardMaterial.STEEL -> {
        val zone = withHairlines(SteelBand.subList(2, 5), STEEL_HAIRLINE)
        val label = readableOver(Kk.Ink, zone)
        KkCardFrame(
            material, null, Color.Unspecified, emptySet(), border = SteelBorder, borderDp = 2.5f, band = SteelBand + FrameWhite + SteelEdge,
            labelZone = zone, label = listOf(label), labelEnd = label, echo = listOf(SteelEcho, SteelBorder[1]), echoes = 0,
            glow = listOf(SteelGlow), glowStrength = 0.9f, halo = emptyList(), haloStrength = 0f, effects = emptyList(),
            layers = setOf(KkCardFrameLayer.BORDER, KkCardFrameLayer.BAND_FINISH, KkCardFrameLayer.LIP, KkCardFrameLayer.BEVEL,
                KkCardFrameLayer.GLOW),
        )
    }
    KkCardMaterial.CHROME -> {
        // The label sits on the polished middle (and on it under the sheen); the horizon stays below it.
        val middle = ChromeBand.subList(2, 5)
        val zone = middle + middle.map { kkMix(it, FrameWhite, CHROME_SHEEN) }
        val label = readableOver(Kk.Ink, zone)
        KkCardFrame(
            material, null, FrameWhite, emptySet(), border = ChromeBorder, borderDp = 3f, band = ChromeBand, labelZone = zone, label = listOf(label), labelEnd = label,
            echo = ChromeEcho + FrameWhite, echoes = 1, glow = listOf(FrameWhite), glowStrength = 1.25f,
            halo = listOf(ChromeHalo), haloStrength = 1f, effects = listOf(FrameWhite),
            layers = setOf(KkCardFrameLayer.BORDER, KkCardFrameLayer.BAND_FINISH, KkCardFrameLayer.LIP, KkCardFrameLayer.BEVEL,
                KkCardFrameLayer.GLOW, KkCardFrameLayer.SPECULAR, KkCardFrameLayer.HALO, KkCardFrameLayer.ECHO, KkCardFrameLayer.SHEEN,
                KkCardFrameLayer.GLINT),
        )
    }
    KkCardMaterial.PRISMATIC -> {
        val zone = listOf(Kk.Ink, kkMix(Kk.Ink, Kk.Bone, PRISM_GLASS_ALPHA), spectrum.streak)
        val loop = spectrum.loop
        KkCardFrame(
            material, spectrum, Color.Unspecified, (loop + spectrum.label).toSet(), border = loop + PrismRim, borderDp = 3.5f,
            band = listOf(Kk.Ink) + loop, labelZone = zone, label = spectrum.label, labelEnd = readableOver(Kk.Bone, zone),
            echo = loop + Kk.Bone, echoes = 2, glow = loop, glowStrength = 1.5f, halo = loop, haloStrength = 1.5f,
            effects = spectrum.stops + Kk.Bone,
            layers = setOf(KkCardFrameLayer.BORDER, KkCardFrameLayer.BAND_FINISH, KkCardFrameLayer.LIP, KkCardFrameLayer.GLOW,
                KkCardFrameLayer.HALO, KkCardFrameLayer.ECHO, KkCardFrameLayer.DOUBLE_ECHO, KkCardFrameLayer.SPECTRUM,
                KkCardFrameLayer.FACETS, KkCardFrameLayer.HALFTONE, KkCardFrameLayer.SPARKS,
                KkCardFrameLayer.GLINT),
        )
    }
}

/**
 * A drawn card layer kept as pixels: [brush] samples them smoothly (bilinear, with mip levels where the platform
 * builds them), so a card dealt rotated and scaled keeps clean edges. Draw it under a translate to where the layer
 * starts. [close] releases the image's own native references. Recorded draws keep their references until released.
 */
internal class KkCardImage(val brush: Brush, val width: Int, val height: Int, private val release: () -> Unit) {
    fun close() = release()
}

/** Draws into a raster, closes its transient canvas, and transfers its pixels to the card image. */
internal expect fun kkCardImage(width: Int, height: Int, density: Density, block: DrawScope.() -> Unit): KkCardImage

/** Whether cards keep drawn layers as images here (desktop and web; Android's renderer keeps its own layers). */
internal expect val KkCardImagesSupported: Boolean

/**
 * The outer halo of a card frame ([drawCardHalo]) as an image keyed by frame, card size and density. A rotated
 * card re-rasterizes wide strokes as software masks. The image replaces those masks with a textured rect.
 * Inner glow strokes still blend individually over the face. Draw thread only. The least recently used entry is
 * closed on replacement. The deal closes the remaining images when it leaves composition.
 */
class KkCardHaloImages {
    private val lastUsed = LongArray(CAPACITY)
    private var uses = 0L
    private val frames = arrayOfNulls<KkCardFrame>(CAPACITY)
    private val keys = FloatArray(CAPACITY * 4)
    private val margins = FloatArray(CAPACITY)
    private val images = arrayOfNulls<KkCardImage>(CAPACITY)

    /** Returns false to draw live when the image is too large or the platform keeps no images. */
    internal fun draw(scope: DrawScope, bounds: Rect, cut: Float, frame: KkCardFrame): Boolean {
        if (!KkCardImagesSupported) return false
        // A frame without that layer draws nothing (as the live strokes would).
        if (frame.haloStrength <= 0f) return true
        val width = bounds.width
        val height = bounds.height
        var slot = -1
        for (index in 0 until CAPACITY) {
            val base = index * 4
            if (frames[index] === frame && keys[base] == width && keys[base + 1] == height &&
                keys[base + 2] == cut && keys[base + 3] == scope.density
            ) {
                slot = index
                break
            }
        }
        if (slot < 0) {
            slot = render(scope, width, height, cut, frame)
            if (slot < 0) return false
        }
        lastUsed[slot] = ++uses
        val image = images[slot] ?: return false
        val margin = margins[slot]
        scope.translate(bounds.left - margin, bounds.top - margin) {
            drawRect(image.brush, Offset.Zero, Size(image.width.toFloat(), image.height.toFloat()))
        }
        return true
    }

    private fun render(scope: DrawScope, width: Float, height: Float, cut: Float, frame: KkCardFrame): Int {
        val margin = ceil(scope.density * HALO_REACH_DP)
        val pixelWidth = ceil(width + margin * 2f).toInt()
        val pixelHeight = ceil(height + margin * 2f).toInt()
        if (pixelWidth <= 0 || pixelHeight <= 0 || pixelWidth.toLong() * pixelHeight > MAX_PIXELS) return -1
        var slot = 0
        for (index in 1 until CAPACITY) if (lastUsed[index] < lastUsed[slot]) slot = index
        val image = kkCardImage(pixelWidth, pixelHeight, Density(scope.density)) {
            val local = Rect(margin, margin, margin + width, margin + height)
            val face = KkPathMemo.slab(local, cut)
            drawCardHalo(face, local, frame)
        }
        images[slot]?.close()
        images[slot] = image
        frames[slot] = frame
        val base = slot * 4
        keys[base] = width
        keys[base + 1] = height
        keys[base + 2] = cut
        keys[base + 3] = scope.density
        margins[slot] = margin
        return slot
    }

    fun close() {
        for (image in images) image?.close()
        images.fill(null)
        frames.fill(null)
        lastUsed.fill(0)
        uses = 0L
    }

    companion object {
        private const val CAPACITY = 8
        private const val MAX_PIXELS = 4L * 1_024 * 1_024

        /** More than half the widest halo stroke (24 dp at the board card's scale), in dp. */
        private const val HALO_REACH_DP = 13f
    }
}
