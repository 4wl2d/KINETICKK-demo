// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.foundation.design

import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.runtime.Composable
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.MotionDurationScale
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlin.coroutines.coroutineContext
import kotlin.time.TimeSource

/**
 * The target upper average frame rate of the continuous frame loops; 0 = no limit. Platforms offer no
 * frame cap of their own, so the loops pace themselves in presentation only: the fixed-step
 * simulation takes its delta from frame times and plays the same at any rate.
 */
val LocalKkFrameRateCap = staticCompositionLocalOf { 0 }

/** The maximum tick rate of a covered screen: enough for its clocks and music. */
const val KK_COVERED_FRAME_RATE_CAP: Int = 60

/** The frame limit of a covered screen: the player's limit when lower, else [KK_COVERED_FRAME_RATE_CAP]. */
fun kkCoveredFrameRateCap(capHz: Int): Int = if (capHz in 1..KK_COVERED_FRAME_RATE_CAP) capHz else KK_COVERED_FRAME_RATE_CAP

/** Wake this much early: a timer that fires late would otherwise miss the frame it waits for. */
private const val PACING_SLACK_NANOS: Long = 500_000L

/**
 * Milliseconds to wait before requesting the next frame, waking early enough to catch V-Sync.
 * [sinceLastFrameNanos] is elapsed time from the previous scheduled frame, which can be negative
 * after an early wake. [KkFramePacer] carries that remainder so timer rounding cannot raise the cap.
 */
fun kkPacingDelayMillis(capHz: Int, sinceLastFrameNanos: Long): Long =
    if (capHz <= 0) 0L else ((1_000_000_000L / capHz - sinceLastFrameNanos - PACING_SLACK_NANOS) / 1_000_000L).coerceAtLeast(0L)

/**
 * Keeps a monotonic schedule instead of starting each interval at an early timer wake. Whole
 * millisecond timers can wake up to 1.5 ms early; carrying that remainder preserves the average cap
 * without rounding every wait up and missing matching V-Sync refreshes. A missed whole interval
 * rebases the schedule so a stall never creates a catch-up backlog. No spinning or blocking;
 * without a cap this is a plain frame await, and returned timestamps always come from Compose.
 * An [infiniteAnimation] pacer drives a decorative loop that never ends: it awaits frames as an
 * infinite animation does, so a UI test's infinite-animation policy stops it like an infinite transition.
 */
class KkFramePacer private constructor(
    capHz: Int,
    private val nowNanos: () -> Long,
    private val waitMillis: suspend (Long) -> Unit,
    private val infiniteAnimation: Boolean,
) {
    constructor(capHz: Int, infiniteAnimation: Boolean = false) :
        this(capHz, monotonicNanoClock(), { delay(it) }, infiniteAnimation)

    internal constructor(capHz: Int, nowNanos: () -> Long, waitMillis: suspend (Long) -> Unit) :
        this(capHz, nowNanos, waitMillis, infiniteAnimation = false)

    private var intervalNanos = if (capHz > 0) 1_000_000_000L / capHz else 0L
    private var scheduledFrameNanos = 0L
    private var started = false

    /** Change on the frame loop's thread to rebase the timer without cancelling its pending frame. */
    var frameRateCap: Int = capHz
        set(value) {
            if (field == value) return
            val wasUnlimited = field <= 0
            field = value
            intervalNanos = if (value > 0) 1_000_000_000L / value else 0L
            // An already-requested unlimited frame can still arrive. Pace the following request from here.
            started = wasUnlimited && value > 0
            if (started) scheduledFrameNanos = nowNanos()
        }

    suspend fun awaitFrame(): Long {
        if (frameRateCap <= 0) return nextFrame()
        if (started) {
            val wait = kkPacingDelayMillis(frameRateCap, nowNanos() - scheduledFrameNanos)
            if (wait > 0L) waitMillis(wait)
        }
        val frame = nextFrame()
        if (frameRateCap > 0) {
            val now = nowNanos()
            val nextScheduledFrame = scheduledFrameNanos + intervalNanos
            scheduledFrameNanos = if (!started || now - nextScheduledFrame >= intervalNanos) now else nextScheduledFrame
            started = true
        }
        return frame
    }

    private suspend fun nextFrame(): Long =
        if (infiniteAnimation) withInfiniteAnimationFrameNanos { it } else withFrameNanos { it }
}

private fun monotonicNanoClock(): () -> Long {
    val origin = TimeSource.Monotonic.markNow()
    return { origin.elapsedNow().inWholeNanoseconds }
}

