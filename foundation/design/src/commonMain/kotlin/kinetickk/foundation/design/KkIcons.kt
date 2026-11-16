// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.foundation.design

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser

/** Stroke and fill styles for icon layers. */
enum class KkIconStyle {
    /** 2-unit stroke, square caps, miter joins. */
    STROKE,

    /** Solid fill. */
    FILL,

    /** 2-unit stroke dashed 3/3. */
    STROKE_DASHED,

    /** 2-unit stroke dotted 1/4. */
    STROKE_DOTTED,
}

/** One SVG path layer on the 24-unit icon grid. */
data class KkIconLayer(val d: String, val style: KkIconStyle)

/** SVG path layers for demo interface icons. [key] links an icon to its gameplay use. */
enum class KkIcon(val key: String, vararg layerData: KkIconLayer) {
    FORMS_CIRCLE(
        "forms.circle",
        KkIconLayer("M4 12a8 8 0 1 0 16 0a8 8 0 1 0 -16 0Z", KkIconStyle.STROKE),
    ),
    FORMS_SQUARE(
        "forms.square",
        KkIconLayer("M5 5h14v14h-14Z", KkIconStyle.STROKE),
    ),
    FORMS_TRIANGLE(
        "forms.triangle",
        KkIconLayer("M12 4 20.5 19h-17Z", KkIconStyle.STROKE),
    ),
    WEAPONS_FLUX_WAKE(
        "weapons.flux_wake",
        KkIconLayer("M2 9c3 0 3.5-3 6.5-3s3.5 3 6.5 3 3.5-3 7-3M2 15c3 0 3.5-3 6.5-3s3.5 3 6.5 3 3.5-3 7-3M2 20h8", KkIconStyle.STROKE),
    ),
    WEAPONS_MORNINGSTAR(
        "weapons.morningstar",
        KkIconLayer("M11 9a4 4 0 1 0 8 0a4 4 0 1 0 -8 0Z", KkIconStyle.STROKE),
        KkIconLayer("M15 2v2.5M15 13.5V16M8 9h2.5M19.5 9H22M10 14 3 21", KkIconStyle.STROKE),
    ),
    WEAPONS_PHASE_LATTICE(
        "weapons.phase_lattice",
        KkIconLayer("M12 3 21 12 12 21 3 12Z", KkIconStyle.STROKE),
        KkIconLayer("M7.5 7.5l9 9M16.5 7.5l-9 9", KkIconStyle.STROKE),
    ),
    WEAPONS_NULL_LANCE(
        "weapons.null_lance",
        // The detached head keeps the weapon glyph legible at icon size.
        KkIconLayer("M3 21 13 11M7 13l4 4M17.5 3 21 6.5 17.5 10 14 6.5Z", KkIconStyle.STROKE),
    ),
    WEAPONS_GRAVITY_MINES(
        "weapons.gravity_mines",
        KkIconLayer("M9 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0Z", KkIconStyle.FILL),
        KkIconLayer("M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0Z", KkIconStyle.STROKE_DASHED),
    ),
    WEAPONS_ARC_COIL(
        "weapons.arc_coil",
        KkIconLayer("M2 12l3-6 3 12 3-12 3 12 3-12 3 12 2-6", KkIconStyle.STROKE),
    ),
    WEAPONS_GYRE_DISC(
        "weapons.gyre_disc",
        KkIconLayer("M4 9a8 3 0 1 0 16 0a8 3 0 1 0 -16 0Z", KkIconStyle.STROKE),
        KkIconLayer("M5 15a7 5 0 0 0 14 0", KkIconStyle.STROKE_DASHED),
    ),
    WEAPONS_SCATTER_BLOOM(
        "weapons.scatter_bloom",
        KkIconLayer("M2 12a2 2 0 1 0 4 0a2 2 0 1 0 -4 0Z", KkIconStyle.FILL),
        KkIconLayer("M11 9h1M11 15h1M15.5 6h1M15.5 12h1M15.5 18h1M20 4h1M20 9.5h1M20 14.5h1M20 20h1", KkIconStyle.STROKE),
    ),
    SYSTEM_INTEGRITY(
        "system.integrity",
        KkIconLayer("M12 2 21 7v10l-9 5-9-5V7Z", KkIconStyle.STROKE),
    ),
    SYSTEM_SHIELD(
        "system.shield",
        KkIconLayer("M12 2 21 7v10l-9 5-9-5V7Z", KkIconStyle.STROKE_DASHED),
        KkIconLayer("M12 7 16.5 9.5v5L12 17l-4.5-2.5v-5Z", KkIconStyle.STROKE),
    ),
    SYSTEM_HEAT(
        "system.heat",
        KkIconLayer("M12 2c1 4 6 6 6 12a6 6 0 0 1-12 0c0-3 2-5 2-7 1.5 1 2.5 2.5 2.5 4.5C12 9 12 5 12 2Z", KkIconStyle.STROKE),
    ),
    SYSTEM_POLARITY(
        "system.polarity",
        KkIconLayer("M5 3v9a7 7 0 0 0 14 0V3h-4v9a3 3 0 0 1-6 0V3Z", KkIconStyle.STROKE),
    ),
    SYSTEM_OVERDRIVE(
        "system.overdrive",
        KkIconLayer("M3 17a9 9 0 0 1 18 0M12 17l6-6M3 20h18", KkIconStyle.STROKE),
    ),
    SYSTEM_DATA(
        "system.data",
        KkIconLayer("M12 3 20 12 12 21 4 12Z", KkIconStyle.STROKE),
        KkIconLayer("M12 8 16 12 12 16 8 12Z", KkIconStyle.FILL),
    ),
    SYSTEM_MATTER(
        "system.matter",
        KkIconLayer("M12 2 20 7v10l-8 5-8-5V7Z", KkIconStyle.STROKE),
        KkIconLayer("M4 7l8 5 8-5M12 12v10", KkIconStyle.STROKE),
    ),
    SYSTEM_KEY(
        "system.key",
        KkIconLayer("M3 12a4 4 0 1 0 8 0a4 4 0 1 0 -8 0Z", KkIconStyle.STROKE),
        KkIconLayer("M11 12h11M18 12v4M21 12v3", KkIconStyle.STROKE),
    ),
    SYSTEM_DASH(
        "system.dash",
        KkIconLayer("M2 7h7M2 12h9M2 17h7M13 12a4 4 0 1 0 8 0a4 4 0 1 0-8 0", KkIconStyle.STROKE),
    ),
    SYSTEM_BRAKE(
        "system.brake",
        KkIconLayer("M8 3h8l5 5v8l-5 5H8l-5-5V8Z", KkIconStyle.STROKE),
        KkIconLayer("M8 12h8", KkIconStyle.STROKE),
    ),
    SYSTEM_ELITE(
        "system.elite",
        KkIconLayer("M12 5 19 9v8l-7 4-7-4V9Z", KkIconStyle.STROKE),
        KkIconLayer("M5 9 3 3l5 3M19 9l2-6-5 3", KkIconStyle.STROKE),
    ),
    SYSTEM_DISMANTLED(
        "system.dismantled",
        KkIconLayer("M12 2 22 12 12 22 2 12Z", KkIconStyle.STROKE),
        KkIconLayer("M12 2l-2 7 4 3-3 10", KkIconStyle.STROKE),
    ),
    SYSTEM_ANOMALY(
        "system.anomaly",
        KkIconLayer("M12 2 22 12 12 22 2 12Z", KkIconStyle.STROKE),
        KkIconLayer("M12 7v6M12 16v1.5", KkIconStyle.STROKE),
    ),
    SYSTEM_CYCLE_CLOCK(
        "system.cycle_clock",
        KkIconLayer("M4 13a8 8 0 1 0 16 0a8 8 0 1 0 -16 0Z", KkIconStyle.STROKE),
        KkIconLayer("M12 9v4l3 2M9 2h6", KkIconStyle.STROKE),
    ),
    SYSTEM_REROLL(
        "system.reroll",
        KkIconLayer("M4 4h16v16H4ZM8 8h.01M16 16h.01M12 12h.01M16 8h.01M8 16h.01", KkIconStyle.STROKE),
    ),
    SYSTEM_LOCKED(
        "system.locked",
        KkIconLayer("M5 11h14v10h-14Z", KkIconStyle.STROKE),
        KkIconLayer("M8 11V7a4 4 0 0 1 8 0v4", KkIconStyle.STROKE),
    ),
    ASPECT_KINETIC(
        "aspect.kinetic",
        KkIconLayer("M12 3v4M12 17v4M3 12h4M17 12h4", KkIconStyle.STROKE),
        KkIconLayer("M10 12a2 2 0 1 0 4 0a2 2 0 1 0 -4 0Z", KkIconStyle.FILL),
    ),
    ASPECT_THERMAL(
        "aspect.thermal",
        KkIconLayer("M12 3c3 4 6 7 6 11a6 6 0 0 1 -12 0c0 -4 3 -7 6 -11Z", KkIconStyle.STROKE),
        KkIconLayer("M12 13c1.2 1.4 2 2.4 2 3.5a2 2 0 0 1 -4 0c0 -1.1 .8 -2.1 2 -3.5Z", KkIconStyle.FILL),
    ),
    ASPECT_CRYO(
        "aspect.cryo",
        KkIconLayer("M12 3v18M4.2 7.5l15.6 9M4.2 16.5l15.6 -9", KkIconStyle.STROKE),
    ),
    ASPECT_ION(
        "aspect.ion",
        KkIconLayer("M3 13l4 -7 5 12 5 -12 4 7", KkIconStyle.STROKE),
    ),
    ASPECT_GRAVITIC(
        "aspect.gravitic",
        KkIconLayer("M4.21 16.5a9 4 -30 1 0 15.58 -9a9 4 -30 1 0 -15.58 9Z", KkIconStyle.STROKE),
        KkIconLayer("M9 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0Z", KkIconStyle.FILL),
    ),
    ASPECT_ENTROPY(
        "aspect.entropy",
        KkIconLayer("M12 4a8 8 0 0 1 8 8M18 17.5a8 8 0 0 1 -9.5 2.5M4.6 15a8 8 0 0 1 2.4 -9.5", KkIconStyle.STROKE),
    ),
    ASPECT_SONIC(
        "aspect.sonic",
        KkIconLayer("M7.5 8.5a5 5 0 0 0 0 7M16.5 8.5a5 5 0 0 1 0 7M4.5 5.5a9 9 0 0 0 0 13M19.5 5.5a9 9 0 0 1 0 13", KkIconStyle.STROKE),
        KkIconLayer("M10.5 12a1.5 1.5 0 1 0 3 0a1.5 1.5 0 1 0 -3 0Z", KkIconStyle.FILL),
    ),
    ASPECT_VOID(
        "aspect.void",
        KkIconLayer("M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0Z", KkIconStyle.STROKE_DASHED),
        KkIconLayer("M9 11a3.5 3.5 0 1 0 7 0a3.5 3.5 0 1 0 -7 0Z", KkIconStyle.STROKE),
    ),
    TAG_RAM(
        "tag.ram",
        KkIconLayer("M5 6h7a6 6 0 0 1 0 12H5ZM2 12h3", KkIconStyle.STROKE),
    ),
    TAG_DRIVE(
        "tag.drive",
        KkIconLayer("M14 8a5 5 0 1 1 0 10a5 5 0 1 1 0 -10ZM2 10h7M2 14h6M4 18h5", KkIconStyle.STROKE),
    ),
    TAG_ANCHOR(
        "tag.anchor",
        KkIconLayer("M12 4v16M8 8h8M5 14a7 7 0 0 0 14 0", KkIconStyle.STROKE),
    ),
    TAG_ARSENAL(
        "tag.arsenal",
        KkIconLayer("M5 20h14M8 17V11M12 17V7M16 17V11", KkIconStyle.STROKE),
    ),
    TAG_PRECISION(
        "tag.precision",
        KkIconLayer("M4 9V4h5M15 4h5v5M20 15v5h-5M9 20H4v-5", KkIconStyle.STROKE),
        KkIconLayer("M10.5 12a1.5 1.5 0 1 0 3 0a1.5 1.5 0 1 0 -3 0Z", KkIconStyle.FILL),
    ),
    TAG_BULWARK(
        "tag.bulwark",
        KkIconLayer("M12 3l8 3v6c0 5 -3.5 8 -8 9c-4.5 -1 -8 -4 -8 -9V6Z", KkIconStyle.STROKE),
    ),
    TAG_OVERCLOCK(
        "tag.overclock",
        KkIconLayer("M4 16a8 8 0 1 1 16 0M12 16l4 -6", KkIconStyle.STROKE),
    ),
    TAG_CHAIN(
        "tag.chain",
        KkIconLayer("M9 9H7a3 3 0 0 0 0 6h3M15 9h2a3 3 0 0 1 0 6h-3M9 12h6", KkIconStyle.STROKE),
    ),
    TAG_FORTUNE(
        "tag.fortune",
        KkIconLayer("M12 3l2.6 5.6 6.1 .7 -4.5 4.2 1.2 6L12 16.5l-5.4 3 1.2 -6 -4.5 -4.2 6.1 -.7Z", KkIconStyle.STROKE),
    ),
    UI_PLUS(
        "ui.plus",
        KkIconLayer("M12 7v10M7 12h10", KkIconStyle.STROKE),
    ),
    ;

