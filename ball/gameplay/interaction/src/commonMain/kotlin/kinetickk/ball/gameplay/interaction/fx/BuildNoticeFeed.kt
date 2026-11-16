// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.interaction.fx

import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.toImmutableList

/** Toast titles the nucleus emits for discoveries (see [BuildNoticeKind]); the feed localizes them. */
internal object BuildNoticeTitles {
    const val REACTION_DISCOVERED = "REACTION DISCOVERED"
    const val PAIRING_DISCOVERED = "PAIRING DISCOVERED"

    /** Interaction's own note for the first Break of an element in a run. */
    const val BREAK = "BREAK"

    fun kind(title: String): BuildNoticeKind = when (title) {
        REACTION_DISCOVERED, PAIRING_DISCOVERED -> BuildNoticeKind.DISCOVERY
        BREAK -> BuildNoticeKind.BREAK
        else -> BuildNoticeKind.CHANGE
    }
}

/**
 * The build toasts of [InteractionFxReducer], in feed order. They age with the reducer's effects and are published as
 * [BuildNotificationProjection]s; [published] rebuilds the list only after a change.
 */
internal class BuildNoticeFeed {
    private val notices = mutableListOf<BuildNotice>()
    private var dirty = false

    val isEmpty: Boolean get() = notices.isEmpty()

    /**
     * Adds a build toast by priority and returns whether its lines show (merged into a toast or opened) or the feed
     * dropped it. Mergeable kinds join a toast of the same title opened less than
     * [InteractionFxLimits.NOTICE_MERGE_SECONDS] ago while it has a free line; otherwise the toast goes after every
     * toast of its priority or lower, and the oldest lowest one leaves when the feed is over its bound (a new toast
     * below a full feed of higher ones is dropped at once).
     */
    fun add(title: String, details: List<String>, kind: BuildNoticeKind): Boolean {
        if (kind.merges) {
            for (index in notices.indices.reversed()) {
                val notice = notices[index]
                if (notice.kind != kind || notice.title != title) continue
                if (notice.age >= InteractionFxLimits.NOTICE_MERGE_SECONDS) break
                var fresh = 0
                for (line in details) if (line !in notice.details) fresh++
                if (fresh == 0) return true
                if (notice.details.size + fresh > InteractionFxLimits.NOTICE_MERGED_LINES) break
                val merged = ArrayList<String>(notice.details.size + fresh)
                merged += notice.details
                for (line in details) if (line !in merged) merged += line
                notice.details = merged.toImmutableList()
                notice.life = notice.maxLife
                dirty = true
                return true
            }
        }
        val notice = BuildNotice(title, details.toImmutableList(), kind.lifeSeconds, kind)
        var at = notices.size
        while (at > 0 && notices[at - 1].kind.priority > kind.priority) at--
        notices.add(at, notice)
        trimFront(notices, InteractionFxLimits.MAX_BUILD_NOTIFICATIONS)
        for (index in notices.indices) {
            if (notices[index] === notice) {
                dirty = true
                return true
            }
        }
        return false
    }

    /** Ages every toast by [delta] and drops the finished ones; [previous] is the list last published. */
    fun advance(delta: Float, previous: List<BuildNotificationProjection>) {
        retainInPlace(notices) { value ->
            value.life -= delta
            value.age += delta
            value.life > 0f
        }
        if (notices.isNotEmpty() || previous.isNotEmpty()) dirty = true
    }

    fun clear() {
        notices.clear()
    }

    /** Marks the published list stale (a clear that changed the reducer's projection). */
    fun invalidate() {
        dirty = true
    }

    /** The list to publish: rebuilt after a change, else [previous] itself. */
    fun published(previous: ImmutableList<BuildNotificationProjection>): ImmutableList<BuildNotificationProjection> {
        if (!dirty) return previous
        dirty = false
        return notices.toNotificationProjections()
    }
}
