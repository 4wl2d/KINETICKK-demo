// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.content.impl

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.EnemyType
import kinetickk.ball.content.api.ItemRarity
import kinetickk.ball.content.api.WeaponId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DemoContentCatalogTest {
    private val catalog = createContentCatalog()

    @Test
    fun playableStageCatalogIsComplete() {
        val gameplay = catalog.gameplayContent()
        val profile = catalog.profilePolicy()

        assertEquals(listOf(CoreShape.ORB, CoreShape.PRISM, CoreShape.SHARD), gameplay.coreShapes.map { it.id })
        assertEquals(
            listOf(
                WeaponId.FLUX_WAKE, WeaponId.MORNINGSTAR, WeaponId.PHASE_LATTICE, WeaponId.NULL_LANCE,
                WeaponId.GRAVITY_MINES, WeaponId.ARC_COIL, WeaponId.GYRE_DISC, WeaponId.SCATTER_BLOOM,
            ),
            gameplay.weapons.map { it.id },
        )
        assertEquals(
            mapOf(ItemRarity.COMMON to 34, ItemRarity.UNCOMMON to 46, ItemRarity.RARE to 32, ItemRarity.EPIC to 23),
            gameplay.items.groupingBy { it.rarity }.eachCount(),
        )
        assertEquals(135, profile.itemCount)
        assertFalse(profile.containsItem(135))
        assertNull(gameplay.item(135))
        assertEquals(11, gameplay.skills.size)
        gameplay.skills.forEach { assertNotNull(gameplay.activeItem(it.id)) }

        assertEquals(10, gameplay.relics.size)
        assertEquals(6, gameplay.build.pairings.size)
        assertEquals(31, gameplay.enemies.size)
        assertEquals(listOf(EnemyType.ARCHITECT), gameplay.enemies.filter { it.isBoss }.map { it.type })
    }

    @Test
    fun implementedUpgradeRowsSetPurchaseAndEffectCeilings() {
        val profile = catalog.profilePolicy()
        val upgrades = profile.upgrades

        assertEquals(listOf(55L, 110L, 220L), (0..2).map { upgrades.nextCost(55, it) })
        assertNull(upgrades.nextCost(55, 3))
        assertNull(upgrades.nextCost(55, Int.MAX_VALUE))
        assertEquals(3, upgrades.clampLevel(Int.MAX_VALUE))
        assertEquals(upgrades.characterPower(3), upgrades.characterPower(Int.MAX_VALUE))
        assertEquals(upgrades.characterIntegrity(3), upgrades.characterIntegrity(Int.MAX_VALUE))
        assertEquals(upgrades.weaponPower(3), upgrades.weaponPower(Int.MAX_VALUE))
        assertEquals(8, profile.matrixNodes.size)
        profile.matrixNodes.forEach { node ->
            assertEquals(2, node.maxRanks)
            assertNull(node.costs.getOrNull(2))
            assertEquals(node.spent(2), node.spent(Int.MAX_VALUE))
        }
    }
}
