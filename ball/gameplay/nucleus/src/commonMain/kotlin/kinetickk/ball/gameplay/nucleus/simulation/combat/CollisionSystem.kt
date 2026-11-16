// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.nucleus.simulation.combat

import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.EffectTrigger
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.StatusElement

import kinetickk.ball.gameplay.api.*
import kinetickk.ball.gameplay.nucleus.render.*
import kinetickk.ball.gameplay.nucleus.model.*
import kinetickk.ball.gameplay.nucleus.protocol.GameplayAudioCue
import kinetickk.ball.gameplay.nucleus.protocol.ShockwaveKind
import kotlin.math.max
import kotlin.math.min
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.characters.*
import kinetickk.ball.gameplay.nucleus.simulation.elements.*
import kinetickk.ball.gameplay.nucleus.simulation.enemies.*
import kinetickk.ball.gameplay.nucleus.simulation.progression.*
import kinetickk.ball.gameplay.nucleus.simulation.skills.*
import kinetickk.ball.gameplay.nucleus.simulation.structures.*
import kinetickk.ball.gameplay.nucleus.simulation.weapons.*
import kinetickk.ball.gameplay.nucleus.simulation.world.*
import kinetickk.ball.gameplay.nucleus.simulation.builds.*


internal fun MutableGameState.resolveCollisions(delta: Float) {
    resolveCollisionPasses(delta)
    // Shots a reaction spent during these passes (Stasis) leave the list only once no pass is walking it.
    projectiles.removeMatchingStable { projectile -> projectile.spent }
}

private fun MutableGameState.resolveCollisionPasses(delta: Float) {
    // Shots stopped by an asteroid are spent before they can hit anything beyond it.
    blockProjectilesAtObstacles()
    blockProjectilesAtWalls()
    resolveEnemyCoreCollisions()
    if (phase != GamePhase.RUNNING) return
    resolveWeaponDamage(delta)
    if (phase != GamePhase.RUNNING) return
    resolveProjectileHits()
    if (phase != GamePhase.RUNNING) return
    // Kills resolve before pickups can open a choice, so an Architect falling in this step still wins.
    sweepKills()
    if (phase != GamePhase.RUNNING) return
    resolvePickupCollection()
}

/**
 * Resolves every enemy at zero integrity. Only enemies resolved by [onEnemyKilled] (marked dead) are
 * removed; anything a death effect, or a later pickup clause, brings to zero stays and resolves in the next
 * running step's sweep, so no kill (and no Architect victory) is ever dropped. Bounded: each enemy resolves
 * at most once.
 */
internal fun MutableGameState.sweepKills() {
    var killedCount = 0
    for (index in enemies.indices) {
        val enemy = enemies[index]
        if (!enemy.dead && enemy.hp <= 0f) killedCount++
    }
    if (killedCount == 0) return
    val killed = scratch.sweptKills
    for (index in enemies.indices) {
        val enemy = enemies[index]
        if (!enemy.dead && enemy.hp <= 0f) killed += enemy
    }
    // The Architect resolves first, so a self-inflicted death later in the same sweep cannot undo a victory.
    for (index in killed.indices) if (killed[index].type == EnemyType.ARCHITECT) onEnemyKilled(killed[index])
    for (index in killed.indices) if (killed[index].type != EnemyType.ARCHITECT) onEnemyKilled(killed[index])
    killed.clear()
    // Matter granted by kills resolved after the terminal bank in this sweep still counts. A choice a kill opened is not
    // the run's end: banking then would leave signals pricing only the Matter earned after it.
    if (phase == GamePhase.GAME_OVER || phase == GamePhase.VICTORY) bankRunMatter()
    enemies.removeMatchingStable { enemy -> enemy.dead }
}

