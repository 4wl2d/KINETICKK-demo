// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.nucleus.simulation.skills

import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.SkillId
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.gameplay.nucleus.model.*
import kinetickk.ball.gameplay.nucleus.protocol.FxColorCode
import kinetickk.ball.gameplay.nucleus.protocol.LinkStyleCode
import kinetickk.ball.gameplay.nucleus.protocol.SkillCueCodes
import kinetickk.ball.gameplay.nucleus.protocol.ShockwaveKind
import kinetickk.ball.gameplay.nucleus.protocol.VisualFxCue
import kinetickk.ball.gameplay.nucleus.render.ProjectileKind
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.builds.*
import kinetickk.ball.gameplay.nucleus.simulation.characters.*
import kinetickk.ball.gameplay.nucleus.simulation.combat.*
import kinetickk.ball.gameplay.nucleus.simulation.elements.*
import kinetickk.ball.gameplay.nucleus.simulation.enemies.*
import kinetickk.ball.gameplay.nucleus.simulation.flight.*
import kinetickk.ball.gameplay.nucleus.simulation.structures.*
import kinetickk.ball.gameplay.nucleus.simulation.world.*
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

// ---------------------------------------------------------------------------------------------
// Effects. Values follow the per-skill order of `defaultSkillDefinitions`.

/** Resolves [id]'s effect for [slot] at [rank]; returns the radius its cue shows. */
internal fun MutableGameState.resolveSkillCast(
    slot: Int,
    id: SkillId,
    rank: Int,
    values: List<Float>,
    aimX: Float,
    aimY: Float,
    anchorX: Float,
    anchorY: Float,
    target: Enemy?,
): Float {
    val runtime = skillRuntime
    val rankThree = rank >= ContentBounds.MAX_SKILL_RANK
    return when (id) {
        SkillId.SOLAR_FLARE -> castSolarFlare(id, values, rankThree)
        SkillId.FROST_LINE -> castFrostLine(slot, values, aimX, aimY, rankThree)
        SkillId.FROST_SNAP -> castFrostSnap(slot, id, values, runtime, rankThree)
        SkillId.ARC_HOOK -> castArcHook(slot, values, target, runtime, rankThree)
        SkillId.MASS_DRIVER -> {
            val seconds = values[MASS_DRIVER_SECONDS]
            runtime[slot, SkillCell.ACTIVE_UNTIL] = elapsed + seconds
            addBuff(BuffSource.skill(id), kinetickk.ball.content.api.BuffStat.DAMAGE_TAKEN, -values[MASS_DRIVER_GUARD], seconds, 1)
            shockwave(coreX, coreY, 0.3f, coreRadius * 4f, FxColorCode.elementTone(StatusElement.GRAVITIC), ShockwaveKind.ACCENT)
            0f
        }
        SkillId.ENTROPIC_TIDE -> castEntropicTide(id, values, rankThree)
        SkillId.SONIC_BOOM -> castSonicBoom(id, values, aimX, aimY, rankThree)
        SkillId.VOID_STEP -> voidStep(slot, values, aimX, aimY, rankThree)
        SkillId.SLINGSHOT -> castSlingshot(slot, values, aimX, aimY, runtime)
        SkillId.FULL_STOP -> castFullStop(id, values, rankThree)
        SkillId.CATALYZE -> {
            catalyzeAround(anchorX, anchorY, values[CATALYZE_RADIUS], values[CATALYZE_TARGETS].toInt(), values[CATALYZE_SCALE],
                values[CATALYZE_FALLBACK].toInt())
            values[CATALYZE_RADIUS]
        }
    }
}

private fun MutableGameState.castSolarFlare(id: SkillId, values: List<Float>, rankThree: Boolean): Float {
    val vented = min(heat, values[SOLAR_VENT_CAP])
    heat -= vented
    val radius = values[SOLAR_RADIUS]
    val damage = (values[SOLAR_DAMAGE] + values[SOLAR_DAMAGE_PER_HEAT] * vented) * skillPower(id)
    val scorch = min(SOLAR_SCORCH_CAP, values[SOLAR_SCORCH].toInt() + floor(vented / SOLAR_HEAT_PER_SCORCH).toInt())
    val element = attuned(StatusElement.THERMAL)
    forEachEnemyWithin(coreX, coreY, radius) { enemy ->
        skillHit(enemy, damage, element)
        if (enemy.alive) applyStatus(enemy, element, scorch, 0)
        if (rankThree && enemy.alive &&
            stacks(enemy, element) >= breakThreshold(element, enemy) - SKILL_BREAK_MARGIN
        ) forceElementBreak(enemy, element)
    }
    shockwave(coreX, coreY, 0.35f, radius, FxColorCode.elementTone(StatusElement.THERMAL), ShockwaveKind.ACCENT)
    return radius
}

