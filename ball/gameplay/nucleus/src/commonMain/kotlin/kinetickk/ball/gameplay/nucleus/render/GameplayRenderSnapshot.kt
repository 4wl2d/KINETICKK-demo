// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.nucleus.render

import kinetickk.ball.gameplay.api.GameplayInstanceId
import kinetickk.ball.gameplay.api.GameplayRevision

/** Ball-internal Nucleus-to-Interaction snapshot; not a Gameplay Application Surface. */
@ConsistentCopyVisibility
data class GameplayRenderSnapshot internal constructor(
    val instanceId: GameplayInstanceId,
    val revision: GameplayRevision,
    val renderModel: GameplayRenderModel?,
    internal val projectionSourceIdentity: Any?,
    /**
     * Accumulated real time past the last simulated state as a fraction of [GameplayRenderModel.FIXED_STEP] (0..1),
     * the one value a frame without a fixed step changes: such a frame republishes the same [renderModel] with a new
     * alpha. Renderers draw moving things at `previous + (current - previous) * alpha`; it is 1 whenever the run is
     * not running, so paused and terminal frames show the exact state, and until the first step after a run starts
     * or resumes (its previous positions are older than one step).
     */
    val interpolationAlpha: Float,
)
