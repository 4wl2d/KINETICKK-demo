// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.interaction.fx

import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.gameplay.nucleus.protocol.ShockwaveKind

/** The burst reach for a cue [radius] under [reach] ([fixed] for [FxReach.FIXED]). */
internal fun fxReachOf(reach: FxReach, radius: Float, fixed: Float = 0f): Float = when (reach) {
    FxReach.AREA -> radius.coerceIn(36f, 240f)
    FxReach.TARGET -> radius.coerceIn(48f, 120f)
    FxReach.FIXED -> fixed
    FxReach.CUE -> if (radius > 0f) radius.coerceIn(24f, 480f) else 120f
}

/** How the pieces of a look travel over the life of their strip. */
internal enum class PieceMotion {
    /** From the look's centre out to its reach, slowing as they arrive. */
    OUT,

    /** From the reach in onto the centre, quickening as they close. */
    IN,

    /** Standing on the reach for their whole life. */
    HOLD,

    /** One after another along the look's direction, out to the reach. */
    ALONG,
}

/** Limits of a look's pieces that its life depends on. */
internal object PixelPieceRules {
    /** Pieces a look may ask for. */
    const val MAX: Int = 20

    /** Each piece of an [PieceMotion.ALONG] run starts this long after the one before it (seconds). */
    const val STAGGER: Float = 0.045f

    /** Every other piece of a set runs this many ticks of its strip behind the rest. */
    const val LAG_TICKS: Int = 1

    /** Seconds until the last piece of a set of [piece] travelling by [motion] has played. */
    fun seconds(piece: PixelStrip, motion: PieceMotion): Float =
        PixelFxClock.seconds(piece) + if (motion == PieceMotion.ALONG) MAX * STAGGER else LAG_TICKS / piece.fps.toFloat()
}

/**
 * A reaction's look: its own figure ([strip], drawn in both elements' hues), and the pieces that carry its reach:
 * [piece] drawn pointing right, [pieceDiagonal] the same piece pointing down-right (null for a piece that points
 * nowhere), travelling by [motion]. A [fan] above zero throws them over that arc (radians) round the reaction's
 * heading instead of all round it.
 */
internal class ReactionLook(
    val strip: PixelStrip,
    val piece: PixelStrip,
    val pieceDiagonal: PixelStrip?,
    val motion: PieceMotion = PieceMotion.OUT,
    val fan: Float = 0f,
) {
    /** Seconds the look plays: its figure, and its pieces to their last frame. */
    val life: Float = maxOf(PixelFxClock.seconds(strip), PixelPieceRules.seconds(piece, motion))
}

/**
 * Which pixel strips play for each status element and reaction. A Break plays its element's signature and scatters
 * the element's mote, the same figure in miniature. Each of the 21 reactions plays a figure and pieces drawn for it
 * alone ([ReactionLook]); the exhaustive `when` makes a new reaction fail to compile until it has a look.
 */
internal object PixelLooks {
    private val breaks: Array<PixelStrip> = Array(StatusElement.entries.size) { ordinal ->
        when (StatusElement.entries[ordinal]) {
            StatusElement.THERMAL -> PixelStrip.THERMAL_BREAK
            StatusElement.CRYO -> PixelStrip.CRYO_BREAK
            StatusElement.ION -> PixelStrip.ION_BREAK
            StatusElement.GRAVITIC -> PixelStrip.GRAVITIC_BREAK
            StatusElement.ENTROPY -> PixelStrip.ENTROPY_BREAK
            StatusElement.SONIC -> PixelStrip.SONIC_BREAK
            StatusElement.VOID -> PixelStrip.VOID_BREAK
        }
    }

    private val motes: Array<PixelStrip> = Array(StatusElement.entries.size) { ordinal ->
        when (StatusElement.entries[ordinal]) {
            StatusElement.THERMAL -> PixelStrip.THERMAL_MOTE
            StatusElement.CRYO -> PixelStrip.CRYO_MOTE
            StatusElement.ION -> PixelStrip.ION_MOTE
            StatusElement.GRAVITIC -> PixelStrip.GRAVITIC_MOTE
            StatusElement.ENTROPY -> PixelStrip.ENTROPY_MOTE
            StatusElement.SONIC -> PixelStrip.SONIC_MOTE
            StatusElement.VOID -> PixelStrip.VOID_MOTE
        }
    }

    /** Each satellite mote of a burst starts this many seconds after the one before it. */
    const val SATELLITE_STAGGER: Float = 0.05f

    /** The most satellites a burst scatters by its reach; merged events and skill ranks add up to [EXTRA_SATELLITES]. */
    const val MAX_SATELLITES: Int = 6
    const val EXTRA_SATELLITES: Int = 2

