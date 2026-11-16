// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.nucleus.simulation.progression

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.profile.api.MAX_RECENT_ITEM_IDS

/**
 * Profile progress awaiting publication: banked Matter, the Architect outcome, achievement counters, first
 * acquisitions, the Matter already banked this run and when urgent progress may publish again. Drained by the
 * progression domain's progress update.
 */
internal class ProgressOutbox(source: ProgressOutbox?) {
    internal var pendingBankedMatter: Long = source?.pendingBankedMatter ?: 0L
    internal var pendingEliteKills: Int = source?.pendingEliteKills ?: 0
    internal var pendingDashHits: Int = source?.pendingDashHits ?: 0
    internal var pendingArchitectDefeatedWith: CoreShape? = source?.pendingArchitectDefeatedWith
    /** The run banked, so the next progress update also publishes its draft history. */
    internal var achievementFlushDue: Boolean = source?.achievementFlushDue ?: false
    /**
     * First acquisitions not yet reported to the Profile's recent-item history, oldest first; immutable and replaced on
     * change, at most [MAX_RECENT_ITEM_IDS] (the oldest drop first).
     */
    internal var pendingAcquiredItemIds: List<Int> = source?.pendingAcquiredItemIds ?: emptyList()
    /** Run Matter already banked; later grants in the same run bank only the difference. */
    internal var bankedRunMatter: Long = source?.bankedRunMatter ?: 0L
    /**
     * Run time before which a running run holds urgent progress back: each progress update sets it
     * [URGENT_PROGRESS_INTERVAL_SECONDS] past the run's `elapsed` time, so the Profile is written at most once per
     * interval of uninterrupted play. Stopping a run (pause, choice, run end, exit) or backgrounding the app
     * publishes at once.
     */
    internal var urgentProgressHeldUntil: Float = source?.urgentProgressHeldUntil ?: 0f
}
