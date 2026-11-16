// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.resource.audio.impl

import kinetickk.resource.audio.api.AudioPreferences
import kinetickk.resource.audio.api.AudioService
import kinetickk.resource.audio.api.SoundId
import kinetickk.resource.audio.api.ToneRequest

/**
 * Ranks effects and drives [music] for the platform player. Every tone it plays is a catalog request validated when
 * the catalog was built, at a gain inside 0..1 by construction, so playing validates and allocates nothing.
 */
class DefaultAudioService(
    private val platform: TonePlaybackCapability,
    private val music: MusicSequencer = LoopMusicSequencer(),
) : AudioService {
    private var preferences = AudioPreferences()
    private var closed = false
    private val selected = arrayOfNulls<SoundId>(MAX_SELECTED_EFFECT_REQUESTS_PER_ADVANCE)
    private val musicVoice = MusicVoice { tone, gain -> platform.playMusic(tone, gain) }

    override suspend fun prepare(requests: List<ToneRequest>) {
        if (!closed) platform.prepare(ToneCatalog.effectTones + music.tones + requests)
    }

    override fun updatePreferences(preferences: AudioPreferences) {
        if (!closed) this.preferences = preferences
    }

    override fun play(sounds: List<SoundId>) {
        if (closed) return
        val volume = effectiveVolume()
        if (!acceptsEffects(volume, sounds.size)) return
        val count = selectRankedDistinct(sounds, selected)
        for (index in 0 until count) {
            val sound = checkNotNull(selected[index])
            val tone = ToneCatalog.request(sound)
            platform.playEffect(tone, tone.gain * volume, ToneCatalog.priority(sound))
        }
    }

    override fun tick(realDeltaSeconds: Float) {
        if (closed) return
        val volume = effectiveVolume()
        if (!preferences.musicEnabled || volume <= 0f) {
            music.reset()
            return
        }
        // A zero delta is not a display frame: it must not start a note on an expired clock.
        val delta = selectMusicAdvanceDeltaSeconds(realDeltaSeconds)
        if (delta <= 0f) return
        music.advance(delta, volume, musicVoice)
    }

    override fun ensureUnlocked() {
        if (!closed) platform.unlock()
    }

    override fun close() {
        if (closed) return
        platform.close()
        closed = true
    }

    // No takeIf on a Float: its nullable result boxes on every frame.
    private fun effectiveVolume(): Float {
        val volume = preferences.masterVolume
        return if (volume.isFinite()) volume.coerceIn(0f, 1f) else 0f
    }

    private fun acceptsEffects(volume: Float, count: Int): Boolean =
        preferences.soundEnabled && volume > 0f && count > 0 && count <= MAX_ACCEPTED_EFFECT_REQUESTS_PER_ADVANCE

    private companion object {
        const val MAX_ACCEPTED_EFFECT_REQUESTS_PER_ADVANCE = 32
        const val MAX_SELECTED_EFFECT_REQUESTS_PER_ADVANCE = 3
    }
}

internal const val MAX_MUSIC_ADVANCE_DELTA_SECONDS: Float = 0.1f

internal fun selectMusicAdvanceDeltaSeconds(realDeltaSeconds: Float): Float =
    if (realDeltaSeconds.isFinite()) realDeltaSeconds.coerceIn(0f, MAX_MUSIC_ADVANCE_DELTA_SECONDS) else 0f

/**
 * Writes the first `into.size` distinct [sounds] of a stable priority ranking (higher first, emission order among
 * equals) into [into] and returns how many: the same as sorting, deduplicating and taking the prefix, without either.
 */
internal fun selectRankedDistinct(sounds: List<SoundId>, into: Array<SoundId?>): Int {
    var count = 0
    for (index in sounds.indices) {
        val sound = sounds[index]
        if (into.containsWithin(count, sound)) continue
        val priority = ToneCatalog.priority(sound)
        var at = count
        while (at > 0 && ToneCatalog.priority(checkNotNull(into[at - 1])) < priority) at--
        // A sound ranked below a full selection stays out; a later copy of it ranks no higher.
        if (at == into.size) continue
        for (shift in minOf(count, into.size - 1) downTo at + 1) into[shift] = into[shift - 1]
        into[at] = sound
        if (count < into.size) count++
    }
    return count
}

private fun Array<SoundId?>.containsWithin(count: Int, sound: SoundId): Boolean {
    for (index in 0 until count) if (this[index] === sound) return true
    return false
}

/** Narrow mechanical capability supplied by the app's platform composition broker. */
interface TonePlaybackCapability {
    /** Prepares the waveforms of [requests] (gain aside) so that playing them synthesizes nothing. */
    suspend fun prepare(requests: List<ToneRequest>) = Unit

    fun unlock()

    /**
     * Starts the waveform of [tone] at [gain] (0..1, in place of the request's own gain) on an effect voice. When
     * every voice is busy it takes the voice of the lowest-ranked effect whose priority does not exceed [priority].
     */
    fun playEffect(tone: ToneRequest, gain: Float, priority: Int)

    /** Starts [tone] at [gain] on the one music voice, ending the note it still holds. */
    fun playMusic(tone: ToneRequest, gain: Float)

    fun close()
}
