// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.api

/** Creates and exposes the one active GameplayRun. */
interface GameplayRunHost {
    fun createRun(
        runId: RunId,
    ): GameplayRunPort

    fun activeRun(): GameplayRunPort?
}