private fun MutableGameState.castFrostLine(slot: Int, values: List<Float>, aimX: Float, aimY: Float, rankThree: Boolean): Float {
    val half = values[FROST_LINE_LENGTH] * 0.5f
    val centreX = coreX + aimX * FROST_LINE_AHEAD
    val centreY = coreY + aimY * FROST_LINE_AHEAD
    // Perpendicular to the aim.
    addField(
        CombatFieldKind.FROST_LINE, FieldSource.SKILL, slot, centreX - aimY * half, centreY + aimX * half,
        radius = values[FROST_LINE_HALF_WIDTH], seconds = values[FROST_LINE_SECONDS], damagePerSecond = 0f,
        element = attuned(StatusElement.CRYO), stacksPerTick = values[FROST_LINE_CHILL].toInt(), generation = 0,
        x2 = centreX + aimY * half, y2 = centreY - aimX * half,
        flags = if (rankThree) FieldFlags.RANK_III else 0, power = values[FROST_LINE_BOLTS],
    )
    return half
}

private fun MutableGameState.castFrostSnap(
    slot: Int, id: SkillId, values: List<Float>, runtime: SkillRuntime, rankThree: Boolean,
): Float {
    val radius = values[FROST_SNAP_RADIUS]
    val shatter = impactPower(max(speed, FROST_SNAP_MIN_SPEED)) * values[FROST_SNAP_SHATTER] * kineticSkillScale(id)
    val element = attuned(StatusElement.CRYO)
    forEachEnemyWithin(coreX, coreY, radius) { enemy ->
        val brittle = enemy.status[StatusCell.BRITTLE] > 0f
        if (isFrozen(enemy) || brittle) {
            if (brittle) enemy.status[StatusCell.BRITTLE] = 0f
            skillHit(enemy, shatter, null, kinetic = true)
        }
        if (enemy.alive) applyStatus(enemy, element, values[FROST_SNAP_CHILL].toInt(), 0)
        if (rankThree && enemy.alive &&
            stacks(enemy, element) >= breakThreshold(element, enemy) - SKILL_BREAK_MARGIN
        ) forceElementBreak(enemy, element)
    }
    runtime[slot, SkillCell.AUX_UNTIL] = elapsed + values[FROST_SNAP_COILED_SECONDS]
    shockwave(coreX, coreY, 0.3f, radius, FxColorCode.elementTone(StatusElement.CRYO), ShockwaveKind.ACCENT)
    return radius
}

private fun MutableGameState.castArcHook(
    slot: Int, values: List<Float>, target: Enemy?, runtime: SkillRuntime, rankThree: Boolean,
): Float {
    val hooked = target ?: return 0f
    applyStatus(hooked, attuned(StatusElement.ION), values[ARC_HOOK_CHARGE].toInt(), 0)
    noteCastTarget(hooked)
    val dx = hooked.x - coreX
    val dy = hooked.y - coreY
    val distance = max(1f, length(dx, dy))
    val dirX = dx / distance
    val dirY = dy / distance
    val along = velocityX * dirX + velocityY * dirY
    val hookSpeed = max(values[ARC_HOOK_SPEED], along)
    velocityX = dirX * hookSpeed
    velocityY = dirY * hookSpeed
    runtime[slot, SkillCell.TARGET_ID] = hooked.id.toFloat()
    runtime[slot, SkillCell.TARGET_UNTIL] = elapsed + values[ARC_HOOK_WINDOW]
    runtime[slot, SkillCell.AUX_UNTIL] = if (rankThree) elapsed + ARC_HOOK_KILL_WINDOW else 0f
    addRelicArc(coreX, coreY, hooked.x, hooked.y, LinkStyleCode.skill(SkillId.ARC_HOOK))
    return distance
}

