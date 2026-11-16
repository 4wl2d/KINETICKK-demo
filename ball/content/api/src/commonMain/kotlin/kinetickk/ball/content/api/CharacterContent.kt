// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.content.api

import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList

/** Cumulative achievement a character unlocks with; persisted counters live in the Profile. Append only. */
enum class CharacterUnlockRequirement {
    AVAILABLE,
    ELITE_KILLS,
    DASH_HITS,
}

/** What feeds a character's mechanic; the Hangar shows it as a label. */
enum class CharacterTriggerKind(val displayLabel: String) {
    MOVEMENT("Movement"),
    KILLS("Kills"),
    DASH("Dash"),
}

/** Base stat changes a character applies when a run starts; neutral values change nothing. */
data class CharacterTraits(
    val massMultiplier: Float = 1f,
    val integrityBonus: Int = 0,
    val dragMultiplier: Float = 1f,
    val dashHeatMultiplier: Float = 1f,
    val radiusMultiplier: Float = 1f,
    val critChanceBonus: Float = 0f,
    val critDamageBonus: Float = 0f,
    val pickupRadiusBonus: Float = 0f,
) {
    init {
        require(massMultiplier in 0.5f..2f && dragMultiplier in 0.5f..2f && dashHeatMultiplier in 0.5f..2f)
        require(radiusMultiplier in 0.5f..2f && integrityBonus in -50..50)
        require(critChanceBonus in 0f..0.5f && critDamageBonus in 0f..1f && pickupRadiusBonus in 0f..300f)
    }
}

/** Content owns the character mechanic and achievement requirements; Profile evaluates access. */
data class CoreShapeDefinition(
    val id: CoreShape,
    val displayName: String = when (id) {
        CoreShape.ORB -> "Circle"
        CoreShape.PRISM -> "Square"
        CoreShape.SHARD -> "Triangle"
    },
    val mechanicDescription: String = when (id) {
        CoreShape.ORB -> "Arc in one direction to charge Orbit; your next hit releases a ring wave that brands its target."
        CoreShape.PRISM -> "Four facets each negate a hit and regrow; Elite kills restore them, all four strengthen impacts."
        CoreShape.SHARD -> "Dash through enemies to mark them; a later hit punctures the mark for heavy damage and vents heat."
    },
    val unlockRequirement: CharacterUnlockRequirement = when (id) {
        CoreShape.ORB -> CharacterUnlockRequirement.AVAILABLE
        CoreShape.PRISM -> CharacterUnlockRequirement.ELITE_KILLS
        CoreShape.SHARD -> CharacterUnlockRequirement.DASH_HITS
    },
    val unlockTarget: Int = when (id) {
        CoreShape.ORB -> 0
        CoreShape.PRISM -> 3
        CoreShape.SHARD -> 20
    },
    val unlockDescription: String = when (id) {
        CoreShape.ORB -> "Available from the start"
        CoreShape.PRISM -> "Defeat 3 elites across runs"
        CoreShape.SHARD -> "Land 20 Dash hits across runs"
    },
    val mechanicName: String = when (id) {
        CoreShape.ORB -> "Orbit"
        CoreShape.PRISM -> "Facets"
        CoreShape.SHARD -> "Puncture"
    },
    val triggerKind: CharacterTriggerKind = when (id) {
        CoreShape.ORB -> CharacterTriggerKind.MOVEMENT
        CoreShape.PRISM -> CharacterTriggerKind.KILLS
        CoreShape.SHARD -> CharacterTriggerKind.DASH
    },
    val traits: CharacterTraits = when (id) {
        CoreShape.ORB -> CharacterTraits()
        CoreShape.PRISM -> CharacterTraits(massMultiplier = 1.25f, dragMultiplier = 1.15f, integrityBonus = 20)
        // A light Core: its Dash marks a pack rather than smashing it, so the marks are left for its Puncture.
        CoreShape.SHARD -> CharacterTraits(massMultiplier = 0.8f, critChanceBonus = 0.08f, integrityBonus = -15,
            radiusMultiplier = 0.9f, dashHeatMultiplier = 0.85f)
    },
    /** 1 (straightforward) to 3 (demanding). */
    val difficulty: Int = when (id) {
        CoreShape.ORB, CoreShape.PRISM -> 1
        CoreShape.SHARD -> 2
    },
    /** The character's level-cap ability. */
    val apex: ApexDefinition = defaultApexDefinition(id),
    /** Matter for Refit level 1 ([UpgradePolicy.nextCost]). */
    val upgradeBaseCost: Int = CHARACTER_UPGRADE_BASE_COST,
    /** The mechanic parameter each Refit level moves. */
    val upgradeSignature: CharacterUpgradeSignature = defaultCharacterUpgradeSignature(id),
    /**
     * The character's lane: three active effects that play into its mechanic. Offers weight their items by
     * [OfferLanePolicy.characterActiveWeight]; bots and cards read the same list.
     */
    val activeLane: ImmutableList<SkillId> = defaultActiveLane(id),
) {
    init {
        require(displayName.isNotBlank() && mechanicDescription.isNotBlank() && unlockDescription.isNotBlank())
        require(mechanicName.isNotBlank())
        require(unlockTarget >= 0)
        require(unlockRequirement != CharacterUnlockRequirement.AVAILABLE || unlockTarget == 0)
        require(difficulty in 1..3)
        require(upgradeBaseCost in 1..ContentBounds.MAX_UPGRADE_BASE_COST) {
            "Refit base cost must be within 1..${ContentBounds.MAX_UPGRADE_BASE_COST}"
        }
        require(activeLane.size == ACTIVE_LANE_SIZE && activeLane.toSet().size == ACTIVE_LANE_SIZE) {
            "A character's active lane names exactly $ACTIVE_LANE_SIZE distinct effects"
        }
    }

    companion object {
        /** Active effects in each character's lane. */
        const val ACTIVE_LANE_SIZE: Int = 3
    }
}

private fun defaultActiveLane(id: CoreShape): ImmutableList<SkillId> = when (id) {
    CoreShape.ORB -> immutableListOf(SkillId.SLINGSHOT, SkillId.ARC_HOOK, SkillId.CATALYZE)
    CoreShape.PRISM -> immutableListOf(SkillId.FULL_STOP, SkillId.FROST_SNAP, SkillId.MASS_DRIVER)
    CoreShape.SHARD -> immutableListOf(SkillId.VOID_STEP, SkillId.ARC_HOOK, SkillId.SONIC_BOOM)
}

fun defaultCoreShapeDefinitions(): ImmutableList<CoreShapeDefinition> =
    CoreShape.entries.map { CoreShapeDefinition(it) }.toImmutableList()
