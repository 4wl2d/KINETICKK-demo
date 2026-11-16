// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.interaction.canvas.fx

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.TileMode
import kinetickk.ball.gameplay.interaction.fx.PixelStrip

/** Repeated 4×4 pixel fills from sparse to dense. */
internal object EffectDither {
    private val bands = arrayOf(
        tile("#...", "....", "..#.", "...."),
        tile("#.#.", "....", ".#.#", "...."),
        tile("#.#.", ".#.#", "#.#.", ".#.#"),
        tile("####", "#.#.", "####", ".#.#"),
    )

    fun shader(level: Int): Shader? = bands.getOrNull(level)
}

internal object EffectLinks {
    private val lightning = arrayOf(
        tile("....+...........", "...#+#....+.....", "++#+.##...+#...+", "......#+.##+###.", ".......++......."),
        tile("..........+#....", ".....+....##+...", "#++.+#...#+..###", "...#+##++.......", ".......+........"),
        tile("...++...........", "..#+##..++......", "+#...+##..+...#+", "...........+#+..", "............+..."),
    )
    private val ember = tile("...#.......#....", "..+....#..+....#", "++##+++##++##++.", ".#...........#..", "................")
    private val frost = tile("...+........+...", "...#......##....", "++++#+##+++#++++", "..#+...#..+#....", "...+.......+....")
    private val beam = tile("~~~~~~~~~~~~~~~~", "##++##++##++##++", "++++++++++++++++", "##++##++##++##++", "~~~~~~~~~~~~~~~~")
    private val sight = tile("................", "..+.......+.....", "++++#...++++#...", "..+.......+.....", "................")
    private val tether = tile("................", "..+.......+.....", "++#+++++#+#+++++", "......+.......+.", "................")

    private val inks = Array(PixelStrip.entries.size) { index -> family(PixelStrip.entries[index]) }

    fun shader(strip: PixelStrip, frame: Int): Shader = when (val ink = inks[strip.ordinal]) {
        null -> lightning[frame % lightning.size]
        else -> ink
    }

    private fun family(strip: PixelStrip): Shader? = when (strip) {
        PixelStrip.LINK_COIL, PixelStrip.LINK_BOLT, PixelStrip.LINK_TELE_WINDUP -> null
        PixelStrip.LINK_EMBER, PixelStrip.LINK_PLACE_EMBER, PixelStrip.LINK_PLACE_FLARE -> ember
        PixelStrip.LINK_FROST, PixelStrip.LINK_FROST_EDGE, PixelStrip.LINK_MARK_BRITTLE, PixelStrip.LINK_MARK_FROZEN -> frost
        PixelStrip.LINK_BEAM, PixelStrip.LINK_TELE_LANE, PixelStrip.LINK_PLACE_LANCE_BEAM, PixelStrip.LINK_PLACE_LANCE_CORE -> beam
        PixelStrip.LINK_TELE_SIGHT, PixelStrip.LINK_ANCHOR_IDLE, PixelStrip.LINK_PLACE_LANCE_EDGE -> sight
        else -> tether
    }
}

private fun tile(vararg rows: String): Shader = ImageShader(effectBitmap(rows), TileMode.Repeated, TileMode.Repeated)

/** Alpha distinguishes soft edges. Red and green are the two tint channels consumed by [PixelPalettes]. */
internal fun effectBitmap(rows: Array<out String>): ImageBitmap {
    val width = rows.first().length
    val image = ImageBitmap(width, rows.size)
    val canvas = Canvas(image)
    val paint = Paint().apply { isAntiAlias = false }
    for (y in rows.indices) {
        val row = rows[y]
        require(row.length == width)
        for (x in row.indices) {
            paint.color = when (row[x]) {
                '+' -> Color.Green
                '#' -> Color.Red
                '~' -> Color.Red.copy(alpha = 0.45f)
                else -> continue
            }
            canvas.drawRect(x.toFloat(), y.toFloat(), x + 1f, y + 1f, paint)
        }
    }
    return image
}
