// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.interaction.fx

import kotlin.math.max

/**
 * Apex activation timing and scatter distance.
 */
internal object PixelApexLook {
    const val REACH: Float = 280f

    /** Fragments thrown out to [REACH]; the first [SIGNATURE_PIECES] draw at every detail level. */
    const val PIECES: Int = 12
    const val SIGNATURE_PIECES: Int = 6

    /** The tick of the burst when the fragments start moving. */
    private const val BREAK_TICK = 6

    /** Seconds into the burst when the fragments start moving. */
    val piecesDelay: Float = BREAK_TICK / PixelStrip.APEX_BURST.fps.toFloat()

    val life: Float = max(PixelFxClock.seconds(PixelStrip.APEX_BURST), piecesDelay + PixelFxClock.seconds(PixelStrip.APEX_SPIRE))

    /**
     * Whole multiples of the texel step the mark is drawn at on [tick] of the burst: one, two and three over the
     * first seven ticks, then four, shown on two ticks of every three until it goes; 0 when it is not drawn.
     */
    fun markScale(tick: Int): Int = when {
        tick < 0 -> 0
        tick < 3 -> 1
        tick < 5 -> 2
        tick < 7 -> 3
        tick < 11 -> if (tick % 3 == 0) 0 else 4
        else -> 0
    }
}
