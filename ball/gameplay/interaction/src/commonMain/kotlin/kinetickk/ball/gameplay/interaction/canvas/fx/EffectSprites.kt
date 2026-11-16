// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.interaction.canvas.fx

import kinetickk.ball.gameplay.interaction.fx.PixelStrip

/** Small pixel drawings for the demo. A plus marks the bright part of a shape. */
internal object EffectSprites {
    private val spark = Sprite(
        "....#....",
        "....#....",
        ".#..+..#.",
        "..#.+.#..",
        "###+++###",
        "..#.+.#..",
        ".#..+..#.",
        "....#....",
        "....#....",
    )
    private val shard = Sprite(
        ".........",
        "......##.",
        ".....#+#.",
        "....#++#.",
        "...#++##.",
        "..#++##..",
        ".#++##...",
        ".####....",
        ".........",
    )
    private val flame = Sprite(
        "....#....",
        "....##...",
        "...#+#...",
        "...#++#..",
        "..##++#..",
        ".##+#++#.",
        ".#++++##.",
        "..####...",
        ".........",
    )
    private val flare = Sprite(
        ".....##....",
        ".##..#+....",
        "..#+.#+.##.",
        "...####+#+.",
        "..#++..##..",
        "++#+....###",
        "###+....+##",
        "..#+#..+#..",
        ".#+###++##.",
        "..#..#+..#.",
        ".....##....",
    )
    private val ion = Sprite(
        ".....#.....",
        ".#...##...#",
        ".##...#+###",
        "...#+..#...",
        "....###....",
        "###.+++##..",
        "..##+++#.##",
        "...#..##..#",
        "..#....+...",
        ".##....##..",
        ".#......#..",
    )
    private val vortex = Sprite(
        "....####...",
        "..###..##..",
        ".##.....##.",
        ".#..+++..#.",
        "#..+##+....",
        "#..+#+#...#",
        "....+##+..#",
        ".#..+++..#.",
        ".##.....##.",
        "..##..###..",
        "...####....",
    )
    private val erosion = Sprite(
        "...#####...",
        ".##+...##..",
        "###.....+#.",
        ".#.......+.",
        "#.........#",
        "+#........#",
        "#.........#",
        ".+........#",
        ".##.....###",
        "..##...+##.",
        "...#####...",
    )
    private val burst = Sprite(
        "..#.....#..",
        "...##.##...",
        ".###...###.",
        ".##.....##.",
        "...+++++...",
        "#..+++++..#",
        "...+++++...",
        ".##.....##.",
        ".###...###.",
        "...##.##...",
        "..#.....#..",
    )
    private val frost = Sprite(
        "..#.+.#..",
        "...#+#...",
        "##..+..##",
        "..#.+.#..",
        ".#+++++#.",
        "..#.+.#..",
        "##..+..##",
        "...#+#...",
        "..#.+.#..",
    )
    private val bolt = Sprite(
        "......##.",
        ".....##..",
        "....##...",
        "...#+##..",
        "..#+++##.",
        ".....+#..",
        "....+#...",
        "...##....",
        "..##.....",
    )
    private val ripple = Sprite(
        "..#####..",
        ".##...##.",
        "##.###.##",
        "#.##.##.#",
        "#.#.+.#.#",
        "#.##.##.#",
        "##.###.##",
        ".##...##.",
        "..#####..",
    )
    private val gate = Sprite(
        "..#...#..",
        ".#+#.#++.",
        "##.....##",
        "#.......#",
        "#...+...#",
        "#.......#",
        "##.....##",
        ".++#.#+#.",
        "..#...#..",
    )
    private val wave = Sprite(
        "...#+....",
        "....#+...",
        "#....#+..",
        ".#....#+.",
        ".....+.+#",
        ".#....#+.",
        "#....#+..",
        "....#+...",
        "...#+....",
    )
    private val frostBeam = Sprite(
        ".............#.",
        "...........###.",
        ".......###++#..",
        "....###+++##...",
        ".###+++###.....",
        "##++###........",
        "##.............",
    )
    private val slingshot = Sprite(
        ".......#...",
        "....###++..",
        "..###....+.",
        ".##......++",
        "##.....####",
        "#.......##.",
        ".........#.",
    )
    private val brake = Sprite(
        "###.....###",
        "#.........#",
        "#.........#",
        "....###....",
        "...#+++#...",
        "...#+.+#...",
        "...#+++#...",
        "....###....",
        "#.........#",
        "#.........#",
        "###.....###",
    )
    private val guard = Sprite(
        "..#####..",
        ".#+++++#.",
        "#++...++#",
        "#+.....+#",
        "#+.....+#",
        ".#+...+#.",
        ".#+...+#.",
        "..#+.+#..",
        "...###...",
    )
    private val debris = Sprite(
        "..#....#.",
        ".........",
        ".....+...",
        "#........",
        "......#..",
        ".........",
        "..##....+",
        ".........",
        "......#..",
    )
    private val slash = Sprite(
        "...........####",
        ".........##++#.",
        "......###++##..",
        "....###++##....",
        ".###+++###.....",
        "##++###........",
        "####...........",
    )
    private val bead = Sprite(
        "..~..",
        ".~#..",
        ".#++.",
        "..##~",
        "...~.",
    )
    private val wake = Sprite(
        "...~.............",
        ".........~.......",
        "......~#......~..",
        "...........~.....",
        "....~.........~~.",
        ".......~+........",
        "..~........~.....",
        "..............~..",
        ".....~...........",
    )
    private val rail = Sprite(
        "..#..",
        "..+..",
        "#+++#",
        "..+..",
        "..#..",
    )
    private val dot = Sprite(
        ".#.",
        "#+#",
        ".#.",
    )

