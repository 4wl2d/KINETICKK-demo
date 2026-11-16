// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.foundation.common.localization

/** Supported interface languages, persisted by their stable language code. */
enum class AppLanguage(val code: String, val nativeName: String) {
    Russian("ru", "Русский"),
    English("en", "English"),
}
