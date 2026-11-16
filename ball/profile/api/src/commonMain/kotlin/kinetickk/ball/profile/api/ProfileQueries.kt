// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.profile.api

import kinetickk.ball.content.api.CoreShape
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.ImmutableSet
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.immutableSetOf

sealed interface ProfileQuery {
    data object GetRunBootstrap : ProfileQuery
    data object GetPreferences : ProfileQuery
    data object GetHomeProgress : ProfileQuery
    data object GetMatrixProgress : ProfileQuery
    data object GetLoadout : ProfileQuery
    data object GetCollection : ProfileQuery
    data object GetPersistenceStatus : ProfileQuery
    /** Availability and progress for each demo character. */
    data object GetHangar : ProfileQuery
}

sealed interface ProfileProjection {
    val instanceId: ProfileInstanceId
    val revision: ProfileRevision
}

data class MatrixProfileSnapshot(
    val economy: PlayerEconomy,
    val progress: MatrixProgress,
)

data class LoadoutProfileSnapshot(
    val economy: PlayerEconomy,
    val loadout: PlayerLoadout,
    /** Bought Refit and Honing levels. */
    val upgrades: PermanentUpgrades = PermanentUpgrades(),
)

data class GameplayProfileSnapshot(
    val preferences: PlayerPreferences,
    val economy: PlayerEconomy,
    val loadout: PlayerLoadout,
    val matrix: MatrixProgress,
    val collection: PlayerCollection,
    /** Refit and Honing levels the run applies; captured when the run starts. */
    val upgrades: PermanentUpgrades = PermanentUpgrades(),
) {
    /** Recent acquisitions the run's offers down-weight (oldest first). */
    val recentItemIds: ImmutableList<Int> get() = collection.recentItemIds
}

sealed interface ProfileRunBootstrapResult {
    data class Ready(val snapshot: GameplayProfileSnapshot) : ProfileRunBootstrapResult
    data class Unavailable(val status: ProfileBootstrapStatus) : ProfileRunBootstrapResult
}

data class RunBootstrapProjection(
    override val instanceId: ProfileInstanceId,
    override val revision: ProfileRevision,
    val result: ProfileRunBootstrapResult,
) : ProfileProjection

data class PreferencesProjection(
    override val instanceId: ProfileInstanceId,
    override val revision: ProfileRevision,
    val preferences: PlayerPreferences,
) : ProfileProjection

data class HomeProgressProjection(
    override val instanceId: ProfileInstanceId,
    override val revision: ProfileRevision,
    val economy: PlayerEconomy,
    val loadout: PlayerLoadout,
    val collection: PlayerCollection,
    val characterAchievements: CharacterAchievementProgress = CharacterAchievementProgress(),
    val unlockedCoreShapes: ImmutableSet<CoreShape> = immutableSetOf(CoreShape.ORB),
    /** Every character's unlock progress in stable order (the Hangar query's [HangarProjection.shapes]). */
    val characterProgress: ImmutableList<HangarShapeProgress> = immutableListOf(),
    /**
     * True when the Profile would accept a Workshop purchase now: a weapon unlock, a Core Matrix rank, a
     * Refit or a Honing level (the same checks its purchase decisions run).
     */
    val workshopAffordable: Boolean = false,
    /** Refit and Honing levels bought across every character and weapon. */
    val upgradeLevels: Int = 0,
) : ProfileProjection

data class MatrixProgressProjection(
    override val instanceId: ProfileInstanceId,
    override val revision: ProfileRevision,
    val snapshot: MatrixProfileSnapshot,
) : ProfileProjection

data class LoadoutProjection(
    override val instanceId: ProfileInstanceId,
    override val revision: ProfileRevision,
    val snapshot: LoadoutProfileSnapshot,
) : ProfileProjection

data class CollectionProjection(
    override val instanceId: ProfileInstanceId,
    override val revision: ProfileRevision,
    val collection: PlayerCollection,
) : ProfileProjection

/**
 * A character’s unlock progress, capped at [target] for display, and whether they defeated the Architect.
 */
data class HangarShapeProgress(
    val shape: CoreShape,
    val unlocked: Boolean,
    val progress: Long,
    val target: Long,
    val victorious: Boolean = false,
)

data class HangarProjection(
    override val instanceId: ProfileInstanceId,
    override val revision: ProfileRevision,
    /** One entry per character in stable [CoreShape] order. */
    val shapes: ImmutableList<HangarShapeProgress>,
    val selected: CoreShape,
) : ProfileProjection

data class PersistenceStatusProjection(
    override val instanceId: ProfileInstanceId,
    override val revision: ProfileRevision,
    val bootstrap: ProfileBootstrapStatus,
    val persistence: ProfilePersistenceStatus,
) : ProfileProjection

/** Read capability of the application-lifetime Profile. */
interface ProfileReadPort {
    val instanceId: ProfileInstanceId

    fun query(query: ProfileQuery.GetPreferences): PreferencesProjection
    fun query(query: ProfileQuery.GetHomeProgress): HomeProgressProjection
    fun query(query: ProfileQuery.GetCollection): CollectionProjection
    fun query(query: ProfileQuery.GetRunBootstrap): RunBootstrapProjection
    fun query(query: ProfileQuery.GetMatrixProgress): MatrixProgressProjection
    fun query(query: ProfileQuery.GetLoadout): LoadoutProjection
    fun query(query: ProfileQuery.GetPersistenceStatus): PersistenceStatusProjection
    fun query(query: ProfileQuery.GetHangar): HangarProjection
}

/** Local Profile interactions with their read capability. */
interface ProfilePort : ProfileReadPort {
    fun accept(pulse: ProfilePulse.Business): ProfileAcceptance
}
