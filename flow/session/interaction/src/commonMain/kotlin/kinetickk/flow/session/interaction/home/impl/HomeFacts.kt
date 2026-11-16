// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.flow.session.interaction.home.impl

import kinetickk.ball.content.api.localizedContent
import kinetickk.flow.session.interaction.home.api.HomeUiModel
import kinetickk.flow.session.interaction.localization.SessionRedesignText
import kinetickk.flow.session.interaction.localization.SessionText
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.common.localization.text

/** Facts card content: the item's label, one big value, facts from real data and its (!) text. */
internal class HomeFacts(
    val label: String,
    val big: String,
    val facts: List<Pair<String, String>>,
    val info: String,
    val sub: String?,
    val stamp: String?,
)

/** The stamp menu row [target] carries when the game's state calls for it (see [homeFacts]). */
internal fun homeMenuStampText(target: HomeLayoutTarget, language: AppLanguage): String? = when (target) {
    HomeLayoutTarget.WORKSHOP -> language.text(SessionRedesignText.AFFORDABLE)
    else -> null
}

internal fun homeFacts(model: HomeUiModel, target: HomeLayoutTarget, language: AppLanguage, textScale: Float): HomeFacts {
    val label = language.text(HomeMenuLabels[HomeMenuTargets.indexOf(target)])
    return when (target) {
        HomeLayoutTarget.WORKSHOP -> {
            val completion = homeWorkshopCompletion(model)
            HomeFacts(
                label,
                language.text(SessionRedesignText.PERCENT, completion),
                buildList {
                    add(language.text(SessionRedesignText.WEAPONS_FACT) to "${model.unlockedWeaponCount}/${model.weaponCount}")
                    if (model.matrixMaxRanks > 0) {
                        add(language.text(SessionRedesignText.RANKS) to "${model.matrixRanks}/${model.matrixMaxRanks}")
                    }
                    if (model.upgradeLevelCap > 0) {
                        add(language.text(SessionRedesignText.UPGRADES) to "${model.upgradeLevels}/${model.upgradeLevelCap}")
                    }
                },
                language.text(SessionRedesignText.WORKSHOP_INFO),
                language.text(SessionRedesignText.PERCENT, completion),
                if (model.workshopAffordable) homeMenuStampText(target, language) else null,
            )
        }
        HomeLayoutTarget.CODEX -> HomeFacts(
            label,
            model.discoveredItemCount.toString(),
            buildList {
                add(language.text(SessionText.ITEMS_TITLE) to "${model.discoveredItemCount}/${model.itemCount}")
                if (model.relicCount > 0) add(language.text(SessionText.RELICS_TITLE) to "${model.discoveredRelicCount}/${model.relicCount}")
                add(language.text(SessionText.FORMS_TITLE) to "${model.unlockedCoreShapes.size}/${model.coreShapes.size}")
            },
            language.text(SessionRedesignText.CODEX_INFO),
            "${model.discoveredItemCount}/${model.itemCount}",
            null,
        )
        HomeLayoutTarget.SETTINGS -> HomeFacts(
            label,
            language.code.uppercase(),
            listOf(
                language.text(SessionRedesignText.LANGUAGE) to language.nativeName,
                language.text(SessionRedesignText.TEXT_SIZE) to "${(textScale * 100f + 0.5f).toInt()}%",
            ),
            language.text(SessionRedesignText.SETTINGS_INFO),
            null,
            null,
        )
        else -> HomeFacts(
            label,
            language.text(SessionRedesignText.DEMO),
            buildList {
                add(language.text(SessionRedesignText.FORM) to model.coreShape(model.coreShape).displayName.localizedContent(language))
            },
            language.text(SessionRedesignText.DEMO_INFO),
            null,
            null,
        )
    }
}

/**
 * How much of the Workshop is bought, in whole percent (rounded down): owned weapons, Matrix ranks and
 * Refit / Honing levels over every weapon, rank and level it sells. Unread parts count as nothing.
 */
internal fun homeWorkshopCompletion(model: HomeUiModel): Int {
    val total = model.weaponCount.toLong() + model.matrixMaxRanks + model.upgradeLevelCap
    if (total <= 0L) return 0
    val owned = model.unlockedWeaponCount.toLong() + model.matrixRanks + model.upgradeLevels
    return (owned.coerceIn(0L, total) * 100L / total).toInt()
}

/** The (!) text of the form panel: the current character's mechanic name and what it does. */
internal fun homeFormInfo(model: HomeUiModel, language: AppLanguage): String {
    val definition = model.coreShape(model.coreShape)
    return definition.mechanicName.localizedContent(language) + "\n" + definition.mechanicDescription.localizedContent(language)
}
