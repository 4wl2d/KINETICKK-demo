// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.profile.nucleus

import kinetickk.ball.profile.api.CollectionEntry
import kinetickk.ball.profile.api.GameplayProfileSnapshot
import kinetickk.ball.profile.api.MatrixProgress
import kinetickk.ball.profile.api.PlayerProfile
import kinetickk.ball.profile.api.ProfileBootstrapStatus
import kinetickk.ball.profile.api.ProfileEffectRef
import kinetickk.ball.profile.api.ProfilePersistenceStatus
import kinetickk.ball.profile.api.ProfilePulse
import kinetickk.ball.profile.api.ProfileRejection
import kinetickk.ball.profile.api.ProfileRevision
import kinetickk.ball.profile.api.ProfileSnapshot
import kinetickk.ball.profile.api.ProfileWriteResult
import kinetickk.foundation.collections.toImmutableSet
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.common.math.saturatingAdd

/**
 * Pure, deterministic authority for every Profile decision and projection. Queries, preference
 * changes, purchases and gameplay progress are its extensions in the sibling `Profile*.kt` files.
 */
object ProfileNucleus {
    fun decide(
        state: ProfileState,
        pulse: ProfileNucleusPulse,
    ): ProfileDecision {
        // Every pulse mutates the Profile, so one gate guards them all.
        mutationGate(state)?.let { return rejected(it) }
        return when (pulse) {
            is ProfileNucleusPulse.Intent -> decideMutation(state, pulse.intent)
            ProfileNucleusPulse.ToggleMute -> toggleMute(state)
            is ProfileNucleusPulse.SelectCoreShape -> selectCoreShape(state, pulse.shape)
            is ProfileNucleusPulse.ApplyGameplayProgress -> applyGameplayProgress(state, pulse.update)
        }
    }

    private fun decideMutation(
        state: ProfileState,
        pulse: ProfilePulse.Business,
    ): ProfileDecision = when (pulse) {
        is ProfilePulse.AdjustPreference -> adjustPreference(state, pulse.adjustment)
        is ProfilePulse.PurchaseMatrixNode -> purchaseMatrixNode(state, pulse.id)
        ProfilePulse.ResetMatrix -> resetMatrix(state)
        is ProfilePulse.PurchaseOrEquipWeapon -> purchaseOrEquipWeapon(state, pulse.id)
        is ProfilePulse.MarkCollectionEntryViewed -> markCollectionEntryViewed(state, pulse.entry)
        is ProfilePulse.PurchaseCharacterUpgrade -> purchaseCharacterUpgrade(state, pulse.shape)
        is ProfilePulse.PurchaseWeaponUpgrade -> purchaseWeaponUpgrade(state, pulse.id)
    }

    private fun markCollectionEntryViewed(state: ProfileState, entry: CollectionEntry): ProfileDecision {
        val collection = state.profile.collection
        val next = when (entry) {
            is CollectionEntry.Item -> collection.copy(newItemIds = (collection.newItemIds - entry.id).toImmutableSet())
            is CollectionEntry.Relic -> collection.copy(newRelicIds = (collection.newRelicIds - entry.id).toImmutableSet())
        }
        if (next == collection) return rejected(ProfileRejection.NoChange)
        return acceptedMutation(state, state.profile.copy(collection = next))
    }

    internal fun acceptedMutation(
        state: ProfileState,
        nextProfile: PlayerProfile,
        commandOutput: ProfileOutput? = null,
    ): ProfileDecision {
        check(state.persistence !is ProfilePersistenceStatus.Pending) {
            "Inline Profile cannot accept another mutation while a Resource effect is pending"
        }
        val revision = state.revision.next()
        val effectRef = resourceEffectRef(revision)
        val nextState = state.copy(
            revision = revision,
            profile = nextProfile,
            persistence = ProfilePersistenceStatus.Pending(
                effectRef = effectRef,
                snapshotRevision = revision,
            ),
        )
        val persist = ProfileOutput.PersistSnapshot(
            effectRef = effectRef,
            snapshot = ProfileSnapshot(
                revision = revision,
                profile = nextProfile,
            ),
        )
        val outputs = if (commandOutput == null) {
            immutableListOf(persist)
        } else {
            immutableListOf(persist, commandOutput)
        }
        return accepted(nextState, outputs)
    }

