// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.content.api

/** Active effects (the active items share their names and summaries): names, summaries and rank III texts. */
internal val SkillPhrasesInRussian: List<Pair<String, String>> = listOf(
    "Solar Flare" to "Солнечная вспышка",
    "Vents heat into a flare; the more heat, the more Scorch." to
        "Сбрасывает жар вспышкой: чем больше жара, тем сильнее Ожог.",
    "Enemies within two Scorch of Ignite ignite at once." to
        "Враги, которым до Воспламенения не хватает двух слоёв Ожога, сразу воспламеняются.",
    "Frost Line" to "Линия инея",
    "Raises a line of frost that slows enemies, stops hostile bolts and slams thrown enemies." to
        "Ставит линию инея: она замедляет врагов, останавливает снаряды и впечатывает отброшенных.",
    "Enemies on the line are embrittled while they stay on it." to "Враги на линии охрупчены, пока остаются на ней.",
    "Frost Snap" to "Морозный щелчок",
    "A freezing pulse that shatters Frozen and Brittle enemies and coils your next Dash." to
        "Морозный импульс раскалывает замёрзших и хрупких врагов и заряжает следующий рывок.",
    "Enemies within two Chill of Freeze freeze at once." to
        "Враги, которым до Заморозки не хватает двух слоёв Стужи, сразу замерзают.",
    "Arc Hook" to "Дуговой крюк",
    "Grapple the enemy nearest your pointer and fly to it; the impact counts as a Dash hit." to
        "Цепляет врага у указателя и бросает вас к нему; удар считается ударом рывком.",
    "If the target dies within 1.5 s, the hook is ready again 2.5 s after the cast." to
        "Если цель погибает за 1,5 с, крюк снова готов через 2,5 с после применения.",
    "Mass Driver" to "Масс-драйвер",
    "Your Core turns heavy: fast impacts throw enemies and add Mass." to
        "Ядро тяжелеет: быстрые удары отшвыривают врагов и добавляют Массу.",
    "Slams while it runs add 1 Mass to both enemies." to
        "Пока эффект действует, столкновения врагов добавляют обоим 1 слой Массы.",
    "Entropic Tide" to "Энтропийный прилив",
    "Draws in all Decay around you: it bursts as damage and mends the Core." to
        "Вбирает весь Распад вокруг: он взрывается уроном и чинит Ядро.",
    "Healing past full integrity becomes shield." to "Лечение сверх полной прочности становится щитом.",
    "Sonic Boom" to "Звуковой удар",
    "A cone of sound blasts enemies away and erases shots; the recoil hops you back." to
        "Звуковой конус отшвыривает врагов и гасит снаряды; отдача отбрасывает вас назад.",
    "Thrown enemies ring for 0.8 s; a slam into one forces Shatter." to
        "Отброшенные враги звенят 0,8 с; удар о такого врага вызывает Раскол.",
    "Void Step" to "Шаг в пустоту",
    "Step through the void toward the pointer, untouchable for a moment; a rift detonates where you were." to
        "Шаг сквозь пустоту к указателю: на миг вы неуязвимы, а позади взрывается разлом.",
    "Enemies along the path turn Brittle and take 1 Hollow." to
        "Враги на пути становятся хрупкими и получают 1 слой Опустошения.",
    "Slingshot" to "Праща",
    "Swing your momentum onto your aim; counts as a sharp turn." to
        "Разворачивает инерцию на прицел; считается резким поворотом.",
    "Impacts during the swing keep your momentum." to "Столкновения во время разворота не гасят вашу скорость.",
    "Full Stop" to "Полный стоп",
    "Stop dead: your speed becomes a shockwave that breaks near-full statuses." to
        "Резкая остановка: ваша скорость становится ударной волной, пробивающей почти полные статусы.",
    "Also counts as a Brake release." to "Также считается отпусканием тормоза.",
    "Catalyze" to "Катализ",
    "Every enemy near the pointer carrying two elements reacts at once." to
        "Каждый враг у указателя с двумя стихиями сразу вступает в реакцию.",
    "Across a wider area, forces up to ten reactions at 130% strength." to
        "На большей площади вызывает до десяти реакций с силой 130 %.",
)

/** Character Apex abilities: names and descriptions. */
internal val ApexPhrasesInRussian: List<Pair<String, String>> = listOf(
    "Perihelion" to "Перигелий",
    "For 10 s Orbit charges on half the arc and every release captures hostile shots; when it ends, they fly back." to
        "10 с орбита заряжается за полдуги, каждый выпуск захватывает вражеские снаряды, а в конце они летят обратно.",
    "Monolith" to "Монолит",
    "Restores every facet; for 8 s facets regrow fast and each blocked hit fires a refraction back." to
        "Восстанавливает все грани; 8 с они быстро отрастают, а каждый погашенный удар выпускает ответный луч.",
    "Vivisect" to "Вивисекция",
    "For 8 s Dashes cost no heat, punctures come faster and every puncture marks the next enemy." to
        "8 с рывки не стоят жара, проколы чаще, а каждый прокол помечает следующего врага.",
)
