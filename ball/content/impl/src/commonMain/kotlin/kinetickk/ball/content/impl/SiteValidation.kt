// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.content.impl

import kinetickk.ball.content.api.EnemyDefinition
import kinetickk.ball.content.api.SitePolicy

/** Cross-checks of the site policy against the rest of the catalog (each policy validates itself on construction). */
internal fun validateSites(sites: SitePolicy, enemies: List<EnemyDefinition>) {
    require(enemies.any { it.type == sites.bounty.bossType && it.isMiniBoss }) {
        "The bounty target ${sites.bounty.bossType} must be a mini-boss"
    }
}
