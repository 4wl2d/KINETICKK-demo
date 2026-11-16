// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.content.impl

import kinetickk.ball.content.api.BuffStat
import kinetickk.ball.content.api.BuildTag.ANCHOR
import kinetickk.ball.content.api.BuildTag.ARSENAL
import kinetickk.ball.content.api.BuildTag.BULWARK
import kinetickk.ball.content.api.BuildTag.CHAIN
import kinetickk.ball.content.api.BuildTag.DRIVE
import kinetickk.ball.content.api.BuildTag.FORTUNE
import kinetickk.ball.content.api.BuildTag.OVERCLOCK
import kinetickk.ball.content.api.BuildTag.PRECISION
import kinetickk.ball.content.api.BuildTag.RAM
import kinetickk.ball.content.api.ConversionKind
import kinetickk.ball.content.api.DamageBasis
import kinetickk.ball.content.api.EffectAction
import kinetickk.ball.content.api.EffectCondition
import kinetickk.ball.content.api.EffectTrigger
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.ElementPick
import kinetickk.ball.content.api.FieldAspect
import kinetickk.ball.content.api.ImpulseDirection
import kinetickk.ball.content.api.ItemEffect
import kinetickk.ball.content.api.ItemRarity.COMMON
import kinetickk.ball.content.api.ItemRarity.EPIC
import kinetickk.ball.content.api.ItemRarity.RARE
import kinetickk.ball.content.api.ItemRarity.UNCOMMON
import kinetickk.ball.content.api.ItemRule
import kinetickk.ball.content.api.MarkPick
import kinetickk.ball.content.api.ReactionAspect
import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.SkillPick
import kinetickk.ball.content.api.WeaponFamily