/** The longest step a presentation clock takes in one frame: after a stall, animations resume instead of jumping. */
const val KK_MAX_PRESENTATION_DELTA_SECONDS: Float = 0.1f

/** A frame interval as a presentation step: never negative, at most [KK_MAX_PRESENTATION_DELTA_SECONDS]. */
fun kkPresentationDelta(intervalSeconds: Float): Float = intervalSeconds.coerceIn(0f, KK_MAX_PRESENTATION_DELTA_SECONDS)

/**
 * Awaits paced frames until [onFrame] returns false. The first frame starts the count; each later one passes its
 * Compose frame time and the seconds since the frame before it. The shared shape of the continuous presentation loops.
 */
suspend inline fun KkFramePacer.awaitFrames(onFrame: (frameNanos: Long, intervalSeconds: Float) -> Boolean) {
    var previous = awaitFrame()
    while (true) {
        val frame = awaitFrame()
        val more = onFrame(frame, (frame - previous) / 1_000_000_000f)
        previous = frame
        if (!more) return
    }
}

/**
 * Awaits paced frames for [durationNanos] after the first one, passing each frame's time since that first frame; the
 * last call has reached the duration. A timed sequence (a screen transition, a feedback burst) at the frame limit.
 */
suspend inline fun KkFramePacer.awaitTimeline(durationNanos: Long, onFrame: (elapsedNanos: Long) -> Unit) {
    val start = awaitFrame()
    do {
        val elapsed = awaitFrame() - start
        onFrame(elapsed)
    } while (elapsed < durationNanos)
}

/**
 * Runs a finite animation at Compose's motion duration scale. Zero scale delivers the end on the next frame.
 * Each frame uses the current scale against time since the first frame, as a Compose finite animation does.
 */
suspend inline fun KkFramePacer.awaitScaledTimeline(durationNanos: Long, onFrame: (elapsedNanos: Long) -> Unit) {
    require(durationNanos >= 0L)
    val motionScale = coroutineContext[MotionDurationScale]
    val initialScale = motionScale?.scaleFactor ?: 1f
    val start = awaitFrame()
    if (initialScale == 0f || durationNanos == 0L) {
        onFrame(durationNanos)
        return
    }
    do {
        val scale = motionScale?.scaleFactor ?: 1f
        val elapsed = awaitFrame() - start
        val playTime = if (scale == 0f) durationNanos else (elapsed / scale).toLong().coerceAtMost(durationNanos)
        onFrame(playTime)
    } while (playTime < durationNanos)
}

/**
 * Seconds into a repeating loop of [periodSeconds] (armed pulses, spinning forms), advanced once per frame at the
 * player's frame limit ([LocalKkFrameRateCap]) while [running], and 0 while stopped. Like a Compose infinite
 * animation, the loop resets on motion scale changes and holds its target without requesting frames at zero scale.
 * Read it in drawing or layers so the loop redraws without recomposing.
 */
@Composable
fun rememberKkLoopClock(periodSeconds: Float, running: Boolean = true): FloatState {
    val clock = remember { mutableFloatStateOf(0f) }
    // Keep the last scale when a different frame limit restarts the effect.
    val lastScale = remember { mutableFloatStateOf(1f) }
    val frameRateCap = LocalKkFrameRateCap.current
    LaunchedEffect(periodSeconds, running, frameRateCap) {
        if (!running) {
            clock.floatValue = 0f
            return@LaunchedEffect
        }
        val pacer = KkFramePacer(frameRateCap, infiniteAnimation = true)
        val motionScale = coroutineContext[MotionDurationScale]
        var frame = pacer.awaitFrame()
        var previousFrame = frame
        var previousScale = lastScale.floatValue
        while (true) {
            val scale = motionScale?.scaleFactor ?: 1f
            clock.floatValue = when {
                scale == 0f -> periodSeconds
                scale != previousScale -> 0f
                else -> {
                    val delta = kkPresentationDelta((frame - previousFrame) / 1_000_000_000f)
                    (clock.floatValue + delta / scale) % periodSeconds
                }
            }
            if (scale != previousScale) {
                previousScale = scale
                lastScale.floatValue = scale
            }
            previousFrame = frame
            if (scale == 0f) snapshotFlow { motionScale?.scaleFactor ?: 1f }.first { it > 0f }
            frame = pacer.awaitFrame()
        }
    }
    return clock
}
