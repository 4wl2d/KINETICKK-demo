// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.profile.interaction.workshop.kit

import kinetickk.ball.profile.interaction.workshop.api.WorkshopTab
import kinetickk.ball.profile.interaction.workshop.api.WorkshopTabRequest

internal sealed interface WorkshopAction {
    /** A shell request (Home row, alias key): applies once per serial and focuses the tab's body. */
    data class Request(val request: WorkshopTabRequest) : WorkshopAction

    /** A tab press, or a digit key ([focusBody]: focus moves to the tab's inspected tile). */
    data class SelectTab(val tab: WorkshopTab, val focusBody: Boolean = false) : WorkshopAction

    /** Left/Right on a focused tab: the neighbour, wrapping; focus stays on the strip. */
    data class StepTab(val forward: Boolean) : WorkshopAction

    data class SelectSegment(val segment: WorkshopSegment) : WorkshopAction

    /** Left/Right inside the focused segment group, wrapping. */
    data class StepSegment(val forward: Boolean) : WorkshopAction

    /** The upgrade block's buy action for [target], whose track the block shows as [track]. */
    data class BuyUpgrade(val target: UpgradeTarget, val track: UpgradeTrackRenderModel) : WorkshopAction
}