    /** A kill's shockwave at least this wide belongs to something large, which is dismantled by its own death. */
    const val BLAST_KILL_RADIUS: Float = 110f

    /** The Break signature of the status element [element] (an ordinal). */
    fun breakStrip(element: Int): PixelStrip = breaks[element.coerceIn(0, breaks.lastIndex)]

    /** The mote of the status element [element] (an ordinal). */
    fun moteStrip(element: Int): PixelStrip = motes[element.coerceIn(0, motes.lastIndex)]

    /** How long the last satellite of a full scatter takes to play out (seconds). */
    val satelliteSeconds: Float = (MAX_SATELLITES + EXTRA_SATELLITES) * SATELLITE_STAGGER + motes.maxOf(PixelFxClock::seconds)

    /** How long a Break flash lives (seconds): its longest signature, and long enough for its last satellite. */
    val breakSeconds: Float = maxOf(breaks.maxOf(PixelFxClock::seconds), satelliteSeconds)

    /**
     * The strip that marks an impact over the enemies, by its shockwave's [kind] ([ShockwaveKind]) and [maxRadius]: the
     * bone pop where an enemy died (a large one's shell caves in and bursts into plates, the Architect's included), the
     * dismantled mark of an Elite, a mini-boss or a secret boss at any width (a cut on the slash axis, the halves parted
     * across it into plates), and the slash of a ram, the only wave that plays it; null for every other wave, whatever
     * its colour.
     */
    fun impactStrip(kind: Int, maxRadius: Float): PixelStrip? = when (kind) {
        ShockwaveKind.KILL -> if (maxRadius >= BLAST_KILL_RADIUS) PixelStrip.DEATH_LARGE else PixelStrip.KILL
        ShockwaveKind.ELITE -> PixelStrip.KILL_ELITE
        ShockwaveKind.RAM -> PixelStrip.SLASH
        else -> null
    }

    /** The fan of Glacier Crush's shards: five of them 25 degrees apart. */
    private const val GLACIER_FAN: Float = 1.75f

