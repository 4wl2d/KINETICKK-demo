// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.flow.session.api

/** Closed inventory of the five application routes. */
sealed interface AppDestination {
    data object Home : AppDestination
    data object Gameplay : AppDestination
    data object Settings : AppDestination
    /**
     * Characters, weapons and the Core Matrix behind one tabbed overlay. It opens over Home and
     * over a paused (or pausing) run; character selection stays a Home-only Session decision.
     */
    data object Workshop : AppDestination
    data object Codex : AppDestination
}

fun AppDestination.isBaseDestination(): Boolean = when (this) {
    AppDestination.Home,
    AppDestination.Gameplay,
    -> true
    AppDestination.Settings,
    AppDestination.Workshop,
    AppDestination.Codex,
    -> false
}

fun AppDestination.isOverlayDestination(): Boolean = !isBaseDestination()

enum class SessionShortcut {
    SETTINGS,
    /** W: toggles the Workshop (the tab aliases L, A and H are presentation keys mapped by Interaction). */
    WORKSHOP,
    CODEX,
    MUTE,
    BACK,
    ENTER,
}

enum class SessionLifecycle {
    READY,
    BOOTSTRAP_UNAVAILABLE,
}
