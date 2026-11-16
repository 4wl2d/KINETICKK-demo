// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.interaction.canvas.fx

import kinetickk.foundation.design.*

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import kinetickk.ball.profile.api.ReactionEffectsLevel
import kotlin.math.max
import kinetickk.ball.gameplay.interaction.canvas.*
import kinetickk.ball.gameplay.interaction.canvas.world.enemies.*

/** Golden-ratio increment of the per-element hash (`0x9E3779B9`). */
private const val GOLDEN = -0x61c88647

/**
 * A stable pseudo-random value in [0, 1) for element [index] of a look with [seed]: the 32-bit
 * finalizer of MurmurHash3 over `seed xor (index · 0x9E3779B9)`. Pure, allocation-free.
 */
internal fun fxHash(seed: Int, index: Int): Float {
    var h = seed xor (index * GOLDEN)
    h = h xor (h ushr 16)
    h *= -0x7a143595
    h = h xor (h ushr 13)
    h *= -0x3d4d51cb
    h = h xor (h ushr 16)
    return (h ushr 8) * (1f / 16_777_216f)
}

/** Draw-operation pools of the world FX ([FxDrawBudget]). */
internal enum class FxPool(val ops: Int) {
    TRANSIENT(900),
    LINKS(600),
    FIELDS(220),
    GLYPHS(480),
    /** Enemy status marks (Frozen, Brittle, Embrittled, primed): always drawn, in a pool of their own so a frozen crowd never costs the control glyphs theirs. */
    STATUS(600),
    CORE_STATES(240),
    /** The Core's trail, wake and dash echoes: always drawn, in a pool of their own so a long wake never costs the Core's state marks theirs. */
    TRAIL(520),
    /** The shards of shattering structures (one line each; floors and the slabs' flash draw regardless). */
    RUBBLE(360),
    /** Dungeon motes, telegraphs and moments (motes are secondary; telegraphs, the guardian and moments are signature). */
    DUNGEON(320),
    /** A dimension's weather: feathers, ember rain, glyph shards and datamosh (thinned first in a crowd). */
    ATMOSPHERE(260),
}

/**
 * Per-frame draw-operation budget of the world FX (hud-fx §4.11). A non-signature layer asks its
 * pool before drawing and is skipped when its operations do not fit. A signature layer always
 * draws and books what it drew, before or after drawing (a telegraph that counts its cells as it
 * lays them books the count afterwards); past its pool's end the excess is recorded as overflow.
 * Reset by [FxFrame.begin]; draw-thread confined.
 */
internal object FxDrawBudget {
    private val used = IntArray(FxPool.entries.size)

    /** Signature operations drawn past a pool's end, per pool (tests read them). */
    private val overflow = IntArray(FxPool.entries.size)

    /** Signature layers drawn and non-signature layers skipped, per pool. */
    private val signatures = IntArray(FxPool.entries.size)
    private val skipped = IntArray(FxPool.entries.size)

    fun reset() {
        used.fill(0)
        overflow.fill(0)
        signatures.fill(0)
        skipped.fill(0)
    }

    fun used(pool: FxPool): Int = used[pool.ordinal]

    fun overflow(pool: FxPool): Int = overflow[pool.ordinal]

    fun signatures(pool: FxPool): Int = signatures[pool.ordinal]

    fun skipped(pool: FxPool): Int = skipped[pool.ordinal]

    fun remaining(pool: FxPool): Int = pool.ops - used[pool.ordinal]

    /** Takes [ops] from [pool] when they fit; a [signature] always takes them (recording any overflow). */
    fun take(pool: FxPool, ops: Int, signature: Boolean): Boolean {
        val index = pool.ordinal
        val left = pool.ops - used[index]
        if (signature) {
            signatures[index]++
            overflow[index] += max(0, ops - max(0, left))
            used[index] += ops
            return true
        }
        if (ops > left) {
            skipped[index]++
            return false
        }
        used[index] += ops
        return true
    }
}

/**
 * World FX state of the frame being drawn: how many enemies are on screen (crowd level of detail,
 * capped just past [CROWD_SIG_ONLY]) and the reaction effects setting. [begin] runs once per world
 * frame before anything draws; draw-thread confined.
 */
internal object FxFrame {
    /** Above this many on-screen enemies secondary layers go (the status patterns' limit). */
    const val CROWD_SECONDARY_OFF = STATUS_PATTERN_ENEMY_LIMIT

    /** Above this many only signature layers draw. */
    const val CROWD_SIG_ONLY = 90

    var crowd: Int = 0
        private set
    var level: ReactionEffectsLevel = ReactionEffectsLevel.FULL
        private set

    fun begin(crowd: Int, level: ReactionEffectsLevel) {
        this.crowd = crowd
        this.level = level
        FxDrawBudget.reset()
    }

    /** Alpha of the pixel effects (bursts, links) for the setting. */
    val peakAlpha: Float
        get() = when (level) {
            ReactionEffectsLevel.FULL -> 1f
            ReactionEffectsLevel.REDUCED -> 0.85f
            ReactionEffectsLevel.MINIMAL -> 0.70f
        }

    /** Styled links draw as one line at the minimal setting. */
    val plainLinks: Boolean get() = level == ReactionEffectsLevel.MINIMAL
}
