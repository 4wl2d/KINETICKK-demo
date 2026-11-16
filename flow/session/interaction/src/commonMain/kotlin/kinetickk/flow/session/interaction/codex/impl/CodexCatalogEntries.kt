// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.flow.session.interaction.codex.impl

import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.CoreShapeDefinition
import kinetickk.ball.content.api.EnemyDefinition
import kinetickk.ball.content.api.EnemyHabitat
import kinetickk.ball.content.api.ItemDefinition
import kinetickk.ball.content.api.RelicDefinition
import kinetickk.ball.content.api.RelicKey
import kinetickk.ball.content.api.RuleTextContext
import kinetickk.ball.content.api.UiCatalogSnapshot
import kinetickk.ball.content.api.WeaponCadence
import kinetickk.ball.content.api.WeaponDefinition
import kinetickk.ball.content.api.lifetimeMatterOfferLevel
import kinetickk.ball.content.api.liabilityText
import kinetickk.ball.content.api.localizedContent
import kinetickk.ball.content.api.ruleLines
import kinetickk.ball.content.api.upsideText
import kinetickk.ball.gameplay.api.GameplayBuildSummaryProjection
import kinetickk.ball.profile.api.HomeProgressProjection
import kinetickk.flow.session.interaction.codex.api.CodexRenderModel
import kinetickk.flow.session.interaction.localization.CodexText
import kinetickk.flow.session.interaction.localization.SessionText
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.Kk

/**
 * Run level from which ordinary offers include [item]: 1 once lifetime Matter has unlocked its level; an active item
 * always waits for the run level itself ([lifetimeMatterOfferLevel]).
 */
internal fun codexOfferedFromLevel(item: ItemDefinition, lifetimeMatter: Long): Int =
    if (!item.isActive && item.unlockLevel <= lifetimeMatterOfferLevel(lifetimeMatter)) 1 else item.unlockLevel

/**
 * The rule-line context of [item] as owned now, as the reward card writes it: the current stacks
 * (one copy when not owned), the surge and relic attunement multipliers, and the run's dominant
 * and secondary elements. Run scales the Codex cannot see stay as the basis phrases.
 */
internal fun codexRuleContext(
    item: ItemDefinition,
    stack: Int,
    surged: Boolean,
    catalog: UiCatalogSnapshot?,
    build: GameplayBuildSummaryProjection? = null,
): RuleTextContext {
    val policy = catalog?.build?.policy
    val copies = stack.coerceIn(1, item.maxStacks.coerceIn(1, ContentBounds.MAX_ITEM_STACKS))
    val attuned = catalog != null && build != null &&
        build.relics.any { bound -> catalog.relic(bound.id).attunedTags.any { it in item.tags } }
    val elements = build?.elements.orEmpty().filter { it.affinity > 0 }
    val dominant = elements.firstOrNull { it.dominant }?.element
    return RuleTextContext(
        stackMagnitude = policy?.stackMagnitude(copies) ?: 1f,
        dominant = dominant,
        secondary = elements.filter { it.element != dominant }.maxByOrNull { it.affinity }?.element,
        stacks = copies,
        bonus = (if (surged && policy != null) policy.surgeMagnitude else 1f) *
            (if (attuned && policy != null) 1f + policy.attunementBonus else 1f),
    )
}

