// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.interaction.rewards

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import kinetickk.ball.content.api.BuffStat
import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.EffectAction
import kinetickk.ball.content.api.EffectClause
import kinetickk.ball.content.api.EffectCondition
import kinetickk.ball.content.api.EffectTrigger
import kinetickk.ball.content.api.ItemDefinition
import kinetickk.ball.content.api.ItemEffect
import kinetickk.ball.content.api.ItemRule
import kinetickk.ball.content.api.ObstacleAspect
import kinetickk.ball.content.api.RelicAspect
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.WeaponFamily
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.gameplay.interaction.canvas.look.*
import kinetickk.ball.gameplay.interaction.canvas.look.elementFill
import kinetickk.ball.gameplay.interaction.canvas.look.overlayColor
import kinetickk.ball.gameplay.interaction.canvas.look.overlayIcon
import kinetickk.ball.gameplay.interaction.canvas.look.skillGlyph
import kinetickk.ball.gameplay.nucleus.render.ChoiceType
import kinetickk.ball.gameplay.nucleus.render.RelicChoiceAction
import kinetickk.ball.gameplay.nucleus.render.TotemAction
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkCardMaterial
import kinetickk.foundation.design.KkCardOrnament
import kinetickk.foundation.design.KkCardStyle
import kinetickk.foundation.design.KkFillKind
import kinetickk.foundation.design.KkIcon
import kinetickk.foundation.design.KkRolePalette

/** The pattern's strength inside the icon plate: faint, or stronger on a [RewardCardLook.plateCue] plate. */
internal fun plateCuePattern(cue: Boolean): Float = if (cue) PLATE_CUE_PATTERN else PLATE_PATTERN

private const val PLATE_PATTERN = 0.28f
private const val PLATE_CUE_PATTERN = 0.6f

/**
 * The look of [presentation] under [roles], memoized by identity: the card canvas reads it every
 * frame without allocating.
 */
internal fun rewardCardLook(presentation: RewardCardPresentation, roles: KkRolePalette): RewardCardLook =
    RewardCardLooks.get(presentation, roles)

/** Seeds of the card kinds: a kind offset plus the stable id, so the watermark step cycles by id. */
private const val ItemSeed = 0x1000
private const val RelicSeed = 0x2000
private const val WeaponSeed = 0x3000
private const val OtherSeed = 0x4000

