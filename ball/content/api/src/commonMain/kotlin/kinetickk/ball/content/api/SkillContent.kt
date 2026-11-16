// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.content.api

import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList

/**
 * The active effects an active item binds to a Z/X/C slot ([ItemDefinition.active]); exactly one item carries each.
 * Append only: gameplay indexes runtime cells, cues and link styles by ordinal.
 */
enum class SkillId {
    SOLAR_FLARE,
    FROST_LINE,
    FROST_SNAP,
    ARC_HOOK,
    MASS_DRIVER,
    ENTROPIC_TIDE,
    SONIC_BOOM,
    VOID_STEP,
    SLINGSHOT,
    FULL_STOP,
    CATALYZE,
}

/**
 * Where a cast lands: [SELF] at the Core, [AIM] along the Core-to-pointer direction, [NEAREST] on the
 * enemy nearest the pointer within reach of the Core, [AREA] at the pointer clamped to reach.
 */
enum class SkillTargeting { SELF, AIM, NEAREST, AREA }

/** Balance band a skill is measured in. */
enum class SkillFamily { STRIKE, MOTION, ZONE, CATALYST, GUARD }

/**
 * One rank of a skill: its base cooldown and the content-owned numbers its effect reads by index
 * (every rank of a skill lists the same number of values, in the order its [SkillId] documents).
 */
data class SkillRank(
    val cooldownSeconds: Float,
    val values: ImmutableList<Float>,
) {
    init {
        require(cooldownSeconds.isFinite() && cooldownSeconds >= ContentBounds.MIN_SKILL_COOLDOWN_SECONDS) {
            "Skill cooldown must be finite and at least ${ContentBounds.MIN_SKILL_COOLDOWN_SECONDS} s"
        }
        require(values.size <= ContentBounds.MAX_SKILL_VALUES) {
            "Skill rank values size ${values.size} exceeds ${ContentBounds.MAX_SKILL_VALUES}"
        }
        require(values.all(Float::isFinite)) { "Skill rank values must be finite" }
    }
}

/**
 * The active-effect table: content owns an effect's identity, text, element, build tags, targeting and per-rank
 * numbers; the run owns slots, cooldowns and casting. The item that carries the effect repeats its [name], [tags]
 * (in order) and [element] (bootstrap checks it), and its stacks are the rank. [element] is null for kinetic and
 * build-element effects. [reach] is the rank I distance a cast can reach from the Core (0 for [SkillTargeting.SELF]).
 */
data class SkillDefinition(
    val id: SkillId,
    val name: String,
    val summary: String,
    /** What rank III adds, shown on the item card that raises the active to rank III and in the Codex. */
    val rankThree: String,
    val element: StatusElement?,
    val tags: ImmutableList<BuildTag>,
    val targeting: SkillTargeting,
    val reach: Float,
    val family: SkillFamily,
    val ranks: ImmutableList<SkillRank>,
) {
    init {
        require(name.isNotBlank() && summary.isNotBlank() && rankThree.isNotBlank()) {
            "Skill $id needs a name, a summary and a rank III text"
        }
        require(tags.size == SKILL_TAG_COUNT && tags.toSet().size == SKILL_TAG_COUNT) {
            "Skill $id carries exactly two distinct build tags"
        }
        require(reach.isFinite() && reach >= 0f) { "Skill $id reach must be finite and non-negative" }
        require((targeting == SkillTargeting.SELF) == (reach == 0f)) {
            "Skill $id reach is zero exactly when it targets itself"
        }
        require(ranks.size == ContentBounds.MAX_SKILL_RANK) {
            "Skill $id defines exactly ${ContentBounds.MAX_SKILL_RANK} ranks"
        }
        require(ranks.zipWithNext().all { (lower, higher) -> higher.cooldownSeconds <= lower.cooldownSeconds }) {
            "Skill $id cooldown must not grow with rank"
        }
        require(ranks.all { it.values.size == ranks.first().values.size }) {
            "Skill $id ranks list the same number of values"
        }
    }

    /** Rank 1..[ContentBounds.MAX_SKILL_RANK]. */
    fun rank(rank: Int): SkillRank = ranks[rank.coerceIn(1, ranks.size) - 1]

    companion object {
        /** Build tags per effect; the active item carrying it adds a point to both, like any item. */
        const val SKILL_TAG_COUNT: Int = 2
    }
}

