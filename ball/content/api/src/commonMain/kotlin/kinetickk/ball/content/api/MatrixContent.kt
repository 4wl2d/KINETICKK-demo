// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.content.api

import kinetickk.foundation.collections.ImmutableList

/** The four Core Matrix branches; each is one column of the Matrix screen. */
enum class MatrixBranch(val displayLabel: String) {
    KINETIC("Kinetic"),
    BULWARK("Bulwark"),
    SYSTEMS("Systems"),
    FORTUNE("Fortune"),
}

/**
 * Stable Core Matrix node ids. Profiles persist them by name and index ranks by ordinal, so new
 * nodes are appended and existing ids are never renamed or removed.
 */
enum class MatrixNodeId {
    IMPACT_DRIVER,
    WEAPON_CALIBRATION,
    REINFORCED_HULL,
    HARDLIGHT_RESERVE,
    MAGNET_ARRAY,
    CRYO_LOOP,
    SALVAGE_PROTOCOL,
    DATA_ARCHIVE,
}

/** Permanent effects, applied once per purchased rank at run start. */
sealed interface MatrixEffect {
    /** A passive stat change per rank, with the same rules as an item stat. */
    data class Stat(val delta: StatDelta) : MatrixEffect

    /**
     * Maximum integrity + [fraction] of the character's base integrity (the class's integrity with its traits) per
     * rank, added to Refit's share at run start; negative for a cost.
     */
    data class IntegrityShare(val fraction: Float) : MatrixEffect {
        init {
            require(fraction.isFinite() && fraction != 0f && fraction in -MAX_INTEGRITY_COST..2f) {
                "Integrity share must be within -$MAX_INTEGRITY_COST..2 and not 0"
            }
        }
    }

    /** Shield capacity + [fraction] of the current maximum integrity per rank, so the shield grows with integrity. */
    data class ShieldShare(val fraction: Float) : MatrixEffect {
        init { requireFraction(fraction) }
    }

    /** Dash heat cost × multiplier. */
    data class DashHeat(val multiplier: Float) : MatrixEffect {
        init { requireMultiplier(multiplier) }
    }

    /** Data from each Data pickup × (1 + fraction). */
    data class PickupValue(val fraction: Float) : MatrixEffect {
        init { requireFraction(fraction) }
    }

    /**
     * The run's draft deck deals [items] more Data-economy items (the level-42 Data ladder) per rank, on top of
     * the deck policy's Data floor: a profile that invested in Data finds its Data cards.
     */
    data class DataStratum(val items: Int) : MatrixEffect {
        init { require(items in 1..MAX_DATA_STRATUM_ITEMS) { "Data stratum items must be 1..$MAX_DATA_STRATUM_ITEMS" } }
    }

    /**
     * Data-economy items ([ItemDefinition.isDataEconomy]) weigh × (1 + [fraction] × ranks) when an ordinary item offer
     * picks a card in any lane, so a profile that invested in Data sees its Data cards more often. It changes only the
     * pick's weights: the offer draws exactly as many reward values as without it.
     */
    data class DataOfferWeight(val fraction: Float) : MatrixEffect {
        init { requireFraction(fraction) }
    }
}

private const val MAX_INTEGRITY_COST: Float = 0.9f
private const val MAX_DATA_STRATUM_ITEMS: Int = 5

private fun requireFraction(value: Float) {
    require(value.isFinite() && value > 0f && value <= 2f) { "Matrix fraction must be within 0..2" }
}

private fun requireMultiplier(value: Float) {
    require(value.isFinite() && value > 0f && value <= 3f && value != 1f) { "Matrix multiplier must be within 0..3 and not 1" }
}

/** One purchasable Matrix node. Each entry in [costs] defines an implemented rank. */
data class MatrixNodeDefinition(
    val id: MatrixNodeId,
    val name: String,
    val description: String,
    val branch: MatrixBranch,
    val row: Int,
    val costs: ImmutableList<Int>,
    val effects: ImmutableList<MatrixEffect>,
) {
    init {
        require(name.isNotBlank()) { "Matrix node name must not be blank" }
        require(description.isNotBlank()) { "Matrix node description must not be blank" }
        require(row in 0 until ContentBounds.MAX_MATRIX_ROWS) { "Matrix node row must be within the row bound" }
        require(costs.isNotEmpty() && costs.all { it > 0 }) { "Matrix node costs must be positive" }
        require(effects.size in 1..ContentBounds.MAX_MATRIX_NODE_EFFECTS) { "Matrix node needs 1..4 effects" }
    }

    val maxRanks: Int get() = costs.size

    fun cost(owned: Int): Int = costs[owned]

    fun spent(owned: Int): Long = costs.take(owned.coerceIn(0, maxRanks)).sumOf(Int::toLong)
}
