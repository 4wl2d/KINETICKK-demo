// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.profile.interaction.workshop

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.MatrixNodeId
import kinetickk.ball.content.api.UpgradePolicy
import kinetickk.ball.content.api.defaultCoreShapeDefinitions
import kinetickk.ball.content.api.defaultMatrixNodes
import kinetickk.ball.profile.api.MatrixProfileSnapshot
import kinetickk.ball.profile.api.MatrixProgress
import kinetickk.ball.profile.api.PlayerEconomy
import kinetickk.ball.profile.interaction.workshop.kit.characterUpgradeTrack
import kinetickk.ball.profile.interaction.workshop.kit.workshopUpgradeTexts
import kinetickk.ball.profile.interaction.workshop.matrix.MatrixNodeStatus
import kinetickk.ball.profile.interaction.workshop.matrix.toRenderModel
import kinetickk.foundation.common.localization.AppLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DemoWorkshopTest {
    @Test
    fun refitOffersTheFinalTierAndStopsEvenWithUnlimitedMatter() {
        val policy = UpgradePolicy()
        val orb = defaultCoreShapeDefinitions().first { it.id == CoreShape.ORB }
        val before = characterUpgradeTrack(policy, orb, level = 2, matter = Long.MAX_VALUE, unlocked = true)
        assertEquals(220L, before.nextPrice)
        assertTrue(before.canBuy)

        val top = characterUpgradeTrack(policy, orb, level = 3, matter = Long.MAX_VALUE, unlocked = true)
        val edited = characterUpgradeTrack(policy, orb, level = Int.MAX_VALUE, matter = Long.MAX_VALUE, unlocked = true)
        assertEquals(top, edited)
        assertEquals(3, top.maxLevel)
        assertFalse(top.canBuy)
        assertTrue(top.effects.all { it.next == null })
        val action = workshopUpgradeTexts(top, AppLanguage.English)
        assertEquals("Maximum", action.label)
        assertNull(action.cost)
    }

    @Test
    fun matrixShowsOnlyImplementedRanksAndDisablesPurchaseWhenMaxed() {
        val definitions = defaultMatrixNodes()
        val economy = PlayerEconomy(Long.MAX_VALUE)
        val almostMaxed = MatrixProfileSnapshot(economy, MatrixProgress(List(MatrixNodeId.entries.size) { 1 }))
            .toRenderModel(definitions)
        val edited = MatrixProfileSnapshot(economy, MatrixProgress(List(MatrixNodeId.entries.size) { Int.MAX_VALUE }))
            .toRenderModel(definitions)

        assertEquals(8, almostMaxed.nodeCount)
        assertTrue(almostMaxed.nodes.all { it.maxRanks == 2 && it.canBuy && it.nextCost > 0L })
        assertTrue(edited.nodes.all { it.rank == 2 && it.status == MatrixNodeStatus.MAXED && !it.canBuy })
        assertTrue(edited.nodes.all { it.nextCost == 0L })
    }
}
