// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.gameplay.nucleus.model.DungeonState
import kinetickk.ball.gameplay.nucleus.model.StructureField
import kinetickk.ball.gameplay.nucleus.simulation.enemies.EncounterState
import kinetickk.foundation.collections.ImmutableList

/*
 * Legacy forwarders; new code uses the holder; never add a forwarder for a new field.
 *
 * Each property forwards one field that moved from [MutableGameState] into a state holder, so the call sites, tests,
 * fingerprints and benchmark adapters written against the flat state read it unchanged. This list may only shrink:
 * a call site moved to `state.<holder>.<field>` lets its forwarder go, and a new field goes straight into its holder.
 */

// CoreReactionState

internal var MutableGameState.ablazeUntil: Float
    get() = coreReactions.ablazeUntil
    set(value) { coreReactions.ablazeUntil = value }

internal var MutableGameState.frictionlessUntil: Float
    get() = coreReactions.frictionlessUntil
    set(value) { coreReactions.frictionlessUntil = value }

internal var MutableGameState.blastJumpUntil: Float
    get() = coreReactions.blastJumpUntil
    set(value) { coreReactions.blastJumpUntil = value }

internal var MutableGameState.weaveUntil: Float
    get() = coreReactions.weaveUntil
    set(value) { coreReactions.weaveUntil = value }

internal var MutableGameState.weaveDamage: Float
    get() = coreReactions.weaveDamage
    set(value) { coreReactions.weaveDamage = value }

internal var MutableGameState.weaveRange: Float
    get() = coreReactions.weaveRange
    set(value) { coreReactions.weaveRange = value }

internal var MutableGameState.guardShards: Int
    get() = coreReactions.guardShards
    set(value) { coreReactions.guardShards = value }

internal var MutableGameState.guardUntil: Float
    get() = coreReactions.guardUntil
    set(value) { coreReactions.guardUntil = value }

internal var MutableGameState.guardDamage: Float
    get() = coreReactions.guardDamage
    set(value) { coreReactions.guardDamage = value }

internal var MutableGameState.voidAnchorId: Int
    get() = coreReactions.voidAnchorId
    set(value) { coreReactions.voidAnchorId = value }

internal var MutableGameState.voidAnchorUntil: Float
    get() = coreReactions.voidAnchorUntil
    set(value) { coreReactions.voidAnchorUntil = value }

internal var MutableGameState.overcurrentReadyAt: Float
    get() = coreReactions.overcurrentReadyAt
    set(value) { coreReactions.overcurrentReadyAt = value }

internal var MutableGameState.flameRefundReadyAt: Float
    get() = coreReactions.flameRefundReadyAt
    set(value) { coreReactions.flameRefundReadyAt = value }

internal var MutableGameState.erasureLatchId: Int
    get() = coreReactions.erasureLatchId
    set(value) { coreReactions.erasureLatchId = value }

internal var MutableGameState.coreImpulseReadyAt: Float
    get() = coreReactions.coreImpulseReadyAt
    set(value) { coreReactions.coreImpulseReadyAt = value }

internal var MutableGameState.primedDashUntil: Float
    get() = coreReactions.primedDashUntil
    set(value) { coreReactions.primedDashUntil = value }

internal var MutableGameState.dashHeatBank: Float
    get() = coreReactions.dashHeatBank
    set(value) { coreReactions.dashHeatBank = value }

internal var MutableGameState.phaseUntil: Float
    get() = coreReactions.phaseUntil
    set(value) { coreReactions.phaseUntil = value }

internal var MutableGameState.shotErasedDispatches: Int
    get() = coreReactions.shotErasedDispatches
    set(value) { coreReactions.shotErasedDispatches = value }

internal var MutableGameState.markDispatches: Int
    get() = coreReactions.markDispatches
    set(value) { coreReactions.markDispatches = value }

internal var MutableGameState.shotsErased: Long
    get() = coreReactions.shotsErased
    set(value) { coreReactions.shotsErased = value }

// RelicMechanicState

internal var MutableGameState.glassInvulnerabilityReadyAt: Float
    get() = relicMechanics.glassInvulnerabilityReadyAt
    set(value) { relicMechanics.glassInvulnerabilityReadyAt = value }

internal var MutableGameState.bulwarkInvulnerabilityReadyAt: Float
    get() = relicMechanics.bulwarkInvulnerabilityReadyAt
    set(value) { relicMechanics.bulwarkInvulnerabilityReadyAt = value }

internal var MutableGameState.stormCharge: Float
    get() = relicMechanics.stormCharge
    set(value) { relicMechanics.stormCharge = value }

internal var MutableGameState.sinceTurn: Float
    get() = relicMechanics.sinceTurn
    set(value) { relicMechanics.sinceTurn = value }

internal var MutableGameState.gyreReadyAt: Float
    get() = relicMechanics.gyreReadyAt
    set(value) { relicMechanics.gyreReadyAt = value }

internal var MutableGameState.gyreEchoAt: Float
    get() = relicMechanics.gyreEchoAt
    set(value) { relicMechanics.gyreEchoAt = value }

internal var MutableGameState.gyreEchoDamage: Float
    get() = relicMechanics.gyreEchoDamage
    set(value) { relicMechanics.gyreEchoDamage = value }

