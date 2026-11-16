// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.content.api

import kinetickk.ball.content.api.MatrixBranch.BULWARK
import kinetickk.ball.content.api.MatrixBranch.FORTUNE
import kinetickk.ball.content.api.MatrixBranch.KINETIC
import kinetickk.ball.content.api.MatrixBranch.SYSTEMS
import kinetickk.ball.content.api.MatrixNodeId.CRYO_LOOP
import kinetickk.ball.content.api.MatrixNodeId.DATA_ARCHIVE
import kinetickk.ball.content.api.MatrixNodeId.HARDLIGHT_RESERVE
import kinetickk.ball.content.api.MatrixNodeId.IMPACT_DRIVER
import kinetickk.ball.content.api.MatrixNodeId.MAGNET_ARRAY
import kinetickk.ball.content.api.MatrixNodeId.REINFORCED_HULL
import kinetickk.ball.content.api.MatrixNodeId.SALVAGE_PROTOCOL
import kinetickk.ball.content.api.MatrixNodeId.WEAPON_CALIBRATION
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.toImmutableList

/** The eight permanent Matter upgrades offered in the demo. */
fun defaultMatrixNodes(): ImmutableList<MatrixNodeDefinition> = DefaultMatrixNodes

private val DefaultMatrixNodes: ImmutableList<MatrixNodeDefinition> = listOf(
    // Kinetic: impact, weapons, criticals and Overdrive.
    node(IMPACT_DRIVER, "Impact Driver", "+15% impact damage per rank.", KINETIC, 0, listOf(400, 1_500),
        effects = listOf(stat(ItemEffect.IMPACT_DAMAGE, 0.15f))),
    node(WEAPON_CALIBRATION, "Weapon Calibration", "+12% weapon power per rank.", KINETIC, 0, listOf(500, 2_000),
        effects = listOf(stat(ItemEffect.WEAPON_POWER, 0.12f))),
    // Bulwark: integrity, shields and survival.
    node(REINFORCED_HULL, "Reinforced Hull", "+15% maximum integrity per rank.", BULWARK, 0, listOf(300, 1_200),
        effects = listOf(MatrixEffect.IntegrityShare(0.15f))),
    node(HARDLIGHT_RESERVE, "Hardlight Reserve", "Shield capacity +10% of maximum integrity per rank.", BULWARK, 0, listOf(800, 3_000),
        effects = listOf(MatrixEffect.ShieldShare(0.1f))),
    // Systems: pickups, heat, Dash and obstacles.
    node(MAGNET_ARRAY, "Magnet Array", "+70 pickup radius and +6% Data from each pickup per rank.", SYSTEMS, 0,
        listOf(200, 800), effects = listOf(stat(ItemEffect.PICKUP_RADIUS, 70f), MatrixEffect.PickupValue(0.06f))),
    node(CRYO_LOOP, "Cryo Loop", "+20% cooling and 10% less Dash heat per rank.", SYSTEMS, 0, listOf(600, 1_800),
        effects = listOf(stat(ItemEffect.COOLING, 0.2f), MatrixEffect.DashHeat(0.9f))),
    // Fortune: Matter, Data and offers.
    node(SALVAGE_PROTOCOL, "Salvage Protocol", "+15% Matter per rank.", FORTUNE, 0, listOf(400, 1_000),
        effects = listOf(stat(ItemEffect.MATTER_GAIN, 0.15f))),
    node(DATA_ARCHIVE, "Data Archive",
        "+15% Data per rank. Each rank deals three more Data items into the run's draft deck and gives Data items +150% " +
            "weight in item offers.",
        FORTUNE, 0, listOf(400, 1_000),
        effects = listOf(stat(ItemEffect.DATA_GAIN, 0.15f), MatrixEffect.DataStratum(3), MatrixEffect.DataOfferWeight(1.5f))),
).toImmutableList()

private fun stat(effect: ItemEffect, amount: Float): MatrixEffect = MatrixEffect.Stat(StatDelta(effect, amount))

private fun node(
    id: MatrixNodeId,
    name: String,
    description: String,
    branch: MatrixBranch,
    row: Int,
    costs: List<Int>,
    effects: List<MatrixEffect>,
): MatrixNodeDefinition = MatrixNodeDefinition(
    id = id,
    name = name,
    description = description,
    branch = branch,
    row = row,
    costs = costs.toImmutableList(),
    effects = effects.toImmutableList(),
)