/** Builds the look (not memoized; see [rewardCardLook]). */
internal fun buildRewardCardLook(presentation: RewardCardPresentation, roles: KkRolePalette): RewardCardLook {
    val item = presentation.item
    val relic = presentation.relic
    val weapon = presentation.weapon
    val choice = presentation.choice
    return when {
        presentation.decline -> {
            val style = KkCardStyle(KkCardMaterial.CALM, Kk.Mute2, spectrum = roles.spectrum)
            RewardCardLook(style, Kk.Mute2, Kk.Mute2)
        }
        item != null -> {
            val primary = item.elements.getOrNull(0)
            val second = item.elements.getOrNull(1)
            // The rarity takes the material of its rank; the type (Kinetic is the preset's volt) takes the face.
            val hue = primary?.overlayColor(roles) ?: roles.kinetic
            val secondHue = second?.overlayColor(roles) ?: Color.Unspecified
            val material = KkCardMaterial.ofRank(item.rarity.rank)
            val style = KkCardStyle(
                material = material,
                hue = hue,
                pattern = primary?.let { elementFill(it.ordinal) } ?: KineticFill,
                ornament = item.tags.firstOrNull()?.cardOrnament() ?: KkCardOrnament.PATTERN_CORNER,
                watermark = item.watermarkIcon(),
                seed = ItemSeed + item.id,
                patternBoost = patternBoost(hue, roles),
                spectrum = roles.spectrum,
            )
            RewardCardLook(style, hue, if (secondHue.isSpecified) secondHue else hue)
        }
        relic != null -> {
            val element = relic.aspect.element
            // An aspect supplies the face hue. Every relic takes the same chrome frame.
            val hue = presentation.accentColor(roles)
            val kinetic = relic.aspect == RelicAspect.KINETIC
            val style = KkCardStyle(
                material = RelicMaterial,
                hue = hue,
                pattern = element?.let { elementFill(it.ordinal) } ?: if (kinetic) KineticFill else KkFillKind.HALFTONE,
                ornament = relic.attunedTags.firstOrNull()?.cardOrnament() ?: KkCardOrnament.PATTERN_CORNER,
                watermark = relic.id.watermarkIcon(),
                seed = RelicSeed + relic.id.ordinal,
                patternBoost = if (element != null || kinetic) patternBoost(hue, roles) else 1f,
                spectrum = roles.spectrum,
            )
            RewardCardLook(style, hue, hue)
        }
        choice.rewardFocus != null -> {
            // A direction: its aspect's hue and pattern, the `you` hue for weapon directions, Kinetic otherwise.
            val focus = requireNotNull(choice.rewardFocus)
            val aspect = RelicAspect.entries.firstOrNull { it.name == focus.name }
            val element = aspect?.element
            val weapons = choice.type == ChoiceType.TOTEM || choice.type == ChoiceType.WEAPON
            val hue = when {
                element != null -> element.overlayColor(roles)
                weapons -> roles.you
                else -> roles.kinetic
            }
            val style = KkCardStyle(
                material = KkCardMaterial.STEEL,
                hue = hue,
                pattern = element?.let { elementFill(it.ordinal) } ?: if (weapons) KkFillKind.HALFTONE else KineticFill,
                ornament = KkCardOrnament.RETICLE,
                watermark = presentation.icon,
                seed = OtherSeed + focus.ordinal,
                patternBoost = patternBoost(hue, roles),
                spectrum = roles.spectrum,
            )
            RewardCardLook(style, hue, hue)
        }
        weapon != null || choice.type == ChoiceType.TOTEM || choice.type == ChoiceType.WEAPON -> {
            // The player's own system: the `you` hue in a steel frame, its pattern and ornament from
            // what the weapon is (an orbital, an aura, a lance, mines ...).
            val hue = roles.you
            val (pattern, ornament) = weapon?.id?.cardMarks()
                ?: (KkFillKind.HALFTONE to if (choice.totemAction == TotemAction.CHANGE_WEAPON) KkCardOrnament.VENTS else KkCardOrnament.RACK)
            val style = KkCardStyle(
                material = KkCardMaterial.STEEL,
                hue = hue,
                pattern = pattern,
                ornament = ornament,
                watermark = presentation.icon,
                seed = WeaponSeed + (weapon?.id?.ordinal ?: (32 + (choice.totemAction?.ordinal ?: 0))),
                spectrum = roles.spectrum,
            )
            RewardCardLook(style, hue, hue)
        }
        choice.relicAction == RelicChoiceAction.MELD -> {
            // A Meld seals an empty slot and has no element: Kinetic in a steel frame, a perforated edge,
            // the joined forms of the sealed slot as its watermark.
            val hue = roles.kinetic
            val style = KkCardStyle(
                material = KkCardMaterial.STEEL,
                hue = hue,
                pattern = KineticFill,
                ornament = KkCardOrnament.PERFORATION,
                watermark = KkIcon.FORMS_SQUARE,
                seed = OtherSeed + 64,
                patternBoost = patternBoost(hue, roles),
                spectrum = roles.spectrum,
            )
            RewardCardLook(style, hue, hue)
        }
        presentation.boonKind != null -> rewardBoonCardLook(presentation, roles)
        else -> {
            // Any other card: the material of its rank in its own hue, nothing behind the text.
            val hue = presentation.accentColor(roles)
            val style = KkCardStyle(KkCardMaterial.ofRank(presentation.rank), hue, seed = OtherSeed + 96, spectrum = roles.spectrum)
            RewardCardLook(style, hue, hue)
        }
    }
}

/** Relic frames use chrome for every aspect. */
internal val RelicMaterial = KkCardMaterial.CHROME

