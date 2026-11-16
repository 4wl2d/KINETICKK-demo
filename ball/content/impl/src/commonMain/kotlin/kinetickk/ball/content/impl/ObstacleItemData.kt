// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.content.impl

import kinetickk.ball.content.api.BuffStat
import kinetickk.ball.content.api.BuildTag.ANCHOR
import kinetickk.ball.content.api.BuildTag.CHAIN
import kinetickk.ball.content.api.BuildTag.DRIVE
import kinetickk.ball.content.api.BuildTag.OVERCLOCK
import kinetickk.ball.content.api.DamageBasis
import kinetickk.ball.content.api.EffectAction
import kinetickk.ball.content.api.EffectCondition
import kinetickk.ball.content.api.EffectTrigger
import kinetickk.ball.content.api.ElementPick
import kinetickk.ball.content.api.ItemRarity.EPIC
import kinetickk.ball.content.api.ItemRarity.UNCOMMON
import kinetickk.ball.content.api.ItemRule
import kinetickk.ball.content.api.ObstacleAspect
import kinetickk.ball.content.api.ObstacleKind
import kinetickk.ball.content.api.StatusElement

private fun about(kind: ObstacleKind): List<EffectCondition> = listOf(EffectCondition.ObstacleIs(kind))

/** Item draft entries linked to obstacles. */
internal val ObstacleDrafts: List<ItemDraft> = listOf(
    // Crystals: what a broken one leaves behind.
    ItemDraft("Rime Splinters", "A crystal breaks into a hundred cold knives.", UNCOMMON, listOf(CHAIN), listOf(StatusElement.CRYO),
        clauses = listOf(on(EffectTrigger.SHATTER, EffectAction.Shards(4, DamageBasis.FLAT, 16f, ElementPick.CRYO),
            given = about(ObstacleKind.CRYSTAL), cooldown = 0.5f)), unlock = 5),

    // Gravity knots: slinging round one and what it drags in.
    ItemDraft("Horizon Surfer", "Leave a knot at speed and a piece of it follows you out.", EPIC, listOf(ANCHOR),
        listOf(StatusElement.GRAVITIC),
        clauses = listOf(on(EffectTrigger.BOOST, EffectAction.Well(220f, 2.5f, DamageBasis.FLAT, 22f, ElementPick.GRAVITIC),
            given = about(ObstacleKind.GRAVITY_WELL), cooldown = 1.5f)),
        rules = listOf(
            ItemRule.ObstacleTuning(ObstacleAspect.WELL_PULL, 0.3f),
            ItemRule.WhileModifier(EffectCondition.NearObstacle(200f), BuffStat.DAMAGE, 0.15f),
        ), unlock = 14),

    // Phase shards: skimming one and striking one while it is real.
    ItemDraft("Phase Slip", "Skim a flickering shard and the dark leaks out after you.", UNCOMMON, listOf(DRIVE), listOf(StatusElement.VOID),
        clauses = listOf(on(EffectTrigger.GRAZE, apply(StatusElement.VOID, 3, 220f), given = about(ObstacleKind.PHASE_SHARD), cooldown = 0.5f)),
        unlock = 6),
    // Thermal vents: riding a plume.
    ItemDraft("Vent Diver", "Dive through the plume, keep the push, leave the heat.", UNCOMMON, listOf(OVERCLOCK), listOf(StatusElement.THERMAL),
        clauses = listOf(on(EffectTrigger.BOOST, apply(StatusElement.THERMAL, 3, 220f), EffectAction.Vent(25f),
            given = about(ObstacleKind.THERMAL_VENT), cooldown = 0.5f)), unlock = 5),
)
