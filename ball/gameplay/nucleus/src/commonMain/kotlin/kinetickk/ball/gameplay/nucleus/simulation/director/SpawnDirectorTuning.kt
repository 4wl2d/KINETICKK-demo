// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.nucleus.simulation.director

/** An ambush waits for the Core to hold this share of the obstacle ricochet speed ([obstacleRicochetSpeed]). */
internal const val AMBUSH_SPEED_FRACTION: Float = 0.75f
internal const val AMBUSH_HOLD_SECONDS: Float = 1.5f
internal const val AMBUSH_COOLDOWN_SECONDS: Float = 20f
internal const val ENCIRCLE_COOLDOWN_SECONDS: Float = 40f
internal const val WALL_COOLDOWN_SECONDS: Float = 30f
internal const val CAP_OVERFLOW_COOLDOWN_SECONDS: Float = 20f
internal const val DIRECTOR_CHECK_SECONDS: Float = 0.4f
/**
 * Overall budget scale: at 1 the director banks one enemy per tempo interval at phase rate 1.
 */
internal const val DIRECTOR_RATE_SCALE: Float = 1f
/** An event never fires below this share of its planned size. */
internal const val MINIMUM_EVENT_SHARE: Float = 0.6f
/** Salt of the hashed kind a planned event takes when the habitat changed under it (no run stream is drawn). */
internal const val EVENT_HABITAT_SALT: Long = 0x0E7E_4AB1_7A7E_0001L
/** A Swarmling fills this share of an ordinary cap slot and of the spawn budget. */
internal const val SWARMLING_CAP_COST: Float = 1f / 3f
internal const val MAX_SPAWN_BUDGET: Float = 14f
/** A type's weight ramps in over this long after its gate opens. */
internal const val TYPE_RAMP_SECONDS: Float = 60f
internal const val MINIMUM_TYPE_RAMP: Float = 0.08f
internal const val HARDENED_ELITE_ESCORTS: Int = 2
internal const val HARDENED_ELITE_INTEGRITY: Float = 1.5f
internal const val BEHIND_REROLL_SPEED: Float = 700f
internal const val DEGREES: Float = (kotlin.math.PI / 180.0).toFloat()
internal const val AHEAD_HALF_ANGLE: Float = 50f * DEGREES
internal const val FLANK_OUTER_ANGLE: Float = 130f * DEGREES