internal var MutableGameState.aegisAbsorbed: Float
    get() = relicMechanics.aegisAbsorbed
    set(value) { relicMechanics.aegisAbsorbed = value }

internal var MutableGameState.aegisNovaReadyAt: Float
    get() = relicMechanics.aegisNovaReadyAt
    set(value) { relicMechanics.aegisNovaReadyAt = value }

internal var MutableGameState.shieldBreakArmed: Boolean
    get() = relicMechanics.shieldBreakArmed
    set(value) { relicMechanics.shieldBreakArmed = value }

internal var MutableGameState.shieldBreakReadyAt: Float
    get() = relicMechanics.shieldBreakReadyAt
    set(value) { relicMechanics.shieldBreakReadyAt = value }

internal var MutableGameState.glassCharge: Float
    get() = relicMechanics.glassCharge
    set(value) { relicMechanics.glassCharge = value }

internal var MutableGameState.necroticClock: Float
    get() = relicMechanics.necroticClock
    set(value) { relicMechanics.necroticClock = value }

// SpawnDirectorState

internal var MutableGameState.spawnClock: Float
    get() = spawnDirector.spawnClock
    set(value) { spawnDirector.spawnClock = value }

internal var MutableGameState.nextEliteAt: Float
    get() = spawnDirector.nextEliteAt
    set(value) { spawnDirector.nextEliteAt = value }

internal var MutableGameState.elitesSpawned: Int
    get() = spawnDirector.elitesSpawned
    set(value) { spawnDirector.elitesSpawned = value }

internal var MutableGameState.directorPhase: Int
    get() = spawnDirector.directorPhase
    set(value) { spawnDirector.directorPhase = value }

internal var MutableGameState.directorPhaseUntil: Float
    get() = spawnDirector.directorPhaseUntil
    set(value) { spawnDirector.directorPhaseUntil = value }

internal var MutableGameState.spawnBudget: Float
    get() = spawnDirector.spawnBudget
    set(value) { spawnDirector.spawnBudget = value }

internal var MutableGameState.pendingEventKind: Int
    get() = spawnDirector.pendingEventKind
    set(value) { spawnDirector.pendingEventKind = value }

internal var MutableGameState.pendingEventSize: Int
    get() = spawnDirector.pendingEventSize
    set(value) { spawnDirector.pendingEventSize = value }

internal var MutableGameState.pendingEventType: Int
    get() = spawnDirector.pendingEventType
    set(value) { spawnDirector.pendingEventType = value }

internal var MutableGameState.recentEventTypes: Long
    get() = spawnDirector.recentEventTypes
    set(value) { spawnDirector.recentEventTypes = value }

internal var MutableGameState.previousEventTypes: Long
    get() = spawnDirector.previousEventTypes
    set(value) { spawnDirector.previousEventTypes = value }

internal var MutableGameState.secretBossMask: Long
    get() = spawnDirector.secretBossMask
    set(value) { spawnDirector.secretBossMask = value }

internal var MutableGameState.encounters: ImmutableList<EncounterState>
    get() = spawnDirector.encounters
    set(value) { spawnDirector.encounters = value }

internal var MutableGameState.ambushReadyAt: Float
    get() = spawnDirector.ambushReadyAt
    set(value) { spawnDirector.ambushReadyAt = value }

internal var MutableGameState.encircleReadyAt: Float
    get() = spawnDirector.encircleReadyAt
    set(value) { spawnDirector.encircleReadyAt = value }

internal var MutableGameState.wallReadyAt: Float
    get() = spawnDirector.wallReadyAt
    set(value) { spawnDirector.wallReadyAt = value }

internal var MutableGameState.capOverflowReadyAt: Float
    get() = spawnDirector.capOverflowReadyAt
    set(value) { spawnDirector.capOverflowReadyAt = value }

internal var MutableGameState.ambushSpeedSeconds: Float
    get() = spawnDirector.ambushSpeedSeconds
    set(value) { spawnDirector.ambushSpeedSeconds = value }

internal var MutableGameState.spawnEventCount: Int
    get() = spawnDirector.spawnEventCount
    set(value) { spawnDirector.spawnEventCount = value }

internal var MutableGameState.lastSpawnEventKind: Int
    get() = spawnDirector.lastSpawnEventKind
    set(value) { spawnDirector.lastSpawnEventKind = value }

internal var MutableGameState.lastSpawnEventPlanned: Int
    get() = spawnDirector.lastSpawnEventPlanned
    set(value) { spawnDirector.lastSpawnEventPlanned = value }

internal var MutableGameState.lastSpawnEventSpawned: Int
    get() = spawnDirector.lastSpawnEventSpawned
    set(value) { spawnDirector.lastSpawnEventSpawned = value }

// StructureRunState

internal var MutableGameState.structureField: StructureField
    get() = structures.structureField
    set(value) { structures.structureField = value }

internal var MutableGameState.dungeon: DungeonState?
    get() = structures.dungeon
    set(value) { structures.dungeon = value }

internal var MutableGameState.spentStructureIds: List<Long>
    get() = structures.spentStructureIds
    set(value) { structures.spentStructureIds = value }

internal var MutableGameState.lastAnnouncedStructureId: Long
    get() = structures.lastAnnouncedStructureId
    set(value) { structures.lastAnnouncedStructureId = value }
