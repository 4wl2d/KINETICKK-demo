// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.profile.nucleus

import kinetickk.ball.content.api.MatrixNodeId
import kinetickk.ball.profile.api.MatrixProgress
import kinetickk.ball.profile.api.PlayerEconomy
import kinetickk.ball.profile.api.PlayerLoadout
import kinetickk.ball.profile.api.ProfileRejection
import kinetickk.ball.profile.api.ProfileCoreShapeSelected
import kotlin.math.max
import kinetickk.foundation.common.math.saturatingAdd

/** Buys the next listed rank of [id]. */
internal fun ProfileNucleus.purchaseMatrixNode(
    state: ProfileState,
    id: MatrixNodeId,
): ProfileDecision {
    val cost = when (val quote = matrixNodeQuote(state.profile, state.policy.matrixNode(id))) {
        is PurchaseQuote.Refused -> return rejected(quote.reason)
        is PurchaseQuote.Priced -> quote.cost
    }
    return acceptedMutation(
        state = state,
        nextProfile = state.profile.copy(
            economy = state.profile.economy.copy(matter = state.profile.economy.matter - cost),
            matrix = MatrixProgress(state.profile.matrix.ranks.raisedAt(id.ordinal)),
        ),
    )
}

/** Refunds the full price of every owned rank and clears the Matrix (free respec). */
internal fun ProfileNucleus.resetMatrix(state: ProfileState): ProfileDecision {
    val matrix = state.profile.matrix
    if (matrix.ownedNodeCount == 0) return rejected(ProfileRejection.NoChange)
    val refund = matrixSpent(matrix, state.policy)
    val economy = state.profile.economy
    val matter = saturatingAdd(economy.matter, refund)
    return acceptedMutation(
        state = state,
        nextProfile = state.profile.copy(
            economy = PlayerEconomy(matter = matter, lifetimeMatter = max(economy.lifetimeMatter, matter)),
            matrix = MatrixProgress(),
        ),
    )
}

/** The Session's character selection command: the shape must be unlocked and not already selected. */
internal fun ProfileNucleus.selectCoreShape(
    state: ProfileState,
    shape: kinetickk.ball.content.api.CoreShape,
): ProfileDecision {
    if (!isCoreShapeUnlocked(state.profile, state.policy.coreShape(shape))) {
        return rejected(ProfileRejection.CoreShapeLocked)
    }
    if (shape == state.profile.loadout.coreShape) return rejected(ProfileRejection.NoChange)
    return acceptedMutation(
        state = state,
        nextProfile = state.profile.copy(
            loadout = state.profile.loadout.copy(coreShape = shape),
        ),
        commandOutput = ProfileOutput.CoreShapeSelected(ProfileCoreShapeSelected(state.revision.next(), shape)),
    )
}

/**
 * Equips an owned demo weapon or pays its Matter price and equips it.
 */
internal fun ProfileNucleus.purchaseOrEquipWeapon(
    state: ProfileState,
    id: kinetickk.ball.content.api.WeaponId,
): ProfileDecision {
    val unlocked = state.profile.loadout.unlockedWeapons.toMutableSet()
    var economy = state.profile.economy
    if (id !in unlocked) {
        val cost = when (val quote = weaponUnlockQuote(state.profile, state.policy.weapon(id))) {
            is PurchaseQuote.Refused -> return rejected(quote.reason)
            is PurchaseQuote.Priced -> quote.cost
        }
        economy = economy.copy(matter = economy.matter - cost)
        unlocked += id
    } else if (id == state.profile.loadout.selectedWeapon) {
        return rejected(ProfileRejection.NoChange)
    }
    return acceptedMutation(
        state = state,
        nextProfile = state.profile.copy(
            economy = economy,
            loadout = PlayerLoadout(
                coreShape = state.profile.loadout.coreShape,
                selectedWeapon = id,
                unlockedWeapons = unlocked,
            ),
        ),
    )
}

/**
 * Buys the next Refit level of [shape] (see [characterUpgradeQuote] for the order of checks).
 * Upgrades are permanent spending, so lifetime Matter is unchanged and nothing refunds them.
 */
internal fun ProfileNucleus.purchaseCharacterUpgrade(
    state: ProfileState,
    shape: kinetickk.ball.content.api.CoreShape,
): ProfileDecision = when (val quote = characterUpgradeQuote(state.profile, state.policy, shape)) {
    is PurchaseQuote.Refused -> rejected(quote.reason)
    is PurchaseQuote.Priced -> acceptedMutation(
        state = state,
        nextProfile = state.profile.copy(
            economy = state.profile.economy.copy(matter = state.profile.economy.matter - quote.cost),
            upgrades = state.profile.upgrades.copy(
                characterLevels = state.profile.upgrades.characterLevels.raisedAt(shape.ordinal),
            ),
        ),
    )
}

/** Buys the next Honing level of [id] (see [weaponUpgradeQuote]); lifetime Matter is unchanged. */
internal fun ProfileNucleus.purchaseWeaponUpgrade(
    state: ProfileState,
    id: kinetickk.ball.content.api.WeaponId,
): ProfileDecision = when (val quote = weaponUpgradeQuote(state.profile, state.policy, id)) {
    is PurchaseQuote.Refused -> rejected(quote.reason)
    is PurchaseQuote.Priced -> acceptedMutation(
        state = state,
        nextProfile = state.profile.copy(
            economy = state.profile.economy.copy(matter = state.profile.economy.matter - quote.cost),
            upgrades = state.profile.upgrades.copy(
                weaponLevels = state.profile.upgrades.weaponLevels.raisedAt(id.ordinal),
            ),
        ),
    )
}
