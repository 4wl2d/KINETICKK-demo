// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.profile.nucleus

import kinetickk.ball.profile.api.MAX_RECENT_ITEM_IDS
import kinetickk.ball.profile.api.PlayerEconomy
import kinetickk.ball.profile.api.CharacterAchievementProgress
import kinetickk.ball.profile.api.ProfileGameplayProgressRejection
import kinetickk.ball.profile.api.ProfileRejection
import kinetickk.ball.profile.api.ProfileProgressApplied
import kinetickk.foundation.collections.toImmutableSet
import kinetickk.foundation.collections.toImmutableList
import kinetickk.foundation.common.math.saturatingAdd

internal fun ProfileNucleus.applyGameplayProgress(
    state: ProfileState,
    update: kinetickk.ball.profile.api.GameplayProgressUpdate,
): ProfileDecision {
    validateGameplayProgress(state, update)?.let {
        return rejected(ProfileRejection.InvalidGameplayProgress(it))
    }

    val economy = if (update.bankedMatter == 0L) {
        state.profile.economy
    } else {
        PlayerEconomy(
            matter = saturatingAdd(state.profile.economy.matter, update.bankedMatter),
            lifetimeMatter = saturatingAdd(state.profile.economy.lifetimeMatter, update.bankedMatter),
        )
    }
    val discoveries = state.profile.collection.discoveredItemIds.toMutableSet().apply {
        addAll(update.discoveredItemIds)
    }
    val previousAchievements = state.profile.characterAchievements
    val achievements = CharacterAchievementProgress(
        eliteKills = saturatingAdd(previousAchievements.eliteKills, update.eliteKills.toLong()),
        dashHits = saturatingAdd(previousAchievements.dashHits, update.dashHits.toLong()),
        architectVictories = saturatingAdd(previousAchievements.architectVictories, if (update.architectDefeatedWith != null) 1L else 0L),
        victoriousCharacters = (previousAchievements.victoriousCharacters + listOfNotNull(update.architectDefeatedWith)).toImmutableSet(),
    )
    val collection = state.profile.collection
    val next = state.profile.copy(
        characterAchievements = achievements,
        economy = economy,
        collection = collection.copy(
            discoveredItemIds = discoveries.toImmutableSet(),
            newItemIds = (collection.newItemIds +
                (update.discoveredItemIds - collection.discoveredItemIds)).toImmutableSet(),
            discoveredRelicIds = (collection.discoveredRelicIds + update.discoveredRelicIds).toImmutableSet(),
            newRelicIds = (collection.newRelicIds +
                (update.discoveredRelicIds - collection.discoveredRelicIds)).toImmutableSet(),
            discoveredReactionIds = (collection.discoveredReactionIds + update.discoveredReactionIds).toImmutableSet(),
            discoveredPairingIds = (collection.discoveredPairingIds + update.discoveredPairingIds).toImmutableSet(),
            discoveredWorldEntries = (collection.discoveredWorldEntries + update.discoveredWorldEntries).toImmutableSet(),
            // Oldest first; only the newest MAX_RECENT_ITEM_IDS acquisitions are remembered.
            recentItemIds = if (update.acquiredItemIds.isEmpty()) {
                collection.recentItemIds
            } else {
                (collection.recentItemIds + update.acquiredItemIds).takeLast(MAX_RECENT_ITEM_IDS).toImmutableList()
            },
        ),
    )
    if (next == state.profile) return rejected(ProfileRejection.NoChange)
    return acceptedMutation(
        state = state,
        nextProfile = next,
        commandOutput = ProfileOutput.ProgressApplied(ProfileProgressApplied(state.revision.next())),
    )
}

private fun validateGameplayProgress(
    state: ProfileState,
    update: kinetickk.ball.profile.api.GameplayProgressUpdate,
): ProfileGameplayProgressRejection? {
    if (update.eliteKills < 0 || update.dashHits < 0) {
        return ProfileGameplayProgressRejection.NegativeAchievementProgress
    }
    update.architectDefeatedWith?.let { character ->
        if (!isCoreShapeUnlocked(state.profile, state.policy.coreShape(character))) {
            return ProfileGameplayProgressRejection.VictoryCharacterLocked
        }
    }
    if (update.bankedMatter < 0L) return ProfileGameplayProgressRejection.NegativeBankedMatter
    if (update.discoveredItemIds.size > state.policy.itemCount) {
        return ProfileGameplayProgressRejection.TooManyDiscoveries
    }
    update.discoveredItemIds.firstOrNull { !state.policy.containsItem(it) }?.let {
        return ProfileGameplayProgressRejection.UnknownItem(it)
    }
    if (update.acquiredItemIds.size > MAX_RECENT_ITEM_IDS) {
        return ProfileGameplayProgressRejection.TooManyAcquiredItems
    }
    update.acquiredItemIds.firstOrNull { !state.policy.containsItem(it) }?.let {
        return ProfileGameplayProgressRejection.UnknownItem(it)
    }
    return null
}