internal fun codexItemEntry(
    item: ItemDefinition,
    model: CodexRenderModel,
    lifetimeMatter: Long,
    language: AppLanguage = AppLanguage.English,
    catalog: UiCatalogSnapshot? = null,
): CodexEntry {
    if (!model.isDiscovered(item.id)) return unknownEntry("item/${item.id}", language)
    val stack = model.itemStack(item.id)
    val build = model.runStacks.build
    val surged = build?.surgedItemIds?.contains(item.id) == true
    val suppressed = build?.suppressedItemIds?.contains(item.id) == true
    // Every part waits for a skill or for a weapon of another family (never the relics' doing).
    val dormant = build?.dormantItemIds?.contains(item.id) == true
    // The run's draft deck left it out: no ordinary offer of this run shows it.
    val outOfDeck = build?.outOfDeckItemIds?.contains(item.id) == true
    val lines = item.ruleLines(language, codexRuleContext(item, stack, surged, catalog, build))
    val facts = buildList {
        // Offers depend on the run: the stack limit, then the game's current eligibility.
        when {
            suppressed -> add(codexStatus(language, language.text(CodexText.SUPPRESSED), language.text(CodexText.SUPPRESSED_EFFECT)))
            dormant -> add(codexStatus(language, language.text(CodexText.DORMANT), language.text(CodexText.DORMANT_HELP)))
            outOfDeck -> add(codexStatus(language, language.text(CodexText.NOT_IN_RUN), language.text(CodexText.NOT_IN_RUN_HELP)))
            stack >= item.maxStacks -> add(codexStatus(language, language.text(SessionText.STACK_LIMIT)))
            build != null && item.id in build.eligibleItemIds -> add(codexStatus(language, language.text(SessionText.NEXT_ACQUISITION)))
            build != null -> add(codexStatus(language, language.text(SessionText.OFFER_LIMIT), language.text(SessionText.OFFER_LIMIT_HELP)))
        }
        add(CodexFact(language.text(SessionText.OFFERS_FROM_LEVEL), language.text(SessionText.LEVEL_SHORT, codexOfferedFromLevel(item, lifetimeMatter)),
            language.text(if (item.isActive) CodexText.ACTIVE_OFFERS_HELP else SessionText.OFFERS_FROM_LEVEL_HELP)))
    }
    val active = codexActivePart(item, catalog?.skills ?: DefaultSkills, stack, language)
    val description = buildString {
        append(item.description.localizedContent(language))
        if (active != null) {
            append("\n\n").append(language.text(CodexText.ACTIVE_PART))
            active.ranks.forEach { append("\n").append(it.label).append(": ").append(it.numbers) }
            append("\n").append(active.rankThree)
            if (lines.isNotEmpty()) append("\n\n").append(language.text(CodexText.PASSIVE_PART))
        }
        if (lines.isNotEmpty()) append(if (active != null) "\n" else "\n\n").append(lines.joinToString("\n") { it.text })
    }
    return CodexEntry(
        "item/${item.id}", item.name.localizedContent(language), description,
        item.rarity.displayLabel.localizedContent(language), "$stack/${item.maxStacks}", facts,
        CodexIcon.Item(item, stack, surged, suppressed), codexItemColor(item), true, item.rarity.rank, item.id in model.newItemIds,
        detail = CodexDetail.Item(item, lines, stack, surged, suppressed, active),
        spoken = if (active != null) language.text(CodexText.ACTIVE_ITEM) else null,
    )
}

/**
 * A weapon, always named with its typed facts (family, element, Relic key, scaling input, cadence,
 * signature character) and its Matter cost before purchase. The detail shows how the equipped
 * weapon interacts with the run's character, bound relics and tag points.
 */
internal fun codexWeaponEntry(
    weapon: WeaponDefinition,
    model: CodexRenderModel,
    progress: HomeProgressProjection,
    catalog: UiCatalogSnapshot,
    language: AppLanguage = AppLanguage.English,
    allowCurrentRun: Boolean = true,
): CodexEntry {
    val build = model.runStacks.build
    val equipped = build?.weapon == weapon.id
    val unlocked = weapon.id in progress.loadout.unlockedWeapons
    val owned = unlocked || (allowCurrentRun && equipped)
    val signature = catalog.coreShapes.firstOrNull { it.id == weapon.signature }?.displayName?.localizedContent(language)
    val facts = buildList {
        add(codexStatus(language, language.text(when {
            !unlocked -> SessionText.LOCKED
            weapon.id == progress.loadout.selectedWeapon -> SessionText.START_WEAPON_SELECTED
            else -> SessionText.START_WEAPON_UNLOCKED
        })))
        if (!unlocked) add(CodexFact(language.text(SessionText.UNLOCK_COST), weapon.permanentUnlockCost.toString()))
        if (equipped) {
            add(CodexFact(language.text(SessionText.WEAPON_LEVEL), language.text(SessionText.LEVEL_SHORT, build.weaponLevel)))
            add(CodexFact(language.text(SessionText.MASTERY), build.mastery.localizedContent(language)))
            // The permanent Honing the run applies to the weapon in hand.
            add(CodexFact(language.text(CodexText.HONING), language.text(SessionText.LEVEL_SHORT, build.weaponUpgradeLevel)))
        }
        add(CodexFact(language.text(CodexText.WEAPON_FAMILY), weapon.family.displayLabel.localizedContent(language)))
        add(CodexFact(language.text(CodexText.WEAPON_ELEMENT), codexWeaponElementLabel(weapon, language)))
        add(CodexFact(language.text(CodexText.WEAPON_KEY), weapon.key.displayLabel.localizedContent(language)))
        add(CodexFact(language.text(CodexText.WEAPON_SCALING), weapon.scaling.displayLabel.localizedContent(language)))
        add(CodexFact(language.text(CodexText.WEAPON_CADENCE), codexCadenceLabel(weapon.cadence, language)))
        signature?.let { add(CodexFact(language.text(CodexText.WEAPON_SIGNATURE), it)) }
    }
    // During a run these synergies apply to the equipped weapon.
    val lit = build?.takeIf { equipped }
    val character = if (build == null) progress.loadout.coreShape else lit?.character
    val bound = lit?.relics.orEmpty().mapNotNull { slot -> catalog.relics.firstOrNull { it.id == slot.id } }
        .filter { weapon.key in it.keys }
    val tagPointsByTag = lit?.let { summary -> weapon.tags.associateWith { tag -> summary.tags.filter { it.tag == tag }.sumOf { it.points } } }
    val formLevels = catalog.weaponMasteries.map { it.minimumLevel }.filter { it > 1 }.sorted().take(weapon.masteryForms.size)
        .takeIf { it.size == weapon.masteryForms.size } ?: listOf(3, 6, 10).take(weapon.masteryForms.size)
    return CodexEntry("weapon/${weapon.id}", weapon.name.localizedContent(language), weapon.description.localizedContent(language),
        language.text(SessionText.WEAPON), if (equipped) language.text(SessionText.LEVEL_SHORT, build.weaponLevel) else CODEX_NONE,
        facts, CodexIcon.Weapon(weapon.id), Kk.Bone, owned,
        help = language.text(if (owned) SessionText.WEAPON_HELP else SessionText.WEAPON_LOCKED_HELP), active = equipped,
        detail = CodexDetail.Weapon(weapon, signature, character != null && character == weapon.signature, formLevels, bound, tagPointsByTag))
}

