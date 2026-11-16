// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.nucleus.simulation.characters

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.gameplay.nucleus.simulation.*
import kotlin.math.max

// ---------------------------------------------------------------------------------------------
// Refit signatures. Each mechanic reads its one upgradable constant through an accessor that applies the
// selected character's Refit level with the content step ([kinetickk.ball.content.api.CharacterUpgradeSignature]);
// the render mapper reads the same accessors.

/** Orbit: the turn arc that charges the next impact (Refit shortens it by a share per level). */
internal fun MutableGameState.orbitArc(): Float = refitSignature(CoreShape.ORB, ORBIT_ARC, share = true)

/** Facets: seconds to regrow one facet (Refit shortens them by a share per level). */
internal fun MutableGameState.facetRegenSeconds(): Float =
    refitSignature(CoreShape.PRISM, FACET_REGEN_SECONDS, share = true)

/** Puncture: heat vented by a puncture. */
internal fun MutableGameState.punctureHeatRefund(): Float = refitSignature(CoreShape.SHARD, PUNCTURE_HEAT_REFUND)

/**
 * [base] moved by the Refit step of [shape] times the run's Refit level: added, or as a [share] of [base]. Another
 * character, or level 0, reads [base] itself. A step never takes a value below [REFIT_SIGNATURE_FLOOR_SHARE] of
 * [base].
 */
private fun MutableGameState.refitSignature(
    shape: CoreShape,
    base: Float,
    share: Boolean = false,
): Float {
    val level = content.upgrades.clampLevel(characterUpgradeLevel)
    if (level == 0 || coreShape != shape) return base
    val signature = content.coreShape(shape).upgradeSignature
    val step = signature.perLevel * level
    return max(base * REFIT_SIGNATURE_FLOOR_SHARE, if (share) base * (1f + step) else base + step)
}
