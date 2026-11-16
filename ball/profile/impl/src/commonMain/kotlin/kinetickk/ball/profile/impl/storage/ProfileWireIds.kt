// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.profile.impl.storage

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.MatrixNodeId
import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.RelicPairingId
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.profile.api.ColorVision
import kinetickk.ball.profile.api.DamageNumberFormat
import kinetickk.ball.profile.api.DamageNumberSize
import kinetickk.ball.profile.api.GameAction
import kinetickk.ball.profile.api.ParticleDensity
import kinetickk.ball.profile.api.ProfileSnapshotRejection
import kinetickk.ball.profile.api.ReactionEffectsLevel
import kinetickk.ball.profile.api.WindowMode
import kinetickk.ball.profile.api.WindowSizePreset
import kinetickk.foundation.common.localization.AppLanguage

internal fun CoreShape.wireId(): String = when (this) {
    CoreShape.ORB -> "ORB"
    CoreShape.PRISM -> "PRISM"
    CoreShape.SHARD -> "SHARD"
}

internal fun String.coreShape(): CoreShape = when (this) {
    "ORB" -> CoreShape.ORB
    "PRISM" -> CoreShape.PRISM
    "SHARD" -> CoreShape.SHARD
    else -> reject(ProfileSnapshotRejection.INVALID_STABLE_ID)
}

internal fun WeaponId.wireId(): String = when (this) {
    WeaponId.FLUX_WAKE -> "FLUX_WAKE"
    WeaponId.MORNINGSTAR -> "MORNINGSTAR"
    WeaponId.PHASE_LATTICE -> "PHASE_LATTICE"
    WeaponId.NULL_LANCE -> "NULL_LANCE"
    WeaponId.GRAVITY_MINES -> "GRAVITY_MINES"
    WeaponId.ARC_COIL -> "ARC_COIL"
    WeaponId.GYRE_DISC -> "GYRE_DISC"
    WeaponId.SCATTER_BLOOM -> "SCATTER_BLOOM"
}

internal fun String.weaponId(): WeaponId = when (this) {
    "FLUX_WAKE" -> WeaponId.FLUX_WAKE
    "MORNINGSTAR" -> WeaponId.MORNINGSTAR
    "PHASE_LATTICE" -> WeaponId.PHASE_LATTICE
    "NULL_LANCE" -> WeaponId.NULL_LANCE
    "GRAVITY_MINES" -> WeaponId.GRAVITY_MINES
    "ARC_COIL" -> WeaponId.ARC_COIL
    "GYRE_DISC" -> WeaponId.GYRE_DISC
    "SCATTER_BLOOM" -> WeaponId.SCATTER_BLOOM
    else -> reject(ProfileSnapshotRejection.INVALID_STABLE_ID)
}

/** Core Matrix wire ids: stable names, never renamed or reused. */
internal fun MatrixNodeId.wireId(): String = when (this) {
    MatrixNodeId.IMPACT_DRIVER -> "IMPACT_DRIVER"
    MatrixNodeId.WEAPON_CALIBRATION -> "WEAPON_CALIBRATION"
    MatrixNodeId.REINFORCED_HULL -> "REINFORCED_HULL"
    MatrixNodeId.HARDLIGHT_RESERVE -> "HARDLIGHT_RESERVE"
    MatrixNodeId.MAGNET_ARRAY -> "MAGNET_ARRAY"
    MatrixNodeId.CRYO_LOOP -> "CRYO_LOOP"
    MatrixNodeId.SALVAGE_PROTOCOL -> "SALVAGE_PROTOCOL"
    MatrixNodeId.DATA_ARCHIVE -> "DATA_ARCHIVE"
}

internal fun String.matrixNodeId(): MatrixNodeId = when (this) {
    "IMPACT_DRIVER" -> MatrixNodeId.IMPACT_DRIVER
    "WEAPON_CALIBRATION" -> MatrixNodeId.WEAPON_CALIBRATION
    "REINFORCED_HULL" -> MatrixNodeId.REINFORCED_HULL
    "HARDLIGHT_RESERVE" -> MatrixNodeId.HARDLIGHT_RESERVE
    "MAGNET_ARRAY" -> MatrixNodeId.MAGNET_ARRAY
    "CRYO_LOOP" -> MatrixNodeId.CRYO_LOOP
    "SALVAGE_PROTOCOL" -> MatrixNodeId.SALVAGE_PROTOCOL
    "DATA_ARCHIVE" -> MatrixNodeId.DATA_ARCHIVE
    else -> reject(ProfileSnapshotRejection.INVALID_STABLE_ID)
}

