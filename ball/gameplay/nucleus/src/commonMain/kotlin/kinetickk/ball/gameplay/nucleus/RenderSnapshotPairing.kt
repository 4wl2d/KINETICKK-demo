// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.nucleus

import kinetickk.ball.gameplay.nucleus.projection.projectionProvenance
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderSnapshot

/**
 * The pure path's pairing rule: [reusableSnapshot] must have been projected from [reusableState], the immediate
 * predecessor of [state] in the same instance and content, before either may stand in for a fresh projection.
 */
internal fun requireReusableRenderPair(
    state: GameplayState,
    reusableState: GameplayState,
    reusableSnapshot: GameplayRenderSnapshot,
) {
    val model = state.engine?.model
    val reusableSource = reusableState.engine?.model
    val reusableModel = reusableSnapshot.renderModel
    require(state.instanceId == reusableState.instanceId) {
        "Reusable Gameplay render source is not this instance's predecessor"
    }
    require(reusableState.revision.value < Long.MAX_VALUE) {
        "Reusable Gameplay render source revision cannot advance"
    }
    require(state.revision.value == reusableState.revision.value + 1L) {
        "Reusable Gameplay render source is not the immediately preceding revision"
    }
    require(reusableSnapshot.instanceId == reusableState.instanceId) {
        "Reusable Gameplay render snapshot belongs to another instance"
    }
    require(reusableSnapshot.revision == reusableState.revision) {
        "Reusable Gameplay render snapshot has a mismatched source revision"
    }
    require(reusableState.content === state.content) {
        "Reusable Gameplay render source belongs to different content"
    }
    require((reusableSource == null) == (reusableModel == null)) {
        "Reusable Gameplay render source/model nullability mismatch"
    }
    require(
        projectionProvenance(reusableSnapshot.projectionSourceIdentity) ===
            reusableState.engine?.renderProjectionIdentity,
    ) {
        "Reusable Gameplay render snapshot was not projected from its claimed source"
    }
    require(
        (reusableModel == null) == (reusableSnapshot.projectionSourceIdentity == null),
    ) {
        "Reusable Gameplay render provenance/model nullability mismatch"
    }
    if (reusableModel != null) {
        require(reusableModel.content === reusableState.content) {
            "Reusable Gameplay render model belongs to different content"
        }
    }
    if (model != null) {
        require(model.content === state.content) {
            "Gameplay render source belongs to different content"
        }
    }
}
