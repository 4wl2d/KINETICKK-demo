// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.content.api

/**
 * Catalog level that a profile's [lifetimeMatter] unlocks for ordinary item offers: one level per
 * 40 Matter, up to 80. A run offers a passive item whose unlock level is at most the higher of this
 * level and the run level; an active item ([ItemDefinition.isActive]) waits for the run level itself.
 */
fun lifetimeMatterOfferLevel(lifetimeMatter: Long): Int =
    (1L + lifetimeMatter / 40L).coerceAtMost(80L).toInt()