    private val reactions: Array<ReactionLook> = Array(ReactionId.entries.size) { ordinal ->
        when (ReactionId.entries[ordinal]) {
            ReactionId.THERMAL_SHOCK -> ReactionLook(PixelStrip.REACT_THERMAL_SHOCK, PixelStrip.REACT_THERMAL_SHOCK_PIECE, PixelStrip.REACT_THERMAL_SHOCK_PIECE_D)
            ReactionId.PLASMA_BURST -> ReactionLook(PixelStrip.REACT_PLASMA_BURST, PixelStrip.REACT_PLASMA_BURST_PIECE, PixelStrip.REACT_PLASMA_BURST_PIECE_D)
            ReactionId.FUSION -> ReactionLook(PixelStrip.REACT_FUSION, PixelStrip.REACT_FUSION_PIECE, PixelStrip.REACT_FUSION_PIECE_D, PieceMotion.IN)
            ReactionId.WILDFIRE -> ReactionLook(PixelStrip.REACT_WILDFIRE, PixelStrip.REACT_WILDFIRE_PIECE, PixelStrip.REACT_WILDFIRE_PIECE_D)
            ReactionId.SUPERCONDUCT -> ReactionLook(PixelStrip.REACT_SUPERCONDUCT, PixelStrip.REACT_SUPERCONDUCT_PIECE, PixelStrip.REACT_SUPERCONDUCT_PIECE_D)
            ReactionId.GLACIER_CRUSH -> ReactionLook(PixelStrip.REACT_GLACIER_CRUSH, PixelStrip.REACT_GLACIER_CRUSH_PIECE, PixelStrip.REACT_GLACIER_CRUSH_PIECE_D,
                fan = GLACIER_FAN)
            ReactionId.EMBRITTLE -> ReactionLook(PixelStrip.REACT_EMBRITTLE, PixelStrip.REACT_EMBRITTLE_PIECE, PixelStrip.REACT_EMBRITTLE_PIECE_D)
            ReactionId.MAGNETIZE -> ReactionLook(PixelStrip.REACT_MAGNETIZE, PixelStrip.REACT_MAGNETIZE_PIECE, PixelStrip.REACT_MAGNETIZE_PIECE_D, PieceMotion.IN)
            ReactionId.FEEDBACK -> ReactionLook(PixelStrip.REACT_FEEDBACK, PixelStrip.REACT_FEEDBACK_PIECE, PixelStrip.REACT_FEEDBACK_PIECE_D)
            ReactionId.ROT_WELL -> ReactionLook(PixelStrip.REACT_ROT_WELL, PixelStrip.REACT_ROT_WELL_PIECE, PixelStrip.REACT_ROT_WELL_PIECE_D, PieceMotion.IN)
            ReactionId.THUNDERCLAP -> ReactionLook(PixelStrip.REACT_THUNDERCLAP, PixelStrip.REACT_THUNDERCLAP_PIECE, PixelStrip.REACT_THUNDERCLAP_PIECE_D)
            ReactionId.SHATTERPOINT -> ReactionLook(PixelStrip.REACT_SHATTERPOINT, PixelStrip.REACT_SHATTERPOINT_PIECE, PixelStrip.REACT_SHATTERPOINT_PIECE_D)
            ReactionId.RESONANT_ARC -> ReactionLook(PixelStrip.REACT_RESONANT_ARC, PixelStrip.REACT_RESONANT_ARC_PIECE, PixelStrip.REACT_RESONANT_ARC_PIECE_D)
            ReactionId.SEISMIC -> ReactionLook(PixelStrip.REACT_SEISMIC, PixelStrip.REACT_SEISMIC_PIECE, PixelStrip.REACT_SEISMIC_PIECE_D)
            ReactionId.FRACTURE -> ReactionLook(PixelStrip.REACT_FRACTURE, PixelStrip.REACT_FRACTURE_PIECE, PixelStrip.REACT_FRACTURE_PIECE_D)
            ReactionId.SILENCE -> ReactionLook(PixelStrip.REACT_SILENCE, PixelStrip.REACT_SILENCE_PIECE, PixelStrip.REACT_SILENCE_PIECE_D, PieceMotion.IN)
            ReactionId.BLACKFIRE -> ReactionLook(PixelStrip.REACT_BLACKFIRE, PixelStrip.REACT_BLACKFIRE_PIECE, PixelStrip.REACT_BLACKFIRE_PIECE_D)
            ReactionId.STASIS -> ReactionLook(PixelStrip.REACT_STASIS, PixelStrip.REACT_STASIS_PIECE, null, PieceMotion.HOLD)
            ReactionId.ANNIHILATE -> ReactionLook(PixelStrip.REACT_ANNIHILATE, PixelStrip.REACT_ANNIHILATE_PIECE, PixelStrip.REACT_ANNIHILATE_PIECE_D)
            ReactionId.NULL_WELL -> ReactionLook(PixelStrip.REACT_NULL_WELL, PixelStrip.REACT_NULL_WELL_PIECE, PixelStrip.REACT_NULL_WELL_PIECE_D, PieceMotion.IN)
            ReactionId.OBLIVION -> ReactionLook(PixelStrip.REACT_OBLIVION, PixelStrip.REACT_OBLIVION_PIECE, PixelStrip.REACT_OBLIVION_PIECE_D, PieceMotion.IN)
        }
    }

    /** The look of the reaction with ordinal [reaction]. */
    fun reaction(reaction: Int): ReactionLook = reactions[reaction.coerceIn(0, reactions.lastIndex)]

    /** Seconds the reaction with ordinal [reaction] plays. */
    fun reactionSeconds(reaction: Int): Float = this.reaction(reaction).life

    /** The longest reaction look (seconds). */
    val maxReactionSeconds: Float = reactions.maxOf { it.life }

    /** The longest of the marks that play over a reaction: the keystone mark and the one on the Core. */
    val reactionMarkSeconds: Float =
        maxOf(PixelFxClock.seconds(PixelStrip.REACT_KEYSTONE), PixelFxClock.seconds(PixelStrip.REACT_VERB))

    /** How a reaction's reach follows its cue: its target's size, its own area, or a fixed reach. */
    fun reactionReach(reaction: ReactionId, radius: Float): Float = when (reaction) {
        ReactionId.THERMAL_SHOCK, ReactionId.EMBRITTLE, ReactionId.BLACKFIRE, ReactionId.ANNIHILATE, ReactionId.OBLIVION ->
            fxReachOf(FxReach.TARGET, radius)
        ReactionId.WILDFIRE, ReactionId.RESONANT_ARC -> 96f
        ReactionId.SUPERCONDUCT -> 72f
        ReactionId.FEEDBACK -> 90f
        ReactionId.GLACIER_CRUSH -> 220f
        ReactionId.SHATTERPOINT -> 240f
        ReactionId.PLASMA_BURST, ReactionId.FUSION, ReactionId.MAGNETIZE, ReactionId.ROT_WELL, ReactionId.THUNDERCLAP, ReactionId.SEISMIC,
        ReactionId.FRACTURE, ReactionId.SILENCE, ReactionId.STASIS, ReactionId.NULL_WELL -> fxReachOf(FxReach.AREA, radius)
    }
}
