// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.flow.session.api

import kinetickk.ball.gameplay.api.RunId
import kinetickk.ball.gameplay.api.RunMode
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.immutableListOf

data class AppShellProjection(
    /** Changes whenever the base or the overlay changes: screens key their per-visit state by it. */
    val routeToken: Long,
    val base: AppDestination,
    val overlay: AppDestination?,
    val activeRunId: RunId?,
    val lifecycle: SessionLifecycle,
    /** The mode every next start carries; chosen on the bare Home, STANDARD at every launch. */
    val runMode: RunMode = RunMode.STANDARD,
    private val participantCommandPending: Boolean = false,
) {
    init {
        require(routeToken >= 0L) { "Session route token must be non-negative" }
        require(base.isBaseDestination()) { "Only Home and Gameplay may be base destinations" }
        require(overlay == null || overlay.isOverlayDestination()) {
            "Only feature routes may be Session overlays"
        }
        require(base != AppDestination.Gameplay || activeRunId != null) {
            "Gameplay base requires an active GameplayRun"
        }
    }

    val active: AppDestination
        get() = overlay ?: base

    val entries: ImmutableList<AppDestination>
        get() = overlay?.let { immutableListOf(base, it) } ?: immutableListOf(base)

    /** Input stays closed until bootstrap and any pending participant command complete. */
    val normalInputEnabled: Boolean
        get() = lifecycle == SessionLifecycle.READY && !participantCommandPending

    /**
     * Characters change between runs: the Workshop offers selection only over the Home base. Session
     * still decides every [SessionInteractionPulse.SelectCoreShapeRequested]; this only tells the UI.
     */
    val characterSelectionAvailable: Boolean
        get() = base == AppDestination.Home
}

/** Local Interaction/query facade. Participant command authority is bound separately by Impl. */
interface AppSessionPort {
    fun accept(pulse: SessionInteractionPulse): SessionAcceptance

    fun shell(): AppShellProjection
}