/** Stable wire ids for the demo Relic catalog. */
internal fun RelicId.wireId(): String = when (this) {
    RelicId.PERPETUAL_VECTOR -> "PERPETUAL_VECTOR"
    RelicId.GYRE_ENGINE -> "GYRE_ENGINE"
    RelicId.GLASS_COMET -> "GLASS_COMET"
    RelicId.ECHO_CHAMBER -> "ECHO_CHAMBER"
    RelicId.FURNACE_COVENANT -> "FURNACE_COVENANT"
    RelicId.ABSOLUTE_ZERO -> "ABSOLUTE_ZERO"
    RelicId.STORMHEART_CAPACITOR -> "STORMHEART_CAPACITOR"
    RelicId.AEGIS_INVERSION -> "AEGIS_INVERSION"
    RelicId.NEUTRON_HEART -> "NEUTRON_HEART"
    RelicId.VERDANT_ROT -> "VERDANT_ROT"
}

internal fun String.relicId(): RelicId = when (this) {
    "PERPETUAL_VECTOR" -> RelicId.PERPETUAL_VECTOR
    "GYRE_ENGINE" -> RelicId.GYRE_ENGINE
    "GLASS_COMET" -> RelicId.GLASS_COMET
    "ECHO_CHAMBER" -> RelicId.ECHO_CHAMBER
    "FURNACE_COVENANT" -> RelicId.FURNACE_COVENANT
    "ABSOLUTE_ZERO" -> RelicId.ABSOLUTE_ZERO
    "STORMHEART_CAPACITOR" -> RelicId.STORMHEART_CAPACITOR
    "AEGIS_INVERSION" -> RelicId.AEGIS_INVERSION
    "NEUTRON_HEART" -> RelicId.NEUTRON_HEART
    "VERDANT_ROT" -> RelicId.VERDANT_ROT
    else -> reject(ProfileSnapshotRejection.INVALID_STABLE_ID)
}

internal fun ReactionId.wireId(): String = when (this) {
    ReactionId.THERMAL_SHOCK -> "THERMAL_SHOCK"
    ReactionId.PLASMA_BURST -> "PLASMA_BURST"
    ReactionId.FUSION -> "FUSION"
    ReactionId.WILDFIRE -> "WILDFIRE"
    ReactionId.SUPERCONDUCT -> "SUPERCONDUCT"
    ReactionId.GLACIER_CRUSH -> "GLACIER_CRUSH"
    ReactionId.EMBRITTLE -> "EMBRITTLE"
    ReactionId.MAGNETIZE -> "MAGNETIZE"
    ReactionId.FEEDBACK -> "FEEDBACK"
    ReactionId.ROT_WELL -> "ROT_WELL"
    ReactionId.THUNDERCLAP -> "THUNDERCLAP"
    ReactionId.SHATTERPOINT -> "SHATTERPOINT"
    ReactionId.RESONANT_ARC -> "RESONANT_ARC"
    ReactionId.SEISMIC -> "SEISMIC"
    ReactionId.FRACTURE -> "FRACTURE"
    ReactionId.SILENCE -> "SILENCE"
    ReactionId.BLACKFIRE -> "BLACKFIRE"
    ReactionId.STASIS -> "STASIS"
    ReactionId.ANNIHILATE -> "ANNIHILATE"
    ReactionId.NULL_WELL -> "NULL_WELL"
    ReactionId.OBLIVION -> "OBLIVION"
}

internal fun String.reactionId(): ReactionId = when (this) {
    "THERMAL_SHOCK" -> ReactionId.THERMAL_SHOCK
    "PLASMA_BURST" -> ReactionId.PLASMA_BURST
    "FUSION" -> ReactionId.FUSION
    "WILDFIRE" -> ReactionId.WILDFIRE
    "SUPERCONDUCT" -> ReactionId.SUPERCONDUCT
    "GLACIER_CRUSH" -> ReactionId.GLACIER_CRUSH
    "EMBRITTLE" -> ReactionId.EMBRITTLE
    "MAGNETIZE" -> ReactionId.MAGNETIZE
    "FEEDBACK" -> ReactionId.FEEDBACK
    "ROT_WELL" -> ReactionId.ROT_WELL
    "THUNDERCLAP" -> ReactionId.THUNDERCLAP
    "SHATTERPOINT" -> ReactionId.SHATTERPOINT
    "RESONANT_ARC" -> ReactionId.RESONANT_ARC
    "SEISMIC" -> ReactionId.SEISMIC
    "FRACTURE" -> ReactionId.FRACTURE
    "SILENCE" -> ReactionId.SILENCE
    "BLACKFIRE" -> ReactionId.BLACKFIRE
    "STASIS" -> ReactionId.STASIS
    "ANNIHILATE" -> ReactionId.ANNIHILATE
    "NULL_WELL" -> ReactionId.NULL_WELL
    "OBLIVION" -> ReactionId.OBLIVION
    else -> reject(ProfileSnapshotRejection.INVALID_STABLE_ID)
}

