// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.content.api

/**
 * Relic names, upside templates (the `{n}` placeholders stay identical to the English source),
 * liabilities and named pairings.
 */
internal val RelicPhrasesInRussian: List<Pair<String, String>> = listOf(
    "Perpetual Vector" to "Вечный вектор",
    "Drag falls by 85 %. Every 400 u/s above 600, up to 2,200, adds {0} % damage." to
        "Сопротивление снижается на 85 %. Каждые 400 ед/с сверх 600, вплоть до 2\u00A0200, добавляют {0} % урона.",
    "Brake grips half as hard, and impacts below 600 u/s deal 30 % less." to
        "Тормоз держит вдвое слабее, а столкновения на скорости ниже 600 ед/с наносят на 30 % меньше урона.",

    "Gyre Engine" to "Вихревой двигатель",
    "Every real turn releases a shock ring for {0} % impact damage and 2 stacks of your dominant element." to
        "Каждый настоящий поворот выпускает ударное кольцо: {0} % урона от столкновения и 2 слоя вашей основной стихии.",
    "Polarity no longer recovers on its own or while braking; only turns restore it, and less than before." to
        "Полярность больше не восстанавливается сама и при торможении: её возвращают только повороты, и слабее, чем прежде.",

    "Glass Comet" to "Стеклянная комета",
    "All your damage +{0} %. Impact kills above 900 u/s grant a quarter second of invulnerability, " +
        "at most once every 1.5 seconds." to
        "Весь ваш урон +{0} %. Убийства столкновением на скорости выше 900 ед/с дают четверть секунды " +
        "неуязвимости, не чаще раза в 1,5 секунды.",
    "You take 50 % more damage." to
        "Вы получаете на 50 % больше урона.",

    "Echo Chamber" to "Камера эха",
    "Weapon hits echo 0.35 seconds later for {0} % damage and apply their on-hit effects again." to
        "Попадания оружия повторяются эхом через 0,35 секунды с {0} % урона и снова применяют свои эффекты при попадании.",
    "Impact damage −50 %." to "Урон от столкновений −50 %.",

    "Furnace Covenant" to "Завет горнила",
    "Overheat never locks your Dash. Every 10 heat above 40 adds {0} % damage, and above 70 heat your " +
        "hits apply Thermal." to
        "Перегрев больше не блокирует рывок. Каждые 10 единиц нагрева сверх 40 добавляют {0} % урона, " +
        "а при нагреве выше 70 ваши удары накладывают Термо.",
    "Cooling −55 %. Above 80 heat you lose 3 integrity per second, and heat past full burns integrity instead." to
        "Охлаждение −55 %. При нагреве выше 80 вы теряете 3 прочности в секунду, а нагрев сверх предела сжигает прочность.",

    "Absolute Zero" to "Абсолютный ноль",
    "Impacts apply {0} Cryo and Dash chills everything around you. Frozen enemies shatter on the next " +
        "direct hit for {1} % damage, chilling their neighbors." to
        "Столкновения накладывают {0} слоя Крио, а рывок остужает всё вокруг вас. Замороженные враги " +
        "раскалываются от следующего прямого удара, нанося {1} % урона и остужая соседей.",
    "Half of the Thermal you apply turns to Cryo and the rest is lost. Overdrive gain −40 %." to
        "Половина накладываемого вами Термо превращается в Крио, остальное теряется. Накопление перегрузки −40 %.",

    "Stormheart Capacitor" to "Грозовой конденсатор",
    "Above 700 u/s the Core charges in 4 seconds. The next impact discharges into five enemies for " +
        "{0} % impact damage and 3 Ion each." to
        "На скорости выше 700 ед/с Ядро заряжается за 4 секунды. Следующее столкновение выпускает заряд " +
        "в пять врагов: каждому {0} % урона от столкновения и 3 слоя Иона.",
    "Taking a hit while half charged or more discharges into you for 5 % of your integrity." to
        "Если вас ранят при заряде от половины, заряд бьёт по вам на 5 % вашей прочности.",

    "Aegis Inversion" to "Инверсия эгиды",
    "All healing becomes shield, and shield capacity grows by 40 % of your maximum integrity. A breaking " +
        "shield releases a nova worth {0} % of the damage it absorbed." to
        "Всё лечение становится щитом, а ёмкость щита растёт на 40 % вашей максимальной прочности. " +
        "Разрушаясь, щит выпускает вспышку силой {0} % поглощённого урона.",
    "Integrity can never be restored, and shields recharge on their own at half speed." to
        "Прочность больше нельзя восстановить, а щиты сами перезаряжаются вдвое медленнее.",

    "Neutron Heart" to "Нейтронное сердце",
    "Mass +{0} %, and enemies barely move you. Slow collisions hurt you only half as much." to
        "Масса +{0} %, враги почти не сдвигают вас. Медленные столкновения ранят вас вдвое слабее.",
    "Dash impulse −35 % and magnetism −20 %." to "Импульс рывка −35 %, магнетизм −20 %.",

    "Verdant Rot" to "Цветущая гниль",
    "Impacts apply 2 Entropy. Every Entropy stack within 320 u heals you {0} per second, up to {1}." to
        "Столкновения накладывают 2 слоя Энтропии. Каждый слой Энтропии в пределах 320 ед лечит вас " +
        "на {0} в секунду, вплоть до {1}.",
    "All other healing is halved." to
        "Любое другое лечение ослаблено вдвое.",

    // Named relic-to-relic pairings.
    "Endless Gyre" to "Бесконечный вихрь",
    "Above 1,200 u/s every turn fires a second, wider ring at 60 % damage a moment later." to
        "На скорости выше 1\u00A0200 ед/с каждый поворот мгновение спустя выпускает второе, более широкое кольцо с 60 % урона.",
    "Lightning Rod" to "Громоотвод",
    "Above 1,400 u/s the capacitor charges 50 % faster, and a discharge throws you forward." to
        "На скорости выше 1\u00A0400 ед/с конденсатор заряжается на 50 % быстрее, а выпущенный заряд бросает вас вперёд.",
    "Rot Ward" to "Гнилостный оберег",
    "The rot feeds the inverted shield, and every nova applies 3 Entropy." to
        "Гниль питает инвертированный щит, а каждая вспышка накладывает 3 слоя Энтропии.",
    "Faraday Cage" to "Клетка Фарадея",
    "A self-discharge strikes your shield first, and every nova applies 3 Ion." to
        "Заряд, бьющий по вам, сначала попадает в щит, а каждая вспышка накладывает 3 слоя Иона.",
    "Thermal Paradox" to "Тепловой парадокс",
    "The furnace's heat bonus is halved, but every converted Thermal stack vents 3 heat." to
        "Бонус горнила от нагрева уменьшен вдвое, но каждый преобразованный слой Термо снижает нагрев на 3.",
    "Shattered Aegis" to "Разбитая эгида",
    "Inverted healing becomes 1 % damage per 3 points instead of shield, lost when hit." to
        "Инвертированное лечение вместо щита даёт 1 % урона за каждые 3 единицы и пропадает при получении удара.",
)
