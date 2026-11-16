// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.content.impl

import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.RelicKey
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.WeaponCadence
import kinetickk.ball.content.api.WeaponCadence.CONTACT
import kinetickk.ball.content.api.WeaponCadence.CONTINUOUS
import kinetickk.ball.content.api.WeaponCadence.DISCRETE
import kinetickk.ball.content.api.WeaponDefinition
import kinetickk.ball.content.api.WeaponFamily
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.content.api.WeaponScaling
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.toImmutableList

/** The weapons usable through the Architect encounter, in [WeaponId] order. */
internal fun defaultWeapons(): ImmutableList<WeaponDefinition> =
    listOf(
        weapon(
            WeaponId.FLUX_WAKE, "Flux Wake",
            "High-speed movement leaves a damaging trail behind the Core; a big crowd in it shares the heat.",
            WeaponFamily.MOMENTUM, StatusElement.THERMAL, CONTINUOUS, BuildTag.DRIVE, BuildTag.OVERCLOCK,
            RelicKey.MOTION, WeaponScaling.SPEED, CoreShape.ORB, cost = 0,
            forms = listOf("Trail width doubles", "The trail lasts 4 s", "Ignite comes one stack sooner in the trail"),
            signature = "A trail laid while turning deals 50% more damage and lasts 50% longer.",
        ),
        weapon(
            WeaponId.MORNINGSTAR, "Morningstar",
            "A heavy orbital mass converts velocity and mass into crushing contact damage.",
            WeaponFamily.ORBITAL, null, CONTACT, BuildTag.RAM, BuildTag.ANCHOR,
            RelicKey.GRAVITY, WeaponScaling.MASS, CoreShape.PRISM, cost = 25,
            forms = listOf("Orbit radius +15%", "A second, smaller star joins the orbit", "Double knockback that slams enemies"),
            signature = "+10% damage and size for each intact facet.",
        ),
        weapon(
            WeaponId.PHASE_LATTICE, "Phase Lattice",
            "A pulsing gravity ring damages enemies held near its unstable perimeter.",
            WeaponFamily.AURA, StatusElement.CRYO, CONTINUOUS, BuildTag.BULWARK, BuildTag.ANCHOR,
            RelicKey.STILLNESS, WeaponScaling.SHIELD, null, cost = 55,
            forms = listOf("A wider ring", "An outer ring joins the lattice", "Chill needs one stack less inside the ring"),
            signature = null,
        ),
        weapon(
            WeaponId.NULL_LANCE, "Null Lance",
            "Momentum periodically projects a piercing lance along the Core's travel vector.",
            WeaponFamily.PROJECTILE, null, DISCRETE, BuildTag.DRIVE, BuildTag.PRECISION,
            RelicKey.MOTION, WeaponScaling.SPEED, CoreShape.SHARD, cost = 95,
            forms = listOf("Lance pierce +3", "A rear lance fires behind the Core", "Lances crit statused or marked enemies"),
            signature = "A lance hitting a marked enemy deals double Puncture and keeps its pierce.",
        ),
        weapon(
            WeaponId.GRAVITY_MINES, "Gravity Mines",
            "Braking plants implosive mines that pull enemies inward before detonating.",
            WeaponFamily.TRAP, StatusElement.GRAVITIC, DISCRETE, BuildTag.ANCHOR, BuildTag.CHAIN,
            RelicKey.STILLNESS, WeaponScaling.BRAKE, null, cost = 145,
            forms = listOf("Mine radius +30%", "Up to 12 mines that detonate in a chain", "Mines collapse into a well for 1.2 s"),
            signature = null,
        ),
        weapon(
            WeaponId.ARC_COIL, "Arc Coil",
            "Stored kinetic charge erupts as lightning that chains between nearby enemies.",
            WeaponFamily.CHAIN, StatusElement.ION, DISCRETE, BuildTag.ARSENAL, BuildTag.CHAIN,
            RelicKey.CHARGE, WeaponScaling.STATUS, null, cost = 430,
            forms = listOf("One more chain target", "Chain range +100", "One more target and an Overload on the last hop"),
            signature = null,
        ),
        weapon(
            WeaponId.GYRE_DISC, "Gyre Disc",
            "Throws a spinning disc along the aim that flies out and returns, cutting every enemy on both passes.",
            WeaponFamily.MOMENTUM, null, DISCRETE, BuildTag.DRIVE, BuildTag.PRECISION,
            RelicKey.MOTION, WeaponScaling.SPEED, CoreShape.ORB, cost = 120,
            forms = listOf("A larger disc", "Two discs fly at once", "Discs ricochet off obstacles and return for 50% more damage"),
            signature = "+25% damage while Orbit is charged.",
        ),
        weapon(
            WeaponId.SCATTER_BLOOM, "Scatter Bloom",
            "Every Dash fires a wide cone of pellets ahead of the Core; a small volley also fires on its own between Dashes.",
            WeaponFamily.PROJECTILE, null, DISCRETE, BuildTag.DRIVE, BuildTag.RAM,
            RelicKey.MOTION, WeaponScaling.DASH, CoreShape.SHARD, cost = 260,
            forms = listOf("Two more pellets", "Pellets pierce one more enemy", "Point-blank pellets keep their pierce"),
            signature = "Pellets leave Puncture marks.",
        ),
    ).toImmutableList().also(::validateWeapons)

private fun weapon(
    id: WeaponId,
    name: String,
    description: String,
    family: WeaponFamily,
    element: StatusElement?,
    cadence: WeaponCadence,
    firstTag: BuildTag,
    secondTag: BuildTag,
    key: RelicKey,
    scaling: WeaponScaling,
    signatureShape: CoreShape?,
    cost: Int,
    forms: List<String>,
    signature: String?,
): WeaponDefinition = WeaponDefinition(
    id = id,
    name = name,
    description = description,
    family = family,
    element = element,
    tags = listOf(firstTag, secondTag),
    key = key,
    signature = signatureShape,
    scaling = scaling,
    cadence = cadence,
    permanentUnlockCost = cost,
    masteryForms = forms,
    signatureText = signature,
)

private fun validateWeapons(weapons: List<WeaponDefinition>) {
    check(weapons.size == WeaponId.entries.size) { "Weapon catalog must define every weapon" }
    check(weapons.withIndex().all { (index, weapon) -> weapon.id.ordinal == index }) {
        "Weapon catalog order must match WeaponId order"
    }
    check(weapons.map { it.id }.toSet().size == weapons.size) { "Weapon ids must be unique" }
    check(weapons.map { it.name }.toSet().size == weapons.size) { "Weapon names must be unique" }
    check(weapons.all { it.tags.toSet().size == WeaponDefinition.WEAPON_TAG_COUNT }) {
        "Every weapon carries exactly two distinct build tags"
    }
}
