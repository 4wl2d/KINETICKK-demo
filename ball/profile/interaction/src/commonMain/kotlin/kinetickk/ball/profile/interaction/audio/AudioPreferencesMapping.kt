// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.profile.interaction.audio

import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.resource.audio.api.AudioPreferences

/** The persisted preference slice that application audio observes. */
fun PlayerPreferences.toAudioPreferences(): AudioPreferences =
    AudioPreferences(
        soundEnabled = soundEnabled,
        musicEnabled = musicEnabled,
        masterVolume = masterVolume,
    )
