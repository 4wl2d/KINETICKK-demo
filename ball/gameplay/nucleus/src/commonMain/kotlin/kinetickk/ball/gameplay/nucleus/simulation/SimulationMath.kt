// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.nucleus.simulation

internal fun square(value: Float): Float = value * value
internal fun MutableGameState.powFast(base: Float, exponent: Int): Float {
    var result = 1f
    repeat(exponent) { result *= base }
    return result
}