internal fun RelicPairingId.wireId(): String = when (this) {
    RelicPairingId.ENDLESS_GYRE -> "ENDLESS_GYRE"
    RelicPairingId.LIGHTNING_ROD -> "LIGHTNING_ROD"
    RelicPairingId.ROT_WARD -> "ROT_WARD"
    RelicPairingId.FARADAY_CAGE -> "FARADAY_CAGE"
    RelicPairingId.THERMAL_PARADOX -> "THERMAL_PARADOX"
    RelicPairingId.SHATTERED_AEGIS -> "SHATTERED_AEGIS"
}

internal fun String.relicPairingId(): RelicPairingId = when (this) {
    "ENDLESS_GYRE" -> RelicPairingId.ENDLESS_GYRE
    "LIGHTNING_ROD" -> RelicPairingId.LIGHTNING_ROD
    "ROT_WARD" -> RelicPairingId.ROT_WARD
    "FARADAY_CAGE" -> RelicPairingId.FARADAY_CAGE
    "THERMAL_PARADOX" -> RelicPairingId.THERMAL_PARADOX
    "SHATTERED_AEGIS" -> RelicPairingId.SHATTERED_AEGIS
    else -> reject(ProfileSnapshotRejection.INVALID_STABLE_ID)
}

internal fun ParticleDensity.wireId(): String = when (this) {
    ParticleDensity.LOW -> "LOW"
    ParticleDensity.NORMAL -> "NORMAL"
    ParticleDensity.HIGH -> "HIGH"
}

internal fun String.particleDensity(): ParticleDensity = when (this) {
    "LOW" -> ParticleDensity.LOW
    "NORMAL" -> ParticleDensity.NORMAL
    "HIGH" -> ParticleDensity.HIGH
    else -> reject(ProfileSnapshotRejection.INVALID_STABLE_ID)
}

internal fun DamageNumberSize.wireId(): String = when (this) {
    DamageNumberSize.SMALL -> "SMALL"
    DamageNumberSize.NORMAL -> "NORMAL"
    DamageNumberSize.LARGE -> "LARGE"
    DamageNumberSize.HUGE -> "HUGE"
}

internal fun String.damageNumberSize(): DamageNumberSize = when (this) {
    "SMALL" -> DamageNumberSize.SMALL
    "NORMAL" -> DamageNumberSize.NORMAL
    "LARGE" -> DamageNumberSize.LARGE
    "HUGE" -> DamageNumberSize.HUGE
    else -> reject(ProfileSnapshotRejection.INVALID_STABLE_ID)
}

internal fun DamageNumberFormat.wireId(): String = when (this) {
    DamageNumberFormat.COMPACT -> "COMPACT"
    DamageNumberFormat.FULL -> "FULL"
}

internal fun String.damageNumberFormat(): DamageNumberFormat = when (this) {
    "COMPACT" -> DamageNumberFormat.COMPACT
    "FULL" -> DamageNumberFormat.FULL
    else -> reject(ProfileSnapshotRejection.INVALID_STABLE_ID)
}

/** DEFAULT has no wire id: it is represented only by the absent field. */
internal fun ColorVision.wireIdOrNull(): String? = when (this) {
    ColorVision.DEFAULT -> null
    ColorVision.PROTAN -> "PROTAN"
    ColorVision.DEUTAN -> "DEUTAN"
    ColorVision.TRITAN -> "TRITAN"
    ColorVision.MONO -> "MONO"
}

internal fun String.colorVision(): ColorVision = when (this) {
    "PROTAN" -> ColorVision.PROTAN
    "DEUTAN" -> ColorVision.DEUTAN
    "TRITAN" -> ColorVision.TRITAN
    "MONO" -> ColorVision.MONO
    else -> reject(ProfileSnapshotRejection.INVALID_STABLE_ID)
}

