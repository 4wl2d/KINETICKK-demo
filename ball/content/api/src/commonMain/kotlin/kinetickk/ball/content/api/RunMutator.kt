// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.content.api

/** A cost an Omen can add to this run. */
enum class RunMutator(val displayName: String, val description: String) {
    PACK_HUNTERS("Pack Hunters", "Enemies arrive in larger packs."),
    HARDENED_ELITES("Hardened Elites", "Elites carry more integrity and arrive with escorts."),
    VOLATILE_REMAINS("Volatile Remains", "Ordinary enemies burst shortly after they die."),
    FRACTURED_SPACE("Fractured Space", "Space holds more obstacles, and some asteroids are unstable."),
    BLACKOUT("Blackout", "Data fades sooner and the minimap sees less."),
}

/** Extra members of enemy packs while the Pack Hunters Omen cost is active. */
const val PACK_HUNTERS_PACK_SIZE_BONUS: Int = 1
