// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.foundation.diagnostics

/** Mechanical, best-effort telemetry. Implementations must not throw or change game decisions. */
interface CrashDiagnostics {
    /** Lets callers skip formatting and context captures when diagnostics are disabled. */
    val enabled: Boolean get() = true
    /**
     * Registers a deferred formatter over immutable values. A managed crash may request details
     * omitted from regular checkpoints. The formatter must avoid querying an owner in either mode.
     */
    fun context(key: String, describe: (includeCrashDetails: Boolean) -> String)
    fun event(category: String, message: String, highFrequency: Boolean = false)


    data object None : CrashDiagnostics {
        override val enabled: Boolean get() = false
        override fun context(key: String, describe: (Boolean) -> String) = Unit
        override fun event(category: String, message: String, highFrequency: Boolean) = Unit
    }
}
