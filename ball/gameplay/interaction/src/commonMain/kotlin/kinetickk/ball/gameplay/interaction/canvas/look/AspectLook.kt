// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.interaction.canvas.look

import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.StatusElement

/** The element of the dungeon whose reward grants the Aspect [boonId], or null. */
internal fun GameplayContentSnapshot.aspectElement(boonId: Int): StatusElement? =
    structures.templates.firstOrNull { template -> template.rewards.any { it.boonId == boonId } }?.element