internal fun MutableGameState.resolveEnemyCoreCollisions() {
    for (index in enemies.indices) {
        val enemy = enemies[index]
        if (phase != GamePhase.RUNNING) return
        if (!(enemy.contactCooldown <= 0f) || enemy.dead || !(enemy.hp > 0f)) continue
        // An out-of-phase enemy cannot be hit.
        if (isPhasedOut(enemy)) continue
        val combined = coreRadius + enemy.radius
        val sweptHit = segmentCircleIntersects(
            previousCoreX - enemy.previousX,
            previousCoreY - enemy.previousY,
            coreX - enemy.x,
            coreY - enemy.y,
            0f,
            0f,
            combined,
        )
        if (sweptHit) {
            val relativeX = velocityX - enemy.vx
            val relativeY = velocityY - enemy.vy
            val impactSpeed = length(relativeX, relativeY)
            val dx = coreX - enemy.x
            val dy = coreY - enemy.y
            val distance = max(1f, length(dx, dy))
            val coreImpactSpeed = kotlin.math.abs(velocityX * dx / distance + velocityY * dy / distance)
            // Read before the hit: a strike or lance in progress hurts whatever the Core's speed.
            val strike = strikeDamage(enemy)
            val newDashContact = dashPhaseTime > 0f && enemy.lastCharacterDash != characterRuntime.dashSequence
            onCharacterDashContact(enemy)
            val characterMultiplier = characterImpactMultiplier(enemy)
            val damage = impactPower(coreImpactSpeed) * characterMultiplier
            val defusable = enemy.type == EnemyType.BOMBER && enemy.mode != EnemyBehavior.PRIMED
            val impact = dealHit(enemy, damage, HitSource.IMPACT, canCrit = true)
            // Ablaze: while the Core burns, every contact sets the enemy burning.
            onAblazeContact(enemy)
            onSkillCoreImpact(enemy, coreImpactSpeed, damage, dashContact = newDashContact)
            onCharacterImpact(enemy, impact, coreImpactSpeed)
            onEnemyCoreImpact(enemy, coreImpactSpeed)
            // A fast impact destroys an unprimed Bomber outright, before it can prime, even through a tether.
            if (defusable && enemy.alive && coreImpactSpeed >= BOMBER_DEFUSE_SPEED) {
                executeEnemy(enemy, HitSource.IMPACT)
            }
            if (newDashContact) onDashHit(enemy, damage)
            onCharacterPrimaryHit(enemy, dash = dashPhaseTime > 0f)
            lastImpact = damage
            lastImpactTime = 0.72f
            enemy.contactCooldown = 0.26f
            screenShake = min(15f, 3f + softVelocity(impactSpeed) * 0.018f)
            // Glass Charge: an Embrittled enemy cannot stop a fast Core.
            val pierced = piercesEmbrittled(enemy, coreImpactSpeed)
            val keepsMomentum = tagTier(BuildTag.RAM) >= 3 && speed >= 1_200f || pierced || slingshotKeepsMomentum()
            if (!keepsMomentum) {
                val recoil = min(220f, softVelocity(impactSpeed) * 0.28f) / max(1f, effectiveMass() / mass)
                velocityX += dx / distance * recoil
                velocityY += dy / distance * recoil
            }
            gainOverdrive(min(12f, coreImpactSpeed / 100f))
            shockwave(enemy.x, enemy.y, 0.26f, min(150f, 55f + damage * 0.9f), 3, ShockwaveKind.RAM)
            directionalBurst(enemy.x, enemy.y, 8, 3, dx / distance, dy / distance)
            emitSound(GameplayAudioCue.IMPACT)
            if (dashPhaseTime <= 0f && (strike > 0f || coreImpactSpeed < 190f)) {
                val neutron = if (build.upside(RelicId.NEUTRON_HEART)) 0.5f else 1f
                val contact = if (strike > 0f) strike else contactDamage(enemy)
                takeDamage(contact * neutron, kind = IncomingDamage.CONTACT)
            }
        }
    }
}

