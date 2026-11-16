// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.nucleus.simulation.builds

import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.ElementPick
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.RelicPairingId
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.gameplay.nucleus.model.*
import kinetickk.ball.gameplay.nucleus.protocol.GameplayAudioCue
import kinetickk.ball.gameplay.nucleus.protocol.VisualFxCue
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.combat.*
import kinetickk.ball.gameplay.nucleus.simulation.elements.*
import kinetickk.ball.gameplay.nucleus.simulation.variety.*
import kinetickk.foundation.collections.toImmutableList
import kotlin.math.max
import kotlin.math.min

/** Stormheart Capacitor's liability: a hit while half charged or more discharges this share of maximum integrity. */
internal const val STORMHEART_SELF_DISCHARGE: Float = 0.05f
internal const val VERDANT_RADIUS: Float = 320f

/** Verdant Rot's heal cap in integrity per second: its second relic value, which its upside text shows. */
internal fun MutableGameState.verdantCap(): Float = content.relic(RelicId.VERDANT_ROT).value(VERDANT_CAP_VALUE, 1)

private const val VERDANT_CAP_VALUE = 1

/** Verdant Rot's liability: every other heal is multiplied by this. */
internal const val VERDANT_OTHER_HEALING: Float = 0.5f

/** Aegis Inversion: shield capacity grows by this share of maximum integrity. */
internal const val AEGIS_INVERSION_CAPACITY: Float = 0.4f

/** Aegis Inversion's liability: shields recharge on their own at this share of the usual rate. */
internal const val AEGIS_INVERSION_RECHARGE: Float = 0.5f
/** Glass Comet's liability: damage taken, collisions included, is multiplied by this. */
internal const val GLASS_COMET_DAMAGE_TAKEN: Float = 1.5f
internal const val GLASS_INVULNERABILITY_SECONDS: Float = 0.25f
internal const val GLASS_INVULNERABILITY_COOLDOWN: Float = 1.5f
internal const val OVERDRIVE_BURNOUT_SECONDS: Float = 3f
internal const val GYRE_ECHO_DELAY: Float = 0.25f

/** The upside value [index] of a bound relic at its rank, scaled by harmony and pairings. */
internal fun MutableGameState.relicValue(id: RelicId, index: Int): Float =
    content.relic(id).value(index, relicRank(id)) * build.relicUpside(id)

/** Every Overdrive gain goes through here: additive bonuses capped, Burnout blocks gain. */
internal fun MutableGameState.gainOverdrive(amount: Float) {
    if (amount <= 0f || overdriveTime > 0f || overdriveBurnoutUntil > elapsed) return
    overdriveCharge = min(125f, overdriveCharge + amount * overdriveGainFactor())
}

internal fun MutableGameState.overdriveGainFactor(): Float {
    var bonus = overdriveGain - 1f
    if (tagTier(BuildTag.OVERCLOCK) >= 2) bonus += 0.3f
    var factor = (1f + bonus).coerceIn(0f, content.build.policy.overdriveGainCap)
    if (build.liability(RelicId.ABSOLUTE_ZERO)) factor *= 0.6f
    return factor
}

// ---------------------------------------------------------------------------------------------
// Per-step rule runtime.

