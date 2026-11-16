// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.content.impl

import kinetickk.ball.content.api.BuildTag.ANCHOR
import kinetickk.ball.content.api.BuildTag.BULWARK
import kinetickk.ball.content.api.BuildTag.CHAIN
import kinetickk.ball.content.api.BuildTag.DRIVE
import kinetickk.ball.content.api.BuildTag.FORTUNE
import kinetickk.ball.content.api.BuildTag.PRECISION
import kinetickk.ball.content.api.DamageBasis
import kinetickk.ball.content.api.EffectAction
import kinetickk.ball.content.api.EffectCondition
import kinetickk.ball.content.api.EffectTrigger
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.ItemEffect
import kinetickk.ball.content.api.ItemRarity.COMMON
import kinetickk.ball.content.api.ItemRarity.UNCOMMON
import kinetickk.ball.content.api.ItemRule

internal val ThemeDrafts: List<ItemDraft> = listOf(
    // SONIC.
    ItemDraft("Bass Drop", "Release the brake and let the floor shake.", COMMON, listOf(ANCHOR), listOf(StatusElement.SONIC),
        clauses = listOf(on(EffectTrigger.BRAKE_RELEASE, apply(StatusElement.SONIC, 3, 210f), EffectAction.Push(210f, 280f))),
        unlock = 5),
    ItemDraft("Harmonic Lens", "Resonating hulls flinch in the same place every time.", UNCOMMON, listOf(PRECISION),
        listOf(StatusElement.SONIC),
        rules = listOf(ItemRule.CritDamageVersus(EffectCondition.TargetHas(StatusElement.SONIC), 1.4f)), unlock = 8),
    // VOID.
    ItemDraft("Vacuum Seal", "A Dash through an enemy leaves the shape of you inside it.", COMMON, listOf(DRIVE),
        listOf(StatusElement.VOID),
        clauses = listOf(on(EffectTrigger.DASH_HIT, apply(StatusElement.VOID, 3, 140f), EffectAction.Strike(DamageBasis.IMPACT, 0.25f))),
        unlock = 4),
    ItemDraft("Memory Leak", "The emptiness forgets nothing, and forgets it slowly.", UNCOMMON, emptyList(), listOf(StatusElement.VOID),
        rules = listOf(ItemRule.StatusDuration(StatusElement.VOID, 1.6f), ItemRule.ElementPotency(StatusElement.VOID, 0.8f)), unlock = 7),
    // RICOCHET and SLAM: obstacle bounces and enemies thrown into things.
    ItemDraft("Splinter Field", "Whatever they hit breaks into something sharp.", UNCOMMON, listOf(CHAIN),
        clauses = listOf(on(EffectTrigger.SLAM, EffectAction.Shards(3, DamageBasis.FLAT, 18f), cooldown = 0.5f)), unlock = 7),
    // FORTUNE.
    ItemDraft("Data Prospector", "Every few fragments, a richer seam.", COMMON, listOf(FORTUNE),
        clauses = listOf(on(EffectTrigger.PICKUP, EffectAction.GainData(2.5f), every = 5)), unlock = 5),
    ItemDraft("Windfall Sensor", "Reads the odds a moment before they land.", UNCOMMON, listOf(FORTUNE),
        stats = listOf(stat(ItemEffect.PICKUP_RADIUS, 40f), stat(ItemEffect.LUCK, 0.3f), stat(ItemEffect.DATA_GAIN, 0.12f)), unlock = 6),
    // ANCHOR.
    ItemDraft("Stopping Power", "Everything you were carrying keeps going without you.", UNCOMMON, listOf(ANCHOR),
        clauses = listOf(on(EffectTrigger.BRAKE_RELEASE, EffectAction.Chain(3, 320f, DamageBasis.BRAKE, 0.5f))), unlock = 8),
    // BULWARK.
    ItemDraft("Momentum Shield", "A Dash spins up a sliver of hard light.", UNCOMMON, listOf(DRIVE, BULWARK),
        stats = listOf(stat(ItemEffect.SHIELD_CAPACITY, 12f)),
        clauses = listOf(on(EffectTrigger.DASH, EffectAction.Shield(8f), cooldown = 1f)), unlock = 6),
    // PRECISION.
    ItemDraft("Spotter Mark", "Every critical hit marks the seam for one more blow.", UNCOMMON, listOf(PRECISION),
        clauses = listOf(on(EffectTrigger.CRIT, EffectAction.Strike(DamageBasis.VICTIM_MAX_HP, 0.04f), cooldown = 0.5f)), unlock = 7),
)
