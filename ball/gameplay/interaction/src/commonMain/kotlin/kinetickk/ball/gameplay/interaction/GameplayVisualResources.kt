// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.interaction

import kinetickk.ball.content.api.EliteAffix
import kinetickk.ball.gameplay.interaction.canvas.WorldStrokes
import kinetickk.ball.gameplay.interaction.canvas.WorldUnitShapes
import kinetickk.ball.gameplay.interaction.fx.PixelFxClock
import kinetickk.ball.gameplay.interaction.fx.PixelLooks
import kinetickk.ball.gameplay.interaction.fx.PixelSkillLooks
import kinetickk.ball.gameplay.interaction.fx.PixelStrip
import kinetickk.ball.gameplay.interaction.canvas.look.EnemySilhouetteReach
import kinetickk.ball.gameplay.interaction.canvas.look.prepareSiteGlyphs
import kinetickk.ball.gameplay.interaction.canvas.world.enemies.AffixGlyphs
import kinetickk.ball.gameplay.interaction.canvas.world.places.prepareDimensionVisualResources
import kinetickk.foundation.design.prepareInterfaceIcons
import kotlinx.coroutines.yield

/**
 * Builds the retained, size-independent visual resources before the menu opens. Call on the UI
 * dispatcher: these are the same draw-thread-confined caches used by the live renderer. Each group
 * is bounded and yields to let the loading screen render and cancellation take effect.
 *
 * Viewport-dependent layouts and procedural world instances keep their existing bounded caches;
 * guessing their keys here would only allocate entries that the first real frame immediately evicts.
 */
suspend fun prepareGameplayVisualResources() {
    yield()
    prepareInterfaceIcons()
    yield()
    prepareSiteGlyphs()
    yield()
    prepareWorldVisualResources()
    yield()
    prepareDimensionVisualResources()
    yield()
    preparePixelFx()
}

/** Prepares the effect timing and look tables before the first run. */
private fun preparePixelFx() {
    PixelFxClock.ticks(PixelStrip.entries.first())
    PixelLooks.maxReactionSeconds
    PixelSkillLooks.maxLife
}

/** Retained unit geometry and native path effects; no guessed viewport or enemy-size cache keys. */
internal fun prepareWorldVisualResources() {
    WorldUnitShapes.shard
    WorldUnitShapes.cometTail
    WorldUnitShapes.cometTailCore
    WorldStrokes.dashedHair
    WorldStrokes.dashedThin
    WorldStrokes.dashedMedium
    WorldStrokes.arc6
    WorldStrokes.dashEffect
    EnemySilhouetteReach
    for (affix in EliteAffix.entries) AffixGlyphs.of(affix)
}
