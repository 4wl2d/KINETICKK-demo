// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.api

data class RunId(
    val value: Long,
) {
    init {
        require(value >= 0L) { "Gameplay run ID must be non-negative" }
    }
}

data class GameplayInstanceId(
    val runId: RunId,
) {
    val canonicalValue: String
        get() = "kinetickk.local/GameplayRun/${runId.value}"
}

data class GameplayRevision(
    val value: Long,
) {
    init {
        require(value >= 0L) { "Gameplay revision must be non-negative" }
    }

    companion object {
        val ZERO: GameplayRevision = GameplayRevision(0L)
    }
}
