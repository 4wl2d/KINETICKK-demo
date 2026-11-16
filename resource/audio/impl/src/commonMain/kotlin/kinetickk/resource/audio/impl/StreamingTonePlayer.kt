// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.resource.audio.impl

import kinetickk.resource.audio.api.ToneRequest
import kotlin.concurrent.Volatile

/** One platform audio device as [StreamingTonePlayer] drives it. Only [close] may come from another thread. */
interface ToneOutput {
    /** Opens the device unless it is open; false when the platform has none (it reports why itself). */
    fun open(): Boolean

    /** Starts playing the blocks written next. */
    fun start()

    /** Writes all of [block], blocking while the device buffer is full; false once the device stops accepting samples. */
    fun write(block: ShortArray): Boolean

    /** Stops playing. Only silence is buffered by then, so the device may discard it. */
    fun stop()

    /** Idempotently closes the device, ending a [write] in progress; the next [open] makes a new one. */
    fun close()
}

/**
 * The tone player the desktop and Android platforms share. Tones are posted from the game thread and one mixing
 * thread streams [ToneMixer] blocks into the platform's [output]: the device starts with a tone, keeps streaming
 * through [TonePlaybackPolicy.IDLE_BLOCKS] silent blocks (the gaps between music notes) and then stops, so no tone
 * waits for another one to drain. The device opens once tones are installed (or with the first tone); a missing
 * device stays closed until a user gesture ([unlock]); a device that fails or stops accepting samples is closed with
 * the tones it held and reopened for the next tone. Faults go to [report] and never end the thread.
 * At shutdown [close] releases the current device. The worker handles device faults and late opens.
 *
 * A platform supplies the device and its lock, condition and thread through the hooks; nothing posted allocates.
 */
abstract class StreamingTonePlayer(
    private val output: ToneOutput,
    private val report: (String) -> Unit,
) : TonePlaybackCapability {
    // Guarded by the platform lock.
    private val pending = PendingTones()
    private var installedTones: PreparedPcmTones? = null
    private var openRequested = false
    private var retryRequested = false
    private var threadStarted = false

    @Volatile private var closed = false

    // Mixing thread only.
    private var outputUnavailable = false
    private val mixer = ToneMixer()
    private val block = ShortArray(TonePlaybackPolicy.BLOCK_FRAMES)
    private var streaming = false
    private var silentBlocks = 0

    /** Takes the platform lock. */
    protected abstract fun acquire()

    protected abstract fun release()

    /** Sleeps until [signalWork]; called with the lock held, which it gives up while sleeping. */
    protected abstract fun awaitWork()

    /** Wakes the mixing thread; called with the lock held. */
    protected abstract fun signalWork()

    /** Starts the mixing thread running [mix]; called once, with the lock held. */
    protected abstract fun startMixingThread(mix: () -> Unit)

    /** Hands [tones] to the mixing thread and opens the device; the platform prepares them off the game thread. */
    protected fun install(tones: PreparedPcmTones) = post {
        installedTones = tones
        openRequested = true
    }

    override fun unlock() {
        acquire()
        try {
            // Retain the gesture even if the device is still opening on the mixing thread.
            if (!closed) retryRequested = true
        } finally {
            release()
        }
    }

    override fun playEffect(tone: ToneRequest, gain: Float, priority: Int) = post { pending.post(tone, gain, priority) }

    override fun playMusic(tone: ToneRequest, gain: Float) = post { pending.postMusic(tone, gain) }

    override fun close() {
        acquire()
        try {
            closed = true
            pending.clear()
            signalWork()
        } finally {
            release()
        }
        closeOutput()?.let(::reportFailure)
    }

    private inline fun post(add: () -> Unit) {
        if (closed) return
        acquire()
        try {
            if (closed) return
            add()
            if (!threadStarted) {
                threadStarted = true
                startMixingThread(::mix)
            }
            signalWork()
        } finally {
            release()
        }
    }

    private fun mix() {
        while (true) {
            val preload: Boolean
            acquire()
            try {
                while (!closed && pending.isEmpty && !streaming && !mixer.active && !openRequested) awaitWork()
                if (closed) break
                installedTones?.let(mixer::prepare)
                installedTones = null
                preload = openRequested
                openRequested = false
                // A failed preload must leave the user's gesture and queued tone for the playback attempt.
                if (!preload) {
                    if (!pending.isEmpty && retryRequested) {
                        retryRequested = false
                        outputUnavailable = false
                    }
                    pending.drainInto(mixer)
                }
            } finally {
                release()
            }
            try {
                if (preload) openOutput() else streamBlock()
            } catch (failure: RuntimeException) {
                dropOutput(failure)
            }
        }
        mixer.clear()
    }

    private fun streamBlock() {
        if (mixer.render(block)) {
            silentBlocks = 0
        } else if (!streaming) {
            return
        } else if (++silentBlocks >= TonePlaybackPolicy.IDLE_BLOCKS) {
            streaming = false
            silentBlocks = 0
            output.stop()
            return
        }
        if (!openOutput()) {
            mixer.clear()
            streaming = false
            return
        }
        if (!streaming) {
            output.start()
            streaming = true
        }
        if (!output.write(block)) dropOutput(null)
    }

    private fun openOutput(): Boolean {
        if (closed || outputUnavailable) return false
        if (!output.open()) {
            outputUnavailable = true
            return false
        }
        // close() may have run while the device was opening.
        if (closed) {
            closeOutput()?.let(::reportFailure)
            return false
        }
        return true
    }

    private fun dropOutput(failure: RuntimeException?) {
        mixer.clear()
        streaming = false
        silentBlocks = 0
        val closeFailure = closeOutput()
        if (!closed) (failure ?: closeFailure)?.let(::reportFailure)
    }

    private fun closeOutput(): RuntimeException? = try {
        output.close()
        null
    } catch (failure: RuntimeException) {
        failure
    }

    private fun reportFailure(failure: RuntimeException) {
        try {
            report("Tone output failed: $failure")
        } catch (_: RuntimeException) {
            // A diagnostics failure must not prevent reopening the device or closing the app.
        }
    }
}
