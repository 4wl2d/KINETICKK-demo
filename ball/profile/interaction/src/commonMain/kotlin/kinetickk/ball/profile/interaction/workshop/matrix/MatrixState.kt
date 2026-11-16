// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.profile.interaction.workshop.matrix

import kinetickk.ball.content.api.MatrixBranch
import kinetickk.ball.content.api.MatrixNodeDefinition
import kinetickk.ball.content.api.MatrixNodeId
import kinetickk.ball.profile.api.MatrixProfileSnapshot
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.toImmutableList
import kinetickk.resource.audio.api.SoundId

internal sealed interface MatrixAction {
    /** The detail panel's Buy rank button for [id]. */
    data class PurchaseRequested(val id: MatrixNodeId) : MatrixAction

    /** Keyboard focus moves the selection (hover only highlights a tile). */
    data class Select(val id: MatrixNodeId) : MatrixAction

    /** Tile activation (click, tap, Enter): a press on the selected tile buys its next rank, otherwise it selects. */
    data class Activate(val id: MatrixNodeId) : MatrixAction

    /** The header Reset: the first press arms it, the second refunds every rank. */
    data object ResetRequested : MatrixAction

    data object Back : MatrixAction
}

/**
 * Matrix presentation state: the Profile-backed [model], the selected node ([selected] null = the
 * first node), whether Reset waits for its confirming press, and the purchase feedback [flash].
 */
internal data class MatrixState(
    val model: MatrixRenderModel,
    val selected: MatrixNodeId? = null,
    val resetArmed: Boolean = false,
    val flash: MatrixPurchaseFlash? = null,
) {
    val selectedNode: MatrixNodeRenderModel?
        get() = model.nodes.firstOrNull { it.id == selected } ?: model.nodes.firstOrNull()
}

/** Purchase feedback: the tile inverts for one frame and nudges (Pull); [sequence] restarts it. */
internal data class MatrixPurchaseFlash(val id: MatrixNodeId, val sequence: Int)

internal const val MATRIX_PURCHASE_FLASH_MS = 500

internal sealed interface MatrixUiEffect {
    data class Purchase(val id: MatrixNodeId) : MatrixUiEffect
    data object Reset : MatrixUiEffect
    data class PlayAudio(val cue: SoundId) : MatrixUiEffect
    data class Emit(val output: MatrixOutput) : MatrixUiEffect
}

internal data class MatrixReduction(
    val state: MatrixState,
    val effects: List<MatrixUiEffect> = emptyList(),
)

internal object MatrixReducer {
    fun reduce(state: MatrixState, action: MatrixAction): MatrixReduction = when (action) {
        is MatrixAction.PurchaseRequested -> MatrixReduction(
            state = state.copy(selected = action.id, resetArmed = false),
            effects = listOf(MatrixUiEffect.Purchase(action.id)),
        )
        is MatrixAction.Select -> MatrixReduction(
            if (state.selected == action.id) state else state.copy(selected = action.id, resetArmed = false),
        )
        is MatrixAction.Activate -> if (state.selectedNode?.id == action.id) {
            reduce(state, MatrixAction.PurchaseRequested(action.id))
        } else {
            MatrixReduction(state.copy(selected = action.id, resetArmed = false))
        }
        MatrixAction.ResetRequested -> when {
            state.model.ownedNodeCount == 0 -> MatrixReduction(state.copy(resetArmed = false))
            !state.resetArmed -> MatrixReduction(
                state = state.copy(resetArmed = true),
                effects = listOf(MatrixUiEffect.PlayAudio(SoundId.UI_CLICK)),
            )
            else -> MatrixReduction(state.copy(resetArmed = false), listOf(MatrixUiEffect.Reset))
        }
        MatrixAction.Back -> MatrixReduction(
            state = state.copy(resetArmed = false),
            effects = listOf(
                MatrixUiEffect.PlayAudio(SoundId.UI_CLICK),
                MatrixUiEffect.Emit(MatrixOutput.Back),
            ),
        )
    }

    /** Applies the Profile's answer to a purchase: a new model, and the flash when accepted. */
    fun purchased(state: MatrixState, id: MatrixNodeId, model: MatrixRenderModel, accepted: Boolean): MatrixState = state.copy(
        model = model,
        flash = if (accepted) MatrixPurchaseFlash(id, (state.flash?.sequence ?: 0) + 1) else state.flash,
    )

    /** Applies the Profile's answer to a reset: the refreshed model, disarmed. */
    fun reset(state: MatrixState, model: MatrixRenderModel): MatrixState = state.copy(model = model, resetArmed = false)
}

internal fun MatrixProfileSnapshot.toRenderModel(
    nodes: ImmutableList<MatrixNodeDefinition>,
): MatrixRenderModel {
    val ranks = nodes.associate { it.id to progress.rank(it.id).coerceIn(0, it.maxRanks) }
    val lanes = matrixLanes(nodes)
    val matter = economy.matter
    return MatrixRenderModel(
        matter = matter,
        nodes = nodes.map { definition ->
            val rank = ranks.getValue(definition.id)
            val maxed = rank >= definition.maxRanks
            val cost = if (maxed) 0L else definition.cost(rank).toLong()
            val status = when {
                maxed -> MatrixNodeStatus.MAXED
                matter < cost -> MatrixNodeStatus.SHORT_OF_MATTER
                else -> MatrixNodeStatus.AVAILABLE
            }
            MatrixNodeRenderModel(
                id = definition.id,
                name = definition.name,
                description = definition.description,
                branch = definition.branch,
                lane = lanes.getValue(definition.id),
                rank = rank,
                maxRanks = definition.maxRanks,
                nextCost = cost,
                status = status,
            )
        }.toImmutableList(),
        refund = nodes.sumOf { it.spent(ranks.getValue(it.id)) },
    )
}

/** Catalog order sets the tile order in each branch. */
private fun matrixLanes(nodes: List<MatrixNodeDefinition>): Map<MatrixNodeId, Int> = buildMap {
    for (branch in MatrixBranch.entries) {
        nodes.filter { it.branch == branch }.forEachIndexed { lane, node -> put(node.id, lane) }
    }
}

/** Owned nodes over all nodes. */
internal fun MatrixRenderModel.nodeTotals(): Pair<Int, Int> = ownedNodeCount to nodeCount