/** A weapon's element as the Codex names it: its element, or Kinetic. */
internal fun codexWeaponElementLabel(weapon: WeaponDefinition, language: AppLanguage): String =
    (weapon.element?.displayLabel ?: CODEX_KINETIC).localizedContent(language)

private const val CODEX_KINETIC = "Kinetic"

/** Display label of a weapon cadence. */
internal fun codexCadenceLabel(cadence: WeaponCadence, language: AppLanguage): String = language.text(when (cadence) {
    WeaponCadence.CONTINUOUS -> CodexText.CADENCE_CONTINUOUS
    WeaponCadence.CONTACT -> CodexText.CADENCE_CONTACT
    WeaponCadence.DISCRETE -> CodexText.CADENCE_DISCRETE
})

internal fun codexRelicEntry(
    relic: RelicDefinition,
    model: CodexRenderModel,
    catalog: UiCatalogSnapshot,
    language: AppLanguage = AppLanguage.English,
    severed: Boolean = false,
): CodexEntry {
    if (!severed && !model.isRelicDiscovered(relic.id)) return unknownEntry("relic/${relic.id}", language)
    val build = model.runStacks.build
    val rank = if (severed) null else build?.relics?.firstOrNull { it.id == relic.id }?.rank
    val maxRank = catalog.relicPolicy.maxRank
    val ranked = relic.values.any { it.perRank != 0f }
    val upsides = (1..maxRank).map { relic.upsideText(language, it) }
    val liability = relic.liabilityText(language)
    val facts = buildList {
        add(when {
            severed -> codexStatus(language, language.text(CodexText.SEVERED), language.text(CodexText.SEVERED_HELP))
            rank != null -> codexStatus(language, language.text(SessionText.EQUIPPED))
            else -> codexStatus(language, language.text(SessionText.RELIC_AVAILABLE))
        })
        if (rank != null) add(CodexFact(language.text(SessionText.RANK), "$rank/$maxRank"))
    }
    val bound = build?.relics.orEmpty().filter { it.id != relic.id }.map { catalog.relic(it.id) }
    val opposedBound = relic.keys.filter { key -> bound.any { other -> other.keys.any { RelicKey.opposed(key, it) } } }.toSet()
    val pairings = catalog.build.pairings.filter { it.involves(relic.id) }.mapNotNull { pairing ->
        val partner = if (pairing.first == relic.id) pairing.second else pairing.first
        if (!model.isRelicDiscovered(partner)) return@mapNotNull null
        CodexPairingNote(catalog.relic(partner), pairing.kind.linkKind(),
            pairing.name.localizedContent(language).takeIf { model.isPairingDiscovered(pairing.id) })
    }
    return CodexEntry("relic/${relic.id}", relic.name.localizedContent(language), "${upsides[(rank ?: 1) - 1]}\n\n$liability",
        relic.aspect.displayLabel.localizedContent(language), "${rank ?: 0}/$maxRank", facts,
        CodexIcon.Relic(relic, rank, severed), relicAspectColor(relic.aspect), isNew = relic.id in model.newRelicIds, active = rank != null,
        detail = CodexDetail.Relic(relic, upsides, rank ?: 1, ranked, liability, opposedBound, pairings, severed))
}

