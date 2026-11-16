// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.nucleus.simulation.characters

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.impl.createContentCatalog
import kinetickk.ball.gameplay.nucleus.model.Enemy
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.simulation.MutableGameState
import kinetickk.ball.gameplay.nucleus.simulation.characterRuntime
import kinetickk.ball.gameplay.nucleus.simulation.combat.takeDamage
import kinetickk.ball.gameplay.nucleus.simulation.coreShape
import kinetickk.ball.gameplay.nucleus.simulation.flight.performDash
import kinetickk.ball.gameplay.nucleus.simulation.startRun
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DemoCoreMechanicsTest {
    @Test
    fun orbReleasesChargedOrbitOnASkillCast() {
        val run = start(CoreShape.ORB)
        val target = dummy()
        run.enemies.add(target)
        run.characterRuntime.charge = 1f

        run.onCharacterSkillCast(target.x, target.y, target)

        assertFalse(run.orbitCharged)
        assertTrue(target.hp < target.maxHp)
    }

    @Test
    fun prismSpendsAFacetToStopAnIncomingHit() {
        val run = start(CoreShape.PRISM)
        val integrity = run.hp
        val facets = run.intactFacets

        run.takeDamage(20f)

        assertEquals(integrity, run.hp)
        assertEquals(facets - 1, run.intactFacets)
    }

    @Test
    fun shardDashMarksFoeForPuncture() {
        val run = start(CoreShape.SHARD)
        val target = dummy()

        run.performDash()
        run.onCharacterDashContact(target)
        assertTrue(run.isMarked(target))

        run.onCharacterPrimaryHit(target)
        assertFalse(run.isMarked(target))
        assertTrue(target.hp < target.maxHp)
    }

    private fun start(shape: CoreShape): MutableGameState =
        MutableGameState(createContentCatalog().gameplayContent()).apply {
            coreShape = shape
            startRun()
        }

    private fun dummy() = Enemy(id = 100_000, type = EnemyType.DRIFTER, x = 20f, y = 0f, hp = 10_000f, maxHp = 10_000f, radius = 10f)
}
