// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.resource.audio.impl

import kinetickk.resource.audio.api.ToneRequest

/** The playback shape the desktop and Android tone players share: one mono 16-bit stream fed by [ToneMixer]. */
object TonePlaybackPolicy {
    const val SAMPLE_RATE: Int = 22_050
    const val BYTES_PER_SAMPLE: Int = 2

    /**
     * Effects that sound at once; a busier moment gives the lowest-ranked one's voice to the newcomer. Late combat
     * at 240 FPS needs at most 9 (measured over a minute of the benchmark's combat run).
     */
    const val EFFECT_VOICES: Int = 10

    /** Frames mixed at a time (about 11.6 ms): a posted tone joins the next block. */
    const val BLOCK_FRAMES: Int = 256

    /** A replaced voice fades out over at most 2.9 ms, avoiding a discontinuity at a block boundary. */
    internal const val VOICE_RELEASE_FRAMES: Int = 64

    /** What the device buffers ahead of playback (about 46 ms): the latency bound once a tone is mixed. */
    const val BUFFER_FRAMES: Int = BLOCK_FRAMES * 4

    /** Tone starts that may wait for the next block; more are dropped (a frame posts at most 3 effects and a note). */
    const val PENDING_TONES: Int = 64

    /** Silent blocks streamed after the last voice ends (about 0.5 s): the gaps between music notes keep the device running. */
    const val IDLE_BLOCKS: Int = 43
}

/**
 * Tone starts posted by the game thread and taken by the mixing thread. It is not thread-safe by itself: a platform
 * guards [post], [postMusic] and [drainInto] with one lock. Nothing here allocates.
 */
class PendingTones(private val capacity: Int = TonePlaybackPolicy.PENDING_TONES) {
    private val tones = arrayOfNulls<ToneRequest>(capacity)
    private val gains = FloatArray(capacity)
    private val priorities = IntArray(capacity)
    private var count = 0

    val isEmpty: Boolean get() = count == 0

    /** Queues an effect for the next block; false (and dropped) when [capacity] starts already wait. */
    fun post(tone: ToneRequest, gain: Float, priority: Int): Boolean = add(tone, gain, priority)

    /** Queues a music note for the next block; false (and dropped) when [capacity] starts already wait. */
    fun postMusic(tone: ToneRequest, gain: Float): Boolean = add(tone, gain, MUSIC)

    /** Starts every waiting tone on [mixer] in posting order. */
    fun drainInto(mixer: ToneMixer) {
        for (index in 0 until count) {
            val tone = checkNotNull(tones[index])
            if (priorities[index] == MUSIC) mixer.startMusic(tone, gains[index]) else mixer.startEffect(tone, gains[index], priorities[index])
            tones[index] = null
        }
        count = 0
    }

    fun clear() {
        tones.fill(null)
        count = 0
    }

    private fun add(tone: ToneRequest, gain: Float, priority: Int): Boolean {
        if (count == capacity) return false
        tones[count] = tone
        gains[count] = gain
        priorities[count] = priority
        count++
        return true
    }

    private companion object {
        /** Effect priorities are never negative; this marks a note for the music voice. */
        const val MUSIC = -1
    }
}

/**
 * The shared software mixer of the desktop and Android tone players: [effectVoices] effect voices plus one music
 * voice, summed into 16-bit mono blocks. A tone alone renders exactly the samples `pcmSample` gives it; overlapping
 * tones add and the sum clips at full scale. A replaced voice releases over a short fade. One mixing thread owns it;
 * rendering and starts of prepared tones allocate nothing.
 */
