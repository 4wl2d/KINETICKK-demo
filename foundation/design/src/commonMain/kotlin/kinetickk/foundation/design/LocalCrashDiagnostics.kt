// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.foundation.design

import androidx.compose.runtime.staticCompositionLocalOf
import kinetickk.foundation.diagnostics.CrashDiagnostics

val LocalCrashDiagnostics = staticCompositionLocalOf<CrashDiagnostics> { CrashDiagnostics.None }