    /**
     * Settles the one committed write. Unlike a request, its completion cannot be rejected.
     * Correlation faults are programming errors at this synchronous, trusted boundary: they
     * commit nothing, and the owner retains the queued completion for diagnosis.
     */
    fun complete(
        state: ProfileState,
        effectRef: ProfileEffectRef,
        result: ProfileWriteResult,
    ): ProfileAcceptedFrame {
        val pending = checkNotNull(state.persistence as? ProfilePersistenceStatus.Pending)
        check(pending.effectRef == effectRef)
        val persistence = checkNotNull(result.toPersistenceStatus(pending))
        return ProfileAcceptedFrame(
            state.copy(revision = state.revision.next(), persistence = persistence),
            immutableListOf(),
        )
    }

    private fun mutationGate(state: ProfileState): ProfileRejection? = when (state.bootstrap) {
        ProfileBootstrapStatus.Ready -> null
        is ProfileBootstrapStatus.Blocked -> ProfileRejection.BootstrapNotReady
    }

    private fun ProfileWriteResult.toPersistenceStatus(
        pending: ProfilePersistenceStatus.Pending,
    ): ProfilePersistenceStatus? = when (this) {
        is ProfileWriteResult.Written -> if (revision == pending.snapshotRevision) {
            ProfilePersistenceStatus.Persisted(revision)
        } else {
            null
        }
        is ProfileWriteResult.Rejected ->
            ProfilePersistenceStatus.Rejected(pending.snapshotRevision, reason)
        is ProfileWriteResult.ResourceFailure ->
            ProfilePersistenceStatus.ResourceFailure(pending.snapshotRevision, reason)
        is ProfileWriteResult.OutcomeUnknown ->
            ProfilePersistenceStatus.OutcomeUnknown(pending.snapshotRevision, reason)
    }

    private fun resourceEffectRef(revision: ProfileRevision): ProfileEffectRef =
        ProfileEffectRef(sourceRevision = revision, ordinal = PROFILE_RESOURCE_OUTPUT_ORDINAL)

    internal fun ProfileRevision.next(): ProfileRevision {
        check(value < Long.MAX_VALUE) { "Profile revision exhausted" }
        return ProfileRevision(value + 1L)
    }

    internal fun PlayerProfile.toGameplaySnapshot(): GameplayProfileSnapshot = GameplayProfileSnapshot(
        preferences = preferences,
        economy = economy,
        loadout = loadout,
        matrix = matrix,
        collection = collection,
        upgrades = upgrades,
    )

    private fun accepted(
        nextState: ProfileState,
        outputs: ImmutableList<ProfileOutput> = immutableListOf(),
    ): ProfileDecision.Accepted {
        check(outputs.size <= MAX_PROFILE_OUTPUTS_PER_DECISION) {
            "Profile semantic output limit exceeded"
        }
        return ProfileDecision.Accepted(ProfileAcceptedFrame(nextState, outputs))
    }

    internal fun rejected(reason: ProfileRejection): ProfileDecision.Rejected =
        ProfileDecision.Rejected(reason)

}

private const val PROFILE_RESOURCE_OUTPUT_ORDINAL: Int = 0

/** Matter paid for every owned Matrix rank under [policy] (saturating). */
internal fun matrixSpent(matrix: MatrixProgress, policy: kinetickk.ball.content.api.ProfilePolicySnapshot): Long =
    policy.matrixNodes.fold(0L) { total, definition -> saturatingAdd(total, definition.spent(matrix.rank(definition.id))) }
