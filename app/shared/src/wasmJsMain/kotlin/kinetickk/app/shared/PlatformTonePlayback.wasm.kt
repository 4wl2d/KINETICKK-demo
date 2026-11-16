// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.app.shared

import kinetickk.foundation.diagnostics.CrashDiagnostics
import kinetickk.resource.audio.api.ToneRequest
import kinetickk.resource.audio.api.ToneWave
import kinetickk.resource.audio.impl.TonePlaybackCapability
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny

internal actual fun createPlatformTonePlaybackCapability(diagnostics: CrashDiagnostics): TonePlaybackCapability =
    WebTonePlaybackCapability()

/** Owns one AudioContext instance and its master bus without publishing them through a JavaScript global cache. */
@OptIn(ExperimentalWasmJsInterop::class)
private class WebTonePlaybackCapability : TonePlaybackCapability {
    /** `{ context, master }`: the AudioContext and its unity-gain master bus, made with the first tone. */
    private var audio: JsAny? = null
    private var closed = false
    private var gestureFeedbackPending = false

    override suspend fun prepare(requests: List<ToneRequest>) {
        if (!closed) audio = prepareWebAudio(audio)
    }

    override fun unlock() {
        if (closed) return
        audio = unlockWebAudio(audio)
        gestureFeedbackPending = true
    }

    // Every tone gets its own oscillator; the browser mixes them, so effects and music need no voice pool here.
    override fun playEffect(tone: ToneRequest, gain: Float, priority: Int) {
        play(tone, gain)
    }

    override fun playMusic(tone: ToneRequest, gain: Float) {
        play(tone, gain)
    }

    private fun play(tone: ToneRequest, gain: Float) {
        if (closed) return
        audio = playWebTone(
            audio,
            tone.frequencyHz.toDouble(),
            tone.durationSeconds.toDouble(),
            gain.toDouble(),
            webToneWaveValue(tone.wave),
            gestureFeedbackPending,
        )
        gestureFeedbackPending = false
    }

    override fun close() {
        if (closed) return
        closeWebAudio(audio)
        audio = null
        closed = true
    }
}

@OptIn(ExperimentalWasmJsInterop::class)
private fun prepareWebAudio(current: JsAny?): JsAny? = js(
    """{
        const AudioContext = globalThis.AudioContext || globalThis.webkitAudioContext;
        try {
            return current || (AudioContext ? { context: new AudioContext(), master: null } : null);
        } catch (failure) {
            // Audio is optional: a browser that refuses a context leaves the game silent.
            return null;
        }
    }""",
)

internal fun webToneWaveValue(wave: ToneWave): String = when (wave) {
    ToneWave.SINE -> "sine"
    ToneWave.SQUARE -> "square"
    ToneWave.SAW -> "sawtooth"
    ToneWave.TRIANGLE -> "triangle"
}

@OptIn(ExperimentalWasmJsInterop::class)
private fun unlockWebAudio(current: JsAny?): JsAny? = js(
    """{
        const AudioContext = globalThis.AudioContext || globalThis.webkitAudioContext;
        if (!AudioContext) return null;
        const audio = current || { context: new AudioContext(), master: null };
        const context = audio.context;
        if (context.state === 'suspended') {
            const resume = context.resume();
            if (resume && typeof resume.catch === 'function') resume.catch(() => undefined);
        }
        return audio;
    }""",
)

@OptIn(ExperimentalWasmJsInterop::class)
private fun playWebTone(
    current: JsAny?,
    frequency: Double,
    duration: Double,
    volume: Double,
    wave: String,
    allowSuspended: Boolean,
): JsAny? = js(
    """{
        const AudioContext = globalThis.AudioContext || globalThis.webkitAudioContext;
        if (!AudioContext) return null;
        const audio = current || { context: new AudioContext(), master: null };
        const context = audio.context;
        // resume() resolves asynchronously. The gesture's first cue can already be scheduled;
        // automatic frames before a gesture must not enqueue sounds into a suspended context.
        if (context.state === 'suspended' && !allowSuspended) return audio;
        const oscillator = context.createOscillator();
        const gain = context.createGain();
        if (!audio.master) {
            // One unity-gain bus per context: every tone reaches the destination through it.
            audio.master = context.createGain();
            audio.master.gain.value = 1;
            audio.master.connect(context.destination);
        }
        oscillator.type = wave;
        oscillator.frequency.setValueAtTime(frequency, context.currentTime);
        gain.gain.setValueAtTime(0.0001, context.currentTime);
        gain.gain.exponentialRampToValueAtTime(Math.max(0.0001, volume), context.currentTime + 0.008);
        gain.gain.exponentialRampToValueAtTime(0.0001, context.currentTime + duration);
        oscillator.connect(gain);
        gain.connect(audio.master);
        oscillator.start();
        oscillator.stop(context.currentTime + duration + 0.015);
        return audio;
    }""",
)

@OptIn(ExperimentalWasmJsInterop::class)
private fun closeWebAudio(current: JsAny?): Unit = js(
    """{
        if (current) {
            const close = current.context.close();
            if (close && typeof close.catch === 'function') close.catch(() => undefined);
        }
    }""",
)
