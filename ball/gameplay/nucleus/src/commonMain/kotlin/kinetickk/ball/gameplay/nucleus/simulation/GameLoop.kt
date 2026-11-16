// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.EffectTrigger
import kinetickk.ball.content.api.RelicId

import kinetickk.ball.gameplay.api.*
import kinetickk.ball.gameplay.nucleus.render.*
import kinetickk.ball.gameplay.nucleus.model.*
import kinetickk.ball.gameplay.nucleus.protocol.GameplayAudioCue
import kinetickk.ball.gameplay.nucleus.protocol.VisualFxCue
import kotlin.math.max
import kotlin.math.min
import kinetickk.ball.gameplay.nucleus.simulation.characters.*
import kinetickk.ball.gameplay.nucleus.simulation.combat.*
import kinetickk.ball.gameplay.nucleus.simulation.dimensions.*
import kinetickk.ball.gameplay.nucleus.simulation.director.*
import kinetickk.ball.gameplay.nucleus.simulation.elements.*
import kinetickk.ball.gameplay.nucleus.simulation.enemies.*
import kinetickk.ball.gameplay.nucleus.simulation.flight.*
import kinetickk.ball.gameplay.nucleus.simulation.progression.*
import kinetickk.ball.gameplay.nucleus.simulation.sites.*
import kinetickk.ball.gameplay.nucleus.simulation.skills.*
import kinetickk.ball.gameplay.nucleus.simulation.structures.*
import kinetickk.ball.gameplay.nucleus.simulation.variety.*
import kinetickk.ball.gameplay.nucleus.simulation.weapons.*
import kinetickk.ball.gameplay.nucleus.simulation.world.*
import kinetickk.ball.gameplay.nucleus.simulation.builds.*


internal fun MutableGameState.update(rawDelta: Float) {
    lastTransitionSteps = 0
    if (phase != GamePhase.RUNNING) {
        accumulator = 0f
        return
    }
    if (!rawDelta.isFinite()) {
        accumulator = 0f
        return
    }
    runAdmittedFrame(
        nextSimulationAccumulator(
            currentAccumulator = accumulator,
            rawDeltaSeconds = rawDelta,
            simulationSpeed = settings.simulationSpeed,
        ),
    )
}

/** Runs a running frame whose real time [nextAccumulator] was already admitted ([nextSimulationAccumulator]). */
internal fun MutableGameState.runAdmittedFrame(nextAccumulator: Float) {
    check(phase == GamePhase.RUNNING) { "Only a running simulation admits frame time" }
    lastTransitionSteps = 0
    accumulator = nextAccumulator
    consumeFixedStepBudget()
}

internal fun nextSimulationAccumulator(
    currentAccumulator: Float,
    rawDeltaSeconds: Float,
    simulationSpeed: Float,
): Float = min(
    MAX_SIMULATION_ACCUMULATOR_SECONDS,
    currentAccumulator +
        rawDeltaSeconds.coerceIn(0f, MAX_SIMULATION_RAW_DELTA_SECONDS) * simulationSpeed,
)

/** Executes the already-admitted accumulator while retaining the first step beyond the frame cap. */
internal fun MutableGameState.consumeFixedStepBudget() {
    var steps = 0
    while (
        fixedStepIsDue(accumulator) &&
        phase == GamePhase.RUNNING &&
        steps < MAX_FIXED_STEPS_PER_FRAME
    ) {
        simulateStep(MutableGameState.FIXED_STEP)
        accumulator -= MutableGameState.FIXED_STEP
        steps++
    }
    lastTransitionSteps = steps
}

/**
 * Whether [accumulator] holds a whole fixed step. Adding frame deltas such as 1/144 s and
 * subtracting steps rounds in Float, so a sum that is nominally on a step boundary can sit an ulp
 * below it; within [FIXED_STEP_ROUNDING_TOLERANCE] the step is due. That keeps the step at which an
 * input lands independent of the frame rate, while a delta that is genuinely shorter (0.02f is
 * below 20 ms) still waits for the next frame.
 */