private fun MutableGameState.castEntropicTide(id: SkillId, values: List<Float>, rankThree: Boolean): Float {
    val radius = values[TIDE_RADIUS]
    val scale = values[TIDE_SCALE] * kineticSkillScale(id)
    var consumed = 0
    forEachEnemyWithin(coreX, coreY, radius) { enemy ->
        val decay = stacks(enemy, StatusElement.ENTROPY)
        if (decay <= 0) return@forEachEnemyWithin
        val amount = detonateDecayAmount(enemy, StatusElement.ENTROPY) * scale
        enemy.status[StatusCell.STACKS + StatusElement.ENTROPY.ordinal] = 0f
        consumed += decay
        skillHit(enemy, amount, attuned(StatusElement.ENTROPY))
    }
    if (consumed > 0) {
        val mended = min(consumed, values[TIDE_HEAL_CAP].toInt()) * values[TIDE_HEAL_PER_STACK]
        val missing = maxHp - hp
        heal(mended, HealSource.SKILL)
        // Rank III: what the Core could not take mends the shield.
        if (rankThree && mended > missing) addShield(mended - max(0f, missing))
        heat = max(0f, heat - consumed * TIDE_HEAT_PER_STACK)
    }
    shockwave(coreX, coreY, 0.4f, radius, FxColorCode.elementTone(StatusElement.ENTROPY), ShockwaveKind.ACCENT)
    return radius
}

private fun MutableGameState.castSonicBoom(id: SkillId, values: List<Float>, aimX: Float, aimY: Float, rankThree: Boolean): Float {
    val range = values[BOOM_RANGE]
    val damage = values[BOOM_DAMAGE] * skillPower(id)
    val cone = cos(BOOM_HALF_ANGLE_DEGREES * RADIANS_PER_DEGREE)
    val element = attuned(StatusElement.SONIC)
    forEachEnemyWithin(coreX, coreY, range) { enemy ->
        if (!inCone(enemy.x, enemy.y, aimX, aimY, cone)) return@forEachEnemyWithin
        skillHit(enemy, damage, element)
        if (!enemy.alive) return@forEachEnemyWithin
        applyStatus(enemy, element, values[BOOM_RESONANCE].toInt(), 0)
        if (!enemy.alive) return@forEachEnemyWithin
        hurl(coreX, coreY, enemy, values[BOOM_PUSH])
        if (rankThree && !enemy.isBossClass) setMark(enemy, MarkKind.BOOMED, BOOMED_SECONDS)
    }
    var erased = 0
    val cap = values[BOOM_BOLTS].toInt()
    for (index in projectiles.indices) {
        if (erased >= cap) break
        val shot = projectiles[index]
        if (!shot.hostile || shot.spent || shot.kind != ProjectileKind.BOLT) continue
        if (distanceSquared(coreX, coreY, shot.x, shot.y) > range * range || !inCone(shot.x, shot.y, aimX, aimY, cone)) continue
        if (eraseHostileShot(shot, EraseCause.SKILL, shot.x, shot.y)) erased++
    }
    applyCoreImpulse(-aimX, -aimY, values[BOOM_RECOIL], gated = false)
    return range
}

private fun MutableGameState.castSlingshot(slot: Int, values: List<Float>, aimX: Float, aimY: Float, runtime: SkillRuntime): Float {
    var swung = (speed * values[SLING_SCALE]).coerceIn(values[SLING_MIN_SPEED], values[SLING_MAX_SPEED])
    // The swing never flings the Core into a rock: it slows to what keeps the next moments' path clear.
    swung = min(swung, blinkRangeClearOfObstacles(aimX, aimY, swung * SLING_CLEAR_SECONDS) / SLING_CLEAR_SECONDS)
    velocityX = aimX * swung
    velocityY = aimY * swung
    runtime[slot, SkillCell.ACTIVE_UNTIL] = elapsed + values[SLING_WINDOW]
    fireTurnEdge()
    return 0f
}

private fun MutableGameState.castFullStop(id: SkillId, values: List<Float>, rankThree: Boolean): Float {
    val impact = impactPower(speed) * values[FULL_STOP_SCALE] * kineticSkillScale(id)
    velocityX = 0f
    velocityY = 0f
    val radius = values[FULL_STOP_RADIUS]
    forEachEnemyWithin(coreX, coreY, radius) { enemy ->
        skillHit(enemy, impact, null, kinetic = true)
        if (enemy.alive) forceBreaks(enemy, values[FULL_STOP_BREAK])
    }
    shockwave(coreX, coreY, 0.35f, radius, 3, ShockwaveKind.ACCENT)
    // Rank III: the stop counts as a Brake release.
    if (rankThree) onBrakeRelease()
    return radius
}

