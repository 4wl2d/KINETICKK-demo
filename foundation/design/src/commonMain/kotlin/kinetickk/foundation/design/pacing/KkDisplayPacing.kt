// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.foundation.design.pacing

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import kotlin.math.abs

/**
 * What a platform tells the frame loops about its display: whether frames wait for V-Sync ([vsync]), the display's
 * refresh rate ([refreshHz], 0 when unknown), the highest frame-rate limit the platform paces ([maxFrameRateCap],
 * 0 when it has no ceiling and a stored Unlimited stays unlimited) and the gameplay pacer's [timer]. Only a precise
 * timer (desktop) lets the gameplay loop pace by the display or on the exact V-Sync-off grid. The default describes a
 * platform that adds nothing.
 */
@Immutable
data class KkDisplayPacing(
    val vsync: Boolean = false,
    val refreshHz: Int = 0,
    val maxFrameRateCap: Int = 0,
    val timer: KkFrameTimer = KkCoarseFrameTimer,
)

/** The display the frame loops pace against; provided once by the composition root. */
val LocalKkDisplayPacing = staticCompositionLocalOf { KkDisplayPacing() }

/**
 * The frame-rate limit the loops pace at for the player's [storedCap] (0 = Unlimited) on [display]. Without a ceiling
 * it is the stored limit. Under a ceiling a stored Unlimited paces at the ceiling and a higher limit is clamped to it,
 * so a saved Unlimited profile loads unchanged and runs at the platform's top rate. Under V-Sync with a known refresh
 * rate the limit becomes the even share of the refresh rate it runs at ([kkVsyncDivisor]): 0 when that is every
 * refresh (the display paces the loops, as a stored Unlimited does), otherwise the refresh rate divided by the
 * divisor, rounded up, which maps back to the same divisor, so decorative timers aim at the same rate.
 */
fun kkPacingFrameRateCap(storedCap: Int, display: KkDisplayPacing): Int {
    val ceiling = display.maxFrameRateCap
    val cap = when {
        ceiling <= 0 -> storedCap
        storedCap <= 0 -> ceiling
        else -> minOf(storedCap, ceiling)
    }
    val refreshHz = display.refreshHz
    if (!display.vsync || refreshHz <= 0) return cap
    val divisor = kkVsyncDivisor(refreshHz, cap)
    return if (divisor == 1) 0 else (refreshHz + divisor - 1) / divisor
}

/**
 * How many refreshes each content frame lasts for a [capHz] limit (0 = none) under V-Sync at [refreshHz]: the limit
 * rounds down to an even share of the refresh rate, allowing 2 % (a 118 limit keeps every 120 Hz refresh), so content
 * comes at an even cadence. 90 on a 120 Hz screen runs at 60, every second refresh; 30 on 144 Hz every fifth. The one
 * place that decides the rule.
 */
fun kkVsyncDivisor(refreshHz: Int, capHz: Int): Int =
    if (capHz <= 0) 1 else maxOf(1L, (49L * refreshHz + 50L * capHz - 1L) / (50L * capHz)).toInt()

/**
 * The V-Sync-off frame interval of a [capHz] limit (above 0): its exact nanosecond period, or, when the limit is within
 * 1 % of a whole multiple m of the known [refreshHz], the refresh period divided by m, so the frames keep one phase
 * against the compositor's refreshes instead of drifting through them (240 on a 120 Hz screen: half a refresh).
 */
internal fun kkTimedIntervalNanos(capHz: Int, refreshHz: Int): Long {
    if (refreshHz > 0) {
        val multiple = (capHz + refreshHz / 2) / refreshHz
        val aligned = multiple.toLong() * refreshHz
        if (multiple >= 1 && 100L * abs(capHz - aligned) <= aligned) return NANOS_PER_SECOND / refreshHz / multiple
    }
    return (NANOS_PER_SECOND + capHz / 2) / capHz
}

private const val NANOS_PER_SECOND = 1_000_000_000L