internal val MechanicDrafts: List<ItemDraft> = listOf(
    // CASTING (SKILL BUILDS).
    ItemDraft("Spark Gap", "After every active the air keeps crackling with charge.", COMMON, listOf(CHAIN), listOf(StatusElement.ION),
        clauses = listOf(on(EffectTrigger.SKILL_CAST, apply(StatusElement.ION, 2, 160f))),
        unlock = 3),
    ItemDraft("Kiln Primer", "Whatever your actives touch comes out of the kiln a little hotter.", COMMON, listOf(OVERCLOCK),
        listOf(StatusElement.THERMAL),
        clauses = listOf(on(EffectTrigger.SKILL_HIT, apply(StatusElement.THERMAL, 1))),
        unlock = 2),
    ItemDraft("Launch Rail", "A moving active leaves the rail greased for the next Dash.", COMMON, listOf(DRIVE),
        clauses = listOf(on(EffectTrigger.SKILL_CAST, EffectAction.PrimeDash, EffectAction.Buff(BuffStat.DAMAGE, 0.3f, 3f),
            given = listOf(EffectCondition.SkillTagged(DRIVE)))),
        unlock = 4),
    ItemDraft("Echo Capacitor", "Stores the ring of the last cast to start the next one sooner.", COMMON, listOf(OVERCLOCK),
        rules = listOf(ItemRule.SkillCooldown(null, 0.15f)),
        unlock = 4),
    ItemDraft("Overcharge Glyph", "A cast spills its surplus into the weapon.", UNCOMMON, listOf(ARSENAL),
        clauses = listOf(on(EffectTrigger.SKILL_CAST, EffectAction.Buff(BuffStat.ACTIVATION, 0.3f, 3f))),
        unlock = 5),
    ItemDraft("Cascade Relay", "An active that lands keeps looking for the next target.", RARE, listOf(CHAIN),
        clauses = listOf(on(EffectTrigger.SKILL_HIT, EffectAction.Chain(2, 280f, DamageBasis.HIT, 0.5f), every = 3)),
        unlock = 7),
    ItemDraft("Finishing Cadence", "Finish the wounded and the rhythm comes round again.", RARE, listOf(PRECISION),
        clauses = listOf(on(EffectTrigger.SKILL_HIT, EffectAction.RefundSkills(0.6f, SkillPick.CAST),
            given = listOf(EffectCondition.TargetBelow(0.3f)), cooldown = 0.5f)),
        unlock = 6),
    ItemDraft("Catalytic Converter", "Every reaction pays its toll back into your actives.", RARE, listOf(OVERCLOCK, CHAIN),
        rules = listOf(ItemRule.CatalysisRate(0.5f)),
        unlock = 6),
    ItemDraft("Alchemical Core", "Your actives forget their own element and learn yours.", EPIC, listOf(CHAIN),
        rules = listOf(ItemRule.SkillAttunement(ElementPick.DOMINANT), ItemRule.SkillPower(null, 0.2f)),
        unlock = 10),
    ItemDraft("Nomad Field", "The fields you raise pack up and follow you.", RARE, listOf(ANCHOR),
        rules = listOf(ItemRule.FieldTuning(FieldAspect.DURATION, 0.2f), ItemRule.Convert(ConversionKind.SKILL_FIELDS_FOLLOW, 0.8f)),
        unlock = 8),

    // ARCHIVE AND APEX.
    ItemDraft("Tithe Collector", "Every tenth fragment goes to the reactor.", COMMON, listOf(FORTUNE),
        stats = listOf(stat(ItemEffect.OVERDRIVE_GAIN, 0.1f), stat(ItemEffect.DATA_GAIN, 0.05f)),
        clauses = listOf(on(EffectTrigger.PICKUP, EffectAction.Overdrive(4f), every = 10))),
    ItemDraft("Mycelial Ledger", "Rot runs through the dead like roots, and the roots carry Data.", COMMON, listOf(FORTUNE),
        listOf(StatusElement.ENTROPY),
        stats = listOf(stat(ItemEffect.DATA_GAIN, 0.02f)),
        clauses = listOf(on(EffectTrigger.KILL, apply(StatusElement.ENTROPY, 2, 130f), EffectAction.GainData(0.3f),
            given = listOf(EffectCondition.TargetHas(StatusElement.ENTROPY, 3)), cooldown = 0.25f)),
        unlock = 3),
    ItemDraft("Scavenger Protocol", "A held enemy cannot hide what it carries.", RARE, listOf(FORTUNE, ANCHOR), listOf(StatusElement.CRYO),
        stats = listOf(stat(ItemEffect.DATA_GAIN, 0.035f)),
        clauses = listOf(on(EffectTrigger.KILL, EffectAction.GainData(0.5f), given = listOf(EffectCondition.TargetControlled), cooldown = 0.3f)),
        unlock = 4),
    ItemDraft("Bit Furnace", "Burn the spare bits to cool the hull.", UNCOMMON, listOf(FORTUNE, OVERCLOCK),
        stats = listOf(stat(ItemEffect.COOLING, 0.1f), stat(ItemEffect.DATA_GAIN, 0.06f)),
        clauses = listOf(on(EffectTrigger.PICKUP, EffectAction.Vent(10f), every = 6)),
        unlock = 5),
    ItemDraft("Rote Memory", "Repetition is how an active gets faster.", RARE, listOf(FORTUNE),
        stats = listOf(stat(ItemEffect.LUCK, 0.2f), stat(ItemEffect.DATA_GAIN, 0.05f)),
        clauses = listOf(on(EffectTrigger.PICKUP, EffectAction.RefundSkills(0.3f, SkillPick.LONGEST), every = 8)),
        unlock = 6),
    ItemDraft("Lodestar Ledger", "Every page of Data is bound in hard light.", RARE, listOf(BULWARK, FORTUNE),
        stats = listOf(stat(ItemEffect.DATA_GAIN, 0.04f), stat(ItemEffect.SHIELD_CAPACITY, 10f)),
        clauses = listOf(on(EffectTrigger.PICKUP, EffectAction.Shield(5f), every = 8)),
        unlock = 7),
    ItemDraft("Debt Collector", "A cashed mark always pays out.", RARE, listOf(FORTUNE, PRECISION),
        stats = listOf(stat(ItemEffect.CRIT_DAMAGE, 0.15f), stat(ItemEffect.DATA_GAIN, 0.1f)),
        clauses = listOf(on(EffectTrigger.MARK_CONSUMED, EffectAction.GainData(2.5f))),
        unlock = 8),
    // KEYSTONES.
    ItemDraft("Quench Seal", "Fire meets frost and the steel sets hard.", EPIC, listOf(PRECISION), listOf(StatusElement.THERMAL, StatusElement.CRYO),
        rules = listOf(ItemRule.ReactionKeystone(ReactionId.THERMAL_SHOCK), ItemRule.CritDamageVersus(EffectCondition.TargetFrozen, 0.5f)),
        unlock = 10),
    ItemDraft("Arc Furnace", "The plasma never pools; it leaps to the next charged hull.", EPIC, listOf(CHAIN),
        listOf(StatusElement.THERMAL, StatusElement.ION),
        clauses = listOf(on(EffectTrigger.REACTION, apply(StatusElement.ION, 2, 220f),
            given = listOf(EffectCondition.ReactionIs(ReactionId.PLASMA_BURST)))),
        rules = listOf(ItemRule.ReactionKeystone(ReactionId.PLASMA_BURST)),
        unlock = 10),
    ItemDraft("Degenerate Matter", "A star too small to burn and too heavy to let go.", EPIC, listOf(ANCHOR),
        listOf(StatusElement.THERMAL, StatusElement.GRAVITIC),
        rules = listOf(ItemRule.ReactionKeystone(ReactionId.FUSION), ItemRule.ReactionTuning(ReactionId.FUSION, ReactionAspect.RADIUS, 0.3f)),
        unlock = 12),
    ItemDraft("Cryogenic Bus", "Cold enough that the current meets no resistance at all.", EPIC, listOf(CHAIN),
        listOf(StatusElement.CRYO, StatusElement.ION),
        rules = listOf(
            ItemRule.ReactionKeystone(ReactionId.SUPERCONDUCT),
            ItemRule.ReactionTuning(ReactionId.SUPERCONDUCT, ReactionAspect.RADIUS, 0.3f),
        ),
        unlock = 12),
    ItemDraft("Like Poles", "Some things are not meant to touch.", EPIC, listOf(RAM), listOf(StatusElement.ION, StatusElement.GRAVITIC),
        clauses = listOf(on(EffectTrigger.SLAM, EffectAction.Burst(120f, DamageBasis.FLAT, 40f), cooldown = 0.5f)),
        rules = listOf(ItemRule.ReactionKeystone(ReactionId.MAGNETIZE)),
        unlock = 12),
    ItemDraft("Grave Mulch", "What dies in the pit feeds everything else in it.", EPIC, listOf(CHAIN),
        listOf(StatusElement.GRAVITIC, StatusElement.ENTROPY),
        rules = listOf(ItemRule.ReactionKeystone(ReactionId.ROT_WELL), ItemRule.ReactionTuning(ReactionId.ROT_WELL, ReactionAspect.RADIUS, 0.3f)),
        unlock = 12),
    // MARKS.
    ItemDraft("Breaker Switch", "Throw it on impact, pull it on release.", RARE, listOf(ANCHOR, RAM),
        clauses = listOf(on(EffectTrigger.IMPACT, EffectAction.ApplyMark(MarkPick.CRACKED, 4f), cooldown = 0.2f)),
        unlock = 7),
    ItemDraft("Spore Brand", "A precise wound is the best soil.", RARE, listOf(PRECISION, FORTUNE), listOf(StatusElement.ENTROPY),
        clauses = listOf(on(EffectTrigger.CRIT, EffectAction.ApplyMark(MarkPick.SPORED, 4f), cooldown = 0.5f)),
        unlock = 8),
    // FIELDS AND TERRAIN.
    ItemDraft("Quarry Rebound", "Bounce off the rock and the rock takes the shots for you.", RARE, listOf(DRIVE, BULWARK),
        clauses = listOf(on(EffectTrigger.RICOCHET, EffectAction.EraseShots(220f, 3), cooldown = 1.5f)),
        unlock = 6),
    ItemDraft("Pocket Well", "Carry a little gravity and drop it where you Dash.", UNCOMMON, listOf(ANCHOR), listOf(StatusElement.GRAVITIC),
        clauses = listOf(on(EffectTrigger.DASH, EffectAction.Well(140f, 1.2f, DamageBasis.IMPACT, 0.2f, ElementPick.GRAVITIC), cooldown = 1.5f)),
        unlock = 7),

    // CORE MOVEMENT.
    ItemDraft("Kickback Plate", "Hit something fast and bounce off harder.", COMMON, listOf(RAM),
        clauses = listOf(on(EffectTrigger.IMPACT, EffectAction.CoreImpulse(400f, ImpulseDirection.AWAY_FROM_TARGET), given = listOf(EffectCondition.SpeedAtLeast(700f)), cooldown = 0.5f)),
        unlock = 3),
    ItemDraft("Long Tether", "Even a distant reaction finds its way home to the Core.", RARE, listOf(DRIVE),
        stats = listOf(stat(ItemEffect.DASH_POWER, 0.1f)),
        rules = listOf(ItemRule.CoreLinkRadius(160f)),
        unlock = 6),
    ItemDraft("Spin Turbine", "Every sharp turn spins the turbine up.", UNCOMMON, listOf(DRIVE),
        clauses = listOf(on(EffectTrigger.TURN, EffectAction.CoreImpulse(300f, ImpulseDirection.FORWARD), cooldown = 1f)),
        unlock = 5),
    ItemDraft("Phase Spurs", "Dash through and kick them into the scenery.", RARE, listOf(RAM, DRIVE),
        clauses = listOf(on(EffectTrigger.DASH_HIT, EffectAction.Throw(600f), cooldown = 0.3f)),
        unlock = 8),
    // ARSENAL FAMILIES.
    ItemDraft("Halo Condenser", "A blow to the hull flares the aura around it.", UNCOMMON, listOf(BULWARK),
        clauses = listOf(on(EffectTrigger.HURT, EffectAction.RefreshWeapon(0.6f), given = listOf(EffectCondition.WeaponFamilyIs(WeaponFamily.AURA)), cooldown = 1f)),
        unlock = 5),
    ItemDraft("Shock Absorber", "Every pulse knocks a few shots out of the air.", RARE, listOf(BULWARK),
        clauses = listOf(on(EffectTrigger.WEAPON_HIT, EffectAction.EraseShots(160f, 2), given = listOf(EffectCondition.WeaponFamilyIs(WeaponFamily.PULSE)), cooldown = 1f, every = 3)),
        unlock = 6),
    ItemDraft("Trigger Discipline", "Steady fire keeps every other system on time.", COMMON, listOf(ARSENAL),
        clauses = listOf(on(EffectTrigger.WEAPON_HIT, EffectAction.RefundSkills(0.3f, SkillPick.LONGEST), every = 10)),
        unlock = 3),
    ItemDraft("Loader Sigil", "Each cast racks the weapon.", UNCOMMON, listOf(OVERCLOCK, ARSENAL),
        clauses = listOf(on(EffectTrigger.SKILL_CAST, EffectAction.RefreshWeapon(0.6f))),
        unlock = 6),
)