/** Ram bumper, Drive slashes, Anchor weight, Arsenal rack, Precision reticle, Bulwark rim, Overclock vents, Chain perforation, Fortune ticket. */
internal fun BuildTag.cardOrnament(): KkCardOrnament = when (this) {
    BuildTag.RAM -> KkCardOrnament.BUMPER
    BuildTag.DRIVE -> KkCardOrnament.SLASHES
    BuildTag.ANCHOR -> KkCardOrnament.WEIGHT
    BuildTag.ARSENAL -> KkCardOrnament.RACK
    BuildTag.PRECISION -> KkCardOrnament.RETICLE
    BuildTag.BULWARK -> KkCardOrnament.DOUBLE_RIM
    BuildTag.OVERCLOCK -> KkCardOrnament.VENTS
    BuildTag.CHAIN -> KkCardOrnament.PERFORATION
    BuildTag.FORTUNE -> KkCardOrnament.TICKET
}

/**
 * The Kinetic body pattern: the slash cut, which no element uses, so a Kinetic card is told from an
 * element's card by its pattern as well as by its volt (Mono's white Kinetic beside its white Cryo).
 */
internal val KineticFill = KkFillKind.SLASH

/**
 * How much stronger the element pattern is drawn for [hue] under [roles]: a pale or greyish hue (Cryo,
 * a slate Ion under Protan, every Mono hue) tells little apart by colour, so its pattern carries the
 * identity; so, a little less, does a hue the preset paints close to another damage tone (the Protan
 * and Deutan volt beside their amber Thermal).
 */
internal fun patternBoost(hue: Color, roles: KkRolePalette): Float {
    if (neutralHue(hue)) return PATTERN_BOOST
    for (index in 0..StatusElement.entries.size) {
        val tone = roles.tone(index)
        if (tone != hue && rgbDistance(tone, hue) < NEAR_TONE) return NEAR_TONE_BOOST
    }
    return 1f
}

/** A neutral hue's pattern at twice the usual boosted strength (the style caps it at KK_PATTERN_MAX_ALPHA). */
private const val PATTERN_BOOST = 4f
private const val NEAR_TONE = 0.4f
private const val NEAR_TONE_BOOST = 1.6f

/**
 * The item's own watermark glyph: an active item's comes from its effect ([activeWatermark]), since its one small
 * passive is never its identity; any other item's is the mark of its headline (its first stat line, else its first
 * clause, by what the clause does on its trigger, else its first rule), so cards that share a rarity, a tag and an
 * element, or a trigger, still carry different glyphs. Never a letter-like or arrow-like mark.
 */
internal fun ItemDefinition.watermarkIcon(): KkIcon {
    active?.let { return skillGlyph(it) }
    stats.firstOrNull()?.let { return it.effect.watermarkIcon() }
    clauses.firstOrNull()?.let { return it.watermarkIcon() }
    for (rule in rules) rule.watermarkIcon()?.let { return it }
    return elements.firstOrNull()?.overlayIcon() ?: KkIcon.FORMS_SQUARE
}

