// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.nucleus.simulation.weapons

import kinetickk.ball.gameplay.nucleus.model.WeaponRuntime

/**
 * The equipped weapon's level, runtime and clock, the Flux trail cursor, and the Morningstar.
 * Written by the weapons domain. Positions follow a rebase.
 */
internal class WeaponRunState(source: WeaponRunState?) {
    internal var weaponLevel: Int = source?.weaponLevel ?: 1
    /** Owned state of the equipped weapon (entities, proc gates, element cadences, scaling inputs); a deep copy forks it. */
    internal var weaponRuntime: WeaponRuntime = source?.weaponRuntime?.fork() ?: WeaponRuntime()
    internal var weaponClock: Float = source?.weaponClock ?: 0f
    internal var trailLastX: Float = source?.trailLastX ?: 0f
    internal var trailLastY: Float = source?.trailLastY ?: 0f
    internal var trailDistanceCarry: Float = source?.trailDistanceCarry ?: 0f
    internal var morningstarAngle: Float = source?.morningstarAngle ?: 0f
    internal var morningstarX: Float = source?.morningstarX ?: 0f
    internal var morningstarY: Float = source?.morningstarY ?: 0f
    /** Morningstar position at the start of the last fixed step (render interpolation only). */
    internal var previousMorningstarX: Float = source?.previousMorningstarX ?: 0f
    internal var previousMorningstarY: Float = source?.previousMorningstarY ?: 0f
}
