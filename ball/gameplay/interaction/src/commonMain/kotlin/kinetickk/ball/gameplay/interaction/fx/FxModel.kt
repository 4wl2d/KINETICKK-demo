// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.interaction.fx

/** How a burst's reach follows its cue. */
internal enum class FxReach {
    /** The cue's radius within 36..240. */
    AREA,

    /** The cue's radius within 48..120 (the enemy's size or the reaction's own radius). */
    TARGET,

    /** A fixed reach. */
    FIXED,

    /** The cue's own effect radius (skills), within 24..480; 120 when the cue has none. */
    CUE,
}