/** A clause's mark: what its first action does, read with its trigger (the hue already names an applied element). */
private fun EffectClause.watermarkIcon(): KkIcon = when (val action = actions.first()) {
    is EffectAction.Chain -> KkIcon.WEAPONS_ARC_COIL
    is EffectAction.Push -> KkIcon.WEAPONS_GRAVITY_MINES
    // A pull on a turn swings the field like a wake; any other pull is the magnet.
    is EffectAction.Pull -> if (trigger == EffectTrigger.TURN) KkIcon.WEAPONS_FLUX_WAKE else KkIcon.SYSTEM_POLARITY
    is EffectAction.Vent -> KkIcon.SYSTEM_HEAT
    is EffectAction.Overdrive -> KkIcon.SYSTEM_OVERDRIVE
    // Data broken out of an obstacle is the crystal's prism; any other Data is the archive's mark.
    is EffectAction.GainData -> if (trigger == EffectTrigger.SHATTER) KkIcon.FORMS_SQUARE else KkIcon.SYSTEM_DATA
    is EffectAction.RefreshWeapon -> KkIcon.TAG_ARSENAL
    is EffectAction.Shield -> KkIcon.SYSTEM_SHIELD
    is EffectAction.Heal -> KkIcon.SYSTEM_INTEGRITY
    is EffectAction.ExtendCombo -> KkIcon.SYSTEM_CYCLE_CLOCK
    is EffectAction.ApplyStatus -> when {
        action.pick.element == null -> KkIcon.FORMS_SQUARE
        // A weapon infusion that splashes around the target reads as a field, not a single hit.
        trigger == EffectTrigger.WEAPON_HIT && action.radius > 0f -> KkIcon.WEAPONS_PHASE_LATTICE
        else -> trigger.watermarkIcon()
    }
    is EffectAction.Burst -> when (trigger) {
        EffectTrigger.DASH -> KkIcon.WEAPONS_NULL_LANCE
        EffectTrigger.OVERDRIVE -> KkIcon.WEAPONS_NULL_LANCE
        // A blast set off by a broken obstacle blooms out of the wreck (the broken hexagon reads as the shield's outline).
        EffectTrigger.SHATTER -> KkIcon.WEAPONS_SCATTER_BLOOM
        else -> trigger.watermarkIcon()
    }
    // Shards are a prism's splinters; a slam's or a broken obstacle's are the wreckage it breaks off.
    is EffectAction.Shards ->
        if (trigger == EffectTrigger.SLAM || trigger == EffectTrigger.SHATTER) trigger.watermarkIcon() else KkIcon.FORMS_SQUARE
    is EffectAction.Strike -> when (trigger) {
        EffectTrigger.WEAPON_HIT -> KkIcon.WEAPONS_MORNINGSTAR
        // A follow-up on a critical hit is a lance into the opened seam.
        EffectTrigger.CRIT -> KkIcon.WEAPONS_NULL_LANCE
        else -> trigger.watermarkIcon()
    }
    is EffectAction.Buff -> when {
        trigger == EffectTrigger.KILL -> KkIcon.SYSTEM_ELITE
        action.stat == BuffStat.CRIT_CHANCE -> KkIcon.TAG_PRECISION
        action.stat == BuffStat.DAMAGE_REDUCTION -> KkIcon.SYSTEM_SHIELD
        action.stat == BuffStat.COOLING -> KkIcon.SYSTEM_HEAT
        action.stat == BuffStat.ACTIVATION -> KkIcon.SYSTEM_CYCLE_CLOCK
        action.stat == BuffStat.WEAPON -> KkIcon.TAG_ARSENAL
        // Damage while overheated: the redline's warning triangle.
        action.stat == BuffStat.DAMAGE && trigger == EffectTrigger.OVERHEAT -> KkIcon.FORMS_TRIANGLE
        else -> trigger.watermarkIcon()
    }
    // A beat coming round again.
    is EffectAction.RefundSkills -> KkIcon.SYSTEM_REROLL
    // The Apex badge.
    // A burst of reactions.
    // A lens placed in the world.
    is EffectAction.Zone -> KkIcon.SYSTEM_ANOMALY
    is EffectAction.EraseShots -> KkIcon.SYSTEM_SHIELD
    // An arc of motion.
    is EffectAction.CoreImpulse -> KkIcon.SYSTEM_DASH
    // A scar left on the target.
    is EffectAction.ApplyMark -> KkIcon.TAG_PRECISION
    // A shove.
    is EffectAction.Throw -> KkIcon.TAG_RAM
    // A coiled spring.
    EffectAction.PrimeDash -> KkIcon.TAG_ANCHOR
    else -> trigger.watermarkIcon()
}

