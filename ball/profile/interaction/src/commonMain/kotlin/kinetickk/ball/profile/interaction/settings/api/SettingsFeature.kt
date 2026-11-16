// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.profile.interaction.settings.api

import androidx.compose.runtime.Composable
import kinetickk.ball.profile.api.PlayerPreferences

sealed interface SettingsOutput {
    data object Back : SettingsOutput
    /** Preferences read back from the Profile after handling a preference adjustment. */
    data class PreferencesChanged(val preferences: PlayerPreferences) : SettingsOutput
}

interface SettingsFeature {
    @Composable
    fun Content(
        routeToken: Long,
        onOutput: (SettingsOutput) -> Unit,
    )
}
