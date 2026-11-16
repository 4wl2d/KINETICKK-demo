// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.nucleus.simulation.weapons

import kinetickk.ball.gameplay.nucleus.model.*

/** Upper bound of [arcCoilTargetCount] after both mastery bonuses. */
internal const val MAX_ARC_COIL_TARGETS: Int = 4
internal const val ARC_COIL_FIRST_RANGE: Float = 420f
internal const val ARC_COIL_HOP_RANGE: Float = 220f
internal const val ARC_COIL_FALLOFF: Float = 0.95f

// ---------------------------------------------------------------------------------------------
// Tuning (weapon power 1).

internal const val AIM_ASSIST_CONE: Float = 0.5f
internal const val AIM_ASSIST_RANGE: Float = 700f
internal const val NULL_LANCE_DAMAGE: Float = 61f
internal const val NULL_LANCE_PIERCE: Int = 5
internal const val NULL_LANCE_REAR_SHARE: Float = 0.3f
internal const val MINE_DAMAGE: Float = 68f
internal const val MINE_PROXIMITY: Float = 0.45f
internal const val GRAVITY_MINE_RADIUS: Float = 100f
internal const val MINE_MASS_INTERVAL: Float = 1.5f
internal const val BASE_MINE_LIMIT: Int = 8
internal const val MINE_CHAIN_FUSE: Float = 0.05f
internal const val MINE_CHAIN_REACH: Float = 0.9f
internal const val MINE_WELL_SECONDS: Float = 1.2f
internal const val MINE_WELL_DPS: Float = 15f
internal const val MINE_WELL_PULL: Float = 240f
internal const val MINE_WELL_REACH: Float = 0.7f
internal const val ARC_COIL_DAMAGE: Float = 15f
internal const val ARC_COIL_COOLDOWN: Float = 1.15f
internal const val ARC_COIL_CHARGE: Int = 1
/** Heading change per dp travelled that counts as a hard turn (60° over 100 dp). */
internal const val FLUX_TURN_CURVATURE: Float = 0.0105f
internal const val FLUX_LONG_LIFE: Float = 4f
internal const val FLUX_SPACING: Float = 22f

internal const val GYRE_DAMAGE: Float = 28f
internal const val GYRE_COOLDOWN: Float = 1.3f
internal const val GYRE_SPEED: Float = 760f
internal const val GYRE_RETURN_SPEED: Float = 900f
internal const val GYRE_REACH: Float = 420f
internal const val GYRE_RADIUS: Float = 11f
/** The first form's larger disc, as a multiple of [GYRE_RADIUS] (about the size of the disc before the retune). */
internal const val GYRE_RADIUS_FORM: Float = 1.65f
internal const val GYRE_SPREAD: Float = 0.45f
internal const val GYRE_MAX_LIFE: Float = 3f
internal const val GYRE_ORBIT_BONUS: Float = 1.25f
internal const val GYRE_RETURN_BONUS: Float = 1.5f
internal const val GYRE_EXTRA_SHARE: Float = 0.6f

internal const val SCATTER_DAMAGE: Float = 8f
internal const val SCATTER_PIERCE: Int = 2
internal const val SCATTER_PELLETS: Int = 7
internal const val SCATTER_PASSIVE_PELLETS: Int = 7
internal const val SCATTER_PASSIVE_INTERVAL: Float = 2.4f
internal const val SCATTER_RANGE: Float = 380f
internal const val SCATTER_SPEED: Float = 950f
internal const val SCATTER_CONE: Float = TAU / 6f
internal const val SCATTER_POINT_BLANK: Float = 140f
internal const val SCATTER_PASSIVE_SPREAD: Float = 0.9f

internal const val GYRE_CATCH_RADIUS: Float = 30f
/** Seconds a returning disc must run free of rocks before its next glance sparks again. */
internal const val GYRE_GLANCE_SPARK_GAP: Float = 0.2f
internal const val GYRE_SPIN: Float = 18f