private fun EffectTrigger.watermarkIcon(): KkIcon = when (this) {
    EffectTrigger.IMPACT -> KkIcon.FORMS_CIRCLE
    EffectTrigger.WEAPON_HIT -> KkIcon.WEAPONS_PHASE_LATTICE
    EffectTrigger.CRIT -> KkIcon.TAG_PRECISION
    EffectTrigger.KILL -> KkIcon.WEAPONS_PHASE_LATTICE
    EffectTrigger.DASH -> KkIcon.SYSTEM_DASH
    EffectTrigger.DASH_HIT -> KkIcon.SYSTEM_DASH
    EffectTrigger.BRAKE_RELEASE -> KkIcon.SYSTEM_BRAKE
    EffectTrigger.TURN -> KkIcon.WEAPONS_FLUX_WAKE
    EffectTrigger.HURT -> KkIcon.SYSTEM_INTEGRITY
    EffectTrigger.SHIELD_BREAK -> KkIcon.SYSTEM_SHIELD
    EffectTrigger.OVERDRIVE -> KkIcon.SYSTEM_OVERDRIVE
    EffectTrigger.OVERHEAT -> KkIcon.SYSTEM_HEAT
    EffectTrigger.REACTION -> KkIcon.FORMS_SQUARE
    EffectTrigger.BREAK -> KkIcon.SYSTEM_DISMANTLED
    EffectTrigger.LEVEL_UP -> KkIcon.FORMS_TRIANGLE
    EffectTrigger.PICKUP -> KkIcon.SYSTEM_DATA
    EffectTrigger.RICOCHET -> KkIcon.SYSTEM_ANOMALY
    EffectTrigger.SLAM -> KkIcon.SYSTEM_DISMANTLED
    EffectTrigger.SKILL_CAST -> KkIcon.SYSTEM_HEAT
    EffectTrigger.SKILL_HIT -> KkIcon.WEAPONS_SCATTER_BLOOM
    EffectTrigger.APEX -> KkIcon.SYSTEM_ELITE
    EffectTrigger.FIELD_ENTER -> KkIcon.WEAPONS_GRAVITY_MINES
    EffectTrigger.SHOT_ERASED -> KkIcon.SYSTEM_SHIELD
    EffectTrigger.MARK_CONSUMED -> KkIcon.WEAPONS_NULL_LANCE
    EffectTrigger.SHATTER -> KkIcon.SYSTEM_DISMANTLED
    EffectTrigger.BOOST -> KkIcon.WEAPONS_GYRE_DISC
    EffectTrigger.GRAZE -> KkIcon.SYSTEM_DASH
}

private fun ItemEffect.watermarkIcon(): KkIcon = when (this) {
    ItemEffect.MASS -> KkIcon.FORMS_CIRCLE
    // Impact damage is the mace's head; mass stays the ring of the Core itself.
    ItemEffect.IMPACT_DAMAGE -> KkIcon.WEAPONS_MORNINGSTAR
    ItemEffect.WEAPON_POWER -> KkIcon.TAG_ARSENAL
    // Attack speed is a stream of small shots, never the cannon's single heavy one.
    ItemEffect.ATTACK_SPEED -> KkIcon.WEAPONS_ARC_COIL
    ItemEffect.MAGNETISM, ItemEffect.PICKUP_RADIUS -> KkIcon.SYSTEM_POLARITY
    ItemEffect.COOLING -> KkIcon.SYSTEM_HEAT
    ItemEffect.MAX_INTEGRITY -> KkIcon.SYSTEM_INTEGRITY
    // Regeneration is a repair swarm at work, never a second hexagon beside the shield's.
    ItemEffect.REGEN -> KkIcon.WEAPONS_ARC_COIL
    ItemEffect.DASH_POWER, ItemEffect.DASH_EFFICIENCY -> KkIcon.SYSTEM_DASH
    ItemEffect.CRIT_CHANCE -> KkIcon.TAG_PRECISION
    // Critical damage is the cut a critical hit leaves.
    ItemEffect.CRIT_DAMAGE -> KkIcon.SYSTEM_DASH
    ItemEffect.LUCK -> KkIcon.FORMS_CIRCLE
    ItemEffect.DATA_GAIN -> KkIcon.SYSTEM_DATA
    ItemEffect.MATTER_GAIN -> KkIcon.SYSTEM_MATTER
    ItemEffect.SHIELD_CAPACITY, ItemEffect.DAMAGE_REDUCTION -> KkIcon.SYSTEM_SHIELD
    ItemEffect.COMBO_WINDOW -> KkIcon.SYSTEM_CYCLE_CLOCK
    ItemEffect.OVERDRIVE_GAIN -> KkIcon.SYSTEM_OVERDRIVE
    // The tether's turning authority.
    ItemEffect.GRIP -> KkIcon.SYSTEM_POLARITY
    // Curves wind speed up.
    ItemEffect.SLING -> KkIcon.TAG_ANCHOR
}

