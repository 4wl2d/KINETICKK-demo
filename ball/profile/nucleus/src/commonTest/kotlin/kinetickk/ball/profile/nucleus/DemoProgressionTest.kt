// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.profile.nucleus

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.MatrixNodeId
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.content.impl.createContentCatalog
import kinetickk.ball.profile.api.GameplayProgressUpdate
import kinetickk.ball.profile.api.MatrixProgress
import kinetickk.ball.profile.api.PermanentUpgrades
import kinetickk.ball.profile.api.PlayerEconomy
import kinetickk.ball.profile.api.ProfilePulse
import kinetickk.ball.profile.api.ProfileQuery
import kinetickk.ball.profile.api.ProfileRejection
import kinetickk.ball.profile.api.ProfileRevision
import kinetickk.ball.profile.api.ProfileRunBootstrapResult
import kinetickk.ball.profile.api.ProfileSnapshot
import kinetickk.ball.profile.api.ProfileSnapshotReadResult
import kinetickk.ball.profile.api.ProfileWriteResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DemoProgressionTest {
    private val policy = createContentCatalog().profilePolicy()

    @Test
    fun maxMatterCannotPurchaseAnUnlistedUpgrade() {
        var state = fundedProfile()
        val refit = ProfilePulse.PurchaseCharacterUpgrade(CoreShape.ORB)
        val honing = ProfilePulse.PurchaseWeaponUpgrade(WeaponId.FLUX_WAKE)
        assertEquals(3, policy.upgrades.maxLevel)
        repeat(3) {
            state = state.apply(ProfileNucleusPulse.Intent(refit))
            state = state.apply(ProfileNucleusPulse.Intent(honing))
        }
        val node = policy.matrixNodes.first()
        val matrix = ProfilePulse.PurchaseMatrixNode(node.id)
        repeat(node.costs.size) {
            state = state.apply(ProfileNucleusPulse.Intent(matrix))
        }
        assertTrue(state.profile.economy.matter > 1_000_000L)
        assertEquals(ProfileDecision.Rejected(ProfileRejection.UpgradeMaxLevel), state.purchase(refit))
        assertEquals(ProfileDecision.Rejected(ProfileRejection.UpgradeMaxLevel), state.purchase(honing))
        assertEquals(ProfileDecision.Rejected(ProfileRejection.MatrixNodeMaxRank), state.purchase(matrix))

        val reloaded = read(state)
        val run = assertIs<ProfileRunBootstrapResult.Ready>(
            ProfileNucleus.query(reloaded, ProfileQuery.GetRunBootstrap).result,
        ).snapshot
        assertEquals(3, run.upgrades.characterLevel(CoreShape.ORB))
        assertEquals(3, run.upgrades.weaponLevel(WeaponId.FLUX_WAKE))
        assertEquals(node.costs.size, run.matrix.rank(node.id))
    }

    @Test
    fun editedLevelsAndHigherEffectLookupsCannotExceedPublishedTiers() {
        val state = fundedProfile()
        val forgedLevel = List(CoreShape.entries.size) { Int.MAX_VALUE }
        val forgedWeaponLevel = List(WeaponId.entries.size) { Int.MAX_VALUE }
        val forgedMatrix = MatrixProgress(List(MatrixNodeId.entries.size) { Int.MAX_VALUE })
        val clean = ProfileState.initial(policy, ProfileSnapshotReadResult.Observed(null))
        assertEquals(clean.profile, read(state.copy(profile = state.profile.copy(
            upgrades = PermanentUpgrades(forgedLevel, forgedWeaponLevel),
        ))).profile)
        assertEquals(clean.profile, read(state.copy(profile = state.profile.copy(matrix = forgedMatrix))).profile)
        assertNull(policy.upgrades.nextCost(1, Int.MAX_VALUE))
        val last = policy.upgrades.maxLevel
        assertEquals(policy.upgrades.characterPower(last), policy.upgrades.characterPower(Int.MAX_VALUE))
        assertEquals(policy.upgrades.characterIntegrity(last), policy.upgrades.characterIntegrity(Int.MAX_VALUE))
        assertEquals(policy.upgrades.weaponPower(last), policy.upgrades.weaponPower(Int.MAX_VALUE))
    }

    @Test
    fun repeatedVictoriesOnlyRecordTheWinAndRunRewards() {
        val initial = ProfileState.initial(policy, ProfileSnapshotReadResult.Observed(null))
        val victory = ProfileNucleusPulse.ApplyGameplayProgress(
            GameplayProgressUpdate(bankedMatter = 123L, architectDefeatedWith = CoreShape.ORB),
        )
        val twice = initial.apply(victory).apply(victory)
        val progress = ProfileNucleus.query(twice, ProfileQuery.GetHomeProgress)
        assertEquals(246L, progress.economy.matter)
        assertEquals(2L, progress.characterAchievements.architectVictories)
        assertEquals(setOf(CoreShape.ORB), progress.unlockedCoreShapes.toSet())
        assertEquals(setOf(CoreShape.ORB), progress.characterAchievements.victoriousCharacters.toSet())
        assertEquals(listOf(CoreShape.ORB, CoreShape.PRISM, CoreShape.SHARD), progress.characterProgress.map { it.shape })
    }

    @Test
    fun demoAchievementsAndMatterCanUnlockOnlyThePublishedLoadouts() {
        var state = fundedProfile()
        assertEquals(
            ProfileDecision.Rejected(ProfileRejection.CoreShapeLocked),
            ProfileNucleus.decide(state, ProfileNucleusPulse.SelectCoreShape(CoreShape.PRISM)),
        )
        state = state.apply(ProfileNucleusPulse.ApplyGameplayProgress(
            GameplayProgressUpdate(eliteKills = Int.MAX_VALUE, dashHits = Int.MAX_VALUE),
        ))
        for (shape in listOf(CoreShape.PRISM, CoreShape.SHARD)) {
            state = state.apply(ProfileNucleusPulse.SelectCoreShape(shape))
            assertEquals(shape, state.profile.loadout.coreShape)
        }
        assertEquals(setOf(CoreShape.ORB, CoreShape.PRISM, CoreShape.SHARD), unlockedCoreShapes(state.profile, policy).toSet())
        for (weapon in policy.weapons.filter { it.id != WeaponId.FLUX_WAKE }) {
            state = state.apply(ProfileNucleusPulse.Intent(ProfilePulse.PurchaseOrEquipWeapon(weapon.id)))
        }
        assertEquals(8, state.profile.loadout.unlockedWeapons.size)
        assertEquals(WeaponId.entries.toSet(), state.profile.loadout.unlockedWeapons.toSet())
    }

    private fun fundedProfile(): ProfileState {
        val initial = ProfileState.initial(policy, ProfileSnapshotReadResult.Observed(null))
        return read(initial.copy(profile = initial.profile.copy(economy = PlayerEconomy(Long.MAX_VALUE))))
    }

    private fun read(state: ProfileState): ProfileState = ProfileState.initial(
        policy, ProfileSnapshotReadResult.Observed(ProfileSnapshot(ProfileRevision(20L), state.profile)),
    )

    private fun ProfileState.purchase(pulse: ProfilePulse.Business): ProfileDecision =
        ProfileNucleus.decide(this, ProfileNucleusPulse.Intent(pulse))

    private fun ProfileState.apply(pulse: ProfileNucleusPulse): ProfileState {
        val frame = assertIs<ProfileDecision.Accepted>(ProfileNucleus.decide(this, pulse)).frame
        val write = assertIs<ProfileOutput.PersistSnapshot>(frame.outputs.first())
        return ProfileNucleus.complete(
            frame.nextState, write.effectRef, ProfileWriteResult.Written(write.snapshot.revision),
        ).nextState
    }
}
