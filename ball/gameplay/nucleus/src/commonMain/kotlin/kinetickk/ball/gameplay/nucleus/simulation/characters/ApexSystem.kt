// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.nucleus.simulation.characters

import kinetickk.ball.content.api.ApexDefinition
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.gameplay.nucleus.model.*
import kinetickk.ball.gameplay.nucleus.protocol.FxColorCode
import kinetickk.ball.gameplay.nucleus.protocol.GameplayAudioCue
import kinetickk.ball.gameplay.nucleus.protocol.LinkStyleCode
import kinetickk.ball.gameplay.nucleus.protocol.VisualFxCue
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.builds.*
import kinetickk.ball.gameplay.nucleus.simulation.combat.*
import kinetickk.ball.gameplay.nucleus.simulation.elements.*
import kinetickk.ball.gameplay.nucleus.simulation.enemies.*
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 * The character's Apex (combat-final §5). It unlocks, ready, the first time the run reaches its level cap; its
 * buffered request casts after the skill slots; Data credited at the cap recharges it through its own refund bank,
 * which never brings it below the definition's minimum cooldown after a cast. Every Apex obeys one boss contract:
 * it never amplifies a mechanic against boss-class enemies, it hits them only through [apexStrike] (at most the
 * definition's strikes per activation, each through the Architect's gate) and it erases hostile bolts only within
 * its bolt budget. The per-character forms live in CharacterApexForms.kt. No gameplay or spawn randomness is drawn.
 */

/** The run character's Apex. */
internal val MutableGameState.apexDefinition: ApexDefinition get() = content.coreShape(coreShape).apex

/** True once the run reached its level cap. */
internal val MutableGameState.apexUnlocked: Boolean get() = skillRuntime.apex(ApexCell.UNLOCKED).toRawBits() != 0

/** True while an activation runs. */
internal fun MutableGameState.apexActive(): Boolean = skillRuntime.apex(ApexCell.ACTIVE_UNTIL) > elapsed

/** True when a request would cast now. */
internal fun MutableGameState.apexReady(): Boolean = apexUnlocked && elapsed >= skillRuntime.apex(ApexCell.READY_AT)

/** The running Apex amplifies its mechanic on [enemy]: never on boss-class enemies (combat-final §5.2 rule 1). */
internal fun MutableGameState.apexAmplifies(enemy: Enemy): Boolean = !enemy.isBossClass && apexActive()

/** Value [index] of the run character's Apex (the per-shape order of `defaultApexDefinition`). */
internal fun MutableGameState.apexValue(index: Int): Float = apexDefinition.values[index]

/** Boss strikes left in the current activation. */
internal val MutableGameState.apexStrikesLeft: Int get() = skillRuntime.apex(ApexCell.STRIKES_LEFT).toInt()

/**
 * The run reached its level cap: the Apex unlocks ready, with its banner and sound. Later calls change nothing.
 */
internal fun MutableGameState.unlockApex() {
    if (apexUnlocked) return
    val runtime = skillRuntime
    runtime.setApex(ApexCell.UNLOCKED, 1f)
    runtime.setApex(ApexCell.READY_AT, elapsed)
    message = APEX_ONLINE_MESSAGE
    messageTime = APEX_MESSAGE_SECONDS
    emitSound(GameplayAudioCue.APEX)
}

/**
 * [wholeData] Data was credited at the level cap: each point takes [APEX_SECONDS_PER_DATA] off the Apex cooldown
 * through [refundApex]. Nothing happens before the unlock or while the Apex is ready.
 */
internal fun MutableGameState.chargeApexWithData(wholeData: Int) {
    if (wholeData <= 0) return
    refundApex(wholeData * APEX_SECONDS_PER_DATA)
}

/**
 * The Apex refund bank: takes up to [seconds] off the running cooldown, never bringing it below the definition's
 * minimum cooldown after the last cast (so every refund of a cycle together stays within cooldown − minimum).
 * Returns the seconds refunded; a locked, ready or floored Apex takes nothing.
 */
internal fun MutableGameState.refundApex(seconds: Float): Float {
    if (!(seconds > 0f) || !apexUnlocked) return 0f
    val runtime = skillRuntime
    val readyAt = runtime.apex(ApexCell.READY_AT)
    if (readyAt <= elapsed) return 0f
    val floor = runtime.apex(ApexCell.LAST_CAST) + apexDefinition.minimumCooldownSeconds
    val refund = min(seconds, readyAt - max(elapsed, floor))
    if (!(refund > 0f)) return 0f
    runtime.setApex(ApexCell.READY_AT, readyAt - refund)
    runtime.setApex(ApexCell.REFUNDED, runtime.apex(ApexCell.REFUNDED) + refund)
    return refund
}

/**
 * Ends a finished activation, then casts a buffered request when the Apex is ready. Called by the skill step
 * after the slots; allocation-free.
 */