    /** Layers in drawing order. */
    val layers: List<KkIconLayer> = layerData.toList()

    companion object {
        /** Icon for a key such as `"weapons.flux_wake"`, or null. */
        fun byKey(key: String): KkIcon? = entries.firstOrNull { it.key == key }

        /** Core form icons in [KkFormGeometry] order. */
        val Forms: List<KkIcon> = entries.filter { it.key.startsWith("forms.") }

        /** Aspect icons in relic-aspect order, beginning with kinetic. */
        val Aspects: List<KkIcon> = entries.filter { it.key.startsWith("aspect.") }

        /** The seven status element glyphs (thermal, cryo, ion, gravitic, entropy, sonic, void). */
        val Elements: List<KkIcon> = Aspects.drop(1)

        /** Archetype tag glyphs in tag order: ram, drive, anchor, arsenal, precision, bulwark, overclock, chain, fortune. */
        val Tags: List<KkIcon> = entries.filter { it.key.startsWith("tag.") }
    }
}

/** Icon grid size. */
const val KK_ICON_GRID = 24f

/**
 * Draws [icon] centered at [center] scaled to [size] px (the 24-unit grid maps to [size]).
 * [strokeWidth] is in grid units (2 = `.ic`, 1.5 = `.ic-thin`). Paths are parsed once per icon and
 * strokes are cached, so this is allocation-free and safe for HUD/world use.
 */