internal fun String.appLanguage(): AppLanguage = when (this) {
    "ru" -> AppLanguage.Russian
    "en" -> AppLanguage.English
    else -> reject(ProfileSnapshotRejection.INVALID_STABLE_ID)
}

internal fun WindowMode.wireId(): String = when (this) {
    WindowMode.WINDOWED -> "WINDOWED"
    WindowMode.BORDERLESS -> "BORDERLESS"
    WindowMode.FULLSCREEN -> "FULLSCREEN"
}

internal fun String.windowMode(): WindowMode = when (this) {
    "WINDOWED" -> WindowMode.WINDOWED
    "BORDERLESS" -> WindowMode.BORDERLESS
    "FULLSCREEN" -> WindowMode.FULLSCREEN
    else -> reject(ProfileSnapshotRejection.INVALID_STABLE_ID)
}

internal fun WindowSizePreset.wireId(): String = when (this) {
    WindowSizePreset.W1280X720 -> "W1280X720"
    WindowSizePreset.W1280X800 -> "W1280X800"
    WindowSizePreset.W1366X768 -> "W1366X768"
    WindowSizePreset.W1440X900 -> "W1440X900"
    WindowSizePreset.W1600X900 -> "W1600X900"
    WindowSizePreset.W1920X1080 -> "W1920X1080"
    WindowSizePreset.W2560X1440 -> "W2560X1440"
}

internal fun String.windowSizePreset(): WindowSizePreset = when (this) {
    "W1280X720" -> WindowSizePreset.W1280X720
    "W1280X800" -> WindowSizePreset.W1280X800
    "W1366X768" -> WindowSizePreset.W1366X768
    "W1440X900" -> WindowSizePreset.W1440X900
    "W1600X900" -> WindowSizePreset.W1600X900
    "W1920X1080" -> WindowSizePreset.W1920X1080
    "W2560X1440" -> WindowSizePreset.W2560X1440
    else -> reject(ProfileSnapshotRejection.INVALID_STABLE_ID)
}

internal fun ReactionEffectsLevel.wireId(): String = when (this) {
    ReactionEffectsLevel.FULL -> "FULL"
    ReactionEffectsLevel.REDUCED -> "REDUCED"
    ReactionEffectsLevel.MINIMAL -> "MINIMAL"
}

internal fun String.reactionEffectsLevel(): ReactionEffectsLevel = when (this) {
    "FULL" -> ReactionEffectsLevel.FULL
    "REDUCED" -> ReactionEffectsLevel.REDUCED
    "MINIMAL" -> ReactionEffectsLevel.MINIMAL
    else -> reject(ProfileSnapshotRejection.INVALID_STABLE_ID)
}

/** Rebindable action wire ids: stable names, never renamed or reused. Keys use [BindableKey.wireId]. */
internal fun GameAction.wireId(): String = when (this) {
    GameAction.DASH -> "DASH"
    GameAction.BRAKE -> "BRAKE"
    GameAction.SKILL_1 -> "SKILL_1"
    GameAction.SKILL_2 -> "SKILL_2"
    GameAction.SKILL_3 -> "SKILL_3"
    GameAction.APEX -> "APEX"
    GameAction.PAUSE -> "PAUSE"
    GameAction.REROLL -> "REROLL"
    GameAction.RESTART -> "RESTART"
    GameAction.CHOICE_1 -> "CHOICE_1"
    GameAction.CHOICE_2 -> "CHOICE_2"
    GameAction.CHOICE_3 -> "CHOICE_3"
    GameAction.CHOICE_4 -> "CHOICE_4"
}

internal fun String.gameAction(): GameAction = when (this) {
    "DASH" -> GameAction.DASH
    "BRAKE" -> GameAction.BRAKE
    "SKILL_1" -> GameAction.SKILL_1
    "SKILL_2" -> GameAction.SKILL_2
    "SKILL_3" -> GameAction.SKILL_3
    "APEX" -> GameAction.APEX
    "PAUSE" -> GameAction.PAUSE
    "REROLL" -> GameAction.REROLL
    "RESTART" -> GameAction.RESTART
    "CHOICE_1" -> GameAction.CHOICE_1
    "CHOICE_2" -> GameAction.CHOICE_2
    "CHOICE_3" -> GameAction.CHOICE_3
    "CHOICE_4" -> GameAction.CHOICE_4
    else -> reject(ProfileSnapshotRejection.INVALID_STABLE_ID)
}
