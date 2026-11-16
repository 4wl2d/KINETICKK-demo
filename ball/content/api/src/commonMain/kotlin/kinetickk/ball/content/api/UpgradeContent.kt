// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.content.api

/** Refit and Honing share one finite table of prices and cumulative effects. */
class UpgradePolicy {
    val maxLevel: Int get() = PaidUpgradeTiers.size

    fun clampLevel(level: Int): Int = level.coerceAtLeast(0).coerceAtMost(PaidUpgradeTiers.size)

    /** The price of the next implemented level, or null when the track is complete. */
    fun nextCost(base: Int, level: Int): Long? {
        require(base >= 0) { "Upgrade base cost must be non-negative" }
        return PaidUpgradeTiers.getOrNull(level)?.let { base.toLong() * it.priceMultiplier }
    }

    fun characterPower(level: Int): Float = tier(level)?.characterPower ?: 1f
    fun characterIntegrity(level: Int): Float = tier(level)?.characterIntegrity ?: 1f
    fun weaponPower(level: Int): Float = tier(level)?.weaponPower ?: 1f

    private fun tier(level: Int): UpgradeTier? =
        PaidUpgradeTiers.getOrNull(clampLevel(level) - 1)
}

private data class UpgradeTier(
    val priceMultiplier: Int,
    val characterPower: Float,
    val characterIntegrity: Float,
    val weaponPower: Float,
)

private val PaidUpgradeTiers = listOf(
    UpgradeTier(priceMultiplier = 1, characterPower = 1.07f, characterIntegrity = 1.05f, weaponPower = 1.085f),
    UpgradeTier(priceMultiplier = 2, characterPower = 1.1449f, characterIntegrity = 1.10f, weaponPower = 1.177225f),
    UpgradeTier(priceMultiplier = 4, characterPower = 1.225043f, characterIntegrity = 1.15f, weaponPower = 1.2772892f),
)

/** The signature parameter one Refit level moves for a character's mechanic. */
data class CharacterUpgradeSignature(val perLevel: Float) {
    init {
        require(perLevel.isFinite()) { "Upgrade signature steps must be finite" }
    }
}

/** Orbit charge arc −2%, Facet regrowth −3%, Puncture heat refund +0.8 per Refit level. */
fun defaultCharacterUpgradeSignature(shape: CoreShape): CharacterUpgradeSignature = when (shape) {
    CoreShape.ORB -> CharacterUpgradeSignature(-0.02f)
    CoreShape.PRISM -> CharacterUpgradeSignature(-0.03f)
    CoreShape.SHARD -> CharacterUpgradeSignature(0.8f)
}

internal const val CHARACTER_UPGRADE_BASE_COST: Int = 55
internal const val WEAPON_UPGRADE_BASE_COST: Int = 40

fun defaultWeaponUpgradeBaseCost(): Int = WEAPON_UPGRADE_BASE_COST