/** Visits every live enemy whose body reaches within [radius] of ([x], [y]), in scan order. */
private inline fun MutableGameState.forEachEnemyWithin(x: Float, y: Float, radius: Float, action: (Enemy) -> Unit) {
    for (index in enemies.indices) {
        val enemy = enemies[index]
        if (!enemy.alive || distanceSquared(x, y, enemy.x, enemy.y) > square(radius + enemy.radius)) continue
        action(enemy)
    }
}

/** True when ([x], [y]) lies within the cone of half-angle cosine [cone] around the aim from the Core. */
private fun MutableGameState.inCone(x: Float, y: Float, aimX: Float, aimY: Float, cone: Float): Boolean {
    val dx = x - coreX
    val dy = y - coreY
    val distance = length(dx, dy)
    if (distance < 1f) return true
    return (dx * aimX + dy * aimY) / distance >= cone
}

/**
 * Void Step: the Core steps toward the aim (stopping short of the singularity and of obstacles), keeps most of
 * its speed and phases out; a rift arms where it stood. Rank III leaves the enemies on the path Brittle and Hollow.
 */
private fun MutableGameState.voidStep(slot: Int, values: List<Float>, aimX: Float, aimY: Float, rankThree: Boolean): Float {
    val runtime = skillRuntime
    val singularityX = cameraX + pointerX - screenWidth * 0.5f
    val singularityY = cameraY + pointerY - screenHeight * 0.5f
    var range = values[VOID_STEP_DISTANCE]
    val ahead = (singularityX - coreX) * aimX + (singularityY - coreY) * aimY
    if (ahead > 0f) range = min(range, max(0f, length(singularityX - coreX, singularityY - coreY) - VOID_STEP_SINGULARITY_GAP))
    range = blinkRangeClearOfObstacles(aimX, aimY, range)
    // The step lands clear of rocks, so the speed it keeps does not carry the Core straight into one.
    while (range > 0f && !landingClearOfObstacles(coreX + aimX * range, coreY + aimY * range, coreRadius + VOID_STEP_OBSTACLE_GAP)) {
        range = max(0f, range - VOID_STEP_OBSTACLE_STEP)
    }
    val startX = coreX
    val startY = coreY
    runtime[slot, SkillCell.ANCHOR_X] = startX
    runtime[slot, SkillCell.ANCHOR_Y] = startY
    runtime[slot, SkillCell.PENDING_AT] = elapsed + values[VOID_STEP_RIFT_DELAY]
    phaseUntil = max(phaseUntil, elapsed + values[VOID_STEP_PHASE])
    if (range <= 0f) return 0f
    val endX = coreX + aimX * range
    val endY = coreY + aimY * range
    if (rankThree) {
        for (index in enemies.indices) {
            val enemy = enemies[index]
            if (!enemy.alive || isShifting(enemy)) continue
            if (!segmentCircleIntersects(startX, startY, endX, endY, enemy.x, enemy.y, enemy.radius + coreRadius)) continue
            enemy.status[StatusCell.BRITTLE] = 1f
            applyStatus(enemy, attuned(StatusElement.VOID), 1, 0)
            noteCastTarget(enemy)
        }
    }
    coreX = endX
    coreY = endY
    previousCoreX = endX
    previousCoreY = endY
    previousSingularityX = singularityX
    previousSingularityY = singularityY
    velocityX *= VOID_STEP_SPEED_RETENTION
    velocityY *= VOID_STEP_SPEED_RETENTION
    markBlink(startX, startY, endX, endY)
    return range
}

/**
 * True when an obstacle comes within [HOOK_OBSTACLE_CLEARANCE] of the hook's line to [target] or of the
 * [HOOK_OVERSHOOT] the Core carries past it, so the hook would fling the Core into the rock.
 */
internal fun MutableGameState.hookLineBlocked(target: Enemy): Boolean {
    val dx = target.x - coreX
    val dy = target.y - coreY
    val distance = max(1f, length(dx, dy))
    val endX = target.x + dx / distance * HOOK_OVERSHOOT
    val endY = target.y + dy / distance * HOOK_OVERSHOOT
    return obstacleBlocksSegment(coreX, coreY, endX, endY, coreRadius + HOOK_OBSTACLE_CLEARANCE)
}

