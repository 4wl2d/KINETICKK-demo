// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.foundation.design.pacing

import kotlin.math.abs

/**
 * The content time of a paced frame loop on a period grid: how far each frame's content advances. Frame timestamps
 * carry thread and dispatch jitter that the display never shows (a V-Sync frame is shown a whole number of refreshes
 * after the one before), so on the grid each frame advances its content by the whole periods that elapsed instead of
 * by its raw interval.
 *
 * [advance] takes the raw frame time. The first frame only primes ([PRIME]). After that:
 * - A frame [STALL_NANOS] or more past the content time is a stall: it passes its raw interval and the content time
 *   rebases onto the frame (the simulation clamps long intervals itself).
 * - Without a period, for a frame that did not come on schedule (onGrid false), or once more than half of the last
 *   32 intervals were shorter than three quarters of the period (the frames run faster than the grid), it passes the
 *   raw interval and keeps the content time's offset from the frames.
 * - Otherwise the content time advances by the nearest whole number k of periods to the frame's distance from it,
 *   plus 1/32 of what is left over, so the content time follows the mean frame phase without a periodic double step.
 *   A frame that no period elapsed for (k = 0, an unthrottled repaint) advances nothing. Jitter below half a period
 *   never reaches the content.
 * - On a timer's own grid ([boundedByRaw]) a snapped step more than half a period from the frame's raw interval passes
 *   the raw interval instead, keeping the offset: frames slower than the grid are late, not jittered, and snapping them
 *   would round their growing lag into a periodic double step.
 *
 * A display reports its refresh rate in whole hertz, up to one hertz from the real one (macOS truncates 59.94 Hz to
 * 59), and the 1/32 term alone absorbs a period error only below 1/64 of the period. So with a [periodToleranceNanos]
 * the grid also learns the real period: the leftovers of the frames it snaps accumulate into a trim of the period,
 * at most the tolerance either way, until they average zero. 0 trusts the period exactly (a timer's own interval).
 *
 * A period change ([periodNanos]) moves the content time to within half the new period of the frames; the next step
 * that advances carries that shift once, so the steps keep summing to the content time and stay within half a period
 * of the raw frame intervals (audio stays locked to wall time). Steps are never negative. Primitive fields only:
 * advancing allocates nothing.
 */
internal class KkFrameGrid {
    private var contentNanos = 0L
    private var lastRawNanos = 0L
    private var started = false
    private var shortBits = 0

    // The content shift of a period change that the next advancing step has not carried yet.
    private var carryNanos = 0L

    // The leftovers of the snapped frames, in 1/2^TRIM_SHIFT ns: the learned trim of the period.
    private var trimSum = 0L

    /** The grid period in nanoseconds; 0 passes raw intervals. A new period forgets the learned trim. */
    var periodNanos: Long = 0L
        set(value) {
            if (field == value) return
            field = value
            shortBits = 0
            trimSum = 0L
            if (started) {
                val half = value / 2L
                val shifted = lastRawNanos + (contentNanos - lastRawNanos).coerceIn(-half, half)
                carryNanos += shifted - contentNanos
                contentNanos = shifted
            }
        }

    /**
     * How far the real period may be from [periodNanos], in nanoseconds (at most an eighth of the period counts); the
     * grid learns the real period within it. 0, the default, keeps the period exact. A new tolerance forgets the trim.
     */
    var periodToleranceNanos: Long = 0L
        set(value) {
            if (field == value) return
            field = value
            trimSum = 0L
        }

    /**
     * Whether a snapped step must stay within half a period of the frame's raw interval: on for a timer's own grid
     * (V-Sync off), where a frame that comes later than the grid is late rather than jittered. Off for a refresh grid,
     * where snapping absorbs up to half a period of timestamp jitter and a missed refresh is a true double step.
     */
    var boundedByRaw: Boolean = false

    /** Forgets the frames so far, but not the learned period: the next frame primes again. */
    fun reset() {
        started = false
        shortBits = 0
        carryNanos = 0L
    }

    /** The content step of the frame at [rawNanos], or [PRIME] for the first frame; [onGrid] is false off schedule. */
    fun advance(rawNanos: Long, onGrid: Boolean): Long {
        if (!started) {
            started = true
            contentNanos = rawNanos
            lastRawNanos = rawNanos
            return PRIME
        }
        val raw = (rawNanos - lastRawNanos).coerceAtLeast(0L)
        lastRawNanos = rawNanos
        if (rawNanos - contentNanos >= STALL_NANOS) {
            contentNanos = rawNanos
            carryNanos = 0L
            return raw
        }
        val period = periodNanos
        shortBits = (shortBits shl 1) or (if (period > 0L && 4L * raw < 3L * period) 1 else 0)
        if (period <= 0L || !onGrid || shortBits.countOneBits() > SHORT_LIMIT) {
            contentNanos += raw
            return carried(raw)
        }
        val working = period + (trimSum shr TRIM_SHIFT)
        val distance = rawNanos - contentNanos
        val periods = if (distance <= -working / 2L) 0L else (distance + working / 2L) / working
        val leftover = distance - periods * working
        val step = if (periods == 0L) 0L else (periods * working + (leftover shr 5)).coerceAtLeast(0L)
        if (boundedByRaw && 2L * abs(step - raw) > period) {
            contentNanos += raw
            return carried(raw)
        }
        if (periods == 0L) return 0L
        contentNanos += step
        val trimLimit = periodToleranceNanos.coerceIn(0L, period shr 3) shl TRIM_SHIFT
        trimSum = (trimSum + leftover).coerceIn(-trimLimit, trimLimit)
        return carried(step)
    }

    private fun carried(step: Long): Long {
        val total = step + carryNanos
        if (total < 0L) {
            carryNanos = total
            return 0L
        }
        carryNanos = 0L
        return total
    }

    companion object {
        /** [advance]'s result for the first frame: it starts the count and advances nothing. */
        const val PRIME: Long = -1L

        /** A frame this far past the content time is a stall, not a frame interval. */
        const val STALL_NANOS: Long = 100_000_000L

        /** More short intervals than this among the last 32 leave the grid for raw intervals. */
        private const val SHORT_LIMIT = 16

        /**
         * The trim learns 1/2^TRIM_SHIFT of each leftover: with the 1/32 phase term the loop settles in about 60 frames
         * with little overshoot, and milliseconds of frame jitter move the period by a few microseconds at most.
         */
        private const val TRIM_SHIFT = 11
    }
}
