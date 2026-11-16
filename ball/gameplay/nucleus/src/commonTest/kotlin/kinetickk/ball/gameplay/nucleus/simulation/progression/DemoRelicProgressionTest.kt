// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.nucleus.simulation.progression

import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.RelicPairingId
import kinetickk.ball.content.impl.createContentCatalog
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.render.RelicChoiceAction
import kinetickk.ball.gameplay.nucleus.simulation.MutableGameState
import kinetickk.ball.gameplay.nucleus.simulation.choose
import kinetickk.ball.gameplay.nucleus.simulation.discoveredPairingMask
import kinetickk.ball.gameplay.nucleus.simulation.rerollChoices
import kinetickk.ball.gameplay.nucleus.simulation.rerollsRemaining
import kinetickk.ball.gameplay.nucleus.simulation.startRun
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DemoRelicProgressionTest {
    @Test
    fun aRelicOfferConsumesOneOpportunityEvenAfterReroll() {
        val run = MutableGameState(createContentCatalog().gameplayContent(), seed = 81)
        run.startRun()
        val budget = run.relicBudgetLeft()

        run.openRelicChoice()
        assertEquals(GamePhase.CHOICE, run.phase)
        assertEquals(budget - 1, run.relicBudgetLeft())
        run.rerollsRemaining = 1
        run.rerollChoices()
        assertEquals(0, run.rerollsRemaining)
        val acquire = run.choices.indexOfFirst { it.relicAction == RelicChoiceAction.ACQUIRE }
        assertTrue(acquire >= 0)
        val relic = run.choices[acquire].relicId
        run.choose(acquire)
        assertEquals(relic, run.equippedRelics.single().id)
        assertEquals(budget - 1, run.relicBudgetLeft())
    }

    @Test
    fun aDemoRelicPairStillCompilesAndBecomesDiscovered() {
        val run = MutableGameState(createContentCatalog().gameplayContent(), seed = 82)
        run.startRun()
        run.acquireRelic(RelicId.PERPETUAL_VECTOR)
        run.acquireRelic(RelicId.GYRE_ENGINE)

        assertTrue(run.build.pairing(RelicPairingId.ENDLESS_GYRE))
        assertTrue(run.discoveredPairingMask and (1 shl RelicPairingId.ENDLESS_GYRE.ordinal) != 0)
    }
}
