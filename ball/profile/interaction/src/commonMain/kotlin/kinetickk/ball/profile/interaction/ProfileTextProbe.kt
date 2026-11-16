// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.profile.interaction

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.toSize
import kinetickk.foundation.design.*

/**
 * One text (or text plate, icon or scroll thumb) the Workshop or Settings screen drew: its
 * [role], [owner] (row, cell), [layout] (null for foundation plates such as tags and stamps, and
 * for shapes), drawn [box] and [color].
 */
internal class ProfileDrawnText(
    val role: String,
    val owner: Any?,
    val layout: TextLayoutResult?,
    val box: Rect,
    val color: Color = Color.Unspecified,
)

/**
 * Test hook: receives every text the profile screens draw ([box] in the drawing scope's
 * coordinates). Null, and free, outside tests; draw-thread confined.
 */
internal object ProfileTextProbe {
    var sink: ((ProfileDrawnText) -> Unit)? = null

    fun record(role: String, owner: Any?, layout: TextLayoutResult?, box: Rect, color: Color = Color.Unspecified) {
        sink?.invoke(ProfileDrawnText(role, owner, layout, box, color))
    }

    fun recordLayout(role: String, layout: TextLayoutResult) {
        val record = sink ?: return
        record(ProfileDrawnText(role, null, layout, Rect(Offset.Zero, layout.size.toSize())))
    }
}

/** [drawKkText] for the profile screens: draws [layout] and reports it to [ProfileTextProbe]. */
internal fun DrawScope.drawProfileText(
    layout: TextLayoutResult,
    x: Float,
    y: Float,
    color: Color,
    role: String,
    owner: Any? = null,
    align: KkAlign = KkAlign.START,
    valign: KkVAlign = KkVAlign.TOP,
    alpha: Float = 1f,
) {
    drawKkText(layout, x, y, color, align, valign, alpha)
    if (ProfileTextProbe.sink == null) return
    val left = when (align) {
        KkAlign.START -> x
        KkAlign.CENTER -> x - layout.size.width * 0.5f
        KkAlign.END -> x - layout.size.width
    }
    val top = when (valign) {
        KkVAlign.TOP -> y - layout.kkBoxTop
        KkVAlign.CENTER -> y - (layout.kkBoxTop + layout.kkBoxBottom) * 0.5f
        KkVAlign.BASELINE -> y - layout.firstBaseline
    }
    ProfileTextProbe.record(role, owner, layout, Rect(left, top, left + layout.size.width, top + layout.size.height), color)
}