internal fun codexShapeEntry(shape: CoreShapeDefinition, model: CodexRenderModel, progress: HomeProgressProjection, language: AppLanguage = AppLanguage.English): CodexEntry {
    val unlocked = shape.id in progress.unlockedCoreShapes
    // The Profile counts unlock progress; a projection without it shows none yet.
    val current = progress.characterProgress.firstOrNull { it.shape == shape.id }?.progress ?: 0L
    val requirement = shape.unlockDescription.localizedContent(language)
    val equipped = shape.id == model.runStacks.build?.character
    val facts = buildList {
        add(codexStatus(language, language.text(when {
            equipped -> SessionText.EQUIPPED
            unlocked -> SessionText.UNLOCKED
            else -> SessionText.LOCKED
        })))
        if (!unlocked) add(CodexFact(language.text(SessionText.PROGRESS), "$current/${shape.unlockTarget}"))
        // The permanent Refit the run applies to the character it plays.
        model.runStacks.build?.takeIf { equipped }?.let { build ->
            add(CodexFact(language.text(CodexText.REFIT), language.text(SessionText.LEVEL_SHORT, build.characterUpgradeLevel)))
        }
        add(CodexFact(language.text(CodexText.MECHANIC), shape.mechanicName.localizedContent(language)))
        add(CodexFact(language.text(CodexText.TRIGGER), shape.triggerKind.displayLabel.localizedContent(language)))
        add(CodexFact(language.text(CodexText.DIFFICULTY), "${shape.difficulty}/3"))
    }
    return CodexEntry("shape/${shape.id}", if (unlocked) shape.displayName.localizedContent(language) else language.text(SessionText.UNKNOWN_CORE),
        if (unlocked) "${shape.mechanicDescription.localizedContent(language)}\n\n$requirement" else requirement,
        language.text(SessionText.CHARACTER), if (equipped) language.text(SessionText.EQUIPPED) else CODEX_NONE,
        facts, CodexIcon.Shape(shape.id), if (unlocked) Kk.Bone else Kk.Mute, unlocked,
        help = if (unlocked) language.text(SessionText.CHARACTER_LAB_HELP) else null, active = equipped)
}

/**
 * A roster enemy: its threat, habitat and when it joins the waves in open space. A secret boss
 * stays anonymous until discovered.
 */
internal fun codexEnemyEntry(
    enemy: EnemyDefinition,
    model: CodexRenderModel,
    language: AppLanguage = AppLanguage.English,
): CodexEntry {
    if (!codexEnemyVisible(enemy, model)) {
        return CodexEntry("enemy/${enemy.type}", language.text(CodexText.UNKNOWN_TITLE), "", language.text(CodexText.SECRET_BOSS_KIND),
            "?/$MAX_CODEX_THREAT", listOf(codexStatus(language, language.text(CodexText.UNDISCOVERED))),
            CodexIcon.Enemy(enemy.type, hidden = true), Kk.Mute, discovered = false,
            help = language.text(CodexText.UNKNOWN_ENEMY_HELP))
    }
    val kind = language.text(when {
        enemy.isBoss -> CodexText.BOSS_KIND
        enemy.isSecretBoss -> CodexText.SECRET_BOSS_KIND
        enemy.isMiniBoss -> CodexText.MINI_BOSS_KIND
        enemy.isElite -> CodexText.ELITE_KIND
        else -> CodexText.ENEMY_KIND
    })
    val threat = "${enemy.threat}/$MAX_CODEX_THREAT"
    val facts = buildList {
        add(codexStatus(language, language.text(CodexText.IN_ROSTER)))
        add(CodexFact(language.text(CodexText.THREAT), threat))
        add(CodexFact(language.text(CodexText.HABITAT), codexHabitats(enemy, language)))
        val arrival = enemy.arrival
        if (arrival != null) {
            add(CodexFact(language.text(CodexText.APPEARS), arrival.localizedContent(language)))
        } else if (enemy.livesIn(EnemyHabitat.SPACE)) {
            add(CodexFact(language.text(CodexText.APPEARS), language.text(CodexText.FROM_TIME, codexClock(enemy.timeGateSeconds))))
        }
    }
    return CodexEntry("enemy/${enemy.type}", enemy.name.localizedContent(language), enemy.description.localizedContent(language),
        kind, threat, facts, CodexIcon.Enemy(enemy.type), Kk.Bone,
        help = language.text(CodexText.ENEMY_HELP))
}

