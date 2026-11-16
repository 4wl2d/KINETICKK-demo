// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.content.api

import kinetickk.foundation.common.localization.AppLanguage

/** The kinds of numbers an active effect's card lines list, each with its English and Russian template. */
enum class SkillLineKind(private val english: String, private val russian: String) {
    RANK("Rank {0}", "Ранг {0}"),
    COOLDOWN("Cooldown {0} s", "Перезарядка {0} с"),
    RADIUS("Radius {0}", "Радиус {0}"),
    DURATION("Duration {0} s", "Длительность {0} с"),
    DAMAGE("Damage {0}", "Урон {0}"),
    STACKS("Stacks {0}", "Слои {0}"),
    ;

    fun text(language: AppLanguage, value: String): String =
        (if (language == AppLanguage.Russian) russian else english).replace("{0}", value)
}

/**
 * The card lines of the active effect at [rank] (the carrying item's stacks): its rank and cooldown, then its radius,
 * duration, damage and stack values in the order its values list them. Pure presentation of content numbers; damage
 * reads at [numberScale] (1 for base values).
 */
fun SkillDefinition.rankLines(rank: Int, language: AppLanguage, numberScale: Double = 1.0): List<String> {
    val values = rank(rank).values
    return buildList {
        add(SkillLineKind.RANK.text(language, rank.coerceIn(1, ranks.size).toString()))
        add(SkillLineKind.COOLDOWN.text(language, language.skillNumber(rank(rank).cooldownSeconds)))
        id.cardValues().forEach { (index, kind) ->
            values.getOrNull(index)?.let { value ->
                val shown = if (kind == SkillLineKind.DAMAGE) language.amount(value.toDouble(), 2, numberScale) else language.skillNumber(value)
                add(kind.text(language, shown))
            }
        }
    }
}

/** Whether the effect's card lines include a damage value, which a run shows at its number scale. */
val SkillDefinition.showsDamage: Boolean get() = id.cardValues().any { (_, kind) -> kind == SkillLineKind.DAMAGE }

/** At most two decimals, trailing zeros dropped, in the language's number style. */
private fun AppLanguage.skillNumber(value: Float): String = number(value.toDouble(), 2)

/** Which values of a skill's rank its card shows, as (value index, kind), in the documented value order. */
private fun SkillId.cardValues(): List<Pair<Int, SkillLineKind>> = when (this) {
    SkillId.SOLAR_FLARE -> listOf(1 to SkillLineKind.RADIUS, 2 to SkillLineKind.DAMAGE, 4 to SkillLineKind.STACKS)
    SkillId.FROST_LINE -> listOf(1 to SkillLineKind.DURATION, 3 to SkillLineKind.STACKS)
    SkillId.FROST_SNAP -> listOf(0 to SkillLineKind.RADIUS, 1 to SkillLineKind.STACKS)
    SkillId.ARC_HOOK -> listOf(1 to SkillLineKind.STACKS, 4 to SkillLineKind.DAMAGE)
    SkillId.MASS_DRIVER -> listOf(0 to SkillLineKind.DURATION)
    SkillId.ENTROPIC_TIDE -> listOf(0 to SkillLineKind.RADIUS)
    SkillId.SONIC_BOOM -> listOf(1 to SkillLineKind.DAMAGE, 2 to SkillLineKind.STACKS)
    SkillId.VOID_STEP -> listOf(1 to SkillLineKind.DAMAGE, 2 to SkillLineKind.STACKS)
    SkillId.SLINGSHOT -> listOf(3 to SkillLineKind.DURATION)
    SkillId.FULL_STOP -> listOf(0 to SkillLineKind.RADIUS)
    SkillId.CATALYZE -> listOf(0 to SkillLineKind.RADIUS)
}
