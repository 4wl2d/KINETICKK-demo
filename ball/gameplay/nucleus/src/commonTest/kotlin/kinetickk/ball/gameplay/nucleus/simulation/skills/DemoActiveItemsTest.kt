// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.nucleus.simulation.skills

import kinetickk.ball.content.impl.createContentCatalog
import kinetickk.ball.gameplay.nucleus.model.Enemy
import kinetickk.ball.gameplay.nucleus.model.ObstacleField
import kinetickk.ball.gameplay.nucleus.protocol.VisualFxCue
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.simulation.MutableGameState
import kinetickk.ball.gameplay.nucleus.simulation.obstacleField
import kinetickk.ball.gameplay.nucleus.simulation.progression.acquireItem
import kinetickk.ball.gameplay.nucleus.simulation.startRun
import kinetickk.ball.gameplay.nucleus.simulation.takeVisualFxCues
import kinetickk.ball.gameplay.nucleus.simulation.updatePointer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DemoActiveItemsTest {
    @Test
    fun everyActiveItemCastsAtItsShippedRanksAndSpendsItsCooldown() {
        val content = createContentCatalog().gameplayContent()
        for (item in content.items.filter { it.active != null }) {
            val skill = checkNotNull(item.active)
            for (rank in 1..item.maxStacks) {
                val run = MutableGameState(content).apply {
                    startRun()
                    obstacleField = ObstacleField.EMPTY
                    enemies.clear()
                    enemies.add(Enemy(10_000, EnemyType.DRIFTER, 200f, 0f, hp = 10_000f, maxHp = 10_000f, radius = 10f))
                    updatePointer(screenWidth / 2f + 320f, screenHeight / 2f)
                    repeat(rank) { acquireItem(item.id) }
                }
                run.takeVisualFxCues()

                assertTrue(run.castSkill(0), "$skill rank $rank")
                val cue = run.takeVisualFxCues().filterIsInstance<VisualFxCue.SkillCast>().first()
                assertEquals(skill, cue.skill)
                assertEquals(rank, cue.rank)
                assertFalse(run.castSkill(0), "$skill rank $rank on cooldown")
            }
        }
    }
}