/**
 * The shipped pool in [SkillId] order: two skills per element, two kinetic and two catalyst skills;
 * every [BuildTag] appears on exactly four. Values per rank, in order:
 * - SOLAR_FLARE: vented heat cap, radius, damage, damage per vented heat, base Scorch
 * - FROST_LINE: length, duration s, bolts erased cap, Chill per tick, band half-width
 * - FROST_SNAP: radius, Chill, shatter impact scale, coiled Dash push, coiled s
 * - ARC_HOOK: reach, Charge, hook speed, chain links, link damage, Dash-hit window s
 * - MASS_DRIVER: duration s, mass multiplier, damage taken cut, throw speed, throw threshold speed
 * - ENTROPIC_TIDE: radius, detonation scale, heal per stack, heal stack cap
 * - SONIC_BOOM: range, damage, Resonance, bolts erased cap, push speed, recoil speed
 * - VOID_STEP: distance, rift damage, Hollow, rift radius, phase s, rift delay s
 * - SLINGSHOT: speed scale, minimum speed, maximum speed, hurl window s, hurl speed
 * - FULL_STOP: radius, impact scale, Break threshold fraction
 * - CATALYZE: radius, targets cap, reaction scale, fallback stacks
 */
fun defaultSkillDefinitions(): ImmutableList<SkillDefinition> = SkillId.entries.map(::defaultSkill).toImmutableList()

