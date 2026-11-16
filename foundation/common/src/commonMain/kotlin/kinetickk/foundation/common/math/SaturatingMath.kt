// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.foundation.common.math

/**
 * [left] + [right], stopping at [Long.MAX_VALUE] instead of wrapping. Meant for counters and Matter
 * totals that only grow: [right] is non-negative at every caller.
 */
fun saturatingAdd(left: Long, right: Long): Long =
    if (left > Long.MAX_VALUE - right) Long.MAX_VALUE else left + right