internal fun MutableGameState.updateApex(delta: Float) {
    if (!apexUnlocked) {
        skillCast.apexBufferTime = 0f
        return
    }
    val runtime = skillRuntime
    val until = runtime.apex(ApexCell.ACTIVE_UNTIL)
    if (until > 0f && elapsed >= until) {
        runtime.setApex(ApexCell.ACTIVE_UNTIL, 0f)
        endApexForm()
    }
    if (skillCast.apexBufferTime.toRawBits() == 0) return
    skillCast.apexBufferTime = max(0f, skillCast.apexBufferTime - delta)
    if (skillCast.apexBufferTime <= 0f || !apexReady()) return
    skillCast.apexBufferTime = 0f
    castApex()
}

/**
 * Casts the Apex for its duration and starts its cooldown and strike and bolt budgets.
 */
private fun MutableGameState.castApex() {
    val runtime = skillRuntime
    val definition = apexDefinition
    runtime.setApex(ApexCell.ACTIVE_UNTIL, elapsed + definition.durationSeconds)
    runtime.setApex(ApexCell.READY_AT, elapsed + max(definition.minimumCooldownSeconds, definition.cooldownSeconds))
    runtime.setApex(ApexCell.LAST_CAST, elapsed)
    runtime.setApex(ApexCell.REFUNDED, 0f)
    runtime.setApex(ApexCell.STRIKES_LEFT, definition.bossStrikes.toFloat())
    runtime.setApex(ApexCell.BOLTS_LEFT, definition.boltBudget.toFloat())
    runtime.setApex(ApexCell.CAST_COUNT, runtime.apex(ApexCell.CAST_COUNT) + 1f)
    if (listens(kinetickk.ball.content.api.EffectTrigger.APEX)) {
        fireClauses(kinetickk.ball.content.api.EffectTrigger.APEX, element = build.dominant)
    }
    beginApexForm()
    emitVisualFx(VisualFxCue.ApexActivated(coreShape, coreX, coreY))
    emitSound(GameplayAudioCue.APEX)
}

/**
 * The boss channel (combat-final §5.2 rule 2, lead addendum B1): a strike on boss-class [enemy] requests its full hit
 * cap, whatever the channel's own number, through the single integrity writer, so the Architect's gate still holds it
 * to the cap and to the current phase gate; the strike counts as
 * character ability output and spends one strike when something landed. A shifting enemy, an ordinary one or an
 * empty strike budget takes nothing and spends nothing. Returns the integrity removed.
 */
internal fun MutableGameState.apexStrike(enemy: Enemy): Float {
    if (!enemy.isBossClass || !enemy.alive || isShifting(enemy)) return 0f
    val runtime = skillRuntime
    val left = runtime.apex(ApexCell.STRIKES_LEFT)
    if (left < 1f) return 0f
    val integrity = enemy.hp
    val result = damageEnemy(enemy, bossHitCap(enemy))
    recordAbilityDamage(integrity, result)
    if (result.amount <= 0f) return 0f
    runtime.setApex(ApexCell.STRIKES_LEFT, left - 1f)
    runtime.apexBossDamage += min(result.amount, integrity.coerceAtLeast(0f)).toDouble()
    shockwave(enemy.x, enemy.y, APEX_STRIKE_RING_SECONDS, APEX_STRIKE_RING_RADIUS, FxColorCode.APEX_GOLD)
    return result.amount
}

/**
 * A proximity channel's event at ([x], [y]) strikes every live, non-shifting boss-class enemy whose edge lies within
 * [APEX_BOSS_REACH] of it, each strike drawn as an Apex link from the event; returns how many landed.
 */
internal fun MutableGameState.apexStrikeBossesNear(x: Float, y: Float): Int {
    var landed = 0
    for (index in enemies.indices) {
        val enemy = enemies[index]
        if (!enemy.isBossClass || !enemy.alive || distanceSquared(x, y, enemy.x, enemy.y) > square(APEX_BOSS_REACH + enemy.radius)) continue
        if (apexStrikeFrom(x, y, enemy) > 0f) landed++
    }
    return landed
}

/** A strike on boss-class [enemy] reached from ([x], [y]), drawn as the character's Apex link; see [apexStrike]. */
private fun MutableGameState.apexStrikeFrom(x: Float, y: Float, enemy: Enemy): Float {
    val landed = apexStrike(enemy)
    if (landed > 0f) addWeaponArc(x, y, enemy.x, enemy.y, APEX_STRIKE_LINK_SECONDS, LinkStyleCode.apex(coreShape))
    return landed
}

/** Spends hostile bolt [shot] from the Apex's bolt budget (see [eraseHostileShot]); true when it was spent. */
internal fun MutableGameState.eraseApexShot(shot: Projectile, x: Float, y: Float): Boolean =
    eraseHostileShot(shot, EraseCause.APEX, x, y)

/**
 * Where an Apex shot from ([x], [y]) flies: a live, non-shifting boss-class enemy within [range] while strikes
 * remain, else the nearest ordinary enemy within [range]; null when neither exists.
 */
