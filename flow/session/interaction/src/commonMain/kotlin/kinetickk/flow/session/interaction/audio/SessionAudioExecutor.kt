// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.flow.session.interaction.audio

import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.interaction.audio.toAudioPreferences
import kinetickk.resource.audio.api.AudioService
import kinetickk.resource.audio.api.SoundId
import kinetickk.resource.audio.api.playUiEffect

/** Session's side of the mechanical Resource boundary: menu sounds, the unlock gesture and the audio preferences. */
class SessionAudioExecutor(
    private val audioService: AudioService,
) {
    fun playUiClick() {
        play(SoundId.UI_CLICK)
    }

    fun updatePreferences(preferences: PlayerPreferences) {
        audioService.updatePreferences(preferences.toAudioPreferences())
    }

    fun ensureUnlocked() {
        audioService.ensureUnlocked()
    }

    fun play(sound: SoundId) {
        audioService.playUiEffect(sound)
    }
}
