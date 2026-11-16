// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.interaction.fx

import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.SkillId
import kinetickk.ball.content.api.StatusElement

/** Which segment strip a link tiles along its length. */
internal enum class LinkKind {
    /** The weapon's Arc Coil zigzag. */
    WEAPON,

    /** Embers hopping along the link (Wildfire, Flame Leap). */
    EMBER,

    /** A thin jagged bolt, redrawn on every tick. */
    LIGHTNING,

    /** An iced wire with small shards that flash in turn. */
    FROST,

    /** An ink beam with colour rims and travelling sparks (Annihilate). */
    BEAM,

    /** A taut line with a hot core and a travelling swell (Arc Hook, Catalyze, Apex). */
    TAUT,

}

/** Which colours a link look takes: the style's element pair, its skill, element or the gold. */
internal enum class LinkTone { REACTION, SKILL, ELEMENT, GOLD, BONE }

/**
 * One link look: the segment strip its [kind] tiles along the link, its colours ([tone]), its presentation [life]
 * (a styled link lives at least this long) and whether its kind's own end strip marks its far end ([endMark]).
 */
internal class LinkLook(
    val kind: LinkKind,
    val tone: LinkTone,
    val life: Float,
    val endMark: Boolean = false,
)

/** Link looks by `LinkStyleCode` (0 weapon, 1 + reaction, 32 + skill, 64 + element Break, 96 + character Apex). */
internal object LinkLooks {
    const val REACTION_BASE: Int = 1
    const val SKILL_BASE: Int = 32
    const val BREAK_BASE: Int = 64
    const val APEX_BASE: Int = 96
    const val CODES: Int = 128

    /** The longest styled link presentation (seconds). */
    const val MAX_LIFE: Float = 0.35f

    private val weapon = LinkLook(LinkKind.WEAPON, LinkTone.REACTION, 0f)
    private val looks: Array<LinkLook> = Array(CODES) { build(it) }

    operator fun get(style: Int): LinkLook = if (style in 0 until CODES) looks[style] else weapon

    /** Whether [style] names a reaction, skill, element or character ([style] 0 and unknown codes draw the weapon look). */
    fun isKnown(style: Int): Boolean = style == 0 || (style in 0 until CODES && looks[style] !== weapon)

    /** The reaction ordinal of a reaction style, else −1. */
    fun reaction(style: Int): Int = (style - REACTION_BASE).takeIf { style >= REACTION_BASE && it < ReactionId.entries.size } ?: -1

    /** The skill ordinal of a skill style, else −1. */
    fun skill(style: Int): Int = (style - SKILL_BASE).takeIf { style >= SKILL_BASE && it < SkillId.entries.size } ?: -1

    /** The element ordinal of a Break style, else −1. */
    fun element(style: Int): Int = (style - BREAK_BASE).takeIf { style >= BREAK_BASE && it < StatusElement.entries.size } ?: -1

    private fun build(style: Int): LinkLook {
        if (style == 0) return weapon
        val reaction = ReactionId.entries.getOrNull(style - REACTION_BASE)?.takeIf { style < SKILL_BASE }
        if (reaction != null) return when (reaction) {
            ReactionId.WILDFIRE -> LinkLook(LinkKind.EMBER, LinkTone.REACTION, 0.30f)
            ReactionId.SUPERCONDUCT -> LinkLook(LinkKind.LIGHTNING, LinkTone.REACTION, 0.22f, endMark = true)
            ReactionId.GLACIER_CRUSH -> LinkLook(LinkKind.FROST, LinkTone.REACTION, 0.25f, endMark = true)
            ReactionId.FEEDBACK -> LinkLook(LinkKind.LIGHTNING, LinkTone.REACTION, 0.22f)
            ReactionId.SHATTERPOINT -> LinkLook(LinkKind.FROST, LinkTone.REACTION, 0.25f, endMark = true)
            ReactionId.RESONANT_ARC -> LinkLook(LinkKind.LIGHTNING, LinkTone.REACTION, 0.22f)
            ReactionId.BLACKFIRE -> LinkLook(LinkKind.EMBER, LinkTone.REACTION, 0.35f)
            ReactionId.ANNIHILATE -> LinkLook(LinkKind.BEAM, LinkTone.REACTION, 0.30f, endMark = true)
            else -> LinkLook(LinkKind.LIGHTNING, LinkTone.REACTION, 0.22f)
        }
        val skill = SkillId.entries.getOrNull(style - SKILL_BASE)?.takeIf { style >= SKILL_BASE && style < BREAK_BASE }
        if (skill != null) return when (skill) {
            SkillId.ARC_HOOK -> LinkLook(LinkKind.TAUT, LinkTone.SKILL, 0.25f, endMark = true)
            SkillId.CATALYZE -> LinkLook(LinkKind.TAUT, LinkTone.BONE, 0.25f, endMark = true)
            else -> LinkLook(LinkKind.LIGHTNING, LinkTone.SKILL, 0.22f)
        }
        val element = StatusElement.entries.getOrNull(style - BREAK_BASE)?.takeIf { style >= BREAK_BASE && style < APEX_BASE }
        if (element != null) {
            return LinkLook(LinkKind.LIGHTNING, LinkTone.ELEMENT, 0.20f)
        }
        if (style >= APEX_BASE && style - APEX_BASE < kinetickk.ball.content.api.CoreShape.entries.size) {
            return LinkLook(LinkKind.TAUT, LinkTone.GOLD, 0.25f)
        }
        return weapon
    }
}
