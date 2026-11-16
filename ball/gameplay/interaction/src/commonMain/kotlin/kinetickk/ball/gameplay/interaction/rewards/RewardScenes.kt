// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.interaction.rewards

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.ItemDefinition
import kinetickk.ball.content.api.SkillId
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.kkMix
import kinetickk.foundation.design.kkStroke
import kotlin.math.cos
import kotlin.math.sin

internal enum class RewardScene {
    FLYWHEEL, DIAL, BRAKE, CAPACITOR, PRISM, SHIELD, REACTOR, RELAY, LATTICE, LEDGER, WELL,
}

internal fun rewardScene(item: ItemDefinition): RewardScene = when (item.active) {
    SkillId.ENTROPIC_TIDE -> RewardScene.WELL
    SkillId.CATALYZE -> RewardScene.LATTICE
    else -> when (item.name) {
        "Dark Star", "Horizon Surfer", "Degenerate Matter", "Like Poles" -> RewardScene.WELL
        "Storm Lattice", "Arc Furnace", "Cryogenic Bus" -> RewardScene.LATTICE
        "Phoenix Coil" -> RewardScene.REACTOR
        "Alchemical Core" -> RewardScene.PRISM
        else -> item.tags.firstOrNull().rewardScene()
    }
}

private fun BuildTag?.rewardScene(): RewardScene = when (this) {
    BuildTag.RAM -> RewardScene.FLYWHEEL
    BuildTag.DRIVE -> RewardScene.DIAL
    BuildTag.ANCHOR -> RewardScene.BRAKE
    BuildTag.ARSENAL -> RewardScene.CAPACITOR
    BuildTag.PRECISION -> RewardScene.PRISM
    BuildTag.BULWARK -> RewardScene.SHIELD
    BuildTag.OVERCLOCK -> RewardScene.REACTOR
    BuildTag.CHAIN -> RewardScene.RELAY
    BuildTag.FORTUNE -> RewardScene.LEDGER
    null -> RewardScene.WELL
}

/** The scene's brushes are shared by all frames of a card, including selection and deal motion. */
internal class RewardSceneInk(hue: Color, secondary: Color) {
    val shadow = kkMix(Kk.Ink, hue, 0.08f)
    val face = kkMix(Kk.Ink, hue, 0.22f)
    val edge = kkMix(Kk.Ink, hue, 0.43f)
    val light = kkMix(Kk.Ink, secondary, 0.69f)
    val glow = Brush.radialGradient(
        0.00f to hue.copy(alpha = 0.20f), 0.60f to hue.copy(alpha = 0.08f), 1f to Color.Transparent,
        center = Offset(65f, 62f),
        radius = 65f,
    )
    val metal = Brush.verticalGradient(listOf(face, shadow, face), startY = 22f, endY = 98f)
}

internal fun DrawScope.drawRewardScene(scene: RewardScene, ink: RewardSceneInk, left: Float, top: Float, width: Float, height: Float) {
    translate(left, top) {
        scale(width / 100f, height / 100f, pivot = Offset.Zero) {
            drawRect(ink.glow, Offset.Zero, Size(100f, 100f))
            when (scene) {
                RewardScene.FLYWHEEL -> drawFlywheel(ink)
                RewardScene.DIAL -> drawDial(ink)
                RewardScene.BRAKE -> drawBrake(ink)
                RewardScene.CAPACITOR -> drawCapacitor(ink)
                RewardScene.PRISM -> drawPrisms(ink)
                RewardScene.SHIELD -> drawShield(ink)
                RewardScene.REACTOR -> drawReactor(ink)
                RewardScene.RELAY -> drawRelay(ink)
                RewardScene.LATTICE -> drawLattice(ink)
                RewardScene.LEDGER -> drawLedger(ink)
                RewardScene.WELL -> drawWell(ink)
            }
        }
    }
}

private fun DrawScope.drawFlywheel(ink: RewardSceneInk) {
    for (ring in 0..3) {
        val width = 85f - 17f * ring
        val drop = 62f - ring * 12f
        val xs = 60f - width / 2f
        oval(ink.shadow, xs, drop + 10f, width, width * 0.47f)
        oval(ink.edge, xs, drop + 10f, width, width * 0.47f, outline = true)
        oval(ink.face, xs, drop, width, width * 0.47f)
        oval(ink.edge, xs, drop, width, width * 0.47f, outline = true)
    }
    oval(ink.shadow, 55f, 22f, 10f, 7f)
    oval(ink.light, 55f, 22f, 10f, 7f, outline = true)
}

