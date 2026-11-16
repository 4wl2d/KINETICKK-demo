// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.profile.interaction.settings.impl

/**
 * The public documentation links that Settings opens.
 */
internal const val SETTINGS_PRIVACY_POLICY_URL = "https://github.com/4wl2d/KINETICKK-demo#privacy"
internal const val SETTINGS_SOURCE_AND_LICENSES_URL = "https://github.com/4wl2d/KINETICKK-demo"

/** The address a link row's button opens in the browser; null for every other row. */
internal val SettingsRow.linkUrl: String?
    get() = when (this) {
        SettingsRow.PRIVACY_POLICY -> SETTINGS_PRIVACY_POLICY_URL
        SettingsRow.SOURCE_AND_LICENSES -> SETTINGS_SOURCE_AND_LICENSES_URL
        else -> null
    }