class ToneMixer(
    private val effectVoices: Int = TonePlaybackPolicy.EFFECT_VOICES,
    blockFrames: Int = TonePlaybackPolicy.BLOCK_FRAMES,
) {
    private var bank = PreparedPcmTones(emptyList(), TonePlaybackPolicy.SAMPLE_RATE)

    // Voices 0 until effectVoices play effects; the last one is the music voice.
    private val voiceSamples = arrayOfNulls<FloatArray>(effectVoices + 1)
    private val voicePositions = IntArray(effectVoices + 1)
    private val voiceGains = FloatArray(effectVoices + 1)
    private val voicePriorities = IntArray(effectVoices + 1)
    private val voiceStarts = LongArray(effectVoices + 1)
    private var startCount = 0L
    private val mixed = FloatArray(blockFrames)
    private val releaseTail = FloatArray(TonePlaybackPolicy.VOICE_RELEASE_FRAMES)
    private var releaseSamples = 0

    init {
        require(effectVoices > 0 && blockFrames > 0)
    }

    /** True while any voice still has samples to play. */
    val active: Boolean
        get() {
            if (releaseSamples != 0) return true
            for (voice in voiceSamples.indices) if (voiceSamples[voice] != null) return true
            return false
        }

    /** The prepared waveforms later starts look up; a tone outside the bank is synthesized on start. */
    fun prepare(bank: PreparedPcmTones) {
        this.bank = bank
    }

    /**
     * Starts [tone] at [gain] on a free effect voice. With every voice busy it takes the voice of the lowest-ranked
     * effect whose priority does not exceed [priority] (the oldest among equals); false when every voice outranks it.
     */
    fun startEffect(tone: ToneRequest, gain: Float, priority: Int): Boolean {
        val voice = effectVoiceFor(priority)
        if (voice < 0) return false
        start(voice, tone, gain, priority)
        return true
    }

    /** Starts [tone] at [gain] on the music voice, ending the note it still holds. */
    fun startMusic(tone: ToneRequest, gain: Float) {
        start(effectVoices, tone, gain, 0)
    }

    /** Silences every voice at once (the output was lost or closed). */
    fun clear() {
        voiceSamples.fill(null)
        releaseTail.fill(0f)
        releaseSamples = 0
    }

    /**
     * Mixes the next [frames] samples of every voice into [out] (silence where none sounds) and advances them.
     * Returns false when no voice was active, so the block is silence.
     */
    fun render(out: ShortArray, frames: Int = out.size): Boolean {
        require(frames in 0..minOf(out.size, mixed.size))
        mixed.fill(0f, 0, frames)
        var sounding = mixRelease(frames)
        for (voice in voiceSamples.indices) {
            val samples = voiceSamples[voice] ?: continue
            sounding = true
            val gain = voiceGains[voice]
            val position = voicePositions[voice]
            val count = minOf(frames, samples.size - position)
            for (frame in 0 until count) mixed[frame] += samples[position + frame] * gain
            if (position + count == samples.size) voiceSamples[voice] = null else voicePositions[voice] = position + count
        }
        for (frame in 0 until frames) out[frame] = pcmSample(mixed[frame])
        return sounding
    }

    private fun start(voice: Int, tone: ToneRequest, gain: Float, priority: Int) {
        fadeOut(voice)
        voiceSamples[voice] = bank.samples(tone)
        voicePositions[voice] = 0
        voiceGains[voice] = gain
        voicePriorities[voice] = priority
        voiceStarts[voice] = startCount++
    }

    private fun fadeOut(voice: Int) {
        val samples = voiceSamples[voice] ?: return
        val position = voicePositions[voice]
        // A voice replaced before its first block has made no sound.
        if (position == 0) return
        val count = minOf(releaseTail.size, samples.size - position)
        val gain = voiceGains[voice]
        for (index in 0 until count) {
            releaseTail[index] += samples[position + index] * gain * ((count - index).toFloat() / count)
        }
        releaseSamples = maxOf(releaseSamples, count)
    }

    private fun mixRelease(frames: Int): Boolean {
        val count = minOf(frames, releaseSamples)
        if (count == 0) return false
        releaseTail.copyInto(mixed, endIndex = count)
        val remaining = releaseSamples - count
        if (remaining != 0) releaseTail.copyInto(releaseTail, startIndex = count, endIndex = releaseSamples)
        releaseTail.fill(0f, remaining, releaseSamples)
        releaseSamples = remaining
        return true
    }

    private fun effectVoiceFor(priority: Int): Int {
        var victim = -1
        for (voice in 0 until effectVoices) {
            if (voiceSamples[voice] == null) return voice
            if (voicePriorities[voice] > priority) continue
            if (victim < 0 || voicePriorities[voice] < voicePriorities[victim] ||
                (voicePriorities[voice] == voicePriorities[victim] && voiceStarts[voice] < voiceStarts[victim])
            ) {
                victim = voice
            }
        }
        return victim
    }
}
