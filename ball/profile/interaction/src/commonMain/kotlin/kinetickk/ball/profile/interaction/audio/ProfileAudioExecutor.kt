// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.profile.interaction.audio

import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.resource.audio.api.AudioService
import kinetickk.resource.audio.api.SoundId
import kinetickk.resource.audio.api.playUiEffect

/** The Profile screens' side of the mechanical Resource boundary: menu sounds and the audio preferences. */
internal class ProfileAudioExecutor(
    private val audioService: AudioService,
) {
    fun play(sound: SoundId) {
        audioService.playUiEffect(sound)
    }

    fun updatePreferences(preferences: PlayerPreferences) {
        audioService.updatePreferences(preferences.toAudioPreferences())
    }
}
