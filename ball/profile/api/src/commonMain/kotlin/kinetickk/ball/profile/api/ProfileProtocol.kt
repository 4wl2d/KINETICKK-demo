// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.profile.api

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.MatrixNodeId
import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.RelicPairingId
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.content.api.WorldDiscovery
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.ImmutableSet
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.immutableSetOf
import kinetickk.foundation.collections.toImmutableList
import kinetickk.foundation.collections.toImmutableSet
import kinetickk.foundation.common.input.BindableKey
import kinetickk.foundation.common.localization.AppLanguage

enum class PreferenceAdjustmentDirection {
    DECREASE,
    INCREASE,
}

/** Closed settings operation interpreted only by Profile Nucleus. */
sealed interface ProfilePreferenceAdjustment {
    data class SetLanguage(val language: AppLanguage) : ProfilePreferenceAdjustment
    data object ToggleSoundEffects : ProfilePreferenceAdjustment
    data object ToggleMusic : ProfilePreferenceAdjustment
    data class StepMasterVolume(val direction: PreferenceAdjustmentDirection) : ProfilePreferenceAdjustment
    data class SetMasterVolume(val percent: Int) : ProfilePreferenceAdjustment {
        init {
            require(percent in 0..100) { "Master volume must be between 0 and 100 percent" }
        }
    }
    data class StepSimulationSpeed(val direction: PreferenceAdjustmentDirection) : ProfilePreferenceAdjustment
    /** Direct choice of one listed speed (segmented control); unlisted speeds are not representable. */
    data class SetSimulationSpeed(val speed: Float) : ProfilePreferenceAdjustment {
        init {
            require(speed in SIMULATION_SPEED_OPTIONS) { "Simulation speed must be one of the listed options" }
        }
    }
    data class StepTextScale(val direction: PreferenceAdjustmentDirection) : ProfilePreferenceAdjustment
    data object ToggleScreenShake : ProfilePreferenceAdjustment
    data object ToggleRunStatisticsSide : ProfilePreferenceAdjustment
    data class StepParticleDensity(val direction: PreferenceAdjustmentDirection) : ProfilePreferenceAdjustment
    data class SetParticleDensity(val density: ParticleDensity) : ProfilePreferenceAdjustment
    data object ToggleDamageNumbers : ProfilePreferenceAdjustment
    data class StepDamageNumberSize(val direction: PreferenceAdjustmentDirection) : ProfilePreferenceAdjustment
    data class SetDamageNumberSize(val size: DamageNumberSize) : ProfilePreferenceAdjustment
    data class StepDamageNumberFormat(val direction: PreferenceAdjustmentDirection) : ProfilePreferenceAdjustment
    data class SetDamageNumberFormat(val format: DamageNumberFormat) : ProfilePreferenceAdjustment
    data class StepDamageNumberTierThreshold(val direction: PreferenceAdjustmentDirection) : ProfilePreferenceAdjustment
    data class SetColorVision(val colorVision: ColorVision) : ProfilePreferenceAdjustment
    data object ToggleMinimap : ProfilePreferenceAdjustment

    // Display and controls. Steppers send the next option the platform offers as a direct choice, so
    // no Step variants exist; a choice equal to the current value is NoChange.
    data class SetWindowMode(val mode: WindowMode) : ProfilePreferenceAdjustment
    data class SetWindowSize(val preset: WindowSizePreset) : ProfilePreferenceAdjustment
    data object ToggleVsync : ProfilePreferenceAdjustment
    data class SetFrameRateCap(val fps: Int) : ProfilePreferenceAdjustment {
        init {
            require(fps in FRAME_RATE_CAP_OPTIONS) { "Frame rate cap must be one of the listed options" }
        }
    }
    data class SetRefreshRate(val hz: Int) : ProfilePreferenceAdjustment {
        init {
            require(hz == REFRESH_RATE_AUTO || hz in REFRESH_RATE_RANGE) { "Refresh rate must be automatic or 24..360 Hz" }
        }
    }
    data object ToggleImmersive : ProfilePreferenceAdjustment
    data class SetInterfaceScale(val percent: Int) : ProfilePreferenceAdjustment {
        init {
            require(percent in INTERFACE_SCALE_OPTIONS) { "Interface scale must be one of the listed options" }
        }
    }
    data object ToggleShowFps : ProfilePreferenceAdjustment
    data object TogglePauseOnFocusLoss : ProfilePreferenceAdjustment
    data class SetReactionEffects(val level: ReactionEffectsLevel) : ProfilePreferenceAdjustment
    /** Binds [key] to [action]; the action that held [key] takes [action]'s previous key ([KeyBindings.with]). */
    data class SetKeyBinding(val action: GameAction, val key: BindableKey) : ProfilePreferenceAdjustment
    data object ResetKeyBindings : ProfilePreferenceAdjustment
}

