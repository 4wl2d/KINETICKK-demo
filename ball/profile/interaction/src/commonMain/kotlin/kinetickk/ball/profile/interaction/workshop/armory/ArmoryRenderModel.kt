// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.profile.interaction.workshop.armory

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.profile.api.PermanentUpgrades
import kinetickk.foundation.collections.ImmutableSet
import kinetickk.ball.content.api.WeaponId

data class ArmoryRenderModel(
    val totalMatter: Long,
    val selectedWeapon: WeaponId,
    val unlockedWeapons: ImmutableSet<WeaponId>,
    val activeRunWeapon: WeaponId?,
    /** The character the next run starts with: the weapons whose signature it is get the signature bonus. */
    val character: CoreShape = CoreShape.ORB,
    /** Bought Honing levels (owned tiles show "Lvl N" from 1). */
    val upgrades: PermanentUpgrades = PermanentUpgrades(),
)

/** What the Weapons body asks of its host (the Workshop's shared header owns Back). */
sealed interface ArmoryOutput {
    data object Back : ArmoryOutput
}
