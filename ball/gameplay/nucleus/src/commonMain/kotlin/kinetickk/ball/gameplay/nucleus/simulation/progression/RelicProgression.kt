// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.nucleus.simulation.progression

import kinetickk.ball.content.api.EquippedRelic
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.gameplay.nucleus.render.*
import kinetickk.ball.gameplay.nucleus.model.*
import kinetickk.ball.gameplay.nucleus.protocol.GameplayAudioCue
import kotlin.math.min
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.combat.*
import kinetickk.ball.gameplay.nucleus.simulation.builds.*

/** Opens a queued Relic offer and claims its opportunity; a stray offer past the budget salvages and opens what waits next. */
internal fun MutableGameState.openRelicChoice() {
    if (!claimRelicOpportunity()) {
        openNextPendingChoice()
        return
    }
    activeChoiceType = ChoiceType.RELIC
    buildRelicChoices()
    clearInputBuffers()
    phase = GamePhase.CHOICE
}

/**
 * The defensive replacement step of an unowned Relic taken with no open slot (the budget leaves one open whenever an
 * offer opens, so only content with more opportunities than slots gets here): one card per bound slot that may break.
 */
internal fun MutableGameState.openRelicBindChoice() {
    activeChoiceType = ChoiceType.RELIC_BIND
    if (pendingChoice.pendingRelicBindAction != RelicChoiceAction.REPLACE) return
    val replacement = content.relic(pendingChoice.pendingBindingRelic ?: return)
    choices = equippedRelics.mapIndexed { index, equipped ->
        ChoiceOption(
            type = ChoiceType.RELIC_BIND,
            title = "Replace ${content.relic(equipped.id).name}",
            description = "Break slot ${index + 1} and bind ${replacement.name} at rank 1.",
            tag = "SLOT ${index + 1} // REPLACE",
            relicId = replacement.id,
            relicAction = RelicChoiceAction.REPLACE,
            relicSlot = index,
        )
    }
    if (choices.isEmpty()) {
        pendingChoice.pendingBindingRelic = null
        pendingChoice.pendingRelicBindAction = null
        grantMatter(content.relicPolicy.salvageMatter.toFloat())
        return
    }
    clearInputBuffers()
    phase = GamePhase.CHOICE
}

/**
 * Takes relic offer card [option] and returns the cue the offer closes with, or null when it does not close here: a
 * card without an action, or an unowned Relic taken with no open slot, which opens the replacement step.
 */
internal fun MutableGameState.chooseRelicCard(option: ChoiceOption): GameplayAudioCue? = when (option.relicAction) {
    RelicChoiceAction.DECLINE -> {
        salvageRelicResonance()
        GameplayAudioCue.UI_CLICK
    }
    RelicChoiceAction.ACQUIRE -> option.relicId?.let { takeRelicCard(it) }
    RelicChoiceAction.MELD -> {
        meldRelicSlot()
        GameplayAudioCue.WEAPON_ACQUIRED
    }
    RelicChoiceAction.REPLACE, null -> null
}

/** Binds or ranks [relicId] from an offer card. */
private fun MutableGameState.takeRelicCard(relicId: RelicId): GameplayAudioCue? {
    if (relicRank(relicId) == 0 && openRelicSlots() <= 0) {
        pendingChoice.pendingBindingRelic = relicId
        pendingChoice.pendingRelicBindAction = RelicChoiceAction.REPLACE
        openRelicBindChoice()
        emitSound(GameplayAudioCue.UI_CLICK)
        return null
    }
    acquireRelic(relicId)
    return GameplayAudioCue.WEAPON_ACQUIRED
}

private fun MutableGameState.discoverRelic(id: RelicId) {
    val bit = 1L shl id.ordinal
    if (discoveredRelicMask and bit == 0L) {
        discoveredRelicMask = discoveredRelicMask or bit
        pendingDiscoveredRelicMask = pendingDiscoveredRelicMask or bit
    }
}

internal fun MutableGameState.acquireRelic(id: RelicId) {
    val currentIndex = equippedRelics.indexOfFirst { it.id == id }
    if (currentIndex >= 0) {
        discoverRelic(id)
        val current = equippedRelics[currentIndex]
        if (current.rank >= content.relicPolicy.maxRank) {
            grantMatter(content.relicPolicy.salvageMatter.toFloat())
            message = content.relic(id).name.uppercase() + " // RESONANCE SALVAGED"
        } else {
            setRelicRank(currentIndex, current.rank + 1)
            message = content.relic(id).name.uppercase() + " // RANK " + (current.rank + 1)
        }
        messageTime = 1.7f
        return
    }
    if (openRelicSlots() <= 0) return
    discoverRelic(id)
    equippedRelics = equippedRelics + EquippedRelic(id, 1)
    relicRanks[id.ordinal] = 1
    severedRelics = severedRelics.filter { it.id != id }
    recordRelicGain(id)
    onRelicSetChanged()
    message = content.relic(id).name.uppercase() + " // BOUND"
    messageTime = 1.7f
}

private fun MutableGameState.setRelicRank(slot: Int, rank: Int) {
    val current = equippedRelics[slot]
    val updated = equippedRelics.toMutableList()
    updated[slot] = current.copy(rank = rank)
    equippedRelics = updated.toList()
    relicRanks[current.id.ordinal] = rank
    // Rank three adds a second affinity point, which is a newer gain than the bind.
    if (rank >= 3) recordRelicGain(current.id)
    onRelicSetChanged()
}

/** Replacing a relic severs it: its liability lingers for the policy's severance time. */
internal fun MutableGameState.replaceRelic(slot: Int, id: RelicId) {
    if (slot !in equippedRelics.indices) return
    val replaced = equippedRelics[slot]
    clearRelicRuntime(replaced.id)
    val updated = equippedRelics.toMutableList()
    discoverRelic(id)
    updated[slot] = EquippedRelic(id, 1)
    equippedRelics = updated.toList()
    relicRanks[replaced.id.ordinal] = 0
    relicRanks[id.ordinal] = 1
    recordRelicGain(id)
    val severance = content.build.policy.severanceSeconds
    if (severance > 0f) {
        severedRelics = (severedRelics.filter { it.id != replaced.id && it.id != id } +
            SeveredRelic(replaced.id, elapsed + severance)).takeLast(MutableGameState.MAX_SEVERED_RELICS)
    }
    onRelicSetChanged()
    message = content.relic(id).name.uppercase() + " // SLOT ${slot + 1} BOUND"
    messageTime = 1.7f
}

private fun MutableGameState.onRelicSetChanged() {
    recompileBuild()
    discoverBoundPairings()
    // Bound relics change effective capacities; keep the gauges within them.
    shield = min(shield, shieldCapacity() * kotlin.math.max(1f, build.overhealOverfill))
}

internal fun MutableGameState.clearRelicRuntime(id: RelicId) {
    val index = id.ordinal
    relicCooldowns[index] = 0f
    when (id) {
        RelicId.STORMHEART_CAPACITOR -> stormCharge = 0f
        RelicId.ECHO_CHAMBER -> echoHits.clear()
        else -> Unit
    }
}
