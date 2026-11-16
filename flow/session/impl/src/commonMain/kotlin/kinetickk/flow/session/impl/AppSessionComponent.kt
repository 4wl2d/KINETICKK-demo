// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.flow.session.impl

import kinetickk.ball.gameplay.api.GameplayRunHost
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.api.ProfileReadPort
import kinetickk.ball.profile.api.ProfileSettings
import kinetickk.ball.profile.api.ProfileLoadout
import kinetickk.flow.session.api.AppSessionPort

fun createAppSessionComponent(
    profilePort: ProfileReadPort,
    profileSettings: ProfileSettings,
    profileLoadout: ProfileLoadout,
    gameplayRunHost: GameplayRunHost,
    updateAudioPreferences: (PlayerPreferences) -> Unit,
    playMuteFeedback: () -> Unit,
): AppSessionPort = DefaultAppSessionComponent.create(
    profilePort = profilePort,
    profileSettings = profileSettings,
    profileLoadout = profileLoadout,
    gameplayRunHost = gameplayRunHost,
    updateAudioPreferences = updateAudioPreferences,
    playMuteFeedback = playMuteFeedback,
)