private fun ItemRule.watermarkIcon(): KkIcon? = when (this) {
    is ItemRule.WhileModifier -> when (val condition = condition) {
        is EffectCondition.SpeedAtLeast, is EffectCondition.SpeedBelow -> KkIcon.WEAPONS_FLUX_WAKE
        EffectCondition.Braking -> KkIcon.SYSTEM_BRAKE
        EffectCondition.ShieldUp -> KkIcon.SYSTEM_SHIELD
        is EffectCondition.IntegrityBelow -> KkIcon.SYSTEM_INTEGRITY
        EffectCondition.CoreInField -> KkIcon.WEAPONS_GRAVITY_MINES
        EffectCondition.ApexActive -> KkIcon.SYSTEM_ELITE
        is EffectCondition.SkillsCharging -> KkIcon.SYSTEM_CYCLE_CLOCK
        is EffectCondition.WeaponFamilyIs -> condition.family.watermarkIcon()
        is EffectCondition.NearObstacle -> KkIcon.FORMS_SQUARE
        else -> KkIcon.SYSTEM_ANOMALY
    }
    is ItemRule.ElementPotency -> KkIcon.FORMS_SQUARE
    is ItemRule.StatusDuration -> KkIcon.SYSTEM_CYCLE_CLOCK
    // Damage that grows with the combo: a cluster of hits, never the combo window's clock.
    is ItemRule.BreakThreshold, is ItemRule.BreakPower -> KkIcon.SYSTEM_DISMANTLED
    is ItemRule.BreakSpread -> KkIcon.WEAPONS_PHASE_LATTICE
    is ItemRule.ReactionPower, is ItemRule.ReactionRecoil -> KkIcon.FORMS_SQUARE
    is ItemRule.AlwaysCrit, is ItemRule.NonCriticalDamage, is ItemRule.GuaranteedCritAfter -> KkIcon.TAG_PRECISION
    is ItemRule.CritDamageVersus -> KkIcon.SYSTEM_ANOMALY
    is ItemRule.BonusRerolls, is ItemRule.OfferCards -> KkIcon.SYSTEM_REROLL
    is ItemRule.FreeDash -> KkIcon.SYSTEM_DASH
    is ItemRule.Convert -> KkIcon.SYSTEM_MATTER
    is ItemRule.ReactionKeystone -> KkIcon.WEAPONS_ARC_COIL
    is ItemRule.ReactionTuning -> KkIcon.FORMS_SQUARE
    is ItemRule.FieldTuning -> KkIcon.WEAPONS_GRAVITY_MINES
    is ItemRule.SkillCooldown -> KkIcon.SYSTEM_CYCLE_CLOCK
    is ItemRule.SkillPower, is ItemRule.SkillAttunement -> KkIcon.SYSTEM_HEAT
    is ItemRule.CatalysisRate -> KkIcon.WEAPONS_ARC_COIL
    is ItemRule.CoreLinkRadius -> KkIcon.SYSTEM_POLARITY
    is ItemRule.ObstacleTuning -> when (aspect) {
        ObstacleAspect.RESTITUTION -> KkIcon.SYSTEM_POLARITY
        ObstacleAspect.COLLISION_DAMAGE -> KkIcon.SYSTEM_SHIELD
        // A roll cage round the Core: integrity's hexagon would repeat the shield's on the same face.
        ObstacleAspect.LETHAL_SPEED -> KkIcon.WEAPONS_GRAVITY_MINES
        ObstacleAspect.BOOST_POWER -> KkIcon.WEAPONS_GYRE_DISC
        ObstacleAspect.WELL_PULL -> KkIcon.TAG_ANCHOR
    }
}