private fun DrawScope.drawDial(ink: RewardSceneInk) {
    drawArc(ink.metal, 196f, 148f, true, Offset(4f, 20f), Size(93f, 117f))
    drawArc(ink.edge, 196f, 148f, false, Offset(4f, 20f), Size(93f, 117f), style = kkStroke(1.2f))
    val center = Offset(50.5f, 78.5f)
    for (tick in 0..16) {
        val angle = 3.42f + tick * 0.16f
        val x = cos(angle)
        val y = sin(angle)
        val inset = if (tick % 4 == 0) 0.76f else 0.86f
        drawLine(ink.edge, center + Offset(x * 42f, y * 52f) * inset, center + Offset(x * 42f, y * 52f), 1.3f)
    }
    drawLine(ink.light, center, Offset(82f, 45f), 2.3f)
    oval(ink.shadow, 43f, 67f, 15f, 23f)
    oval(ink.edge, 43f, 67f, 15f, 23f, outline = true)
    oval(ink.light, 48f, 75f, 5f, 7f)
}

private fun DrawScope.drawBrake(ink: RewardSceneInk) {
    oval(ink.shadow, 27f, 11f, 69f, 101f)
    oval(ink.edge, 27f, 11f, 69f, 101f, outline = true)
    oval(ink.face, 35f, 22f, 53f, 79f)
    oval(ink.edge, 47f, 40f, 29f, 43f, outline = true)
    oval(ink.shadow, 56f, 52f, 12f, 19f)
    for (hole in 0..5) {
        val angle = hole * 1.0472f
        oval(ink.shadow, 60f + cos(angle) * 19f, 59f + sin(angle) * 29f, 4f, 6f)
    }
    drawPath(BrakePad, ink.metal)
    drawPath(BrakePad, ink.edge, style = kkStroke(1f))
    drawLine(ink.light, Offset(18f, 32f), Offset(33f, 26f), 1.5f)
}

private fun DrawScope.drawCapacitor(ink: RewardSceneInk) {
    for (cell in 0..3) {
        val left = 14f + cell * 18f
        val top = 43f - cell * 5f
        drawRect(ink.metal, Offset(left, top), Size(13f, 42f))
        oval(ink.face, left, top - 4f, 13f, 8f)
        oval(ink.edge, left, top - 4f, 13f, 8f, outline = true)
        drawLine(ink.edge, Offset(left, top), Offset(left, top + 42f), 1f)
        drawLine(ink.edge, Offset(left + 13f, top), Offset(left + 13f, top + 42f), 1f)
        oval(ink.edge, left, top + 37f, 13f, 9f, outline = true)
        drawLine(ink.light, Offset(left + 3f, top + 9f), Offset(left + 10f, top + 9f), 1f)
    }
    drawLine(ink.edge, Offset(11f, 90f), Offset(85f, 75f), 2.2f)
}

private fun DrawScope.drawPrisms(ink: RewardSceneInk) {
    for (index in CrystalShards.indices) {
        drawPath(CrystalShards[index], if (index == 0) ink.metal else ink.glow)
        drawPath(CrystalShards[index], ink.edge, style = kkStroke(1f))
    }
    drawPath(CrystalLightFacet, ink.light.copy(alpha = 0.30f))
    drawPath(CrystalDarkFacet, ink.shadow.copy(alpha = 0.80f))
    drawPath(CrystalFractures, ink.edge, style = kkStroke(0.7f))
    drawLine(ink.light, Offset(64f, 17f), Offset(55f, 52f), 1.3f)
}

private fun DrawScope.drawShield(ink: RewardSceneInk) {
    drawPath(Shield, ink.metal)
    drawPath(Shield, ink.edge, style = kkStroke(2f))
    drawPath(ShieldInset, ink.shadow)
    drawPath(ShieldInset, ink.edge, style = kkStroke(1f))
    drawLine(ink.light, Offset(57f, 28f), Offset(44f, 37f), 1.5f)
    drawLine(ink.edge, Offset(57f, 51f), Offset(57f, 82f), 1f)
}

private fun DrawScope.drawReactor(ink: RewardSceneInk) {
    oval(ink.shadow, 24f, 19f, 63f, 72f)
    oval(ink.edge, 24f, 19f, 63f, 72f, outline = true)
    for (row in 0..5) {
        val y = 24f + row * 11f
        drawArc(ink.edge, 10f, 165f, false, Offset(24f, y), Size(63f, 25f), style = kkStroke(2.2f))
        drawArc(ink.light, 47f, 43f, false, Offset(24f, y), Size(63f, 25f), style = kkStroke(1.25f))
    }
    oval(ink.face, 45f, 8f, 21f, 17f)
    oval(ink.light, 45f, 8f, 21f, 17f, outline = true)
}

