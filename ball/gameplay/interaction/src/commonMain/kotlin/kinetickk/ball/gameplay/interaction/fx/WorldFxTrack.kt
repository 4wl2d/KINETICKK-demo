// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.interaction.fx

import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.mapToImmutableList

/**
 * A bounded list of world-anchored looks of [InteractionFxReducer]: at most [maximum] entries (the oldest leaves
 * first), each living out the life it starts with. They mark the world, so a rebase moves them; a clear ends them.
 * Every change reports whether the published list changes; [published] rebuilds the list only after one.
 */
internal abstract class WorldFxTrack<Live : WorldFxTrack.Entry, Published>(private val maximum: Int) {
    /** A live look: its remaining [life] runs down in place and [shift] moves its world anchors. */
    abstract class Entry(var life: Float) {
        abstract fun shift(shiftX: Float, shiftY: Float)
    }

    private val entries = mutableListOf<Live>()
    private var dirty = false
    private var cached: ImmutableList<Published> = immutableListOf()

    val isEmpty: Boolean get() = entries.isEmpty()

    /** The published copy of one live entry. */
    protected abstract fun publish(entry: Live): Published

    /** Starts [entry]; the oldest leaves past [maximum]. */
    protected fun start(entry: Live): Boolean {
        entries += entry
        while (entries.size > maximum) entries.removeAt(0)
        dirty = true
        return true
    }

    fun clear(): Boolean {
        if (entries.isEmpty()) return false
        entries.clear()
        dirty = true
        return true
    }

    fun rebase(shiftX: Float, shiftY: Float): Boolean {
        if (entries.isEmpty()) return false
        for (index in entries.indices) entries[index].shift(shiftX, shiftY)
        dirty = true
        return true
    }

    /** Ages every entry by [delta] and drops the finished ones. */
    fun advance(delta: Float): Boolean {
        if (entries.isEmpty()) return false
        var kept = 0
        for (index in entries.indices) {
            val entry = entries[index]
            entry.life -= delta
            if (entry.life > 0f) entries[kept++] = entry
        }
        while (entries.size > kept) entries.removeAt(entries.lastIndex)
        dirty = true
        return true
    }

    /** The published list: rebuilt only after a change, so a quiet frame reuses the previous instance. */
    fun published(): ImmutableList<Published> {
        if (!dirty) return cached
        cached = if (entries.isEmpty()) immutableListOf() else entries.mapToImmutableList { value -> publish(value) }
        dirty = false
        return cached
    }
}
