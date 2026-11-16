// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.interaction.fx

import kinetickk.ball.content.api.SkillId

/**
 * A skill's look, drawn for that skill alone: its figure [strip] at the cast point, and for a look with a reach the
 * pieces that carry it ([piece] pointing right, [pieceDiagonal] its down-right twin, null for a piece that points
 * nowhere) travelling by [motion], over a [fan] (radians) round the cast direction when that is above zero. A figure
 * with a [diagonal] twin is turned to the cast direction. A [duo] look is drawn in the skill's two hues at once.
 */
internal class PixelSkillLook(
    val strip: PixelStrip,
    val diagonal: PixelStrip? = null,
    val piece: PixelStrip? = null,
    val pieceDiagonal: PixelStrip? = null,
    val motion: PieceMotion = PieceMotion.OUT,
    val fan: Float = 0f,
    val duo: Boolean = false,
    private val reach: FxReach = FxReach.CUE,
    private val fixedReach: Float = 0f,
) {
    /** Seconds the look plays: its figure, and its last piece to its last frame. */
    val life: Float = maxOf(PixelFxClock.seconds(strip), if (piece == null) 0f else PixelPieceRules.seconds(piece, motion))

    fun reachOf(radius: Float): Float = fxReachOf(reach, radius, fixedReach)
}

/**
 * Skill cast and delayed-impact looks by skill and phase. The exhaustive `when` makes a new skill fail to compile
 * until it has a look.
 */
internal object PixelSkillLooks {
    private const val PHASES = 2

    /** The cone of Sonic Boom's sound (radians). */
    private const val BOOM_FAN = 1.2f

    /** The look of [skill] at [phase], or null when that skill has no such phase. */
    fun of(skill: SkillId, phase: Int): PixelSkillLook? = of(skill.ordinal, phase)

    fun of(skill: Int, phase: Int): PixelSkillLook? =
        if (skill !in SkillId.entries.indices || phase !in 0 until PHASES) null else looks[skill * PHASES + phase]

    private val looks: Array<PixelSkillLook?> = arrayOfNulls<PixelSkillLook>(SkillId.entries.size * PHASES).also { table ->
        for (skill in SkillId.entries) for (phase in 0 until PHASES) table[skill.ordinal * PHASES + phase] = if (phase == 0) cast(skill) else impact(skill)
    }

    /** The longest skill look (seconds). */
    val maxLife: Float = looks.maxOf { it?.life ?: 0f }

    private fun cast(skill: SkillId): PixelSkillLook = when (skill) {
        SkillId.SOLAR_FLARE -> PixelSkillLook(PixelStrip.SKILL_SOLAR_FLARE, piece = PixelStrip.SKILL_SOLAR_FLARE_PIECE,
            pieceDiagonal = PixelStrip.SKILL_SOLAR_FLARE_PIECE_D)
        SkillId.FROST_LINE -> PixelSkillLook(PixelStrip.SKILL_FROST_LINE, piece = PixelStrip.SKILL_FROST_LINE_PIECE,
            pieceDiagonal = PixelStrip.SKILL_FROST_LINE_PIECE_D, motion = PieceMotion.ALONG)
        SkillId.FROST_SNAP -> PixelSkillLook(PixelStrip.SKILL_FROST_SNAP, piece = PixelStrip.SKILL_FROST_SNAP_PIECE)
        SkillId.ARC_HOOK -> PixelSkillLook(PixelStrip.SKILL_ARC_HOOK)
        SkillId.MASS_DRIVER -> PixelSkillLook(PixelStrip.SKILL_MASS_DRIVER)
        SkillId.ENTROPIC_TIDE -> PixelSkillLook(PixelStrip.SKILL_ENTROPIC_TIDE, piece = PixelStrip.SKILL_ENTROPIC_TIDE_PIECE,
            pieceDiagonal = PixelStrip.SKILL_ENTROPIC_TIDE_PIECE_D, motion = PieceMotion.IN)
        SkillId.SONIC_BOOM -> PixelSkillLook(PixelStrip.SKILL_SONIC_BOOM, PixelStrip.SKILL_SONIC_BOOM_D, PixelStrip.SKILL_SONIC_BOOM_PIECE,
            PixelStrip.SKILL_SONIC_BOOM_PIECE_D, fan = BOOM_FAN)
        SkillId.VOID_STEP -> PixelSkillLook(PixelStrip.SKILL_VOID_STEP, piece = PixelStrip.SKILL_VOID_STEP_PIECE,
            pieceDiagonal = PixelStrip.SKILL_VOID_STEP_PIECE_D, motion = PieceMotion.ALONG)
        SkillId.SLINGSHOT -> PixelSkillLook(PixelStrip.SKILL_SLINGSHOT, PixelStrip.SKILL_SLINGSHOT_D)
        SkillId.FULL_STOP -> PixelSkillLook(PixelStrip.SKILL_FULL_STOP, piece = PixelStrip.SKILL_FULL_STOP_PIECE,
            pieceDiagonal = PixelStrip.SKILL_FULL_STOP_PIECE_D)
        SkillId.CATALYZE -> PixelSkillLook(PixelStrip.SKILL_CATALYZE, piece = PixelStrip.SKILL_CATALYZE_PIECE, duo = true)
    }

    /** Void Step's delayed impact at the rift. */
    private fun impact(skill: SkillId): PixelSkillLook? = when (skill) {
        SkillId.VOID_STEP -> PixelSkillLook(PixelStrip.SKILL_VOID_STEP_HIT, piece = PixelStrip.SKILL_VOID_STEP_HIT_PIECE,
            pieceDiagonal = PixelStrip.SKILL_VOID_STEP_HIT_PIECE_D, reach = FxReach.FIXED, fixedReach = 90f)
        else -> null
    }
}
