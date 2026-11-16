// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.nucleus

import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.api.GameplayInstanceId
import kinetickk.ball.gameplay.api.GameplayRevision
import kinetickk.ball.gameplay.api.GameplayRunPhase
import kinetickk.ball.gameplay.api.RunId
import kinetickk.ball.gameplay.nucleus.reducer.EngineState

@ConsistentCopyVisibility
data class GameplayState internal constructor(
    val instanceId: GameplayInstanceId,
    val revision: GameplayRevision,
    val phase: GameplayRunPhase,
    val content: GameplayContentSnapshot,
    val engine: EngineState?,
    /** Earned progress was submitted and its outcome has not yet been accepted. */
    val progressPending: Boolean,
) {
    init {
        require((phase == GameplayRunPhase.CREATED) == (engine == null)) {
            "Only a created GameplayRun may have no simulation engine"
        }
        if (engine != null) {
            require(engine.model.phase == phase.renderPhase()) {
                "Gameplay lifecycle phase must match the captured simulation phase"
            }
        }
    }

    companion object {
        fun initial(runId: RunId, content: GameplayContentSnapshot): GameplayState = GameplayState(
            instanceId = GameplayInstanceId(runId),
            revision = GameplayRevision.ZERO,
            phase = GameplayRunPhase.CREATED,
            content = content,
            engine = null,
            progressPending = false,
        )
    }
}

/**
 * The phase the run's simulation, and so its render model, is in for this lifecycle phase: an exited run rests
 * PAUSED. A created run has no simulation.
 */
fun GameplayRunPhase.renderPhase(): GamePhase = when (this) {
    GameplayRunPhase.CREATED -> error("A created GameplayRun has no simulation phase")
    GameplayRunPhase.RUNNING -> GamePhase.RUNNING
    GameplayRunPhase.PAUSED -> GamePhase.PAUSED
    GameplayRunPhase.CHOICE -> GamePhase.CHOICE
    GameplayRunPhase.GAME_OVER -> GamePhase.GAME_OVER
    GameplayRunPhase.VICTORY -> GamePhase.VICTORY
    GameplayRunPhase.EXITED -> GamePhase.PAUSED
}

internal fun GamePhase.toRunPhase(): GameplayRunPhase = when (this) {
    GamePhase.RUNNING -> GameplayRunPhase.RUNNING
    GamePhase.PAUSED -> GameplayRunPhase.PAUSED
    GamePhase.CHOICE -> GameplayRunPhase.CHOICE
    GamePhase.GAME_OVER -> GameplayRunPhase.GAME_OVER
    GamePhase.VICTORY -> GameplayRunPhase.VICTORY
}
