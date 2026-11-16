// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.interaction.canvas.fx

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import kinetickk.foundation.design.Kk
import kotlin.math.roundToInt

/** Uppercase 5 × 7 letterforms and numerals for the brief combat labels. */
private val CombatLetters = arrayOf(
    "0 ..##./.##.#/##..#/##.##/#..##/#.##./.##..",
    "1 ..##./.###./..##./..##./..##./..##./.####",
    "2 .###./#..##/...##/..##./.##../##..#/#####",
    "3 .###./#..##/...##/..##./...##/#..##/.###.",
    "4 ..##./.###./##.#./#####/...#./...#./...#.",
    "5 #####/##.../##.../####./...##/...##/####.",
    "6 ..##./.##../##.../####./##.##/##..#/.###.",
    "7 #####/#..##/...##/..##./..#../.##../.##..",
    "8 .###./#..##/##..#/.###./#..##/##..#/.###.",
    "9 .###./##..#/##.##/.####/...##/..##./.##..",
    "A ..##./.####/##..#/##.##/#####/##.##/#...#",
    "B ####./##.##/##.##/####./##.##/##.##/####.",
    "C .###./##..#/##.../##.../##.../##..#/.###.",
    "D ####./##..#/##.##/##.##/##.##/##..#/###..",
    "E #####/##..#/##.../####./##.../##..#/#####",
    "F #####/##..#/##.../####./##.../##.../#....",
    "G .###./##..#/##.../##.##/##.##/##.##/.###.",
    "H #...#/##.##/##.##/#####/##.##/##.##/#...#",
    "I #####/..#../..#../..#../..#../..#../#####",
    "J ..###/...##/...##/...##/...##/##.##/.###.",
    "K ##..#/##.##/####./###../####./##.##/##..#",
    "L ##.../##.../##.../##.../##.../##.../#####",
    "M #...#/##.##/#####/#.#.#/#...#/#...#/#...#",
    "N #...#/##..#/###.#/#.###/#..##/#...#/#...#",
    "O .###./##..#/##.##/##.##/##.##/#..##/.###.",
    "P ####./##..#/##.##/###../##.../##.../#....",
    "Q .###./##.##/##.##/##.##/##.##/.###./....#",
    "R ####./##.##/##.##/####./###../##.#./##.##",
    "S .####/##.../##.../.###./...##/...##/####.",
    "T #####/..#../..#../..#../..#../..#../..#..",
    "U #...#/##.##/##.##/##.##/##.##/##..#/.###.",
    "V #...#/#...#/#...#/.#.#./.#.#./..#../..#..",
    "W #...#/#...#/#...#/#.#.#/#.#.#/#####/.#.#.",
    "X #...#/#...#/.#.#./..#../.#.#./#...#/#...#",
    "Y #...#/#...#/.#.#./..#../..#../..#../..#..",
    "Z #####/....#/...#./..#../.#.../#..../#####",
    "Б #####/##.../##.../####./##.##/##.##/####.",
    "Г #####/##.../##.../##.../##.../##.../##...",
    "Д .###./.#.#./.#.#./.#.#./.#.#./#####/#...#",
    "Ё .#.#./...../#####/#..../####./#..../#####",
    "Ж #.#.#/#.#.#/.###./..#../.###./#.#.#/#.#.#",
    "З ####./....#/....#/.###./....#/....#/####.",
    "И #...#/#...#/#..##/#.#.#/##..#/#...#/#...#",
    "Й .#.#./..#../#...#/#..##/#.#.#/##..#/#...#",
    "Л ..###/.##.#/.#..#/.#..#/.#..#/.#..#/##..#",
    "П #####/##.##/##.##/##.##/##.##/##.##/##.##",
    "У #...#/#...#/.#.#./..#../..#../.#.../##...",
    "Ф ..#../.###./#.#.#/#.#.#/#.#.#/.###./..#..",
    "Ц #..#./#..#./#..#./#..#./#..#./#####/....#",
    "Ч #...#/#...#/#...#/.####/....#/....#/....#",
    "Ш #.#.#/#.#.#/#.#.#/#.#.#/#.#.#/#.#.#/#####",
    "Щ #.#.#/#.#.#/#.#.#/#.#.#/#.#.#/#####/....#",
    "Ъ ##.../.#.../.#.../.###./.#..#/.#..#/.###.",
    "Ы #...#/#...#/#...#/###.#/#.#.#/#.#.#/###.#",
    "Ь #..../#..../#..../####./#...#/#...#/####.",
    "Э .###./#...#/....#/..###/....#/#...#/.###.",
    "Ю #.##./##..#/##..#/###.#/##..#/##..#/#.##.",
    "Я .####/##.##/##.##/.####/..###/.#.##/##.##",
    "? .###./#...#/...#./..#../..#../...../..#..",
    "- ...../...../...../#####/...../...../.....",
    ". ...../...../...../...../...../...../##...",
    ", ...../...../...../...../...../.##../##...",
)