data class GameplayProgressUpdate(
    val bankedMatter: Long = 0L,
    val discoveredItemIds: ImmutableSet<Int> = immutableSetOf(),
    val eliteKills: Int = 0,
    val dashHits: Int = 0,
    val architectDefeatedWith: CoreShape? = null,
    val discoveredRelicIds: ImmutableSet<RelicId> = immutableSetOf(),
    val discoveredReactionIds: ImmutableSet<ReactionId> = immutableSetOf(),
    val discoveredPairingIds: ImmutableSet<RelicPairingId> = immutableSetOf(),
    /** Items first acquired in the run since the last update, in acquisition order. */
    val acquiredItemIds: ImmutableList<Int> = immutableListOf(),
    /** World places and secrets first found since the last update; Profile keeps their union. */
    val discoveredWorldEntries: ImmutableSet<WorldDiscovery> = immutableSetOf(),
) {
    constructor(
        bankedMatter: Long = 0L,
        discoveredItemIds: Set<Int>,
        eliteKills: Int = 0,
        dashHits: Int = 0,
        architectDefeatedWith: CoreShape? = null,
        discoveredRelicIds: Set<RelicId> = emptySet(),
        discoveredReactionIds: Set<ReactionId> = emptySet(),
        discoveredPairingIds: Set<RelicPairingId> = emptySet(),
        acquiredItemIds: List<Int> = emptyList(),
        discoveredWorldEntries: Set<WorldDiscovery> = emptySet(),
    ) : this(
        bankedMatter, discoveredItemIds.toImmutableSet(),
        eliteKills, dashHits, architectDefeatedWith, discoveredRelicIds.toImmutableSet(),
        discoveredReactionIds.toImmutableSet(), discoveredPairingIds.toImmutableSet(),
        acquiredItemIds.toImmutableList(), discoveredWorldEntries.toImmutableSet(),
    )

    /** True when the update carries no fact for Profile to apply, so no command needs to be sent. */
    val isEmpty: Boolean
        get() = bankedMatter == 0L && discoveredItemIds.isEmpty() &&
            eliteKills == 0 && dashHits == 0 && architectDefeatedWith == null &&
            discoveredRelicIds.isEmpty() && discoveredReactionIds.isEmpty() && discoveredPairingIds.isEmpty() &&
            acquiredItemIds.isEmpty() && discoveredWorldEntries.isEmpty()
}

sealed interface CollectionEntry {
    data class Item(val id: Int) : CollectionEntry
    data class Relic(val id: RelicId) : CollectionEntry
}

/** Narrow Profile capability used by the collection's presentation. */
fun interface ProfileCollectionVisits {
    fun markViewed(entry: CollectionEntry): ProfileAcceptance
}

/** Closed local Interaction intent inventory. */
sealed interface ProfilePulse {
    sealed interface Business : ProfilePulse

    data class AdjustPreference(
        val adjustment: ProfilePreferenceAdjustment,
    ) : Business

    /** Buys the next rank of a Core Matrix node. */
    data class PurchaseMatrixNode(
        val id: MatrixNodeId,
    ) : Business

    /** Refunds every owned Core Matrix rank (a free respec). */
    data object ResetMatrix : Business

    data class PurchaseOrEquipWeapon(
        val id: WeaponId,
    ) : Business

    data class MarkCollectionEntryViewed(val entry: CollectionEntry) : Business

    /**
     * Buys the next Refit level of an unlocked character. Permanent (never refunded); a run captures
     * the levels when it starts, so a level bought mid-run applies from the next run.
     */
    data class PurchaseCharacterUpgrade(val shape: CoreShape) : Business

    /** Buys the next Honing level of an owned weapon; permanent, and applied from the next run. */
    data class PurchaseWeaponUpgrade(val id: WeaponId) : Business
}

sealed interface ProfileGameplayProgressRejection {
    data object NegativeAchievementProgress : ProfileGameplayProgressRejection
    data object VictoryCharacterLocked : ProfileGameplayProgressRejection
    data object NegativeBankedMatter : ProfileGameplayProgressRejection
    data class UnknownItem(val itemId: Int) : ProfileGameplayProgressRejection
    data object TooManyDiscoveries : ProfileGameplayProgressRejection
    /** More first acquisitions than the Profile remembers ([MAX_RECENT_ITEM_IDS]) in one update. */
    data object TooManyAcquiredItems : ProfileGameplayProgressRejection
}

sealed interface ProfileRejection {
    data object BootstrapNotReady : ProfileRejection
    data object NoChange : ProfileRejection
    data object InsufficientMatter : ProfileRejection
    data object MatrixNodeMaxRank : ProfileRejection
    data object CoreShapeLocked : ProfileRejection
    /** The Refit or Honing track is already at the policy's maximum level. */
    data object UpgradeMaxLevel : ProfileRejection
    /** Honing is sold only for a weapon the profile owns. */
    data object WeaponNotOwned : ProfileRejection

    data class InvalidGameplayProgress(
        val reason: ProfileGameplayProgressRejection,
    ) : ProfileRejection

}

sealed interface ProfileAcceptance {
    val instanceId: ProfileInstanceId

    data class Accepted(
        override val instanceId: ProfileInstanceId,
        val revision: ProfileRevision,
    ) : ProfileAcceptance

    data class Rejected(
        override val instanceId: ProfileInstanceId,
        val observedRevision: ProfileRevision,
        val reason: ProfileRejection,
    ) : ProfileAcceptance
}

sealed interface ProfileBootstrapBlockReason {
    data class ResourceFailure(val reason: ProfileReadFailure) : ProfileBootstrapBlockReason
}

sealed interface ProfileBootstrapStatus {
    data object Ready : ProfileBootstrapStatus
    data class Blocked(val reason: ProfileBootstrapBlockReason) : ProfileBootstrapStatus
}

sealed interface ProfilePersistenceStatus {
    data object NotAttempted : ProfilePersistenceStatus

    data class Pending(
        val effectRef: ProfileEffectRef,
        val snapshotRevision: ProfileRevision,
    ) : ProfilePersistenceStatus

    data class Persisted(
        val snapshotRevision: ProfileRevision,
    ) : ProfilePersistenceStatus

    data class Rejected(
        val snapshotRevision: ProfileRevision,
        val reason: ProfileSnapshotRejection,
    ) : ProfilePersistenceStatus

    data class ResourceFailure(
        val snapshotRevision: ProfileRevision,
        val reason: ProfileWriteFailure,
    ) : ProfilePersistenceStatus

    data class OutcomeUnknown(
        val snapshotRevision: ProfileRevision,
        val reason: ProfileWriteOutcomeUnknownReason,
    ) : ProfilePersistenceStatus
}