internal fun MutableGameState.updateRelicRuntime(delta: Float) {
    for (index in relicCooldowns.indices) {
        val cooldown = relicCooldowns[index]
        if (cooldown.toRawBits() != 0) relicCooldowns[index] = max(0f, cooldown - delta)
    }
    if (severedRelics.isNotEmpty() && severedRelics.any { it.until <= elapsed }) {
        severedRelics = severedRelics.filter { it.until > elapsed }
        recompileBuild()
    }
    if (glassCharge > 0f && !build.pairing(RelicPairingId.SHATTERED_AEGIS)) glassCharge = 0f
    sinceTurn += delta
    expireBuffs()
    if (build.upside(RelicId.STORMHEART_CAPACITOR) && speed > 700f && stormCharge < 1f) {
        val rate = if (build.pairing(RelicPairingId.LIGHTNING_ROD) && speed > 1_400f) 1.5f else 1f
        stormCharge = min(1f, stormCharge + delta / 4f * rate)
    }
    if (build.upside(RelicId.VERDANT_ROT)) {
        var nearby = 0
        val radiusSquared = square(VERDANT_RADIUS)
        for (index in enemies.indices) {
            val enemy = enemies[index]
            if (enemy.alive && distanceSquared(enemy.x, enemy.y, coreX, coreY) <= radiusSquared) {
                nearby += stacks(enemy, StatusElement.ENTROPY)
            }
        }
        if (nearby > 0) heal(min(verdantCap(), nearby * relicValue(RelicId.VERDANT_ROT, 0)) * delta, HealSource.VERDANT)
    }
    if (build.liability(RelicId.FURNACE_COVENANT) && heat >= 80f) {
        loseIntegrity(3f * delta)
    }
    if (build.regenerationToDecay > 0f && regenPerSecond > 0f) {
        necroticClock += delta
        if (necroticClock >= 1f) {
            necroticClock -= 1f
            val count = max(1, (build.regenerationToDecay * regenPerSecond).toInt())
            applyStatusAround(coreX, coreY, 220f, StatusElement.ENTROPY, count.toFloat(), generation = 0)
        }
    }
    if (gyreEchoAt >= 0f && elapsed >= gyreEchoAt) {
        gyreEchoAt = -1f
        if (build.pairing(RelicPairingId.ENDLESS_GYRE)) gyreRing(220f, gyreEchoDamage)
    }
    updateEchoHits(delta)
}

private fun MutableGameState.updateEchoHits(delta: Float) {
    if (echoHits.isEmpty()) return
    // Each echo counts down; a due one strikes its target if that still lives, and leaves.
    echoHits.removeMatchingStable { echo ->
        echo.delay -= delta
        if (echo.delay > 0f) return@removeMatchingStable false
        val target = enemies.firstOrNull { it.id == echo.enemyId && it.alive } ?: return@removeMatchingStable true
        val result = dealHit(target, echo.damage, HitSource.ECHO, canCrit = false)
        if (result.amount > 0f) burst(target.x, target.y, 4, 2)
        true
    }
}

/** Integrity loss that is not an incoming hit (no shields, no triggers). */
internal fun MutableGameState.loseIntegrity(amount: Float) {
    if (amount <= 0f || invulnerableUntil > elapsed) return
    hp -= amount
    damageTaken += amount.toDouble()
    if (hp <= 0f) endRun(CORE_FRACTURED_REASON)
}

// ---------------------------------------------------------------------------------------------
// Hooks.

internal fun MutableGameState.relicOnDirectHit(enemy: Enemy, source: HitSource) {
    if (!enemy.alive && source != HitSource.IMPACT) return
    val impact = source == HitSource.IMPACT || source == HitSource.DASH
    if (impact && build.upside(RelicId.ABSOLUTE_ZERO) && enemy.alive) {
        applyStatus(enemy, StatusElement.CRYO, content.relic(RelicId.ABSOLUTE_ZERO).value(0, 1).toInt(), 0)
    }
    if (impact && build.upside(RelicId.VERDANT_ROT) && enemy.alive) applyStatus(enemy, StatusElement.ENTROPY, 2, 0)
    if (build.upside(RelicId.FURNACE_COVENANT) && heat >= 70f && enemy.alive && source != HitSource.ECHO) {
        if (relicCooldowns[RelicId.FURNACE_COVENANT.ordinal] <= 0f) {
            relicCooldowns[RelicId.FURNACE_COVENANT.ordinal] = 0.15f
            applyStatus(enemy, StatusElement.THERMAL, 1, 0)
        }
    }
    if (source == HitSource.IMPACT && build.upside(RelicId.STORMHEART_CAPACITOR) && stormCharge >= 1f) {
        stormCharge = 0f
        val damage = impactPower(speed) * relicValue(RelicId.STORMHEART_CAPACITOR, 0) / 100f
        chain(enemy.x, enemy.y, enemy.id, 5, 420f) { target ->
            dealProcDamage(target, damage)
            if (target.alive) applyStatus(target, StatusElement.ION, 3, 0)
        }
        if (build.pairing(RelicPairingId.LIGHTNING_ROD)) {
            velocityX *= 1.1f
            velocityY *= 1.1f
        }
        emitSound(GameplayAudioCue.OVERDRIVE)
    }
}