internal fun MutableGameState.apexShotTarget(x: Float, y: Float, range: Float): Enemy? {
    if (skillRuntime.apex(ApexCell.STRIKES_LEFT) >= 1f) {
        val rangeSquared = range * range
        for (index in enemies.indices) {
            val enemy = enemies[index]
            if (enemy.isBossClass && enemy.alive && !isShifting(enemy) && distanceSquared(x, y, enemy.x, enemy.y) <= rangeSquared) {
                return enemy
            }
        }
    }
    return nearestOrdinaryEnemy(x, y, range)
}

/** The nearest live ordinary enemy within [range] of ([x], [y]). */
private fun MutableGameState.nearestOrdinaryEnemy(x: Float, y: Float, range: Float): Enemy? {
    var nearest: Enemy? = null
    var nearestDistanceSquared = range * range
    for (index in enemies.indices) {
        val enemy = enemies[index]
        if (enemy.isBossClass || !enemy.alive || isShifting(enemy)) continue
        val distance = distanceSquared(x, y, enemy.x, enemy.y)
        if (distance <= nearestDistanceSquared && (nearest == null || distance < nearestDistanceSquared)) {
            nearest = enemy
            nearestDistanceSquared = distance
        }
    }
    return nearest
}

/**
 * Fires an Apex shot from ([x], [y]) at [target]; an [element] lands one stack on an ordinary enemy it hits. A shot
 * at a boss-class target cuts through up to [APEX_BOSS_SHOT_PIERCE] ordinary enemies on its way (each takes its hit),
 * so escorts and adds between the Core and the Architect do not swallow it.
 */
internal fun MutableGameState.fireApexShot(x: Float, y: Float, target: Enemy, damage: Float, element: StatusElement? = null) {
    val angle = atan2(target.y - y, target.x - x)
    fireApexShotAlong(x, y, cos(angle), sin(angle), damage, element, pierce = if (target.isBossClass) APEX_BOSS_SHOT_PIERCE else 0)
}

/** Fires an Apex shot from ([x], [y]) along the unit direction ([directionX], [directionY]). */
private fun MutableGameState.fireApexShotAlong(
    x: Float,
    y: Float,
    directionX: Float,
    directionY: Float,
    damage: Float,
    element: StatusElement? = null,
    pierce: Int = 0,
) {
    addProjectile(Projectile(
        x = x,
        y = y,
        vx = directionX * APEX_SHOT_SPEED,
        vy = directionY * APEX_SHOT_SPEED,
        radius = APEX_SHOT_RADIUS,
        life = APEX_SHOT_LIFE,
        hostile = false,
        damage = damage,
        pierce = pierce,
        colorIndex = FxColorCode.APEX_GOLD,
        sourceWeapon = null,
        statusElement = element?.ordinal ?: -1,
        procKey = WeaponRuntime.PROC_APEX,
    ))
}

/** An Apex shot meets boss-class [enemy] with no strike left: it passes through without spending itself. */
internal fun MutableGameState.apexShotPassesThrough(enemy: Enemy): Boolean =
    enemy.isBossClass && skillRuntime.apex(ApexCell.STRIKES_LEFT) < 1f

/**
 * An Apex shot requests a strike on boss-class enemies. Other enemies take [damage] and one stack of
 * [statusElement] (ordinal, or -1).
 */
internal fun MutableGameState.apexShotHit(enemy: Enemy, damage: Float, statusElement: Int) {
    if (enemy.isBossClass) {
        apexStrike(enemy)
        return
    }
    dealCharacterDamage(enemy, damage)
    if (statusElement >= 0 && enemy.alive) applyStatus(enemy, StatusElement.entries[statusElement], 1, 0)
}

/** Banner shown when the Apex unlocks. */
internal const val APEX_ONLINE_MESSAGE: String = "APEX ONLINE"
private const val APEX_MESSAGE_SECONDS: Float = 2.5f
/** Cooldown seconds each whole Data credited at the level cap takes off the Apex (combat-final §5.1). */
internal const val APEX_SECONDS_PER_DATA: Float = 0.03f
/**
 * How far a proximity boss channel reaches. The Architect orbits outside the Core's normal
 * impact range, so a proximity strike checks for the boss within this radius.
 */
internal const val APEX_BOSS_REACH: Float = 420f
private const val APEX_STRIKE_LINK_SECONDS: Float = 0.2f
private const val APEX_STRIKE_RING_SECONDS: Float = 0.3f
private const val APEX_STRIKE_RING_RADIUS: Float = 50f
internal const val APEX_SHOT_SPEED: Float = 720f
private const val APEX_SHOT_RADIUS: Float = 5f
private const val APEX_SHOT_LIFE: Float = 2f
/** Ordinary enemies an Apex shot aimed at a boss-class enemy passes through on its way. */
internal const val APEX_BOSS_SHOT_PIERCE: Int = 4
/** How far an Apex looks for the target of a shot it returns (refraction or held shot). */
internal const val APEX_SHOT_RANGE: Float = 800f
