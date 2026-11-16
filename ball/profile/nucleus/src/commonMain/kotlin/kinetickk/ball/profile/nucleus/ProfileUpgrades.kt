// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.profile.nucleus

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.MatrixNodeDefinition
import kinetickk.ball.content.api.ProfilePolicySnapshot
import kinetickk.ball.content.api.UpgradePolicy
import kinetickk.ball.content.api.WeaponDefinition
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.profile.api.PermanentUpgrades
import kinetickk.ball.profile.api.PlayerEconomy
import kinetickk.ball.profile.api.PlayerProfile
import kinetickk.ball.profile.api.ProfileRejection
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.mapIndexedToImmutableList

/** A purchase the Profile would accept now for [Priced.cost] Matter, or the first check it fails. */
internal sealed interface PurchaseQuote {
    data class Priced(val cost: Long) : PurchaseQuote
    data class Refused(val reason: ProfileRejection) : PurchaseQuote
}

/** The price of the next listed Core Matrix rank. */
internal fun matrixNodeQuote(profile: PlayerProfile, definition: MatrixNodeDefinition): PurchaseQuote {
    val matrix = profile.matrix
    val currentRank = matrix.rank(definition.id)
    val cost = definition.costs.getOrNull(currentRank)?.toLong()
        ?: return PurchaseQuote.Refused(ProfileRejection.MatrixNodeMaxRank)
    if (profile.economy.matter < cost) return PurchaseQuote.Refused(ProfileRejection.InsufficientMatter)
    return PurchaseQuote.Priced(cost)
}

/** The price to unlock a demo weapon. */
internal fun weaponUnlockQuote(profile: PlayerProfile, definition: WeaponDefinition): PurchaseQuote {
    val cost = definition.permanentUnlockCost.toLong()
    if (profile.economy.matter < cost) return PurchaseQuote.Refused(ProfileRejection.InsufficientMatter)
    return PurchaseQuote.Priced(cost)
}

/**
 * The next Refit level of [shape], checked in order: the character is unlocked, its level is below
 * the last listed level, then the price against the Matter balance.
 */
internal fun characterUpgradeQuote(
    profile: PlayerProfile,
    policy: ProfilePolicySnapshot,
    shape: CoreShape,
): PurchaseQuote {
    val definition = policy.coreShapes.firstOrNull { it.id == shape }
    if (definition == null || !isCoreShapeUnlocked(profile, definition)) {
        return PurchaseQuote.Refused(ProfileRejection.CoreShapeLocked)
    }
    return upgradeQuote(profile.economy, policy.upgrades, definition.upgradeBaseCost, profile.upgrades.characterLevel(shape))
}

/**
 * The next Honing level of [id], checked in order: the weapon is owned, its level is below the
 * last listed level, then the price against the Matter balance.
 */
internal fun weaponUpgradeQuote(
    profile: PlayerProfile,
    policy: ProfilePolicySnapshot,
    id: WeaponId,
): PurchaseQuote {
    val definition = policy.weapons.firstOrNull { it.id == id }
    if (definition == null || id !in profile.loadout.unlockedWeapons) {
        return PurchaseQuote.Refused(ProfileRejection.WeaponNotOwned)
    }
    return upgradeQuote(profile.economy, policy.upgrades, definition.upgradeBaseCost, profile.upgrades.weaponLevel(id))
}

private fun upgradeQuote(economy: PlayerEconomy, policy: UpgradePolicy, base: Int, level: Int): PurchaseQuote {
    val cost = policy.nextCost(base, level) ?: return PurchaseQuote.Refused(ProfileRejection.UpgradeMaxLevel)
    if (economy.matter < cost) return PurchaseQuote.Refused(ProfileRejection.InsufficientMatter)
    return PurchaseQuote.Priced(cost)
}

/**
 * True when the Workshop would sell something now: a weapon unlock, a Core Matrix rank, a Refit or a
 * Honing level. Evaluates the same quotes the purchase decisions use.
 */
internal fun isWorkshopAffordable(profile: PlayerProfile, policy: ProfilePolicySnapshot): Boolean =
    policy.weapons.any { it.id !in profile.loadout.unlockedWeapons && weaponUnlockQuote(profile, it) is PurchaseQuote.Priced } ||
        policy.matrixNodes.any { matrixNodeQuote(profile, it) is PurchaseQuote.Priced } ||
        policy.coreShapes.any { characterUpgradeQuote(profile, policy, it.id) is PurchaseQuote.Priced } ||
        policy.weapons.any { weaponUpgradeQuote(profile, policy, it.id) is PurchaseQuote.Priced }

/** Every bought Refit and Honing level. */
internal val PermanentUpgrades.totalLevels: Int get() = characterLevels.sum() + weaponLevels.sum()

/** [levels] with the entry at [index] one level higher. */
internal fun ImmutableList<Int>.raisedAt(index: Int): ImmutableList<Int> =
    mapIndexedToImmutableList { position, level -> if (position == index) level + 1 else level }

/** Upgrade lists sized for the closed catalogs, every level within `0..maxLevel`. */
internal fun PermanentUpgrades.isWithin(policy: UpgradePolicy): Boolean =
    characterLevels.size == CoreShape.entries.size && weaponLevels.size == WeaponId.entries.size &&
        characterLevels.all { policy.clampLevel(it) == it } &&
        weaponLevels.all { policy.clampLevel(it) == it }