/** A weapon family's mark (a family-gated "while" rule names the family by its emblem weapon or form). */
private fun WeaponFamily.watermarkIcon(): KkIcon = when (this) {
    WeaponFamily.MOMENTUM -> KkIcon.WEAPONS_GYRE_DISC
    WeaponFamily.ORBITAL -> KkIcon.SYSTEM_POLARITY
    WeaponFamily.AURA -> KkIcon.WEAPONS_PHASE_LATTICE
    WeaponFamily.PROJECTILE -> KkIcon.WEAPONS_SCATTER_BLOOM
    WeaponFamily.TRAP -> KkIcon.WEAPONS_GRAVITY_MINES
    WeaponFamily.CHAIN -> KkIcon.TAG_CHAIN
    WeaponFamily.PULSE -> KkIcon.TAG_RAM
}

/** Each relic's own watermark glyph (its rule in one mark), so relics of one aspect differ. */
internal fun RelicId.watermarkIcon(): KkIcon = when (this) {
    RelicId.PERPETUAL_VECTOR -> KkIcon.SYSTEM_DASH
    RelicId.GYRE_ENGINE -> KkIcon.FORMS_CIRCLE
    RelicId.GLASS_COMET -> KkIcon.SYSTEM_DISMANTLED
    RelicId.ECHO_CHAMBER -> KkIcon.WEAPONS_FLUX_WAKE
    RelicId.FURNACE_COVENANT -> KkIcon.SYSTEM_HEAT
    RelicId.ABSOLUTE_ZERO -> KkIcon.TAG_PRECISION
    RelicId.STORMHEART_CAPACITOR -> KkIcon.WEAPONS_ARC_COIL
    RelicId.AEGIS_INVERSION -> KkIcon.SYSTEM_SHIELD
    RelicId.NEUTRON_HEART -> KkIcon.SYSTEM_MATTER
    RelicId.VERDANT_ROT -> KkIcon.SYSTEM_INTEGRITY
}

/** A weapon's body pattern and frame ornament, from what it is (orbital, aura, lance, mines, drones ...). */
internal fun WeaponId.cardMarks(): Pair<KkFillKind, KkCardOrnament> = when (this) {
    WeaponId.FLUX_WAKE -> KkFillKind.SHIMMER to KkCardOrnament.SLASHES
    WeaponId.MORNINGSTAR -> KkFillKind.HALFTONE_LARGE to KkCardOrnament.BUMPER
    WeaponId.PHASE_LATTICE -> KkFillKind.LATTICE to KkCardOrnament.DOUBLE_RIM
    WeaponId.NULL_LANCE -> KkFillKind.SLASH to KkCardOrnament.RETICLE
    WeaponId.GRAVITY_MINES -> KkFillKind.RINGS to KkCardOrnament.WEIGHT
    WeaponId.ARC_COIL -> KkFillKind.ZIGZAG to KkCardOrnament.PERFORATION
    WeaponId.GYRE_DISC -> KkFillKind.RINGS to KkCardOrnament.SLASHES
    WeaponId.SCATTER_BLOOM -> KkFillKind.HALFTONE to KkCardOrnament.RETICLE
}

/** Identity-keyed memo of card looks (draw thread; a dealt hand plus the gallery's sheets). */
private object RewardCardLooks {
    private const val CAPACITY = 16
    private val presentations = arrayOfNulls<RewardCardPresentation>(CAPACITY)
    private val palettes = arrayOfNulls<KkRolePalette>(CAPACITY)
    private val looks = arrayOfNulls<RewardCardLook>(CAPACITY)
    private var next = 0

    fun get(presentation: RewardCardPresentation, roles: KkRolePalette): RewardCardLook {
        for (index in 0 until CAPACITY) {
            if (presentations[index] === presentation && palettes[index] === roles) return looks[index]!!
        }
        val look = buildRewardCardLook(presentation, roles)
        presentations[next] = presentation
        palettes[next] = roles
        looks[next] = look
        next = (next + 1) % CAPACITY
        return look
    }
}

/** Resolves a reward tone to its color; [fixed] is used for [RewardTone.FIXED]. */
internal fun RewardTone.color(roles: KkRolePalette, fixed: Color): Color = when (this) {
    RewardTone.FIXED -> fixed
    RewardTone.YOU -> roles.you
    RewardTone.THREAT -> roles.threat
    RewardTone.MUTE -> Kk.Mute
}