internal fun MutableGameState.resolveProjectileHits() {
    val needsLiveEnemyIds = resolveHostileProjectileHits()
    var liveEnemyIds: IntArray? = null
    var liveEnemyCount = 0
    if (needsLiveEnemyIds) {
        if (scratch.liveEnemyIds.size < enemies.size) scratch.liveEnemyIds = IntArray(max(enemies.size, scratch.liveEnemyIds.size * 2))
        val ids = scratch.liveEnemyIds
        var idsAreSorted = true
        for (enemyIndex in enemies.indices) {
            val enemy = enemies[enemyIndex]
            if (enemy.dead) continue
            if (liveEnemyCount > 0 && ids[liveEnemyCount - 1] > enemy.id) {
                idsAreSorted = false
            }
            ids[liveEnemyCount++] = enemy.id
        }
        if (!idsAreSorted) ids.sort(0, liveEnemyCount)
        liveEnemyIds = ids
    }
    resolveFriendlyProjectileHits(liveEnemyIds, liveEnemyCount)
    liveEnemyIds?.fill(0, 0, liveEnemyCount)
}

/** Hostile shots against the Core (damage or a Dash phase); returns whether a friendly shot keeps enemy hits to prune. */
private fun MutableGameState.resolveHostileProjectileHits(): Boolean {
    var needsLiveEnemyIds = false
    var hostileIndex = 0
    var hostileRemaining = projectiles.size
    while (hostileRemaining > 0) {
        val projectile = projectiles[hostileIndex]
        hostileRemaining--
        if (!projectile.hostile) {
            needsLiveEnemyIds = needsLiveEnemyIds || projectile.hasRecordedEnemyHits
            hostileIndex++
            continue
        }
        if (phase != GamePhase.RUNNING) {
            var scanIndex = hostileIndex + 1
            repeat(hostileRemaining) {
                val remainingProjectile = projectiles[scanIndex++]
                if (!remainingProjectile.hostile && remainingProjectile.hasRecordedEnemyHits) {
                    needsLiveEnemyIds = true
                }
            }
            break
        }
        if (projectile.spent) {
            hostileIndex++
            continue
        }
        if (projectile.kind == ProjectileKind.MINE) {
            if (resolveMine(projectile)) projectiles.removeAt(hostileIndex) else hostileIndex++
            continue
        }
        val hitRadius = coreRadius + projectile.radius
        val hit = segmentCircleIntersects(
            projectile.previousX - previousCoreX,
            projectile.previousY - previousCoreY,
            projectile.x - coreX,
            projectile.y - coreY,
            0f,
            0f,
            hitRadius,
        )
        if (hit) {
            projectiles.removeAt(hostileIndex)
            if (dashPhaseTime <= 0f) {
                // The shot carries the damage its source computed when it fired.
                takeDamage(projectile.damage, kind = IncomingDamage.PROJECTILE)
                screenShake = max(screenShake, 6f)
            } else {
                grantMatter(1f)
                burst(projectile.x, projectile.y, 5, 2)
            }
        } else {
            hostileIndex++
        }
    }
    return needsLiveEnemyIds
}

