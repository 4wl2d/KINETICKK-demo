// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.resource.audio.impl

import kinetickk.resource.audio.api.ToneRequest
import kinetickk.resource.audio.api.ToneWave

/** Where a [MusicSequencer] starts its notes: the platform's one music voice. */
fun interface MusicVoice {
    fun play(tone: ToneRequest, gain: Float)
}

/**
 * The music behind [DefaultAudioService.tick], and the seam adaptive music replaces: a sequencer owns its notes,
 * their clock and their level. The service owns the preferences and hands it the real time of each display frame.
 */
interface MusicSequencer {
    /** Every tone the sequencer can play, prepared with the effects so that no note is synthesized while playing. */
    val tones: List<ToneRequest>

    /** Advances by one display frame of [deltaSeconds] (positive, clamped) at master [volume] (positive) and starts due notes on [voice]. */
    fun advance(deltaSeconds: Float, volume: Float, voice: MusicVoice)

    /** Music is off or muted: the next [advance] starts a note at once. */
    fun reset()
}

/** The shipped music: a loop of eight triangle notes, one every 0.32 s, the first of every eight accented. */
class LoopMusicSequencer : MusicSequencer {
    private var clock = 0f
    private var step = 0

    override val tones: List<ToneRequest> = NOTES.distinct().map(::note)
    private val noteTones = Array(NOTES.size) { index -> tones.first { it.frequencyHz == NOTES[index] } }

    override fun advance(deltaSeconds: Float, volume: Float, voice: MusicVoice) {
        clock -= deltaSeconds
        if (clock <= 0f) {
            clock += STEP_SECONDS
            val accent = if (step % 8 == 0) 1.35f else 1f
            voice.play(noteTones[step % NOTES.size], volume * 0.035f * accent)
            step++
        }
    }

    override fun reset() {
        clock = 0f
    }

    private companion object {
        const val STEP_SECONDS = 0.32f
        val NOTES = floatArrayOf(110f, 146.83f, 164.81f, 220f, 196f, 164.81f, 146.83f, 123.47f)

        /** The one music tone shape; the voice applies each note's level. */
        fun note(frequencyHz: Float) = ToneRequest(frequencyHz, 0.18f, 1f, ToneWave.TRIANGLE)
    }
}
