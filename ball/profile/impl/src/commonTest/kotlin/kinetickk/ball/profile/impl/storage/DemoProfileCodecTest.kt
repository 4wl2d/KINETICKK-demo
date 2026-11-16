// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.profile.impl.storage

import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.KINETICKK_CONTENT_VERSION
import kinetickk.ball.content.api.MatrixNodeId
import kinetickk.ball.content.api.ProfilePolicySnapshot
import kinetickk.ball.content.api.RelicKey
import kinetickk.ball.content.api.WeaponCadence
import kinetickk.ball.content.api.WeaponDefinition
import kinetickk.ball.content.api.WeaponFamily
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.content.api.WeaponScaling
import kinetickk.ball.content.api.defaultCoreShapeDefinitions
import kinetickk.ball.profile.api.MatrixProgress
import kinetickk.ball.profile.api.PermanentUpgrades
import kinetickk.ball.profile.api.PlayerEconomy
import kinetickk.ball.profile.api.PlayerProfile
import kinetickk.ball.profile.api.ProfileRevision
import kinetickk.ball.profile.api.ProfileSnapshot
import kinetickk.ball.profile.api.ProfileSnapshotRejection
import kinetickk.foundation.collections.immutableListOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals

class DemoProfileCodecTest {
    private val policy = ProfilePolicySnapshot(
        version = KINETICKK_CONTENT_VERSION,
        itemCount = 2,
        coreShapes = defaultCoreShapeDefinitions(),
        weapons = immutableListOf(WeaponDefinition(
            id = WeaponId.FLUX_WAKE,
            name = "Test weapon",
            description = "The starting weapon for save round trips.",
            family = WeaponFamily.MOMENTUM,
            element = null,
            tags = listOf(BuildTag.DRIVE, BuildTag.OVERCLOCK),
            key = RelicKey.MOTION,
            signature = null,
            scaling = WeaponScaling.SPEED,
            cadence = WeaponCadence.CONTINUOUS,
            permanentUnlockCost = 0,
            masteryForms = listOf("I", "II", "III"),
            signatureText = null,
        )),
    )
    private val node = policy.matrixNode(MatrixNodeId.IMPACT_DRIVER)
    private val maxLevel = policy.upgrades.maxLevel

    private val cappedSnapshot = ProfileSnapshot(
        revision = ProfileRevision(7L),
        profile = PlayerProfile(
            economy = PlayerEconomy(matter = Long.MAX_VALUE, lifetimeMatter = Long.MAX_VALUE),
            matrix = MatrixProgress(MatrixNodeId.entries.map { if (it == node.id) node.costs.size else 0 }),
            upgrades = PermanentUpgrades(
                characterLevels = CoreShape.entries.map { if (it == CoreShape.ORB) maxLevel else 0 },
                weaponLevels = WeaponId.entries.map { if (it == WeaponId.FLUX_WAKE) maxLevel else 0 },
            ),
        ),
    )

    @Test
    fun cappedLevelsSurviveReloadWithExtraMatter() {
        val encoded = assertIs<ProfileEncodeResult.Encoded>(ProfileCodec.encode(cappedSnapshot, policy))
        val decoded = assertIs<ProfileDecodeResult.Decoded>(ProfileCodec.decode(encoded.payload, policy))
        assertEquals(cappedSnapshot, decoded.snapshot)
    }

    @Test
    fun editsCannotAddATierToAnyPermanentTrack() {
        val encoded = assertIs<ProfileEncodeResult.Encoded>(ProfileCodec.encode(cappedSnapshot, policy)).payload
        listOf(
            node.id.wireId() to node.costs.size,
            CoreShape.ORB.wireId() to maxLevel,
            WeaponId.FLUX_WAKE.wireId() to maxLevel,
        ).forEach { (id, rank) ->
            val edited = encoded.replace(rankRecord(id, rank), rankRecord(id, rank + 1))
            assertNotEquals(encoded, edited)
            val rejected = assertIs<ProfileDecodeResult.Rejected>(ProfileCodec.decode(edited, policy))
            assertEquals(ProfileSnapshotRejection.VALUE_OUT_OF_RANGE, rejected.reason)
        }
    }

    @Test
    fun editsCannotLoadUnknownContent() {
        val encoded = assertIs<ProfileEncodeResult.Encoded>(ProfileCodec.encode(cappedSnapshot, policy)).payload
        val unlistedCharacter = encoded.replace(
            """"coreShapeId":"${CoreShape.ORB.wireId()}"""",
            """"coreShapeId":"UNKNOWN_CHARACTER"""",
        )
        val unlistedWeapon = encoded.replace(
            """"unlockedWeaponIds":["${WeaponId.FLUX_WAKE.wireId()}"]""",
            """"unlockedWeaponIds":["${WeaponId.FLUX_WAKE.wireId()}","UNKNOWN_WEAPON"]""",
        )
        for (edited in listOf(unlistedCharacter, unlistedWeapon)) {
            assertNotEquals(encoded, edited)
            val rejected = assertIs<ProfileDecodeResult.Rejected>(ProfileCodec.decode(edited, policy))
            assertEquals(ProfileSnapshotRejection.INVALID_STABLE_ID, rejected.reason)
        }
        val unlistedItem = encoded.replace(
            """"discoveredItemIds":[]""",
            """"discoveredItemIds":[${policy.itemCount}]""",
        )
        assertNotEquals(encoded, unlistedItem)
        val rejected = assertIs<ProfileDecodeResult.Rejected>(ProfileCodec.decode(unlistedItem, policy))
        assertEquals(ProfileSnapshotRejection.VALUE_OUT_OF_RANGE, rejected.reason)
    }

    private fun rankRecord(id: String, rank: Int): String = """{"id":"$id","rank":$rank}"""
}