internal fun fixedStepIsDue(accumulator: Float): Boolean =
    accumulator >= MutableGameState.FIXED_STEP - FIXED_STEP_ROUNDING_TOLERANCE

/**
 * Two ulps of the fixed step: Float rounding of the accumulator, never real time. `Float.ulp` is
 * not common Kotlin (no Wasm), so the ulp is the exact gap to the next representable Float.
 */
internal val FIXED_STEP_ROUNDING_TOLERANCE: Float =
    (Float.fromBits(MutableGameState.FIXED_STEP.toRawBits() + 1) - MutableGameState.FIXED_STEP) * 2f

/**
 * Defensive bound on the steps one frame runs. The frame path never reaches it: a frame admits at most
 * [MAX_SIMULATION_RAW_DELTA_SECONDS] at the top simulation speed (2×, about 25 steps), and the accumulator holds at
 * most [MAX_SIMULATION_ACCUMULATOR_SECONDS] (36 steps). It only stops an accumulator set from outside the admission.
 */
internal const val MAX_FIXED_STEPS_PER_FRAME: Int = 48
internal const val MAX_SIMULATION_RAW_DELTA_SECONDS: Float = 0.1f
internal const val MAX_SIMULATION_ACCUMULATOR_SECONDS: Float = 0.3f

/**
 * Runs the fixed-step phases in order. A phase can finish its current group of updates before its next phase check.
 * The checks in and between the phases stop the step when the run leaves RUNNING.
 */
internal fun MutableGameState.simulateStep(delta: Float) {
    beginStep(delta)
    decayStepTimers(delta)
    stepVitals(delta)
    if (phase != GamePhase.RUNNING) return
    stepCoreMotion(delta)
    // A slam can level up through a Data clause and open a choice: nothing else acts once it is open.
    if (phase != GamePhase.RUNNING) return
    stepWorld(delta)
    // A world-event strike can end the run: nothing else acts once it did.
    if (phase != GamePhase.RUNNING) return
    stepCombat(delta)
    if (phase != GamePhase.RUNNING) return
    rebaseWorldIfNeeded()
    updateTotemChests()

    openNextPendingChoice()

    spawnArchitectWhenDue()
}

/** Step bookkeeping: interpolation history, the run clock and the per-step cue budgets. */
private fun MutableGameState.beginStep(delta: Float) {
    interpolationPrimed = true
    ageCharacterStepFlags()
    previousCameraX = cameraX
    previousCameraY = cameraY
    elapsed += delta
    // One cue budget covers every Break and reaction source in the step.
    scratch.statusCuesThisStep = 0
    scratch.heldStatusCues = 0
    shotErasedDispatches = 0
    markDispatches = 0
}

/** The step's countdowns, and the combo lapse once its window ran out. */
private fun MutableGameState.decayStepTimers(delta: Float) {
    if (runGrace.toRawBits() != 0) runGrace = max(0f, runGrace - delta)
    if (hurtCooldown.toRawBits() != 0) hurtCooldown = max(0f, hurtCooldown - delta)
    if (dashPhaseTime.toRawBits() != 0) {
        dashPhaseTime = max(0f, dashPhaseTime - delta)
    }
    if (screenShake.toRawBits() != 0) screenShake = max(0f, screenShake - delta * 18f)
    if (lastImpactTime.toRawBits() != 0) lastImpactTime = max(0f, lastImpactTime - delta)
    if (damageFlash.toRawBits() != 0) damageFlash = max(0f, damageFlash - delta * 3.4f)
    if (messageTime.toRawBits() != 0) messageTime = max(0f, messageTime - delta)
    if (comboTime.toRawBits() != 0) comboTime = max(0f, comboTime - delta)
    if (comboTime <= 0f && combo > 0) {
        // CHAIN III: a lapsing combo halves instead of resetting.
        combo = if (tagTier(BuildTag.CHAIN) >= 3 && combo >= 2) {
            comboTime = effectiveComboWindow() * 0.5f
            combo / 2
        } else 0
    }
}

