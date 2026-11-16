// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.app.shared

import kinetickk.foundation.design.pacing.KkCoarseFrameTimer
import kinetickk.foundation.design.pacing.KkFrameTimer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.LockSupport
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * The desktop's precise frame timer for the gameplay pacer. Swing timers wake on whole milliseconds behind other event
 * work; this one wakes within tens of microseconds. One daemon thread, started on the first wait, serves one waiter at
 * a time: it parks until a margin before the deadline, spins the rest with [Thread.onSpinWait], then resumes the
 * waiter through the waiter's own dispatcher (the frame loop's, which hands it to the event thread). The margin
 * follows how late parking wakes: twice its moving average plus 50 µs, between 100 µs and 1 ms. A deadline less than
 * 50 µs away returns at once, and a second waiter while one waits falls back to the coarse millisecond delay. Times are
 * [KkCoarseFrameTimer]'s, so a plan can switch between the two. Nothing is allocated per wait beyond the suspension.
 */
internal class DesktopFrameTimer(private val threadName: String = "kk-frame-timer") : KkFrameTimer {
    private val waiter = AtomicReference<CancellableContinuation<Unit>?>(null)
    private val claimed = AtomicBoolean(false)

    // The waiter's deadline on System.nanoTime, written before the waiter is published.
    @Volatile
    private var deadlineSystemNanos = 0L

    @Volatile
    private var thread: Thread? = null

    // Confined to the timer thread: the moving average of how late a park returned.
    private var lateAverageNanos = 0L
    private val onCancel: (Throwable?) -> Unit = { dropCancelled() }

    override val precise: Boolean get() = true

    override fun nowNanos(): Long = KkCoarseFrameTimer.nowNanos()

    /** Whether a waiter is parked on the timer thread now. */
    internal val waiting: Boolean get() = waiter.get() != null

    override suspend fun sleepUntil(deadlineNanos: Long) {
        val remaining = deadlineNanos - nowNanos()
        if (remaining < IMMEDIATE_NANOS) return
        if (!claimed.compareAndSet(false, true)) {
            // Another loop holds the timer thread: the coarse delay serves this one.
            KkCoarseFrameTimer.sleepUntil(deadlineNanos)
            return
        }
        try {
            suspendCancellableCoroutine { continuation ->
                deadlineSystemNanos = System.nanoTime() + remaining
                waiter.set(continuation)
                continuation.invokeOnCancellation(onCancel)
                LockSupport.unpark(worker())
            }
        } finally {
            claimed.set(false)
        }
    }

    private fun dropCancelled() {
        val current = waiter.get() ?: return
        if (current.isCancelled && waiter.compareAndSet(current, null)) thread?.let { LockSupport.unpark(it) }
    }

    private fun worker(): Thread = thread ?: synchronized(this) {
        thread ?: Thread(::serve, threadName).also { started ->
            started.isDaemon = true
            started.start()
            thread = started
        }
    }

    private fun serve() {
        while (true) {
            val continuation = waiter.get()
            if (continuation == null) {
                LockSupport.park(this)
                continue
            }
            if (await(continuation, deadlineSystemNanos) && waiter.compareAndSet(continuation, null)) {
                continuation.resume(Unit)
            }
        }
    }

    /** Parks, then spins, until [deadline]; false when [continuation] stopped waiting meanwhile. */
    private fun await(continuation: CancellableContinuation<Unit>, deadline: Long): Boolean {
        val margin = (2L * lateAverageNanos + MARGIN_BASE_NANOS).coerceIn(MIN_MARGIN_NANOS, MAX_MARGIN_NANOS)
        val wake = deadline - margin
        var now = System.nanoTime()
        var parked = false
        while (now < wake) {
            LockSupport.parkNanos(this, wake - now)
            if (waiter.get() !== continuation) return false
            parked = true
            now = System.nanoTime()
        }
        if (parked) lateAverageNanos += (now - wake - lateAverageNanos) / LATE_AVERAGE_WEIGHT
        while (System.nanoTime() < deadline) {
            if (waiter.get() !== continuation) return false
            Thread.onSpinWait()
        }
        return true
    }

    companion object {
        /** The timer the desktop display host hands to the gameplay pacer. */
        val Shared = DesktopFrameTimer()

        private const val IMMEDIATE_NANOS = 50_000L
        private const val MARGIN_BASE_NANOS = 50_000L
        private const val MIN_MARGIN_NANOS = 100_000L
        private const val MAX_MARGIN_NANOS = 1_000_000L
        private const val LATE_AVERAGE_WEIGHT = 8L
    }
}
