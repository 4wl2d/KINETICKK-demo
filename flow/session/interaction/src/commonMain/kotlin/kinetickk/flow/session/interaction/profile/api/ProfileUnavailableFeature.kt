// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.flow.session.interaction.profile.api

import androidx.compose.runtime.Composable

/** Blocking presentation used when the local Profile cannot be bootstrapped safely. */
interface ProfileUnavailableFeature {
    @Composable
    fun Content()
}
