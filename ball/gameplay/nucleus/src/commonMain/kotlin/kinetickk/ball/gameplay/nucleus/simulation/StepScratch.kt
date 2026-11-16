// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.gameplay.nucleus.model.Enemy
import kinetickk.ball.gameplay.nucleus.simulation.MutableGameState.Companion.MAX_CHAIN_SCRATCH
import kinetickk.ball.gameplay.nucleus.simulation.weapons.MAX_ARC_COIL_TARGETS

/**
 * Per-step scratch of the combat, elements and weapons domains: the status cue budget, bounded chain targeting, the
 * enemy a hit is shattering, and the buffers of the kill sweep, the projectile pass and Arc Coil, so a step allocates
 * none of them in steady state. A deep copy keeps [statusCuesThisStep] and reserves the same capacity for the
 * projectile pass in a fresh buffer. No IDs or enemy references from these passes survive the step.
 */
internal class StepScratch(source: StepScratch?) {
    /** Visual cue budget for statuses and reactions within the current fixed step. */
    internal var statusCuesThisStep: Int = source?.statusCuesThisStep ?: 0
    /** Slots of that budget held for the cues of events whose wave already promised their own look; zero between steps. */
    internal var heldStatusCues: Int = source?.heldStatusCues ?: 0
    /** Candidate-private scratch for bounded chain targeting; never read across steps. */
    internal val chainScratch: IntArray = IntArray(MAX_CHAIN_SCRATCH)
    /** Nesting level of [chainScratch] use within the current step; zero between steps. */
    internal var chainDepth: Int = 0
    /** The enemy whose Freeze the current hit is shattering, so that hit's clauses still see it Frozen; -1 between hits. */
    internal var shatteringEnemyId: Int = -1
    /** The enemies one kill sweep resolves, in list order; empty between sweeps. */
    internal val sweptKills: ArrayList<Enemy> = ArrayList()
    /** Sorted live enemy IDs in the pass's used prefix. Zeroed after the pass while keeping capacity for reuse. */
    internal var liveEnemyIds: IntArray = IntArray(source?.liveEnemyIds?.size ?: 0)
    /** The targets of one Arc Coil fire; all null between fires. */
    internal val arcCoilTargets: Array<Enemy?> = arrayOfNulls(MAX_ARC_COIL_TARGETS)
}