private fun DrawScope.drawRelay(ink: RewardSceneInk) {
    drawPath(RelayTrace, ink.edge, style = kkStroke(1.3f))
    for (index in 0..3) {
        val x = 18f + index * 19f
        val y = 63f - index * 9f
        drawRect(ink.metal, Offset(x, y), Size(12f, 18f))
        drawRect(ink.edge, Offset(x, y), Size(12f, 18f), style = kkStroke(1f))
        drawCircle(ink.light, 1.6f, Offset(x + 6f, y + 7f))
    }
    drawLine(ink.edge, Offset(18f, 88f), Offset(90f, 71f), 1f)
}

private fun DrawScope.drawLattice(ink: RewardSceneInk) {
    for (row in 0..2) for (column in 0..3) {
        val x = 12f + column * 20f + row * 5f
        val y = 31f + row * 17f
        drawLine(ink.edge, Offset(x, y), Offset(x + 13f, y + 16f), 0.8f)
        drawLine(ink.edge, Offset(x + 20f, y), Offset(x + 13f, y + 16f), 0.8f)
        drawLine(ink.face, Offset(x, y), Offset(x + 20f, y), 0.8f)
        drawCircle(ink.edge, 2.5f, Offset(x + 13f, y + 16f))
    }
    drawPath(RelayTrace, ink.edge, style = kkStroke(4f))
    drawPath(RelayTrace, ink.light, style = kkStroke(1.2f))
}

private fun DrawScope.drawLedger(ink: RewardSceneInk) {
    val center = Offset(52f, 91f)
    for (card in -2..2) {
        rotate(card * 18f, center) {
            drawPath(LedgerCard, ink.metal)
            drawPath(LedgerCard, ink.edge, style = kkStroke(1f))
            drawPath(LedgerInlay, ink.edge, style = kkStroke(0.8f))
        }
    }
}

private fun DrawScope.drawWell(ink: RewardSceneInk) {
    rotate(-12f, Offset(63f, 61f)) {
        for (ring in 0..3) {
            drawArc(ink.edge, 173f - ring * 13f, 304f, false, Offset(19f + ring * 7f, 40f + ring * 4f),
                Size(80f - ring * 14f, 39f - ring * 8f), style = kkStroke(if (ring == 0) 1.4f else 0.8f))
        }
        oval(Kk.Ink, 48f, 38f, 25f, 40f)
        oval(ink.edge, 48f, 38f, 25f, 40f, outline = true)
        drawArc(ink.light, 9f, 125f, false, Offset(30f, 51f), Size(59f, 24f), style = kkStroke(0.9f))
    }
}

private fun DrawScope.oval(color: Color, x: Float, y: Float, width: Float, height: Float, outline: Boolean = false) {
    val origin = Offset(x, y)
    val size = Size(width, height)
    if (outline) drawOval(color, origin, size, style = kkStroke(1f)) else drawOval(color, origin, size)
}

private val BrakePad = outline(15f, 34f, 32f, 24f, 43f, 36f, 36f, 49f, 33f, 71f, 38f, 85f, 23f, 93f, 13f, 76f)
private val CrystalShards = arrayOf(
    outline(65f, 13f, 79f, 55f, 48f, 94f, 42f, 56f),
    outline(36f, 37f, 41f, 74f, 24f, 82f, 20f, 61f),
    outline(85f, 40f, 95f, 64f, 79f, 85f, 76f, 71f),
)
private val CrystalLightFacet = outline(65f, 13f, 55f, 52f, 42f, 56f)
private val CrystalDarkFacet = outline(55f, 52f, 79f, 55f, 48f, 94f)
private val CrystalFractures = Path().apply {
    moveTo(24f, 82f)
    lineTo(30f, 61f)
    lineTo(36f, 37f)
    moveTo(42f, 56f)
    lineTo(55f, 52f)
    lineTo(79f, 55f)
    moveTo(55f, 52f)
    lineTo(48f, 94f)
    moveTo(85f, 40f)
    lineTo(84f, 65f)
    lineTo(79f, 85f)
}
private val Shield = outline(57f, 13f, 89f, 31f, 83f, 64f, 71f, 84f, 57f, 98f, 39f, 84f, 24f, 64f, 20f, 33f)
private val ShieldInset = outline(57f, 26f, 76f, 37f, 73f, 60f, 57f, 84f, 40f, 61f, 36f, 38f)
private val LedgerCard = outline(39f, 22f, 62f, 22f, 66f, 30f, 66f, 90f, 37f, 90f, 37f, 30f)
private val LedgerInlay = outline(52f, 38f, 60f, 55f, 52f, 70f, 44f, 55f)
private val RelayTrace = Path().apply {
    moveTo(5f, 84f)
    lineTo(29f, 59f)
    lineTo(45f, 72f)
    lineTo(57f, 39f)
    lineTo(72f, 52f)
    lineTo(95f, 19f)
}

private fun outline(vararg points: Float) = Path().apply {
    moveTo(points[0], points[1])
    for (index in 2 until points.size step 2) lineTo(points[index], points[index + 1])
    close()
}