/** Friendly shots against enemies, pruning hit histories to [liveEnemyIds] (the first [liveEnemyCount], sorted) when given. */
private fun MutableGameState.resolveFriendlyProjectileHits(liveEnemyIds: IntArray?, liveEnemyCount: Int) {
    var friendlyIndex = 0
    var friendlyRemaining = projectiles.size
    while (friendlyRemaining > 0) {
        val projectile = projectiles[friendlyIndex]
        friendlyRemaining--
        if (projectile.hostile || projectile.spent) {
            friendlyIndex++
            continue
        }
        if (liveEnemyIds != null && projectile.hasRecordedEnemyHits) {
            projectile.retainLiveEnemyHits(liveEnemyIds, liveEnemyCount)
        }
        var consumed = false
        for (enemyIndex in enemies.indices) {
            val enemy = enemies[enemyIndex]
            // Shots pass through a shifting Architect without spending pierce.
            if (enemy.dead || isShifting(enemy)) continue
            val hitRadius = projectile.radius + enemy.radius
            val hit = segmentCircleIntersects(
                projectile.previousX - enemy.previousX,
                projectile.previousY - enemy.previousY,
                projectile.x - enemy.x,
                projectile.y - enemy.y,
                0f,
                0f,
                hitRadius,
            )
            // A hit history may span many enemies. Only geometrically overlapping pairs need
            // its lookup; the broad phase rejects the far more numerous misses cheaply.
            if (hit && !projectile.hasRecordedEnemyHit(enemy.id)) {
                // An Apex shot with no strike left passes through boss-class enemies without spending itself.
                if (projectile.procKey == WeaponRuntime.PROC_APEX && apexShotPassesThrough(enemy)) continue
                if (!projectile.tryRecordEnemyHit(enemy.id)) continue
                // A directional guard meets shots by their approach to the enemy.
                val shieldBefore = enemy.guarded
                val directional = enemy.type.profile.directionalGuard
                val guardFaces = directional && shieldFaces(enemy, projectile.previousX, projectile.previousY)
                if (directional) enemy.guarded = guardFaces
                val sourceWeapon = projectile.sourceWeapon
                if (sourceWeapon != null) {
                    weaponShotHit(projectile, sourceWeapon, enemy)
                } else if (projectile.procKey == WeaponRuntime.PROC_APEX) {
                    apexShotHit(enemy, projectile.damage, projectile.statusElement)
                } else {
                    // Relic shots deal element-less damage; a status element is a rider applied after it.
                    dealProcDamage(enemy, projectile.damage)
                    if (projectile.statusElement >= 0 && enemy.alive) {
                        applyStatus(enemy, kinetickk.ball.content.api.StatusElement.entries[projectile.statusElement], 2, 1)
                    }
                }
                if (directional) enemy.guarded = shieldBefore
                burst(enemy.x, enemy.y, 4, projectile.colorIndex)
                if (guardFaces) {
                    consumed = true
                    break
                }
                projectile.pierce--
                if (projectile.pierce < 0) {
                    consumed = true
                    break
                }
            }
        }
        if (consumed) {
            projectiles.removeAt(friendlyIndex)
        } else {
            friendlyIndex++
        }
    }
}

internal fun MutableGameState.resolvePickupCollection() {
    var pickupIndex = 0
    var pickupsRemaining = pickups.size
    while (pickupsRemaining > 0) {
        val pickup = pickups[pickupIndex]
        pickupsRemaining--
        if (segmentCircleIntersects(
                previousCoreX - pickup.previousX,
                previousCoreY - pickup.previousY,
                coreX - pickup.x,
                coreY - pickup.y,
                0f,
                0f,
                content.pickups.collectionRadius,
            )
        ) {
            pickups.removeAt(pickupIndex)
            pickupsCollected++
            when (pickup.type) {
                PickupType.DATA -> {
                    gainData(dataGain * content.tempo.dataPickupMultiplier * (1f + matrix.pickupValueBonus))
                    fireClauses(EffectTrigger.PICKUP)
                    dataPickupRechargesSkills()
                }
                PickupType.KEY -> {
                    keys++
                    keysCollected++
                    message = "ELITE KEY ACQUIRED"
                    messageTime = 1.4f
                }
                PickupType.REPAIR -> heal(22f, HealSource.REPAIR)
                PickupType.RELIC -> {
                    pendingChoice.pendingRelicChoices++
                    message = "RELIC RESONANCE"
                    messageTime = 1.4f
                }
            }
            burst(pickup.x, pickup.y, 7, if (pickup.type == PickupType.KEY || pickup.type == PickupType.RELIC) 3 else 1)
            emitSound(GameplayAudioCue.PICKUP)
            openNextPendingChoice()
        } else {
            pickupIndex++
        }
    }
}