fun DrawScope.drawKkIcon(
    icon: KkIcon,
    center: Offset,
    size: Float,
    color: Color,
    alpha: Float = 1f,
    strokeWidth: Float = 2f,
) {
    if (size <= 0f || alpha <= 0f) return
    val paths = KkIconPaths.of(icon)
    val scale = size / KK_ICON_GRID
    translate(center.x - size * 0.5f, center.y - size * 0.5f) {
        scale(scale, scale, Offset.Zero) {
            for (index in paths.indices) {
                val style = when (icon.layers[index].style) {
                    KkIconStyle.FILL -> Fill
                    KkIconStyle.STROKE -> KkIconStrokes.solid(strokeWidth)
                    KkIconStyle.STROKE_DASHED -> KkIconStrokes.dashed(strokeWidth)
                    KkIconStyle.STROKE_DOTTED -> KkIconStrokes.dotted(strokeWidth)
                }
                drawPath(paths[index], color, alpha, style)
            }
        }
    }
}

/** Prepares the normalized paths and common strokes retained by every icon draw. UI thread only. */
fun prepareInterfaceIcons() {
    for (icon in KkIcon.entries) KkIconPaths.of(icon)
    KkIconStrokes.solid(2f)
    KkIconStrokes.solid(1.5f)
}

