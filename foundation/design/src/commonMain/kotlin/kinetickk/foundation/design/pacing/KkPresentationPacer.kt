// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.foundation.design.pacing

import androidx.compose.runtime.withFrameNanos
import kinetickk.foundation.design.kkPacingDelayMillis

/**
 * One paced frame of a continuous loop. It runs inside the frame callback, before that frame's composition, layout
 * and drawing, so the state it writes is drawn in the same frame and asks for no frame of its own. [frameNanos] is
 * the Compose frame time; [contentDeltaNanos] is how far the frame's content advances since the previous content
 * frame.
 */
fun interface KkFrameCallback {
    fun onFrame(frameNanos: Long, contentDeltaNanos: Long)
}

/**
 * The gameplay loop's pacer. [run] requests every frame with one callback allocated with the pacer and calls the
 * loop's [KkFrameCallback] inside it. How it paces follows the [display] and the [frameRateCap], planned again only
 * when one of them changes:
 * - Display-paced, under V-Sync with a known refresh rate and a precise [KkDisplayPacing.timer]: it never waits. The
 *   next frame is requested from the frame's own continuation, which runs while that frame draws, so the request
 *   reaches the frame loop before its post-draw V-Sync gate and every frame starts on a refresh. Content advances by
 *   whole refresh periods ([KkFrameGrid]), the period learned within a hertz of the reported rate. A limit below the
 *   refresh rate shows content on an even share of the refreshes ([kkVsyncDivisor]); the frames in between hold,
 *   replaying the last content without a callback, so the display keeps its cadence and content changes every few
 *   refreshes exactly. A frame no refresh elapsed for (an unthrottled repaint) reaches no callback either.
 * - Timed, with V-Sync off, a cap and a precise timer: each request waits for its deadline on an exact nanosecond grid
 *   ([KkTimedSchedule], [kkTimedIntervalNanos]). A frame that came on schedule advances its content by whole
 *   intervals, unless that would move it more than half an interval from its own (frames slower than the grid); a
 *   late one rebases the grid without catching up and advances by its raw interval.
 * - Otherwise, with a cap it waits before each request on the whole-millisecond schedule of `KkFramePacer` (early
 *   wakes carry forward, a missed whole interval rebases without a catch-up backlog); without a cap it requests every
 *   frame. Content advances by the raw frame intervals.
 *
 * Schedules are anchored at the timer's time stamped inside the frame callback, so a frame's own work counts toward
 * its interval. The first frame of a run only starts the count.
 */
class KkPresentationPacer(capHz: Int, display: KkDisplayPacing = KkDisplayPacing()) {
    private val grid = KkFrameGrid()
    private val schedule = KkTimedSchedule()
    private var mode = LEGACY

    // Display-paced: refreshes per content frame, and the content held since the last content frame.
    private var divisor = 1
    private var heldNanos = 0L

    // The millisecond schedule.
    private var intervalNanos = intervalNanos(capHz)
    private var scheduledFrameNanos = 0L
    private var started = false

    // Whether the pending request was paced; a frame requested without a cap never moves the schedule.
    private var pacedRequest = false

    // The timer's time stamped in the last frame callback, valid while the timer stays the same.
    private var lastFrameNowNanos = 0L
    private var framed = false

    private var callback: KkFrameCallback? = null
    private val frame: (Long) -> Unit = { frameNanos -> onFrameTime(frameNanos) }

    /** Change on the frame loop's thread to rebase the timer without cancelling its pending frame. */
    var frameRateCap: Int = capHz
        set(value) {
            if (field == value) return
            val wasUnlimited = field <= 0
            field = value
            intervalNanos = intervalNanos(value)
            // An already-requested unlimited frame can still arrive. Pace the following request from here.
            started = wasUnlimited && value > 0
            if (started) scheduledFrameNanos = display.timer.nowNanos()
            replan()
        }

    /** The display the frames pace against; change on the frame loop's thread. */
    var display: KkDisplayPacing = display
        set(value) {
            if (field == value) return
            val timerChanged = field.timer !== value.timer
            field = value
            if (timerChanged) {
                // Times of another timer: the schedules start again from this timer's clock.
                framed = false
                schedule.reset()
                if (started) scheduledFrameNanos = value.timer.nowNanos()
            }
            replan()
        }

    init {
        replan()
    }