/** True when no obstacle surface lies within [clearance] of ([x], [y]). */
private fun MutableGameState.landingClearOfObstacles(x: Float, y: Float, clearance: Float): Boolean {
    forEachObstacleNear(x, y, clearance) { _, obstacle ->
        if (distanceSquared(x, y, obstacle.x, obstacle.y) < square(obstacle.radius + clearance)) return false
    }
    return wallsClearOf(x, y, clearance)
}

/** The Void Step rift detonates where the step began. */
internal fun MutableGameState.voidRift(slot: Int) {
    val runtime = skillRuntime
    val rank = skillLoadout.rank(slot)
    val values = content.skill(SkillId.VOID_STEP).rank(rank).values
    val x = runtime[slot, SkillCell.ANCHOR_X]
    val y = runtime[slot, SkillCell.ANCHOR_Y]
    val radius = values[VOID_STEP_RIFT_RADIUS]
    val damage = values[VOID_STEP_RIFT_DAMAGE] * skillPower(SkillId.VOID_STEP)
    val element = attuned(StatusElement.VOID)
    forEachEnemyWithin(x, y, radius) { enemy ->
        skillHit(enemy, damage, element)
        if (enemy.alive) applyStatus(enemy, element, values[VOID_STEP_HOLLOW].toInt(), 0)
    }
    shockwave(x, y, 0.3f, radius, FxColorCode.elementTone(StatusElement.VOID), ShockwaveKind.ACCENT)
    emitVisualFx(VisualFxCue.SkillCast(SkillId.VOID_STEP, x, y, 0f, 0f, radius, rank, SkillCueCodes.PHASE_IMPACT))
}

/**
 * Fills [skillScratch] with the ids of up to [count] live, non-shifting enemies nearest ([x], [y]) within
 * [range], nearest first; returns how many it found.
 */
private fun MutableGameState.pickNearestEnemies(x: Float, y: Float, range: Float, count: Int): Int {
    val limit = min(count, skillCast.skillScratch.size)
    var picked = 0
    val rangeSquared = range * range
    while (picked < limit) {
        var best: Enemy? = null
        var bestDistance = rangeSquared
        for (index in enemies.indices) {
            val enemy = enemies[index]
            if (!enemy.alive || isShifting(enemy)) continue
            var taken = false
            for (scan in 0 until picked) if (skillCast.skillScratch[scan] == enemy.id) taken = true
            if (taken) continue
            val distance = distanceSquared(x, y, enemy.x, enemy.y)
            if (distance <= bestDistance && (best == null || distance < bestDistance)) {
                best = enemy
                bestDistance = distance
            }
        }
        val chosen = best ?: break
        skillCast.skillScratch[picked++] = chosen.id
    }
    return picked
}

/**
 * Catalyze: up to [count] enemies nearest ([x], [y]) within [radius] react at once ([scale] magnitude); an enemy
 * with a single element takes [fallback] stacks of the build's partner element instead and may react on its own.
 */
private fun MutableGameState.catalyzeAround(x: Float, y: Float, radius: Float, count: Int, scale: Float, fallback: Int) {
    val picked = pickNearestEnemies(x, y, radius + MAX_ENEMY_RADIUS_SLACK, count)
    for (index in 0 until picked) {
        val enemy = enemyById(skillCast.skillScratch[index]) ?: continue
        if (!enemy.alive || distanceSquared(x, y, enemy.x, enemy.y) > square(radius + enemy.radius)) continue
        var elements = 0
        var only: StatusElement? = null
        for (element in StatusElement.entries) {
            if (stacks(enemy, element) > 0) {
                elements++
                only = element
            }
        }
        if (elements >= 2) {
            if (forceReaction(enemy, 0, scale)) noteCastTarget(enemy)
        } else if (elements == 1 && only != null) {
            applyStatus(enemy, catalystPartner(only), fallback, 0)
            noteCastTarget(enemy)
        }
    }
    shockwave(x, y, 0.35f, radius, 2, ShockwaveKind.ACCENT)
}

/** The element Catalyze adds to an enemy that holds only [element]: the build's secondary, else dominant, else Thermal/Cryo. */
private fun MutableGameState.catalystPartner(element: StatusElement): StatusElement {
    val secondary = build.secondary
    val dominant = build.dominant
    return when {
        secondary != null && secondary != element -> secondary
        dominant != null && dominant != element -> dominant
        element == StatusElement.THERMAL -> StatusElement.CRYO
        else -> StatusElement.THERMAL
    }
}
