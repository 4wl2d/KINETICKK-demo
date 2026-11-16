// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.foundation.design.pacing

import kotlinx.coroutines.delay
import kotlin.time.TimeSource

/**
 * The clock and timer the gameplay pacer waits on. [nowNanos] is monotonic, on the base of Compose frame times;
 * [sleepUntil] suspends until [nowNanos] reaches a deadline (at once for one already passed), resumes on the caller's
 * dispatcher and is cancellable. A [precise] timer wakes within a fraction of a millisecond of the deadline, which
 * display pacing and the exact V-Sync-off grid rely on; a coarse one waits whole milliseconds.
 */
interface KkFrameTimer {
    val precise: Boolean

    fun nowNanos(): Long

    suspend fun sleepUntil(deadlineNanos: Long)
}

/**
 * The timer every platform has: system monotonic time and the platform's whole-millisecond delay (a Swing timer on
 * desktop), rounded to the nearest millisecond. Android, the web and tests pace with it; the desktop's precise timer
 * keeps its times, so a plan can switch between them.
 */
object KkCoarseFrameTimer : KkFrameTimer {
    private val origin = TimeSource.Monotonic.markNow()

    override val precise: Boolean get() = false

    override fun nowNanos(): Long = origin.elapsedNow().inWholeNanoseconds

    override suspend fun sleepUntil(deadlineNanos: Long) {
        val milliseconds = (deadlineNanos - nowNanos() + HALF_MILLI_NANOS) / MILLI_NANOS
        if (milliseconds > 0L) delay(milliseconds)
    }

    private const val MILLI_NANOS = 1_000_000L
    private const val HALF_MILLI_NANOS = 500_000L
}

/**
 * The V-Sync-off request schedule: an exact nanosecond grid of [intervalNanos]. [targetNanos] is when the next frame
 * is requested. [frame] takes the time stamped inside a frame callback. A frame within half an interval of its target
 * came on schedule and the target advances one interval, so the grid keeps its phase and an early or late wake never
 * accumulates. Any other frame rebases the grid one interval after it, without catching up. Primitive fields only.
 */
internal class KkTimedSchedule {
    var intervalNanos = 0L

    /** Whether [targetNanos] is set; until then the next frame is requested at once. */
    var started = false
        private set

    var targetNanos = 0L
        private set

    fun reset() {
        started = false
    }

    /** The next request comes one interval after [fromNanos]. */
    fun restart(fromNanos: Long) {
        targetNanos = fromNanos + intervalNanos
        started = true
    }

    /** The frame stamped at [nowNanos]; true when it came on schedule. */
    fun frame(nowNanos: Long): Boolean {
        val offset = nowNanos - targetNanos
        val onSchedule = started && offset <= intervalNanos / 2L && -offset <= intervalNanos / 2L
        targetNanos = if (onSchedule) targetNanos + intervalNanos else nowNanos + intervalNanos
        started = true
        return onSchedule
    }
}
