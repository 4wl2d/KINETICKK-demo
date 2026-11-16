// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.nucleus.simulation.characters

internal const val DASH_PHASE_SECONDS: Float = 0.24f

/** Apex value indices (the per-shape order of `defaultApexDefinition`) and the forms' own numbers. */
internal const val PERIHELION_ARC = 0
internal const val PERIHELION_CAPTURE_RADIUS = 1
internal const val PERIHELION_HELD_DAMAGE = 2
internal const val PERIHELION_RELEASE_INTERVAL = 3
internal const val PERIHELION_MIN_DISTANCE: Float = 50f
internal const val PERIHELION_MIN_SPEED: Float = 150f
internal const val PERIHELION_SHIFT_GRACE: Float = 0.5f
internal const val PERIHELION_MAX_WAIT: Float = 9f
internal const val PERIHELION_RELEASE_RANGE: Float = 600f
internal const val MONOLITH_REGROWTH_SECONDS = 0
internal const val MONOLITH_IMPACT_BONUS = 1
internal const val MONOLITH_REFRACTION_DAMAGE = 2
internal const val VIVISECT_INTERVAL = 0
internal const val VIVISECT_SPREAD_RADIUS = 1
internal const val VIVISECT_FREE_DASH_GAP = 2
/** No Refit signature moves its constant below this share of the base ([refitSignature]). */
internal const val REFIT_SIGNATURE_FLOOR_SHARE: Float = 0.5f

internal const val ORBIT_ARC: Float = 0.698f
internal const val ORBIT_MIN_DISTANCE: Float = 100f
internal const val ORBIT_MIN_SPEED: Float = 200f
internal const val ORBIT_RADIUS: Float = 210f
internal const val ORBIT_DAMAGE: Float = 42f
internal const val ORBIT_STACKS: Int = 2

internal const val FACET_REGEN_SECONDS: Float = 6f
internal const val FACET_PAUSE_SECONDS: Float = 2f
internal const val FACET_CUT_SECONDS: Float = 1f
internal const val FACET_CUT_INTERVAL: Float = 1.2f
internal const val FACET_IMPACT_BONUS: Float = 1.2f
internal const val FACET_BREAK_RADIUS: Float = 180f
internal const val FACET_BREAK_PUSH: Float = 520f
internal const val HEAVY_IMPACT_SHARE: Float = 0.25f
internal const val HEAVY_IMPACT_SPEED: Float = 450f

internal const val MARK_SECONDS: Float = 3f
internal const val PUNCTURE_DAMAGE: Float = 180f
internal const val PUNCTURE_HEAT_REFUND: Float = 12f
internal const val PUNCTURE_INTERVAL: Float = 0.25f
