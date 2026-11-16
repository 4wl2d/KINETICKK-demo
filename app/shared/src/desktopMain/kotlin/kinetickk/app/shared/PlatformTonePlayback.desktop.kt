// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.app.shared

import java.awt.EventQueue
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.LineUnavailableException
import javax.sound.sampled.SourceDataLine
import kinetickk.foundation.diagnostics.CrashDiagnostics
import kinetickk.resource.audio.api.ToneRequest
import kinetickk.resource.audio.impl.PreparedPcmTones
import kinetickk.resource.audio.impl.StreamingTonePlayer
import kinetickk.resource.audio.impl.ToneOutput
import kinetickk.resource.audio.impl.TonePlaybackCapability
import kinetickk.resource.audio.impl.TonePlaybackPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal const val DESKTOP_AUDIO_THREAD_NAME = "kinetickk-audio"

internal actual fun createPlatformTonePlaybackCapability(diagnostics: CrashDiagnostics): TonePlaybackCapability =
    DesktopTonePlaybackCapability(diagnostics = diagnostics)

/** The shared streaming player on one Java Sound line, mixed on the `kinetickk-audio` daemon thread. */
internal class DesktopTonePlaybackCapability(
    createLine: (AudioFormat) -> SourceDataLine = AudioSystem::getSourceDataLine,
    diagnostics: CrashDiagnostics = CrashDiagnostics.None,
) : StreamingTonePlayer(JavaSoundOutput(createLine, diagnostics), diagnostics::postAudioEvent) {
    private val lock = ReentrantLock()
    private val work = lock.newCondition()

    override suspend fun prepare(requests: List<ToneRequest>) {
        install(withContext(Dispatchers.IO) { PreparedPcmTones(requests, TonePlaybackPolicy.SAMPLE_RATE) })
    }

    override fun acquire() = lock.lock()

    override fun release() = lock.unlock()

    override fun awaitWork() = work.await()

    override fun signalWork() = work.signal()

    override fun startMixingThread(mix: () -> Unit) {
        Thread(mix, DESKTOP_AUDIO_THREAD_NAME).apply {
            isDaemon = true
            start()
        }
    }
}

/** One 16-bit mono Java Sound line holding [TonePlaybackPolicy.BUFFER_FRAMES] ahead of playback. */
private class JavaSoundOutput(
    private val createLine: (AudioFormat) -> SourceDataLine,
    private val diagnostics: CrashDiagnostics,
) : ToneOutput {
    private val format = AudioFormat(TonePlaybackPolicy.SAMPLE_RATE.toFloat(), 16, 1, true, false)
    private val bytes = ByteArray(TonePlaybackPolicy.BLOCK_FRAMES * TonePlaybackPolicy.BYTES_PER_SAMPLE)
    private val line = AtomicReference<SourceDataLine?>()

    override fun open(): Boolean {
        if (line.get() != null) return true
        val output = try {
            createLine(format).also { output ->
                try {
                    output.open(format, TonePlaybackPolicy.BUFFER_FRAMES * TonePlaybackPolicy.BYTES_PER_SAMPLE)
                } catch (failure: Exception) {
                    output.close()
                    throw failure
                }
            }
        } catch (failure: LineUnavailableException) {
            return unavailable(failure)
        } catch (failure: IllegalArgumentException) {
            // Java Sound uses this when no mixer supports the requested PCM format.
            return unavailable(failure)
        } catch (failure: SecurityException) {
            return unavailable(failure)
        }
        line.set(output)
        return true
    }

    override fun start() {
        line.get()?.start()
    }

    override fun write(block: ShortArray): Boolean {
        val output = line.get() ?: return false
        for (index in block.indices) {
            val sample = block[index].toInt()
            bytes[index * 2] = (sample and 0xFF).toByte()
            bytes[index * 2 + 1] = (sample shr 8 and 0xFF).toByte()
        }
        var written = 0
        while (written < bytes.size) {
            val count = output.write(bytes, written, bytes.size - written)
            // A lost (or closed) device stops accepting samples.
            if (count <= 0) return false
            written += count
        }
        return true
    }

    override fun stop() {
        line.get()?.let { output ->
            output.stop()
            output.flush()
        }
    }

    override fun close() {
        line.getAndSet(null)?.close()
    }

    private fun unavailable(failure: Exception): Boolean {
        diagnostics.postAudioEvent("No desktop tone output: $failure")
        return false
    }
}

private fun CrashDiagnostics.postAudioEvent(message: String) {
    if (this === CrashDiagnostics.None) return
    // The Debug recorder shares its event ring with game updates on the EDT.
    val record = Runnable { runCatching { event("audio", message) } }
    if (EventQueue.isDispatchThread()) record.run() else EventQueue.invokeLater(record)
}
