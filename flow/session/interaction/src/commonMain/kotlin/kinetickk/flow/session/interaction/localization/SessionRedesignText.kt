// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.flow.session.interaction.localization

import kinetickk.foundation.common.localization.TextResource

/** Session copy added by the redesign: short labels, (!) explanations and the version line. */
internal enum class SessionRedesignText(
    override val english: String,
    override val russian: String,
) : TextResource {
    MATTER("Matter", "Материя"),
    FORM("Form", "Форма"),
    MAXED("Maxed", "На максимуме"),
    RANKS("Ranks", "Ранги"),
    DIRECTIVE("Directive", "Директива"),
    TIER("Tier", "Ступень"),
    RANK("Rank", "Ранг"),
    NEW_TIER("New tier", "Новая ступень"),
    SELECTED("Selected", "Выбрана"),
    STATUS("Status", "Статус"),
    READY("Ready", "Готово"),
    AFFORDABLE("Affordable", "Доступно"),
    UPGRADES("Upgrades", "Улучшения"),
    WEAPONS_FACT("Weapons", "Оружие"),
    PERCENT("{0}%", "{0}%"),
    LANGUAGE("Language", "Язык"),
    TEXT_SIZE("Text size", "Размер текста"),

    WORKSHOP_INFO(
        "Spend Matter on characters, weapons and the Core Matrix. Upgrades carry over between demo runs.",
        "Тратьте материю на персонажей, оружие и Матрицу Ядра. Улучшения сохраняются между забегами.",
    ),
    CODEX_INFO(
        "Everything you have found in runs: items, weapons, relics, forms, reactions and relic pairings.",
        "Всё, что вы нашли в забегах: предметы, оружие, реликвии, формы, реакции и сочетания реликвий.",
    ),
    SETTINGS_INFO(
        "Game, sound, graphics and interface options. Changes apply immediately.",
        "Параметры игры, звука, графики и интерфейса. Изменения применяются сразу.",
    ),

    DEMO("Demo", "Демо"),
    DEMO_INFO(
        "Defeat the Architect to complete the demo. You can start another run at any time.",
        "Победите Архитектора, чтобы пройти демо. Новый забег можно начать в любое время.",
    ),
    VERSION("Demo v0.6.1", "Демо v0.6.1"),
    QUIT("Quit", "Выход"),
    QUIT_DESCRIPTION("Quit the game", "Выйти из игры"),

    BACK("Back", "Назад"),
    CLOSE("Close", "Закрыть"),
    SEARCH("Search", "Поиск"),
    RARITY("Rarity", "Редкость"),
    STATS("Stats", "Характеристики"),
    MAX_STACKS("Max {0}", "Макс. {0}"),

    BABY_MODE("Baby mode", "Режим малыша"),
    BABY_MODE_ON("On", "Включён"),
    BABY_MODE_OFF("Off", "Выключен"),
    BABY_MODE_INFO(
        "Baby mode: the cursor turns green and harmless, incoming damage is halved and rock collisions never finish you off. Nothing is counted: no Matter, Codex entries or unlocks. It stays on until you switch it off or restart the game.",
        "Режим малыша: курсор становится зелёным и безвредным, входящий урон уменьшается вдвое, а столкновения с камнями никогда не добивают. Ничего не засчитывается: ни материя, ни записи справочника, ни открытия. Режим действует, пока вы его не выключите или не перезапустите игру.",
    ),
}
