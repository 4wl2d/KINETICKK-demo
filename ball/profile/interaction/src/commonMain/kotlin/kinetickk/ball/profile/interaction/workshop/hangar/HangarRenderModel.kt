// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.profile.interaction.workshop.hangar

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.CoreShapeDefinition
import kinetickk.ball.content.api.WeaponDefinition
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.immutableListOf

/**
 * One character in the Hangar roster: its content [definition], the Profile's unlock state
 * ([progress] of [target], capped) and its trait bars normalized over the roster (0..1).
 */
data class HangarShapeRenderModel(
    val definition: CoreShapeDefinition,
    val unlocked: Boolean,
    val progress: Long,
    val target: Long,
    val massBar: Float,
    val integrityBar: Float,
    val handlingBar: Float,
    val heatBar: Float,
    /** This character has defeated the Architect: its tile and details show the victory. */
    val victorious: Boolean = false,
    /** Bought Refit levels (the tile shows "Lvl N" from 1). */
    val upgradeLevel: Int = 0,
) {
    val shape: CoreShape get() = definition.id
}

/**
 * Whether a signature weapon is owned and selected for the next run.
 */
data class HangarWeaponRenderModel(
    val definition: WeaponDefinition,
    val owned: Boolean,
    val starter: Boolean,
)

/** Small immutable payload rendered by the Hangar feature. */
data class HangarRenderModel(
    val shapes: ImmutableList<HangarShapeRenderModel>,
    /** The character the next run starts with. */
    val selected: CoreShape,
    val matter: Long,
    /** Every weapon in catalog order, for each character's signature row. */
    val weapons: ImmutableList<HangarWeaponRenderModel> = immutableListOf(),
) {
    val unlockedCount: Int get() = shapes.count { it.unlocked }

    fun shape(id: CoreShape): HangarShapeRenderModel? = shapes.firstOrNull { it.shape == id }

    /** The weapons whose signature character is [id]. */
    fun signatureWeapons(id: CoreShape): List<HangarWeaponRenderModel> = weapons.filter { it.definition.signature == id }
}

/** What the Characters body asks of its host (the Workshop's shared header owns Back). */
sealed interface HangarOutput {
    data object Back : HangarOutput
}
