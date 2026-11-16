// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.profile.impl.storage

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

@Serializable
internal data class ProfileSnapshotDto(
    val revision: String,
    val profile: PlayerProfileDto,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
internal data class PlayerProfileDto(
    val preferences: PlayerPreferencesDto,
    val economy: PlayerEconomyDto,
    val loadout: PlayerLoadoutDto,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val matrix: MatrixProgressDto = MatrixProgressDto(emptyList()),
    val collection: PlayerCollectionDto,
    val characterAchievements: CharacterAchievementProgressDto,
    // Omit empty Refit and Honing tracks.
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val upgrades: PermanentUpgradesDto = PermanentUpgradesDto(),
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
internal data class PlayerPreferencesDto(
    val soundEnabled: Boolean,
    val musicEnabled: Boolean,
    val masterVolumePercent: Int,
    val simulationSpeedPercent: Int,
    val textScalePercent: Int,
    val screenShake: Boolean,
    val particleDensityId: String,
    val damageNumbers: Boolean,
    val damageNumberSizeId: String,
    val damageNumberFormatId: String,
    val damageNumberTierThreshold: Int,
    val languageCode: String,
    val runStatisticsOnLeft: Boolean,
    // Absence means ColorVision.DEFAULT.
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val colorVisionId: String? = null,
    // Absence means the minimap is on.
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val minimapEnabled: Boolean? = null,
    // Optional display and control fields are stored only for non-default values.
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val windowModeId: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val windowSizeId: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val vsyncEnabled: Boolean? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val frameRateCap: Int? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val refreshRateHz: Int? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val immersiveEnabled: Boolean? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val interfaceScalePercent: Int? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val showFps: Boolean? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val pauseOnFocusLoss: Boolean? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val reactionEffectsId: String? = null,
    // Rebound actions only, strictly sorted by action wire id; absent while every key is the default.
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val keyBindings: List<KeyBindingDto> = emptyList(),
)

@Serializable
internal data class KeyBindingDto(
    val action: String,
    val key: String,
)

@Serializable
internal data class PlayerEconomyDto(
    val matter: String,
    val lifetimeMatter: String,
)

@Serializable
internal data class PlayerLoadoutDto(
    val coreShapeId: String,
    val selectedWeaponId: String,
    val unlockedWeaponIds: List<String>,
)

@Serializable
internal data class MatrixProgressDto(
    val ranks: List<ProfileRankDto>,
)

@Serializable
internal data class ProfileRankDto(
    val id: String,
    val rank: Int,
)

/** Refit and Honing ranks by character and weapon id, respectively. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
internal data class PermanentUpgradesDto(
    @EncodeDefault(EncodeDefault.Mode.NEVER) val c: List<ProfileRankDto> = emptyList(),
    @EncodeDefault(EncodeDefault.Mode.NEVER) val w: List<ProfileRankDto> = emptyList(),
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
internal data class PlayerCollectionDto(
    val discoveredItemIds: List<Int>,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val newItemIds: List<Int> = emptyList(),
    @EncodeDefault(EncodeDefault.Mode.NEVER) val discoveredRelicIds: List<String> = emptyList(),
    @EncodeDefault(EncodeDefault.Mode.NEVER) val newRelicIds: List<String> = emptyList(),
    @EncodeDefault(EncodeDefault.Mode.NEVER) val discoveredReactionIds: List<String> = emptyList(),
    @EncodeDefault(EncodeDefault.Mode.NEVER) val discoveredPairingIds: List<String> = emptyList(),
    // Recent first acquisitions, oldest first with repeats.
    @EncodeDefault(EncodeDefault.Mode.NEVER) val recentItemIds: List<Int> = emptyList(),
    // World discoveries as bits of WorldDiscovery.ordinal.
    @EncodeDefault(EncodeDefault.Mode.NEVER) val worldDiscoveryMask: Int = 0,
)

@Serializable
internal data class CharacterAchievementProgressDto(
    val eliteKills: String,
    val dashHits: String,
    val architectVictories: String,
    val victoriousCharacterIds: List<String>,
)