    /**
     * Paces frames and calls [callback] inside each one until the caller is cancelled; an exception the callback
     * throws ends the run and surfaces here. One run at a time.
     */
    suspend fun run(callback: KkFrameCallback): Nothing {
        check(this.callback == null) { "The pacer already runs a frame loop" }
        this.callback = callback
        grid.reset()
        heldNanos = 0L
        try {
            while (true) {
                val cap = frameRateCap
                pacedRequest = mode == LEGACY && cap > 0
                if (pacedRequest && started) {
                    val timer = display.timer
                    val now = timer.nowNanos()
                    val wait = kkPacingDelayMillis(cap, now - scheduledFrameNanos)
                    if (wait > 0L) timer.sleepUntil(now + wait * NANOS_PER_MILLI)
                } else if (mode == TIMED && schedule.started) {
                    display.timer.sleepUntil(schedule.targetNanos)
                }
                // A display-paced request never waits: it runs from the previous frame's continuation.
                withFrameNanos(frame)
            }
        } finally {
            this.callback = null
        }
    }

    private fun replan() {
        val previous = mode
        val display = display
        val refreshHz = display.refreshHz
        val cap = frameRateCap
        val precise = display.timer.precise
        mode = when {
            display.vsync && refreshHz > 0 && precise -> DISPLAY
            !display.vsync && cap > 0 && precise -> TIMED
            else -> LEGACY
        }
        divisor = if (mode == DISPLAY) kkVsyncDivisor(refreshHz, cap) else 1
        val period = when (mode) {
            DISPLAY -> NANOS_PER_SECOND / refreshHz
            TIMED -> kkTimedIntervalNanos(cap, refreshHz)
            else -> 0L
        }
        if (mode == LEGACY && previous != LEGACY) {
            // The frames so far were not on this schedule, as from an unlimited loop: pace the following request.
            started = cap > 0
            if (started) scheduledFrameNanos = display.timer.nowNanos()
        }
        if (mode == TIMED && (previous != TIMED || period != schedule.intervalNanos || !schedule.started)) {
            schedule.intervalNanos = period
            // The next request comes one interval after the last frame; before any frame, at once.
            if (framed) schedule.restart(lastFrameNowNanos) else schedule.reset()
        }
        grid.periodNanos = period
        // Frames slower than the V-Sync-off grid are late, not jittered: they keep their raw intervals.
        grid.boundedByRaw = mode == TIMED
        // A refresh rate in whole hertz is up to one hertz from the real one (macOS truncates 59.94 Hz to 59): the
        // display-paced grid learns the real refresh period within that. The V-Sync-off grid is the timer's own.
        grid.periodToleranceNanos = if (mode == DISPLAY) period / refreshHz else 0L
    }

    private fun onFrameTime(frameNanos: Long) {
        val now = display.timer.nowNanos()
        lastFrameNowNanos = now
        framed = true
        var onGrid = true
        if (mode == LEGACY) {
            if (pacedRequest && frameRateCap > 0) {
                val next = scheduledFrameNanos + intervalNanos
                scheduledFrameNanos = if (!started || now - next >= intervalNanos) now else next
                started = true
            }
        } else if (mode == TIMED) {
            onGrid = schedule.frame(now)
        }
        val loop = callback ?: return
        val step = grid.advance(frameNanos, onGrid)
        if (step == KkFrameGrid.PRIME) return
        heldNanos += step
        if (mode == DISPLAY && !kkShowsContent(heldNanos, divisor, grid.periodNanos)) return
        val content = heldNanos
        heldNanos = 0L
        loop.onFrame(frameNanos, content)
    }

    private fun intervalNanos(capHz: Int): Long = if (capHz > 0) NANOS_PER_SECOND / capHz else 0L
}

/**
 * Whether a display-paced frame shows new content after [heldNanos] since the last content frame, for content every
 * [divisor] refreshes of [periodNanos]: with every refresh, any advance (a frame no refresh elapsed for holds); with
 * fewer, once the held time reaches all but half a refresh of the share, so a missed refresh lengthens one content
 * frame instead of shifting every later one. The frames that do not show content hold.
 */
internal fun kkShowsContent(heldNanos: Long, divisor: Int, periodNanos: Long): Boolean =
    if (divisor <= 1) heldNanos > 0L else 2L * heldNanos >= (2L * divisor - 1L) * periodNanos

/** The millisecond schedule of `KkFramePacer`, raw frame intervals. */
private const val LEGACY = 0

/** Requested from each frame's continuation, content on whole refresh periods. */
private const val DISPLAY = 1

/** Requested on the exact V-Sync-off grid, content on whole intervals when on schedule. */
private const val TIMED = 2

private const val NANOS_PER_SECOND = 1_000_000_000L
private const val NANOS_PER_MILLI = 1_000_000L