/** The Core's integrity, overdrive, heat and skills. */
private fun MutableGameState.stepVitals(delta: Float) {
    updateSurvival(delta)
    updateOverdrive(delta)
    updateHeat(delta)
    updateSkills(delta)
}

/** The Core moves, meets the obstacles, and its movement edges and character rules follow. */
private fun MutableGameState.stepCoreMotion(delta: Float) {
    updateCore(delta)
    if (phase != GamePhase.RUNNING) return
    // Obstacles bend the Core's path before anything meets it at its new position.
    updateObstacles()
    if (phase != GamePhase.RUNNING) return
    emitVisualFx(
        VisualFxCue.MotionSample(
            deltaSeconds = delta,
            previousCoreX = previousCoreX,
            previousCoreY = previousCoreY,
            speed = speed,
            dashPhaseTime = dashPhaseTime,
        ),
    )
    updateBuildEdges(delta)
    if (phase != GamePhase.RUNNING) return
    updateCharacterRuntime(delta)
}

/** The camera and the world around the Core: sites, structures, the dimension, Core Freeze and the run's variety. */
private fun MutableGameState.stepWorld(delta: Float) {
    updateCamera(delta)
    updateSites(delta)
    updateStructures()
    updateDimension(delta)
    updateCoreStasis(delta)
    if (phase != GamePhase.RUNNING) return
    updateRunVariety(delta)
}

/** Weapons and enemies act, statuses and fields tick, then shots, pickups, spawns and collisions resolve. */
private fun MutableGameState.stepCombat(delta: Float) {
    updateWeapons(delta)
    updateEnemies(delta)
    if (phase != GamePhase.RUNNING) return
    updateBossEncounter()
    updateStatuses(delta)
    updateCombatFields(delta)
    updateCoreReactionStates()
    updateRelicRuntime(delta)
    if (phase != GamePhase.RUNNING) return
    updateProjectiles(delta)
    updatePickups(delta)
    emitEffectsAdvanced(delta)
    spawnWave(delta)
    resolveCollisions(delta)
    updateVolatileRemains()
}

/** The Architect arrives once the tempo's boss time is reached while the run is still running. */
private fun MutableGameState.spawnArchitectWhenDue() {
    if (!bossSpawned && phase == GamePhase.RUNNING && elapsed >= content.tempo.bossAtSeconds) {
        bossSpawned = true
        message = "THE ARCHITECT"
        messageTime = 3f
        projectiles.removeMatchingStable { projectile -> projectile.hostile }
        // The Architect meets the Core in realspace, and no encounter survives into its fight.
        forceRealspaceBeforeArchitect()
        endAllEncounters(despawn = true)
        // At the hard cap the oldest ordinary enemy makes room for the terminal boss, else the oldest enemy.
        if (enemies.size >= MAX_ACTIVE_ENEMIES) {
            evictOldestOrdinaryEnemy()
            if (enemies.size >= MAX_ACTIVE_ENEMIES) enemies.removeAt(0)
        }
        spawnEnemy(EnemyType.ARCHITECT)
    }
}

