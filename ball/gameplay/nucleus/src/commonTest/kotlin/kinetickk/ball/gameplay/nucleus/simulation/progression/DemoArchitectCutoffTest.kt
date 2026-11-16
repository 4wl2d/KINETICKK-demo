// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.nucleus.simulation.progression

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.impl.createContentCatalog
import kinetickk.ball.gameplay.api.GameplayInteractionPulse
import kinetickk.ball.gameplay.nucleus.reducer.EngineState
import kinetickk.ball.gameplay.nucleus.reducer.GameReducer
import kinetickk.ball.gameplay.nucleus.reducer.GameReductionResult
import kinetickk.ball.gameplay.nucleus.reducer.initialEngineState
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.simulation.combat.damageEnemy
import kinetickk.ball.gameplay.nucleus.simulation.startRun
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DemoArchitectCutoffTest {
    @Test
    fun defeatingTheArchitectFinishesEachRunAndBanksMatterOnce() {
        val content = createContentCatalog().gameplayContent()
        val reducer = GameReducer()
        val frame = GameplayInteractionPulse.FrameElapsed.fromValidated(0.02f)

        for (seed in listOf(97, 98)) {
            val run = initialEngineState(content, seed, bootstrapProgress = null)
            run.model.startRun()
            assertEquals(GamePhase.RUNNING, run.model.phase)
            assertFalse(run.model.enemies.any { it.type == EnemyType.ARCHITECT })

            // Jump to the normal arrival time and let the simulation schedule the fight.
            run.model.elapsed = content.tempo.bossAtSeconds
            val encounter = reducer.apply(run, frame).state
            val architect = encounter.model.enemies.single { it.type == EnemyType.ARCHITECT }
            assertEquals(GamePhase.RUNNING, encounter.model.phase)

            // Set up the final strike and a reward queued on this very frame.
            architect.phase = checkNotNull(content.enemy(architect.type).encounter).phases - 1
            architect.shiftUntil = 0f
            architect.hp = 1f
            encounter.model.pendingChoice.pendingLevelChoices = 1
            assertTrue(encounter.model.damageEnemy(architect, 5f).amount > 0f)

            val victory = reducer.apply(encounter, frame)
            val finished = victory.state.model
            assertEquals(GamePhase.VICTORY, finished.phase)
            assertTrue(finished.choices.isEmpty())
            assertFalse(finished.enemies.any { it.type == EnemyType.ARCHITECT })
            val progress = assertNotNull(victory.outputs.progressUpdate)
            assertEquals(CoreShape.ORB, progress.architectDefeatedWith)
            assertTrue(progress.bankedMatter > 0L)

            val choice = GameplayInteractionPulse.ChoiceSelected.fromValidated(0)
            val afterChoice = reducer.apply(victory.state, choice)
            assertEquals(GamePhase.VICTORY, afterChoice.state.model.phase)
            assertTrue(afterChoice.state.model.choices.isEmpty())
            assertNull(afterChoice.outputs.progressUpdate)

            val afterFrame = reducer.apply(afterChoice.state, frame)
            assertEquals(GamePhase.VICTORY, afterFrame.state.model.phase)
            assertEquals(finished.elapsed, afterFrame.state.model.elapsed)
            assertNull(afterFrame.outputs.progressUpdate)
        }
    }

    private fun GameReducer.apply(
        state: EngineState,
        pulse: GameplayInteractionPulse,
    ): GameReductionResult.Accepted = assertIs<GameReductionResult.Accepted>(reduce(state, pulse))
}
