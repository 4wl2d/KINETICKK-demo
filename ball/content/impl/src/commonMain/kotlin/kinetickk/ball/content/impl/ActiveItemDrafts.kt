// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.content.impl

import kinetickk.ball.content.api.BuffStat
import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.EffectCondition
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.ItemRarity
import kinetickk.ball.content.api.ItemRarity.COMMON
import kinetickk.ball.content.api.ItemRarity.EPIC
import kinetickk.ball.content.api.ItemRarity.RARE
import kinetickk.ball.content.api.ItemRarity.UNCOMMON
import kinetickk.ball.content.api.ItemRule
import kinetickk.ball.content.api.SkillDefinition
import kinetickk.ball.content.api.SkillId
import kinetickk.ball.content.api.defaultSkillDefinitions

/** The shipped active effects the active items are authored from; initialised before [ActiveDrafts] reads them. */
private val DefaultSkills: List<SkillDefinition> = defaultSkillDefinitions()

/** Active items in skill order. Each also grants one passive effect. */
internal val ActiveDrafts: List<ItemDraft> = SkillId.entries.map { id ->
    when (id) {
        SkillId.SOLAR_FLARE -> active(id, RARE, ItemRule.ElementPotency(StatusElement.THERMAL, 0.346f))
        SkillId.FROST_LINE -> active(id, UNCOMMON, ItemRule.StatusDuration(StatusElement.CRYO, 1.384f))
        SkillId.FROST_SNAP -> active(id, RARE, ItemRule.CritDamageVersus(EffectCondition.TargetFrozen, 0.219f))
        SkillId.ARC_HOOK -> active(id, UNCOMMON, ItemRule.ElementPotency(StatusElement.ION, 0.346f))
        SkillId.MASS_DRIVER -> active(id, RARE, ItemRule.ElementPotency(StatusElement.GRAVITIC, 0.346f))
        SkillId.ENTROPIC_TIDE -> active(id, EPIC, ItemRule.StatusDuration(StatusElement.ENTROPY, 1.384f))
        SkillId.SONIC_BOOM -> active(id, UNCOMMON, ItemRule.BreakPower(StatusElement.SONIC, 0.4225f))
        SkillId.VOID_STEP -> active(id, UNCOMMON, ItemRule.BreakPower(StatusElement.VOID, 0.4225f))
        SkillId.SLINGSHOT ->
            active(id, UNCOMMON, ItemRule.WhileModifier(EffectCondition.SpeedAtLeast(900f), BuffStat.COOLING, 0.25f))
        SkillId.FULL_STOP ->
            active(id, UNCOMMON, ItemRule.WhileModifier(EffectCondition.Braking, BuffStat.DAMAGE_TAKEN, -0.15f))
        SkillId.CATALYZE -> active(id, EPIC, ItemRule.ReactionPower(0.2685f))
    }
}

/**
 * The active item for [id]: its name, tags and element are the effect's own and its flavor line is the effect's summary
 * (bootstrap checks the same equality for every catalog); it stacks to rank III and unlocks by run level per rarity.
 */
private fun active(id: SkillId, rarity: ItemRarity, passive: ItemRule): ItemDraft {
    val effect = DefaultSkills[id.ordinal]
    return ItemDraft(
        effect.name, effect.summary, rarity, effect.tags, listOfNotNull(effect.element),
        rules = listOf(passive),
        stacks = ContentBounds.MAX_SKILL_RANK,
        unlock = activeUnlockLevel(rarity),
        active = id,
    )
}

/** Minimum run level for an active item of this rarity. */
private fun activeUnlockLevel(rarity: ItemRarity): Int = when (rarity) {
    UNCOMMON -> 5
    RARE -> 6
    EPIC -> 9
    COMMON -> error("Active items are Uncommon, Rare or Epic")
}
