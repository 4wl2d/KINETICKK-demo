// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.content.api

import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.toImmutableList

/**
 * A character's level-cap ability. It runs for [durationSeconds], then recharges over
 * [cooldownSeconds]; Data credited after the cap shortens the recharge, never below
 * [minimumCooldownSeconds]. Against boss-class enemies it spends at most [bossStrikes] strikes and
 * erases at most [boltBudget] hostile bolts per activation. [values] are read by index in the order
 * [defaultApexDefinition] documents for the shape.
 */
data class ApexDefinition(
    val name: String,
    val description: String,
    val durationSeconds: Float,
    val cooldownSeconds: Float = DEFAULT_APEX_COOLDOWN_SECONDS,
    val minimumCooldownSeconds: Float = DEFAULT_APEX_MINIMUM_COOLDOWN_SECONDS,
    val bossStrikes: Int = DEFAULT_APEX_BOSS_STRIKES,
    val boltBudget: Int,
    val values: ImmutableList<Float>,
) {
    init {
        require(name.isNotBlank() && description.isNotBlank()) { "An Apex needs a name and a description" }
        require(cooldownSeconds.isFinite() && cooldownSeconds > 0f) { "Apex cooldown must be finite and positive" }
        require(durationSeconds.isFinite() && durationSeconds > 0f && durationSeconds < cooldownSeconds) {
            "Apex duration must be positive and shorter than its cooldown"
        }
        require(minimumCooldownSeconds.isFinite() && minimumCooldownSeconds in durationSeconds..cooldownSeconds) {
            "Apex minimum cooldown must lie between its duration and its cooldown"
        }
        require(bossStrikes in 0..ContentBounds.MAX_APEX_STRIKES) {
            "Apex boss strikes $bossStrikes exceed ${ContentBounds.MAX_APEX_STRIKES}"
        }
        require(boltBudget in 0..ContentBounds.MAX_APEX_BOLTS) {
            "Apex bolt budget $boltBudget exceeds ${ContentBounds.MAX_APEX_BOLTS}"
        }
        require(values.size <= ContentBounds.MAX_APEX_VALUES) {
            "Apex values size ${values.size} exceeds ${ContentBounds.MAX_APEX_VALUES}"
        }
        require(values.all(Float::isFinite)) { "Apex values must be finite" }
    }

    companion object {
        const val DEFAULT_APEX_COOLDOWN_SECONDS: Float = 60f
        const val DEFAULT_APEX_MINIMUM_COOLDOWN_SECONDS: Float = 30f
        const val DEFAULT_APEX_BOSS_STRIKES: Int = 6
    }
}

/**
 * Apex values, in order:
 * - ORB Perihelion: Orbit charge arc rad, capture radius, held shot damage, release interval s
 * - PRISM Monolith: facet regrowth s, full-facet impact multiplier, refraction damage
 * - SHARD Vivisect: puncture interval s, mark spread radius, free Dash gap s
 */
fun defaultApexDefinition(shape: CoreShape): ApexDefinition = when (shape) {
    CoreShape.ORB -> apex(
        "Perihelion",
        "For 10 s Orbit charges on half the arc and every release captures hostile shots; when it ends, they fly back.",
        10f, 24, 12, 0.349f, 210f, 45f, 0.1f,
    )
    CoreShape.PRISM -> apex(
        "Monolith",
        "Restores every facet; for 8 s facets regrow fast and each blocked hit fires a refraction back.",
        8f, 16, 6, 1f, 1.4f, 90f,
    )
    CoreShape.SHARD -> apex(
        "Vivisect",
        "For 8 s Dashes cost no heat, punctures come faster and every puncture marks the next enemy.",
        8f, 0, 7, 0.12f, 260f, 0.35f,
    )
}

private fun apex(
    name: String,
    description: String,
    durationSeconds: Float,
    boltBudget: Int,
    bossStrikes: Int,
    vararg values: Float,
): ApexDefinition = ApexDefinition(
    name = name,
    description = description,
    durationSeconds = durationSeconds,
    bossStrikes = bossStrikes,
    boltBudget = boltBudget,
    values = values.toList().toImmutableList(),
)
