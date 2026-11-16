// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.profile.impl.storage

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.MatrixNodeId
import kinetickk.ball.content.api.ProfilePolicySnapshot
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.profile.api.DAMAGE_NUMBER_TIER_THRESHOLD_OPTIONS
import kinetickk.ball.profile.api.FRAME_RATE_CAP_OPTIONS
import kinetickk.ball.profile.api.INTERFACE_SCALE_OPTIONS
import kinetickk.ball.profile.api.MAX_RECENT_ITEM_IDS
import kinetickk.ball.profile.api.PlayerProfile
import kinetickk.ball.profile.api.ProfileSnapshotRejection
import kinetickk.ball.profile.api.REFRESH_RATE_AUTO
import kinetickk.ball.profile.api.REFRESH_RATE_RANGE
import kinetickk.ball.profile.api.SIMULATION_SPEED_OPTIONS
import kotlin.math.roundToInt

internal fun validateProfile(profile: PlayerProfile, policy: ProfilePolicySnapshot) {
    val achievements = profile.characterAchievements
    rejectUnless(
        achievements.eliteKills >= 0L && achievements.dashHits >= 0L &&
            achievements.architectVictories >= 0L &&
            achievements.victoriousCharacters.size <= CoreShape.entries.size &&
            achievements.victoriousCharacters.size.toLong() <= achievements.architectVictories,
        ProfileSnapshotRejection.INCONSISTENT_PROFILE,
    )
    val preferences = profile.preferences
    rejectUnless(
        preferences.masterVolume.isFinite() && preferences.masterVolume in 0f..1f &&
            preferences.simulationSpeed.isFinite() && preferences.simulationSpeed in 0.75f..2f &&
            preferences.simulationSpeed in SIMULATION_SPEED_OPTIONS &&
            preferences.textScale.isFinite() && preferences.textScale in 1f..1.75f &&
            preferences.damageNumberTierThreshold in DAMAGE_NUMBER_TIER_THRESHOLD_OPTIONS &&
            preferences.frameRateCap in FRAME_RATE_CAP_OPTIONS &&
            (preferences.refreshRateHz == REFRESH_RATE_AUTO || preferences.refreshRateHz in REFRESH_RATE_RANGE) &&
            preferences.interfaceScalePercent in INTERFACE_SCALE_OPTIONS,
        ProfileSnapshotRejection.VALUE_OUT_OF_RANGE,
    )
    rejectUnless(
        profile.economy.matter >= 0L &&
            profile.economy.lifetimeMatter >= profile.economy.matter,
        ProfileSnapshotRejection.INCONSISTENT_PROFILE,
    )

    rejectUnless(
        profile.loadout.unlockedWeapons.isNotEmpty() &&
            profile.loadout.unlockedWeapons.size <= policy.weapons.size,
        ProfileSnapshotRejection.VALUE_OUT_OF_RANGE,
    )
    rejectUnless(
        profile.loadout.selectedWeapon in profile.loadout.unlockedWeapons,
        ProfileSnapshotRejection.INCONSISTENT_PROFILE,
    )

    rejectUnless(
        profile.matrix.ranks.size == MatrixNodeId.entries.size,
        ProfileSnapshotRejection.INCONSISTENT_PROFILE,
    )
    rejectUnless(
        MatrixNodeId.entries.all { id ->
            val definition = policy.matrixNodes.firstOrNull { it.id == id }
            val rank = profile.matrix.rank(id)
            rank == 0 || definition != null && rank in 1..definition.costs.size
        },
        ProfileSnapshotRejection.VALUE_OUT_OF_RANGE,
    )

    rejectUnless(
        profile.collection.discoveredItemIds.size <= policy.itemCount &&
            profile.collection.discoveredItemIds.all(policy::containsItem),
        ProfileSnapshotRejection.VALUE_OUT_OF_RANGE,
    )
    rejectUnless(
        profile.collection.discoveredItemIds.containsAll(profile.collection.newItemIds) &&
            profile.collection.discoveredRelicIds.containsAll(profile.collection.newRelicIds) &&
            profile.collection.discoveredRelicIds.size <= RelicId.entries.size,
        ProfileSnapshotRejection.INCONSISTENT_PROFILE,
    )
    val recent = profile.collection.recentItemIds
    rejectUnless(
        recent.size <= MAX_RECENT_ITEM_IDS && recent.all(policy::containsItem),
        ProfileSnapshotRejection.VALUE_OUT_OF_RANGE,
    )
    val upgrades = profile.upgrades
    rejectUnless(
        upgrades.characterLevels.size == CoreShape.entries.size && upgrades.weaponLevels.size == WeaponId.entries.size,
        ProfileSnapshotRejection.INCONSISTENT_PROFILE,
    )
    rejectUnless(
        upgrades.characterLevels.all { policy.upgrades.clampLevel(it) == it } &&
            upgrades.weaponLevels.all { policy.upgrades.clampLevel(it) == it },
        ProfileSnapshotRejection.VALUE_OUT_OF_RANGE,
    )
}

internal fun <T : Comparable<T>> List<T>.isStrictlySorted(): Boolean {
    for (index in 1 until size) {
        if (this[index - 1] >= this[index]) return false
    }
    return true
}

internal fun Float.toPercent(): Int {
    rejectUnless(isFinite(), ProfileSnapshotRejection.VALUE_OUT_OF_RANGE)
    return (this * 100f).roundToInt()
}

internal fun String.parseCanonicalNonNegativeLong(): Long {
    if (isEmpty() || this != "0" && (first() == '0' || any { it !in '0'..'9' })) {
        reject(ProfileSnapshotRejection.INVALID_DECIMAL)
    }
    if (this == "0") return 0L
    if (any { it !in '0'..'9' }) reject(ProfileSnapshotRejection.INVALID_DECIMAL)
    val value = toLongOrNull() ?: reject(ProfileSnapshotRejection.INVALID_DECIMAL)
    rejectUnless(value >= 0L && value.toString() == this, ProfileSnapshotRejection.INVALID_DECIMAL)
    return value
}
