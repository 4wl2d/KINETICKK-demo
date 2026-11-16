// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.content.impl

import kinetickk.ball.content.api.BuildCatalog
import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.RelicAspect
import kinetickk.ball.content.api.RelicDefinition
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.RelicKey
import kinetickk.ball.content.api.RelicPairingKind
import kinetickk.ball.content.api.RelicValue
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.toImmutableList

private fun relic(
    id: RelicId,
    name: String,
    aspect: RelicAspect,
    keys: List<RelicKey>,
    attuned: List<BuildTag>,
    upside: String,
    liability: String,
    vararg values: RelicValue,
) = RelicDefinition(
    id = id,
    name = name,
    aspect = aspect,
    keys = keys.toImmutableList(),
    attunedTags = attuned.toImmutableList(),
    upside = upside,
    liability = liability,
    values = values.toList().toImmutableList(),
)

internal fun defaultRelics(): ImmutableList<RelicDefinition> = listOf(
    relic(
        RelicId.PERPETUAL_VECTOR, "Perpetual Vector", RelicAspect.KINETIC,
        listOf(RelicKey.MOTION), listOf(BuildTag.DRIVE, BuildTag.RAM),
        "Drag falls by 85 %. Every 400 u/s above 600, up to 2,200, adds {0} % damage.",
        "Brake grips half as hard, and impacts below 600 u/s deal 30 % less.",
        RelicValue(12f, 3f),
    ),
    relic(
        RelicId.GYRE_ENGINE, "Gyre Engine", RelicAspect.KINETIC,
        listOf(RelicKey.MOTION), listOf(BuildTag.DRIVE, BuildTag.CHAIN),
        "Every real turn releases a shock ring for {0} % impact damage and 2 stacks of your dominant element.",
        "Polarity no longer recovers on its own or while braking; only turns restore it, and less than before.",
        RelicValue(55f, 20f),
    ),
    relic(
        RelicId.GLASS_COMET, "Glass Comet", RelicAspect.KINETIC,
        listOf(RelicKey.FRAGILE, RelicKey.CRITICAL), listOf(BuildTag.PRECISION, BuildTag.RAM),
        "All your damage +{0} %. Impact kills above 900 u/s grant a quarter second of invulnerability, " +
            "at most once every 1.5 seconds.",
        "You take 50 % more damage.",
        RelicValue(100f, 25f),
    ),
    relic(
        RelicId.ECHO_CHAMBER, "Echo Chamber", RelicAspect.KINETIC,
        listOf(RelicKey.ECHO), listOf(BuildTag.ARSENAL),
        "Weapon hits echo 0.35 seconds later for {0} % damage and apply their on-hit effects again.",
        "Impact damage −50 %.",
        RelicValue(50f, 10f),
    ),
    relic(
        RelicId.FURNACE_COVENANT, "Furnace Covenant", RelicAspect.THERMAL,
        listOf(RelicKey.HEAT), listOf(BuildTag.OVERCLOCK),
        "Overheat never locks your Dash. Every 10 heat above 40 adds {0} % damage, and above 70 heat your " +
            "hits apply Thermal.",
        "Cooling −55 %. Above 80 heat you lose 3 integrity per second, and heat past full burns integrity instead.",
        RelicValue(8f, 2f),
    ),
    relic(
        RelicId.ABSOLUTE_ZERO, "Absolute Zero", RelicAspect.CRYO,
        listOf(RelicKey.COLD, RelicKey.CRITICAL), listOf(BuildTag.RAM, BuildTag.PRECISION),
        "Impacts apply {0} Cryo and Dash chills everything around you. Frozen enemies shatter on the next " +
            "direct hit for {1} % damage, chilling their neighbors.",
        "Half of the Thermal you apply turns to Cryo and the rest is lost. Overdrive gain −40 %.",
        RelicValue(3f), RelicValue(250f, 50f),
    ),
    relic(
        RelicId.STORMHEART_CAPACITOR, "Stormheart Capacitor", RelicAspect.ION,
        listOf(RelicKey.CHARGE, RelicKey.MOTION), listOf(BuildTag.DRIVE, BuildTag.RAM),
        "Above 700 u/s the Core charges in 4 seconds. The next impact discharges into five enemies for " +
            "{0} % impact damage and 3 Ion each.",
        "Taking a hit while half charged or more discharges into you for 5 % of your integrity.",
        RelicValue(200f, 40f),
    ),
    relic(
        RelicId.AEGIS_INVERSION, "Aegis Inversion", RelicAspect.ION,
        listOf(RelicKey.CHARGE, RelicKey.LIFE), listOf(BuildTag.BULWARK),
        "All healing becomes shield, and shield capacity grows by 40 % of your maximum integrity. A breaking " +
            "shield releases a nova worth {0} % of the damage it absorbed.",
        "Integrity can never be restored, and shields recharge on their own at half speed.",
        RelicValue(150f, 50f),
    ),
    relic(
        RelicId.NEUTRON_HEART, "Neutron Heart", RelicAspect.GRAVITIC,
        listOf(RelicKey.GRAVITY, RelicKey.STILLNESS), listOf(BuildTag.RAM, BuildTag.ANCHOR),
        "Mass +{0} %, and enemies barely move you. Slow collisions hurt you only half as much.",
        "Dash impulse −35 % and magnetism −20 %.",
        RelicValue(80f, 30f),
    ),
    relic(
        RelicId.VERDANT_ROT, "Verdant Rot", RelicAspect.ENTROPY,
        listOf(RelicKey.DECAY, RelicKey.LIFE), listOf(BuildTag.BULWARK),
        "Impacts apply 2 Entropy. Every Entropy stack within 320 u heals you {0} per second, up to {1}.",
        "All other healing is halved.",
        RelicValue(0.4f, 0.05f, powerUnits = true),
        // The heal cap in integrity per second (the simulation reads it).
        RelicValue(5f, powerUnits = true),
    ),
).toImmutableList().also(::validateRelics)

