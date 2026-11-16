// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.nucleus.simulation.weapons

import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.gameplay.nucleus.model.*
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.characters.*
import kinetickk.ball.gameplay.nucleus.simulation.combat.*
import kinetickk.ball.gameplay.nucleus.simulation.elements.*

// ---------------------------------------------------------------------------------------------
// Weapon shots (player projectiles of the equipped weapon).

/**
 * A weapon shot meets [enemy]: its forms and signature twists, the hit itself (proc key from the shot)
 * and the weapon's own element. Runs inside the projectile pass; the pass removes spent shots.
 */
internal fun MutableGameState.weaponShotHit(projectile: Projectile, sourceWeapon: WeaponId, enemy: Enemy) {
    val equipped = sourceWeapon == weapon
    var forceCrit = false
    when (sourceWeapon) {
        WeaponId.NULL_LANCE -> if (equipped) {
            // Third form: statused or marked enemies take a crit.
            if (hasMasteryForm(3) && (totalStacks(enemy) > 0 || isMarked(enemy) || enemy.status[StatusCell.MARKED_UNTIL] > elapsed)) {
                forceCrit = true
            }
            // Shard signature: a lance on a mark strikes a double Puncture and keeps its pierce.
            if (signatureActive() && tryPuncture(enemy, 2f)) projectile.pierce++
        }
        // Third form: a point-blank pellet tears through without spending its pierce.
        WeaponId.SCATTER_BLOOM -> if (equipped && hasMasteryForm(3) &&
            distanceSquared(coreX, coreY, enemy.x, enemy.y) <= square(SCATTER_POINT_BLANK + enemy.radius)
        ) projectile.pierce++
        else -> Unit
    }
    if (!enemy.alive) return
    val result = dealWeaponDamage(enemy, projectile.damage, canCrit = true, sourceWeapon = sourceWeapon, procKey = projectile.procKey,
        forceCrit = forceCrit)
    if (!equipped || result.amount <= 0f) return
    if (sourceWeapon == WeaponId.SCATTER_BLOOM && signatureActive()) markForPuncture(enemy)
}