private fun defaultSkill(id: SkillId): SkillDefinition = when (id) {
    SkillId.SOLAR_FLARE -> skill(
        id, "Solar Flare",
        "Vents heat into a flare; the more heat, the more Scorch.",
        "Enemies within two Scorch of Ignite ignite at once.",
        StatusElement.THERMAL, BuildTag.OVERCLOCK, BuildTag.BULWARK, SkillTargeting.SELF, 0f, SkillFamily.STRIKE,
        rank(8.5f, 30f, 200f, 42f, 0.7f, 2f),
        rank(7.65f, 40f, 220f, 56f, 0.7f, 2f),
        rank(6.8f, 50f, 240f, 70f, 0.7f, 2f),
    )
    SkillId.FROST_LINE -> skill(
        id, "Frost Line",
        "Raises a line of frost that slows enemies, stops hostile bolts and slams thrown enemies.",
        "Enemies on the line are embrittled while they stay on it.",
        StatusElement.CRYO, BuildTag.BULWARK, BuildTag.ANCHOR, SkillTargeting.AIM, 160f, SkillFamily.ZONE,
        rank(10.2f, 320f, 7f, 30f, 1f, 48f),
        rank(9.35f, 380f, 7.7f, 40f, 1f, 60f),
        rank(8.5f, 440f, 8.4f, 50f, 1f, 72f),
    )
    SkillId.FROST_SNAP -> skill(
        id, "Frost Snap",
        "A freezing pulse that shatters Frozen and Brittle enemies and coils your next Dash.",
        "Enemies within two Chill of Freeze freeze at once.",
        StatusElement.CRYO, BuildTag.ANCHOR, BuildTag.PRECISION, SkillTargeting.SELF, 0f, SkillFamily.STRIKE,
        rank(9f, 180f, 3f, 0.84f, 1.35f, 2f),
        rank(8f, 200f, 3f, 1.05f, 1.45f, 2f),
        rank(7f, 220f, 4f, 1.26f, 1.55f, 2f),
    )
    SkillId.ARC_HOOK -> skill(
        id, "Arc Hook",
        "Grapple the enemy nearest your pointer and fly to it; the impact counts as a Dash hit.",
        "If the target dies within 1.5 s, the hook is ready again 2.5 s after the cast.",
        StatusElement.ION, BuildTag.DRIVE, BuildTag.CHAIN, SkillTargeting.NEAREST, 460f, SkillFamily.MOTION,
        rank(7f, 460f, 3f, 1_050f, 2f, 84f, 0.7f),
        rank(6.5f, 520f, 4f, 1_100f, 3f, 91f, 0.7f),
        rank(6f, 580f, 5f, 1_150f, 3f, 98f, 0.7f),
    )
    SkillId.MASS_DRIVER -> skill(
        id, "Mass Driver",
        "Your Core turns heavy: fast impacts throw enemies and add Mass.",
        "Slams while it runs add 1 Mass to both enemies.",
        StatusElement.GRAVITIC, BuildTag.RAM, BuildTag.BULWARK, SkillTargeting.SELF, 0f, SkillFamily.MOTION,
        rank(11f, 2f, 1.4f, 0.1f, 700f, 500f),
        rank(10f, 2.25f, 1.52f, 0.15f, 760f, 500f),
        rank(9f, 2.5f, 1.65f, 0.2f, 820f, 500f),
    )
    SkillId.ENTROPIC_TIDE -> skill(
        id, "Entropic Tide",
        "Draws in all Decay around you: it bursts as damage and mends the Core.",
        "Healing past full integrity becomes shield.",
        StatusElement.ENTROPY, BuildTag.BULWARK, BuildTag.FORTUNE, SkillTargeting.SELF, 0f, SkillFamily.GUARD,
        rank(10.2f, 300f, 1f, 3.8f, 8f),
        rank(9.35f, 320f, 1.2f, 3.9f, 10f),
        rank(8.5f, 340f, 1.4f, 4f, 12f),
    )
    SkillId.SONIC_BOOM -> skill(
        id, "Sonic Boom",
        "A cone of sound blasts enemies away and erases shots; the recoil hops you back.",
        "Thrown enemies ring for 0.8 s; a slam into one forces Shatter.",
        StatusElement.SONIC, BuildTag.RAM, BuildTag.PRECISION, SkillTargeting.AIM, 300f, SkillFamily.STRIKE,
        rank(7f, 300f, 65f, 3f, 8f, 620f, 420f),
        rank(6f, 330f, 85f, 3f, 10f, 620f, 420f),
        rank(5f, 360f, 100f, 4f, 12f, 620f, 420f),
    )
    SkillId.VOID_STEP -> skill(
        id, "Void Step",
        "Step through the void toward the pointer, untouchable for a moment; a rift detonates where you were.",
        "Enemies along the path turn Brittle and take 1 Hollow.",
        StatusElement.VOID, BuildTag.PRECISION, BuildTag.DRIVE, SkillTargeting.AIM, 240f, SkillFamily.MOTION,
        rank(7f, 240f, 40f, 3f, 90f, 0.3f, 0.5f),
        rank(5.75f, 270f, 50f, 4f, 90f, 0.3f, 0.5f),
        rank(4.5f, 300f, 60f, 5f, 90f, 0.3f, 0.5f),
    )
    SkillId.SLINGSHOT -> skill(
        id, "Slingshot",
        "Swing your momentum onto your aim; counts as a sharp turn.",
        "Impacts during the swing keep your momentum.",
        null, BuildTag.DRIVE, BuildTag.RAM, SkillTargeting.SELF, 0f, SkillFamily.MOTION,
        rank(8.5f, 1.2f, 700f, 1_100f, 0.6f, 420f),
        rank(7f, 1.4f, 700f, 1_100f, 0.75f, 420f),
        rank(6f, 1.55f, 700f, 1_100f, 0.85f, 420f),
    )
    SkillId.FULL_STOP -> skill(
        id, "Full Stop",
        "Stop dead: your speed becomes a shockwave that breaks near-full statuses.",
        "Also counts as a Brake release.",
        null, BuildTag.ANCHOR, BuildTag.OVERCLOCK, SkillTargeting.SELF, 0f, SkillFamily.STRIKE,
        rank(10f, 160f, 0.98f, 0.6f),
        rank(9f, 190f, 1.19f, 0.5f),
        rank(8f, 220f, 1.4f, 0.4f),
    )
    SkillId.CATALYZE -> skill(
        id, "Catalyze",
        "Every enemy near the pointer carrying two elements reacts at once.",
        "Across a wider area, forces up to ten reactions at 130% strength.",
        null, BuildTag.CHAIN, BuildTag.OVERCLOCK, SkillTargeting.AREA, 420f, SkillFamily.CATALYST,
        rank(7.65f, 308f, 8f, 1.3f, 2f),
        rank(6.8f, 350f, 10f, 1.3f, 2f),
        rank(5.95f, 392f, 10f, 1.3f, 2f),
    )
}

private fun skill(
    id: SkillId,
    name: String,
    summary: String,
    rankThree: String,
    element: StatusElement?,
    firstTag: BuildTag,
    secondTag: BuildTag,
    targeting: SkillTargeting,
    reach: Float,
    family: SkillFamily,
    rankOne: SkillRank,
    rankTwo: SkillRank,
    rankThreeValues: SkillRank,
): SkillDefinition = SkillDefinition(
    id = id,
    name = name,
    summary = summary,
    rankThree = rankThree,
    element = element,
    tags = immutableListOf(firstTag, secondTag),
    targeting = targeting,
    reach = reach,
    family = family,
    ranks = immutableListOf(rankOne, rankTwo, rankThreeValues),
)

private fun rank(cooldownSeconds: Float, vararg values: Float): SkillRank =
    SkillRank(cooldownSeconds, values.toList().toImmutableList())
