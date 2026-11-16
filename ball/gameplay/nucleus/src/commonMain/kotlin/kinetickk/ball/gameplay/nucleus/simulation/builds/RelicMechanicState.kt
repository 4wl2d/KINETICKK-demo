// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.nucleus.simulation.builds


/**
 * Retained timers, charges and counters of relic mechanics (and the Matrix's free Dash). Written by the build
 * domain's relic rules.
 */
internal class RelicMechanicState(source: RelicMechanicState?) {
    internal var glassInvulnerabilityReadyAt: Float = source?.glassInvulnerabilityReadyAt ?: 0f
    internal var bulwarkInvulnerabilityReadyAt: Float = source?.bulwarkInvulnerabilityReadyAt ?: 0f
    internal var stormCharge: Float = source?.stormCharge ?: 0f
    internal var sinceTurn: Float = source?.sinceTurn ?: 0f
    internal var gyreReadyAt: Float = source?.gyreReadyAt ?: 0f
    /** Endless Gyre's delayed second ring: when it fires (negative when none is pending) and its damage. */
    internal var gyreEchoAt: Float = source?.gyreEchoAt ?: -1f
    internal var gyreEchoDamage: Float = source?.gyreEchoDamage ?: 0f
    internal var aegisAbsorbed: Float = source?.aegisAbsorbed ?: 0f
    internal var aegisNovaReadyAt: Float = source?.aegisNovaReadyAt ?: 0f
    internal var shieldBreakArmed: Boolean = source?.shieldBreakArmed ?: false
    internal var shieldBreakReadyAt: Float = source?.shieldBreakReadyAt ?: 0f
    internal var glassCharge: Float = source?.glassCharge ?: 0f
    /** When the next heat-free Matrix Dash is ready. */
    internal var necroticClock: Float = source?.necroticClock ?: 0f

    /** A new run: every timer, charge and counter at its initial value. */
    fun reset() {
        glassInvulnerabilityReadyAt = 0f
        bulwarkInvulnerabilityReadyAt = 0f
        stormCharge = 0f
        sinceTurn = 0f
        gyreReadyAt = 0f
        gyreEchoAt = -1f
        gyreEchoDamage = 0f
        aegisAbsorbed = 0f
        aegisNovaReadyAt = 0f
        shieldBreakArmed = false
        shieldBreakReadyAt = 0f
        glassCharge = 0f
        necroticClock = 0f
    }
}