internal const val COMBAT_LETTER_HEIGHT = 7
private const val LETTER_ADVANCE = 6
private const val SPACE_ADVANCE = 3
private const val SLANT_REACH = 2

/** Layout and paths stay in texels; the caller places each label on the device pixel grid. */
internal fun combatLabelWidth(text: String, slanted: Boolean = false): Int {
    var width = 0
    for (char in text) width += if (char.isWhitespace()) SPACE_ADVANCE else LETTER_ADVANCE
    return (width - 1).coerceAtLeast(0) + if (slanted) SLANT_REACH else 0
}

private class CombatLetter(pattern: String, slanted: Boolean) {
    val face = Path()
    val silhouette = Path()

    init {
        val rows = pattern.split('/')
        for (row in rows.indices) {
            val shift = if (slanted) (COMBAT_LETTER_HEIGHT - 1 - row) / 3 else 0
            var col = 0
            while (col < rows[row].length) {
                if (rows[row][col] != '#') {
                    col++
                    continue
                }
                val first = col
                while (col < rows[row].length && rows[row][col] == '#') col++
                val left = (first + shift).toFloat()
                val right = (col + shift).toFloat()
                val top = row.toFloat()
                face.addRect(Rect(left, top, right, top + 1f))
                silhouette.addRect(Rect(left - 1f, top - 1f, right + 1f, top + 2f))
            }
        }
    }
}

private object CombatType {
    private val upright = arrayOfNulls<CombatLetter>('Я'.code + 1)
    private val oblique = arrayOfNulls<CombatLetter>(upright.size)
    val paint = Paint().apply { isAntiAlias = false }

    init {
        for (entry in CombatLetters) {
            val pattern = entry.substring(2)
            val index = entry[0].code
            upright[index] = CombatLetter(pattern, slanted = false)
            oblique[index] = CombatLetter(pattern, slanted = true)
        }
    }

    fun letter(char: Char, slanted: Boolean): CombatLetter {
        val ch = char.uppercaseChar()
        val key = when (ch) {
            '−', '–' -> '-'
            'А' -> 'A'
            'В' -> 'B'
            'Е' -> 'E'
            'К' -> 'K'
            'М' -> 'M'
            'Н' -> 'H'
            'О' -> 'O'
            'Р' -> 'P'
            'С' -> 'C'
            'Т' -> 'T'
            'Х' -> 'X'
            else -> ch
        }
        val letters = if (slanted) oblique else upright
        return letters.getOrNull(key.code) ?: checkNotNull(letters['?'.code])
    }
}

/** Warm the glyph paths before the first combat frame. */
internal fun prepareCombatPixelText() {
    CombatType.letter('0', slanted = false)
}

/** Draw the dark outline and ink face as separate passes so adjacent glyphs stay legible. */
internal fun DrawScope.drawCombatLabel(
    text: String,
    left: Float,
    top: Float,
    step: Int,
    color: Color,
    slanted: Boolean = false,
    silhouetteOnly: Boolean = false,
) {
    val canvas = drawContext.canvas
    canvas.save()
    canvas.translate(left.roundToInt().toFloat(), top.roundToInt().toFloat())
    canvas.scale(step.toFloat(), step.toFloat())
    val outline = if (silhouetteOnly) color else Kk.Ink
    canvas.drawCombatLetters(text, outline, slanted, silhouette = true)
    if (!silhouetteOnly) canvas.drawCombatLetters(text, color, slanted, silhouette = false)
    canvas.restore()
}

private fun Canvas.drawCombatLetters(text: String, color: Color, slanted: Boolean, silhouette: Boolean) {
    val paint = CombatType.paint
    paint.color = color
    save()
    for (char in text) {
        if (char.isWhitespace()) {
            translate(SPACE_ADVANCE.toFloat(), 0f)
        } else {
            val glyph = CombatType.letter(char, slanted)
            drawPath(if (silhouette) glyph.silhouette else glyph.face, paint)
            translate(LETTER_ADVANCE.toFloat(), 0f)
        }
    }
    restore()
}