/** Whether the Codex may name [enemy]: every kind but a secret boss whose revealing discovery is not recorded yet. */
internal fun codexEnemyVisible(enemy: EnemyDefinition, model: CodexRenderModel): Boolean =
    !enemy.isSecretBoss || model.isWorldDiscovered(checkNotNull(enemy.revealedBy) { "A secret boss names its discovery" })

/** Named enemies out of the demo's complete roster. */
internal fun codexEnemyCounts(model: CodexRenderModel, catalog: UiCatalogSnapshot): Pair<Int, Int> =
    catalog.enemies.count { codexEnemyVisible(it, model) } to catalog.enemies.size

/** "Everywhere" for a kind of every habitat, else its habitats' names in habitat order ("Open space, Structures"). */
private fun codexHabitats(enemy: EnemyDefinition, language: AppLanguage): String {
    if (enemy.habitats == EnemyHabitat.ALL) return language.text(CodexText.HABITAT_ANY)
    return EnemyHabitat.entries.filter(enemy::livesIn).joinToString(", ") { habitat ->
        language.text(when (habitat) {
            EnemyHabitat.SPACE -> CodexText.HABITAT_SPACE
            EnemyHabitat.STRUCTURE -> CodexText.HABITAT_STRUCTURE
            EnemyHabitat.ZENITH -> CodexText.HABITAT_ZENITH
            EnemyHabitat.NADIR -> CodexText.HABITAT_NADIR
            EnemyHabitat.ARCHIVE -> CodexText.HABITAT_ARCHIVE
        })
    }
}

private const val MAX_CODEX_THREAT = 5

/** Run clock as m:ss ("5:30"), the way the Codex states when an enemy arrives. */
internal fun codexClock(seconds: Float): String {
    val total = seconds.toInt().coerceAtLeast(0)
    return "${total / 60}:${(total % 60).toString().padStart(2, '0')}"
}

/**
 * The Catalog tab's entries of [category]. Items (0) are the matrix of [sort] in reading order, row by
 * row and slot by slot (see [codexItemRows]); the other categories keep catalog order.
 */
internal fun codexCatalogEntries(
    category: Int,
    search: String,
    filter: CodexItemFilter,
    model: CodexRenderModel,
    catalog: UiCatalogSnapshot,
    progress: HomeProgressProjection,
    language: AppLanguage = AppLanguage.English,
    sort: CodexItemSort = CodexItemSort.TAG,
): List<CodexEntry> {
    return when (category) {
        0 -> codexItemRows(sort, search, filter, model, catalog, progress, language).entries
        // Weapons are always named (an unowned one keeps the locked look), so search reaches every one.
        1 -> catalog.weapons.map { codexWeaponEntry(it, model, progress, catalog, language, allowCurrentRun = false) }
            .filter { search.isBlank() || it.title.contains(search, ignoreCase = true) }
        2 -> catalog.relics.filter { model.isRelicDiscovered(it.id) }
            .map { codexRelicEntry(it, model, catalog, language) }
            .filter { it.title.contains(search, ignoreCase = true) }
        3 -> catalog.coreShapes.map { codexShapeEntry(it, model, progress, language) }.filter { search.isBlank() || it.discovered && it.title.contains(search, ignoreCase = true) }
        // A hidden secret boss never matches a search.
        CODEX_ENEMIES_CATEGORY -> catalog.enemies.map { codexEnemyEntry(it, model, language) }
            .filter { search.isBlank() || (it.icon as? CodexIcon.Enemy)?.hidden != true && it.title.contains(search, ignoreCase = true) }
        CODEX_WORLD_CATEGORY -> codexWorldEntries(search, model, catalog, language)
        else -> error("No Codex catalog category $category")
    }
}

/** Catalog category of the enemy roster (after Items, Weapons, Relics and Forms). */
internal const val CODEX_ENEMIES_CATEGORY = 4

/** Catalog category of the world's places and secrets (after the enemies). */
internal const val CODEX_WORLD_CATEGORY = 5
