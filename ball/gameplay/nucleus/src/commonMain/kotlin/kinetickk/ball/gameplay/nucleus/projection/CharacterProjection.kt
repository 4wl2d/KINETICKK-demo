// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.nucleus.projection

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.gameplay.nucleus.model.CharacterRuntime
import kinetickk.ball.gameplay.nucleus.render.CharacterAbilityProjection
import kinetickk.ball.gameplay.nucleus.simulation.MutableGameState
import kinetickk.ball.gameplay.nucleus.simulation.characterRuntime
import kinetickk.ball.gameplay.nucleus.simulation.coreShape
import kinetickk.ball.gameplay.nucleus.simulation.characters.facetRegrowthSeconds

/** Reuse the previous projection when neither readiness nor facet state changed. */
internal fun MutableGameState.characterAbilityProjection(
    reusable: CharacterAbilityProjection?,
    identityRuntime: CharacterRuntime?,
): CharacterAbilityProjection {
    val runtime = characterRuntime
    if (reusable != null && runtime === identityRuntime) return reusable
    val facets = if (coreShape == CoreShape.PRISM) runtime.facets else 0
    val facetProgress = if (coreShape == CoreShape.PRISM && facets < CharacterRuntime.MAX_FACETS) {
        (runtime.facetTimer / facetRegrowthSeconds()).coerceIn(0f, 1f)
    } else {
        0f
    }
    if (reusable != null && reusable.charge.sameBitsAs(runtime.charge) &&
        reusable.facets == facets && reusable.facetProgress.sameBitsAs(facetProgress)
    ) {
        return reusable
    }
    return CharacterAbilityProjection(runtime.charge, facets, facetProgress)
}
