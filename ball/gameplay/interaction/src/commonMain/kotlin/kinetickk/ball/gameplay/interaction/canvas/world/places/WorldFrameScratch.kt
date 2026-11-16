// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.interaction.canvas.world.places

import kinetickk.ball.gameplay.interaction.canvas.world.*

/** HUD regions, the edge-marker table and the frame's off-screen targets (draw-thread confined). */
internal object WorldOverlayScratch {
    val keepOut = WorldHudKeepOut()
    val planner = EdgeMarkerPlanner()
    val markers = EdgeMarkerBatch()
}

/**
 * Where the world frame reports the boxes of the point marks, names and timers, the orbit label and the edge markers it
 * draws: nowhere in the app, where [recorder] stays null; a test installs one to read the boxes back. Draw-thread
 * confined.
 */
internal object WorldDrawProbe {
    var recorder: WorldDrawRecorder? = null

    fun begin() {
        recorder?.begin()
    }

    fun record(kind: WorldDrawn, left: Float, top: Float, right: Float, bottom: Float) {
        recorder?.record(kind, left, top, right, bottom)
    }
}

/** Receives the boxes [WorldDrawProbe] reports, from the start of one world frame to the next. */
internal interface WorldDrawRecorder {
    fun begin()

    fun record(kind: WorldDrawn, left: Float, top: Float, right: Float, bottom: Float)
}

/**
 * What [WorldDrawProbe] records: a point's mark (offer, vault, target beacon, a race's next gate), a
 * point timer, an edge marker, the place pips of the target beacon or the next gate as drawn, an
 * offer's name, the collapsing orbit's seconds-in-ring label.
 */
internal enum class WorldDrawn { POINT_MARK, POINT_TIMER, EDGE_MARKER, BEACON_PIPS, POINT_NAME, ORBIT_LABEL }