private fun validateRelics(relics: List<RelicDefinition>) {
    check(relics.size <= ContentBounds.MAX_RELICS) { "Relic catalog exceeds ${ContentBounds.MAX_RELICS} relics" }
    check(relics.size == RelicId.entries.size) { "Relic catalog must define every RelicId" }
    check(relics.withIndex().all { (index, relic) -> relic.id.ordinal == index }) {
        "Relic catalog order must match RelicId order"
    }
    check(relics.map { it.name }.toSet().size == relics.size) { "Relic names must be unique" }
    check(relics.map { it.upside }.toSet().size == relics.size) { "Relic upsides must be unique" }
    check(relics.all { relic ->
        val placeholders = Placeholder.findAll(relic.upside).map { it.groupValues[1].toInt() }.toSet()
        placeholders == relic.values.indices.toSet()
    }) { "Every Relic value is referenced exactly by its upside text" }
}

/** Every named pairing must agree with the public keys so players can predict it. */
internal fun validatePairings(relics: List<RelicDefinition>, build: BuildCatalog) {
    val byId = relics.associateBy { it.id }
    build.pairings.forEach { pairing ->
        val first = requireNotNull(byId[pairing.first]) { "${pairing.id} joins a Relic missing from the catalog" }
        val second = requireNotNull(byId[pairing.second]) { "${pairing.id} joins a Relic missing from the catalog" }
        when (pairing.kind) {
            RelicPairingKind.CONCORD -> require(first.harmonizes(second)) {
                "${pairing.id} must join Relics that share a key and have no opposed keys"
            }
            RelicPairingKind.DISCORD -> require(first.dissonates(second)) {
                "${pairing.id} must join opposed keys"
            }
        }
    }
}

private val Placeholder = Regex("\\{([0-9]+)\\}")
