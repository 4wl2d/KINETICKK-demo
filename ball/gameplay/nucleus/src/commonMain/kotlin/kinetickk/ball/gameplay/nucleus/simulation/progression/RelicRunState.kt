// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.nucleus.simulation.progression

import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.gameplay.nucleus.model.EchoHit
import kinetickk.ball.gameplay.nucleus.model.RunFloatArray
import kinetickk.ball.gameplay.nucleus.model.RunIntArray
import kinetickk.ball.gameplay.nucleus.model.SeveredRelic

/**
 * Relic ranks, cooldowns, echo hits, severed Relics, and the run's Relic budget and Melds.
 * Relic progression owns ranks, severance, the budget, and Melds. Relic rules own cooldowns and echo hits.
 */
internal class RelicRunState(
    content: GameplayContentSnapshot,
    source: RelicRunState?,
) {
    internal val relicRanks: RunIntArray = source?.relicRanks?.copy()
        ?: RunIntArray(IntArray(content.relics.size))
    internal val relicCooldowns: RunFloatArray = source?.relicCooldowns?.copy()
        ?: RunFloatArray(FloatArray(content.relics.size))
    internal val echoHits: MutableList<EchoHit> = source?.echoHits
        ?.let { hits ->
            hits.mapTo(ArrayList(hits.size), EchoHit::copy)
        }
        ?: mutableListOf()
    internal var severedRelics: List<SeveredRelic> = source?.severedRelics ?: emptyList()
    /** Relic opportunities claimed by opening Relic offers this run. */
    internal var relicClaims: Int = source?.relicClaims ?: 0
    /** Relic slots sealed by a Meld this run; they stay empty and no Relic binds there. */
    internal var meldedSlots: Int = source?.meldedSlots ?: 0
    /** The Melds' multiplier on all damage dealt (1 without a Meld). */
    internal var meldDamageMultiplier: Float = source?.meldDamageMultiplier ?: 1f

    /** Clears run-scoped relic state before a new run. */
    fun reset() {
        relicRanks.fill(0)
        relicCooldowns.fill(0f)
        echoHits.clear()
        severedRelics = emptyList()
        relicClaims = 0
        meldedSlots = 0
        meldDamageMultiplier = 1f
    }
}
