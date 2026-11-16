// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.nucleus.simulation.builds

import kinetickk.ball.content.api.ItemEffect
import kinetickk.ball.content.api.MatrixEffect
import kinetickk.ball.content.api.MatrixNodeDefinition
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.progression.*
import kotlin.math.max

/** The effects purchased in the demo's Core Matrix, folded once when a run starts. */
internal data class MatrixModifiers(
    val statTotals: List<Float> = List(ItemEffect.entries.size) { 0f },
    val integrityShare: Float = 0f,
    val shieldShare: Float = 0f,
    val dashHeatMultiplier: Float = 1f,
    val pickupValueBonus: Float = 0f,
    val dataStratumItems: Int = 0,
    val dataOfferWeight: Float = 1f,
) {
    fun statTotal(effect: ItemEffect): Float = statTotals[effect.ordinal]
}

/** Folds each node's purchased ranks from the same tier definitions that the menu uses. */
internal fun matrixModifiers(nodes: List<MatrixNodeDefinition>, rank: (index: Int) -> Int): MatrixModifiers {
    val stats = FloatArray(ItemEffect.entries.size)
    var result = MatrixModifiers()
    nodes.forEachIndexed { index, node ->
        val ranks = rank(index).coerceIn(0, node.costs.size)
        if (ranks == 0) return@forEachIndexed
        node.effects.forEach { effect ->
            result = result.with(effect, ranks, stats)
        }
    }
    return result.copy(statTotals = stats.toList())
}

private fun MatrixModifiers.with(effect: MatrixEffect, ranks: Int, stats: FloatArray): MatrixModifiers =
    when (effect) {
        is MatrixEffect.Stat -> {
            stats[effect.delta.effect.ordinal] += effect.delta.amount * ranks
            this
        }
        is MatrixEffect.IntegrityShare -> copy(integrityShare = integrityShare + effect.fraction * ranks)
        is MatrixEffect.ShieldShare -> copy(shieldShare = shieldShare + effect.fraction * ranks)
        is MatrixEffect.DashHeat -> copy(
            dashHeatMultiplier = max(0.1f, dashHeatMultiplier + (effect.multiplier - 1f) * ranks),
        )
        is MatrixEffect.PickupValue -> copy(pickupValueBonus = pickupValueBonus + effect.fraction * ranks)
        is MatrixEffect.DataStratum -> copy(dataStratumItems = dataStratumItems + effect.items * ranks)
        is MatrixEffect.DataOfferWeight -> copy(dataOfferWeight = dataOfferWeight + effect.fraction * ranks)
    }

/** Folds the owned Core Matrix ranks of this run's profile. */
internal fun MutableGameState.foldMatrix(): MatrixModifiers =
    matrixModifiers(content.matrixNodes) { index -> matrixRanks[index] }

/**
 * Applies the folded Matrix stat totals to the run-start stats, once per stat, with the same rules
 * as an item stat ([applyStatDelta]).
 */
internal fun MutableGameState.applyMatrixStats() {
    ItemEffect.entries.forEach { effect ->
        val total = matrix.statTotal(effect)
        if (total != 0f) applyStatDelta(effect, total)
    }
}

/**
 * [base] after one application of the Matrix total for [effect], mirroring [applyStatDelta] for a
 * single scalar; the build summary reports the difference as the Matrix contribution.
 */
internal fun MatrixModifiers.statValue(effect: ItemEffect, base: Float, pickupRadiusFloor: Float): Float {
    val amount = statTotal(effect)
    if (amount == 0f) return base
    return when (effect) {
        ItemEffect.MAGNETISM, ItemEffect.COOLING, ItemEffect.DASH_POWER -> base * max(0.2f, 1f + amount)
        ItemEffect.DASH_EFFICIENCY -> max(12f, base * (1f - amount.coerceAtMost(0.25f)))
        ItemEffect.CRIT_CHANCE -> (base + amount * (1f - base / 0.75f)).coerceIn(0f, 0.75f)
        ItemEffect.DAMAGE_REDUCTION -> (base + amount * (1f - base / 0.65f)).coerceIn(0f, 0.65f)
        ItemEffect.CRIT_DAMAGE -> max(1f, base + amount)
        ItemEffect.PICKUP_RADIUS -> max(pickupRadiusFloor, base + amount)
        ItemEffect.MAX_INTEGRITY -> max(10f, base + amount)
        else -> base + amount
    }
}

/**
 * The permanent shares of the character's base integrity a run starts with: the owned Matrix's
 * ([MatrixModifiers.integrityShare]) plus Refit's at [refitLevel].
 */
internal fun MutableGameState.permanentIntegrityShare(refitLevel: Int): Float =
    matrix.integrityShare + content.upgrades.characterIntegrity(refitLevel) - 1f

/** Maximum integrity a run never starts below, whatever its costs. */
internal const val MIN_RUN_INTEGRITY: Float = 10f
