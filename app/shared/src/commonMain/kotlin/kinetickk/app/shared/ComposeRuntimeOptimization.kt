// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.app.shared

import androidx.compose.runtime.ComposeRuntimeFlags
import androidx.compose.runtime.ExperimentalComposeApi

/** Selects the faster app-wide SlotTable before the first Composition is created. */
@OptIn(ExperimentalComposeApi::class)
fun enableKinetickkComposeRuntimeOptimizations() {
    ComposeRuntimeFlags.isLinkBufferComposerEnabled = true
}