/** Parsed icon paths, one array per icon, prepared at startup or on first standalone draw. */
internal object KkIconPaths {
    private val cache = arrayOfNulls<Array<Path>>(KkIcon.entries.size)

    fun of(icon: KkIcon): Array<Path> = cache[icon.ordinal] ?: Array(icon.layers.size) { index ->
        PathParser().parsePathString(icon.layers[index].d).toPath()
    }.also { cache[icon.ordinal] = it }
}

private object KkIconStrokes {
    // Dash intervals are in grid units: the draw transform scales them with the icon.
    private val dashEffect: PathEffect by lazy { PathEffect.dashPathEffect(floatArrayOf(3f, 3f)) }
    private val dotEffect: PathEffect by lazy { PathEffect.dashPathEffect(floatArrayOf(1f, 4f)) }
    private val defaults: Array<Stroke> by lazy { create(2f) }
    // A few custom widths at once (band pips, card watermarks, thin glyphs) without thrashing.
    private val customWidths = FloatArray(4) { Float.NaN }
    private val customs = arrayOfNulls<Array<Stroke>>(4)
    private var customNext = 0

    fun solid(w: Float): Stroke = set(w)[0]
    fun dashed(w: Float): Stroke = set(w)[1]
    fun dotted(w: Float): Stroke = set(w)[2]

    private fun set(w: Float): Array<Stroke> {
        if (w == 2f) return defaults
        for (index in 0 until 4) if (customWidths[index] == w) return customs[index]!!
        return create(w).also {
            customs[customNext] = it
            customWidths[customNext] = w
            customNext = (customNext + 1) % 4
        }
    }

    private fun create(w: Float): Array<Stroke> = arrayOf(
        Stroke(w, miter = 4f, cap = StrokeCap.Square, join = StrokeJoin.Miter),
        Stroke(w, miter = 4f, cap = StrokeCap.Square, join = StrokeJoin.Miter, pathEffect = dashEffect),
        Stroke(w, miter = 4f, cap = StrokeCap.Square, join = StrokeJoin.Miter, pathEffect = dotEffect),
    )
}