    private val families = Array(PixelStrip.entries.size) { index -> family(PixelStrip.entries[index]) }

    fun of(strip: PixelStrip, frame: Int, aimed: Boolean): Sprite {
        val primary = families[strip.ordinal]
        val finalTransientFrame = frame > 0 && frame == strip.frames - 1 &&
            (primary === spark || primary === shard || primary === flare || primary === burst)
        if (finalTransientFrame) return debris
        return if (aimed && primary === dot) wave else primary
    }

    private fun family(strip: PixelStrip): Sprite {
        val name = strip.name
        return when {
            strip == PixelStrip.SLASH -> slash
            strip == PixelStrip.SKILL_FROST_LINE -> frostBeam
            strip == PixelStrip.SKILL_SLINGSHOT || strip == PixelStrip.SKILL_SLINGSHOT_D -> slingshot
            strip == PixelStrip.SKILL_FULL_STOP -> brake
            strip == PixelStrip.SKILL_CATALYZE -> ripple
            name.startsWith("CORE_RIBBON_") -> bead
            strip == PixelStrip.TRAIL_WAKE || strip == PixelStrip.TRAIL_WAKE_WIDE -> wake
            strip == PixelStrip.CORE_RAIL -> rail
            strip == PixelStrip.KILL_ELITE || strip == PixelStrip.DEATH_LARGE || strip == PixelStrip.CORE_DEATH -> burst
            strip == PixelStrip.THERMAL_BREAK || strip == PixelStrip.SKILL_SOLAR_FLARE -> flare
            strip == PixelStrip.ION_BREAK || strip == PixelStrip.ION_MOTE -> ion
            name.contains("GRAVITIC") || name.contains("ATTRACTOR") || name.contains("ROT_WELL") ||
                name.contains("ENTROPIC_TIDE") || name.contains("MASSDRIVER") || name.contains("MASS_DRIVER") -> vortex
            name.contains("ENTROPY") || name.contains("DARKSTAR") || name.contains("SPORED") -> erosion
            name.contains("SONIC_BREAK") || name.contains("SONIC_MOTE") || name.contains("TIMEBUBBLE") -> ripple
            name.contains("PHASED") -> gate
            name.contains("SKATE") || name.contains("WEAVE") -> frost
            name.contains("PRIMED") || name.contains("PLASMA") -> spark
            name.contains("COIL") -> bolt
            name.contains("BLASTJUMP") -> wave
            name.contains("FLARE") || name.contains("EMBER") || name.contains("THERMAL") ||
                name.contains("BLACKFIRE") || name.contains("ABLAZE") || name.contains("VENT_LICK") -> flame
            name.contains("FROST") || name.contains("CRYO") || name.contains("FROZEN") ||
                name.contains("ICE") || name.contains("GLACIER") -> frost
            name.contains("BOLT") || name.contains("SHOCK") || name.contains("ION_BREAK") ||
                name.contains("ION_MOTE") || name.contains("ARC_HOOK") || name.contains("CONDUCT") -> bolt
            name.contains("SHIELD") || name.contains("GUARD") || name.contains("WARD") -> guard
            name.contains("VOID") || name.contains("RIFT") || name.contains("NULL") ||
                name.contains("STASIS") || name.contains("SILENCE") -> gate
            name.contains("SONIC") || name.contains("WINDUP") || name.contains("REACH") ||
                name.contains("WAVE_EDGE") -> wave
            name.endsWith("PIP") || name == "TRAIL_DOT" || name == "TRAIL_EDGE" -> dot
            name.contains("SHARD") || name.contains("PIECE") || name.contains("CRACK") ||
                name.contains("TOOTH") || name.contains("SPIRE") || name == "SLASH" -> shard
            name.contains("KILL") || name.contains("DEATH") || name.contains("HIT") ||
                name.startsWith("SPARK") || name.contains("GLINT") || name == "CRIT" -> spark
            strip.w >= 24 && strip.h >= 24 -> ripple
            else -> dot
        }
    }
}

internal class Sprite(vararg rows: String) {
    val width = rows.first().length
    val height = rows.size
    val bitmap = effectBitmap(rows)
}