internal fun MutableGameState.relicOnKill(source: HitSource?) {
    if (source == HitSource.IMPACT && build.upside(RelicId.GLASS_COMET) && speed > 900f && glassInvulnerabilityReadyAt <= elapsed) {
        glassInvulnerabilityReadyAt = elapsed + GLASS_INVULNERABILITY_COOLDOWN
        invulnerableUntil = max(invulnerableUntil, elapsed + GLASS_INVULNERABILITY_SECONDS)
    }
}

/** Relic rules of every Dash departure. */
internal fun MutableGameState.relicOnDashDeparture() {
    if (build.upside(RelicId.ABSOLUTE_ZERO)) applyStatusAround(coreX, coreY, 140f, StatusElement.CRYO, 3f, generation = 0)
}

internal fun MutableGameState.relicOnTurn() {
    sinceTurn = 0f
    if (!build.upside(RelicId.GYRE_ENGINE) || gyreReadyAt > elapsed) return
    gyreReadyAt = elapsed + 1f
    val damage = impactPower(speed) * relicValue(RelicId.GYRE_ENGINE, 0) / 100f
    gyreRing(150f, damage)
    if (build.pairing(RelicPairingId.ENDLESS_GYRE) && speed > 1_200f) {
        // The 1 s ring cooldown keeps at most one echo pending.
        gyreEchoAt = elapsed + GYRE_ECHO_DELAY
        gyreEchoDamage = damage * 0.6f
    }
}

private fun MutableGameState.gyreRing(radius: Float, damage: Float) {
    val element = resolvePick(ElementPick.DOMINANT)
    for (index in enemies.indices) {
        val enemy = enemies[index]
        if (!enemy.alive || distanceSquared(coreX, coreY, enemy.x, enemy.y) > square(radius + enemy.radius)) continue
        // The ring deals impact damage; the dominant element is a rider, never the damage's element.
        dealProcDamage(enemy, if (element == null) damage * 1.2f else damage)
        if (element != null && enemy.alive) applyStatus(enemy, element, 2, 0)
    }
    shockwave(coreX, coreY, 0.3f, radius, 0)
}

internal fun MutableGameState.relicOnShieldBreak(absorbed: Float) {
    if (build.upside(RelicId.AEGIS_INVERSION) && aegisNovaReadyAt <= elapsed && absorbed > 0f) {
        aegisNovaReadyAt = elapsed + 3f
        val nova = absorbed * relicValue(RelicId.AEGIS_INVERSION, 0) / 100f * kotlin.math.sqrt(runScale())
        for (index in enemies.indices) {
            val enemy = enemies[index]
            if (!enemy.alive || distanceSquared(coreX, coreY, enemy.x, enemy.y) > square(220f + enemy.radius)) continue
            dealProcDamage(enemy, nova)
            if (enemy.alive && build.pairing(RelicPairingId.ROT_WARD)) applyStatus(enemy, StatusElement.ENTROPY, 3, 0)
            if (enemy.alive && build.pairing(RelicPairingId.FARADAY_CAGE)) applyStatus(enemy, StatusElement.ION, 3, 0)
        }
        shockwave(coreX, coreY, 0.35f, 220f, 1)
    }
}

/** Records discovery of every named pairing whose two relics are bound. */
internal fun MutableGameState.discoverBoundPairings() {
    val mask = build.pairingMask
    val fresh = mask and discoveredPairingMask.inv()
    if (fresh == 0) return
    discoveredPairingMask = discoveredPairingMask or fresh
    pendingDiscoveredPairingMask = pendingDiscoveredPairingMask or fresh
    if (!announcesDiscoveries) return
    val names = content.build.pairings.filter { fresh and (1 shl it.id.ordinal) != 0 }.map {
        (if (it.kind == kinetickk.ball.content.api.RelicPairingKind.CONCORD) "+ " else "− ") + it.name
    }
    emitVisualFx(VisualFxCue.BuildChanged("PAIRING DISCOVERED", names.toImmutableList()))
}
