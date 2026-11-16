// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.profile.interaction.workshop.kit

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kinetickk.ball.content.api.WeaponId
import kinetickk.foundation.design.*

private val WeaponIcons: Map<WeaponId, KkIcon?> =
    WeaponId.entries.associateWith { KkIcon.byKey("weapons." + it.name.lowercase()) }

internal fun armoryWeaponIcon(id: WeaponId): KkIcon? = WeaponIcons[id]