internal fun MutableGameState.updateSurvival(delta: Float) {
    timeSinceDamage += delta
    if (shieldRechargeDelay.toRawBits() != 0) {
        shieldRechargeDelay = max(0f, shieldRechargeDelay - delta)
    }
    if (regenPerSecond > 0f && timeSinceDamage >= 3f && build.regenerationToDecay <= 0f) {
        heal(regenPerSecond * delta, HealSource.REGENERATION)
    }
    val capacity = shieldCapacity()
    if (shield > capacity) {
        // Overfilled shield (Hardlight Weave) decays back toward capacity.
        shield = max(capacity, shield - 3f * delta)
    } else if (capacity > 0f && shieldRechargeDelay <= 0f) {
        val inverted = if (build.liability(RelicId.AEGIS_INVERSION)) AEGIS_INVERSION_RECHARGE else 1f
        shield = min(capacity, shield + max(2f, capacity * 0.09f) * inverted * delta)
        if (shield >= capacity * 0.5f) shieldBreakArmed = true
    }
    if (shield > capacity * (if (build.overhealOverfill > 0f) build.overhealOverfill else 1f)) {
        shield = capacity * if (build.overhealOverfill > 0f) build.overhealOverfill else 1f
    }
}

internal fun MutableGameState.updateOverdrive(delta: Float) {
    if (overdriveTime > 0f) {
        overdriveTime = max(0f, overdriveTime - delta)
        if (overdriveTime <= 0f) {
            overdriveBurnoutUntil = elapsed + OVERDRIVE_BURNOUT_SECONDS
        }
    } else if (overdriveCharge >= 100f) {
        overdriveCharge -= 100f
        overdriveTime = 7f
        if (tagTier(BuildTag.OVERCLOCK) >= 3) {
            heat = 0f
            overdriveTime += 2f
        }
        emitSound(GameplayAudioCue.OVERDRIVE)
        fireClauses(EffectTrigger.OVERDRIVE)
    }
    if (speed > 450f) gainOverdrive(min(5f, (speed - 450f) / 900f) * delta * 8f)
}

internal fun MutableGameState.updateHeat(delta: Float) {
    val brakeCooling = if (tagTier(BuildTag.ANCHOR) >= 1) 2.4f else 1.6f
    val cooling = effectiveCooling() * if (braking) brakeCooling else 1f
    if (overheatHoldTime.toRawBits() != 0) overheatHoldTime = max(0f, overheatHoldTime - delta)
    if (overheatHoldTime <= 0f) heat = max(0f, heat - cooling * delta)
    if (!overheatArmed && heat <= 70f) overheatArmed = true
    if (build.freeDashHeat > 0f && heat < build.freeDashHeat && freeDashReadyAt <= elapsed) freeDashPrimed = true
    if (overheated && heat <= 28f) {
        overheated = false
        emitSound(GameplayAudioCue.RECOVERED)
    }
    if (dashBufferTime.toRawBits() != 0) dashBufferTime = max(0f, dashBufferTime - delta)
    if (dashBufferTime > 0f && buildDashReady()) {
        dashBufferTime = 0f
        performDash()
    }
}

/**
 * Heat reaching full from any source: the Dash locks at full heat unless Furnace Covenant holds, then OVERHEAT
 * clauses fire once per climb (re-armed at 70). Locking first lets a clause that vents heat count down from full
 * and shorten the lockout. Call after every heat increase with the heat it started from.
 */
internal fun MutableGameState.applyOverheatEdge(before: Float) {
    if (before >= MutableGameState.MAX_HEAT || heat < MutableGameState.MAX_HEAT) return
    if (!build.upside(RelicId.FURNACE_COVENANT) && !overheated) {
        heat = MutableGameState.MAX_HEAT
        overheated = true
        overheatHoldTime = 0.08f
        emitSound(GameplayAudioCue.OVERHEAT)
    }
    if (overheatArmed) {
        overheatArmed = false
        fireClauses(EffectTrigger.OVERHEAT)
    }
}

/** Brake release and other movement edges, derived inside the fixed step (inputs only set flags). */
internal fun MutableGameState.updateBuildEdges(delta: Float) {
    if (braking) {
        brakeHoldTime += delta
    } else if (wasBraking) {
        if (brakeHoldTime >= 0.35f) onBrakeRelease()
        brakeHoldTime = 0f
        brakeShedSpeed = 0f
    }
    wasBraking = braking
}
