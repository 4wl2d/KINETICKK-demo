// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.interaction.canvas.world.enemies

import kinetickk.foundation.design.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import kinetickk.ball.gameplay.interaction.canvas.fx.PixelPalettes
import kinetickk.ball.gameplay.interaction.fx.PixelFxClock
import kinetickk.ball.gameplay.interaction.fx.PixelStrip
import kinetickk.ball.gameplay.nucleus.render.EnemyAffinity
import kinetickk.ball.gameplay.nucleus.render.EnemyBehavior
import kinetickk.ball.gameplay.nucleus.render.EnemyProjection
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kinetickk.ball.gameplay.interaction.canvas.*
import kinetickk.ball.gameplay.interaction.canvas.look.*

/** How far off screen an enemy still draws: its telegraphs and tethers reach farther than its body. */
internal fun enemyRenderMargin(enemy: EnemyProjection): Float = when (enemy.type) {
    EnemyType.WARDEN -> 460f
    EnemyType.ARCHITECT -> 220f
    EnemyType.BOMBER -> BOMBER_BLAST_REACH + 20f
    EnemyType.LANCER, EnemyType.SNIPER ->
        if (enemy.behavior == EnemyBehavior.TELEGRAPH || enemy.behavior == EnemyBehavior.STRIKE) LANCE_LANE_LENGTH + 20f else 120f
    EnemyType.COMET, EnemyType.PYLON, EnemyType.SENTRY ->
        max(worldsTelegraphReach(enemy), if (enemy.linkedTo >= 0) AEGIS_TETHER_REACH else 120f)
    EnemyType.SERAPH ->
        if (enemy.actionTimer in 0f..SERAPH_MEND_LEAD) SERAPH_MEND_REACH + 20f else if (enemy.linkedTo >= 0) AEGIS_TETHER_REACH else 120f
    EnemyType.CHOIR -> CHOIR_WAVE_REACH + 20f
    EnemyType.WARDEN_PRIME -> PRIME_WELL_REACH + 20f
    EnemyType.THRONE -> if (enemy.behavior == EnemyBehavior.TELEGRAPH) THRONE_LANE_REACH + 20f else MINI_BOSS_MARGIN
    EnemyType.BROOD_QUEEN, EnemyType.PYRE_HERALD, EnemyType.ANTECEDENT -> MINI_BOSS_MARGIN
    EnemyType.CARTOGRAPHER -> if (enemy.behavior == EnemyBehavior.TELEGRAPH) CARTO_LANE_REACH + 20f else MINI_BOSS_MARGIN
    else -> if (enemy.linkedTo >= 0) AEGIS_TETHER_REACH else 120f
}

/** How far a Comet's lane and streak, a Pylon's sight line or a Sentry's bolt line reach while they show; 0 otherwise. */
private fun worldsTelegraphReach(enemy: EnemyProjection): Float = when (enemy.type) {
    EnemyType.COMET ->
        if (enemy.behavior == EnemyBehavior.TELEGRAPH || enemy.behavior == EnemyBehavior.STRIKE) COMET_LANE_REACH + 20f else 0f
    EnemyType.PYLON -> if (enemy.behavior == EnemyBehavior.TELEGRAPH) PYLON_SIGHT_REACH + 20f else 0f
    EnemyType.SENTRY -> if (enemy.behavior == EnemyBehavior.TELEGRAPH) SENTRY_LINE_REACH + 20f else 0f
    else -> 0f
}

/** Presentation copies of gameplay reaches: the Bomber's blast, the Lancer's lane, an Aegis tether, a mine's trigger. */
private const val BOMBER_BLAST_REACH = 120f
private const val LANCE_LANE_LENGTH = 900f
private const val AEGIS_TETHER_REACH = 240f
internal const val MINE_TRIGGER_REACH = 60f
internal const val MINE_ARMED_LIFE = 7.2f

/** Presentation copies of the 0.6.0 lanes, sight lines and their timings (Comet, Pylon, Sentry), and the mount arc. */
private const val COMET_LANE_REACH = 1_500f
private const val COMET_MARK_SECONDS = 0.6f
private const val PYLON_SIGHT_REACH = 900f
private const val PYLON_AIM_SECONDS = 0.8f
private const val PYLON_LOCK_SECONDS = 0.2f
private const val SENTRY_LINE_REACH = 760f
private const val SENTRY_AIM_SECONDS = 0.45f
internal const val MOUNT_ARC_DEGREES = 120f
internal const val MOUNT_ARC_GAP = 5f

/** Presentation copies of the dimension kinds' reaches and timings (Seraph, Choir, Imp, Hound, Cipher), and accents. */
private const val SERAPH_MEND_REACH = 260f
private const val SERAPH_MEND_LEAD = 0.4f
internal const val SERAPH_RING = 0.5f
private const val CHOIR_WAVE_REACH = 300f
private const val CHOIR_SING_SECONDS = 0.6f
internal const val CHOIR_RING = 0.32f
private const val IMP_BLAST_REACH = 95f
private const val IMP_FUSE_SECONDS = 0.5f
private const val HOUND_WINDUP_SECONDS = 0.35f
private const val CIPHER_AIM_SECONDS = 0.3f
private const val CIPHER_GLITCH_OFFSET = 4f
private const val CIPHER_GLITCH_ALPHA = 0.28f

/**
 * Presentation copies of the mini-bosses' reaches and timings (Warden Prime's well and its double volley cycle, the
 * Throne's arcs and judgement lanes), the Brood Queen's sac pips and a mini-boss's draw margin.
 */
private const val MINI_BOSS_MARGIN = 220f
private const val PRIME_WELL_REACH = 560f
/** Warden Prime's second phase fires a ring at the half of its double cycle: the half is one volley period. */
private const val PRIME_PAIR_HALF = 2f
private const val PRIME_VOLLEY_LEAD = 0.35f
internal const val QUEEN_SAC_RADIUS = 4.5f
internal const val QUEEN_SAC_GAP = 10f
internal const val QUEEN_SAC_ORBIT_RATE = 0.7f
internal const val QUEEN_SAC_SPACING = (2.0 * PI / 3.0).toFloat()
private const val THRONE_ARC_DEGREES = 80f
private const val THRONE_ARC_SPACING = (2.0 * PI / 3.0).toFloat()
private const val THRONE_ARC_GAP = 9f
private const val THRONE_LANE_REACH = 900f
private const val THRONE_JUDGEMENT_SECONDS = 1f

/**
 * Presentation copies of the secret bosses' marks and reaches: the anomaly glyph and its turning bone ring (24 s per
 * turn), and the Cartographer's three Survey lanes, their spacing and how long they are marked.
 */
private const val SECRET_GLYPH = 0.75f

/** A streaking Comet's tail: its length in radii, the full streak's alpha and its brighter core's (together 0.55 at the body). */
private const val COMET_TAIL_LENGTH = 7f
private const val COMET_TAIL_ALPHA = 0.3f
private const val COMET_TAIL_CORE_ALPHA = 0.36f

/** Half side of the Mono ink plate under the secret boss glyph, in glyph sizes: its diamond's 10/24 half diagonal. */
private const val SECRET_GLYPH_PLATE = (10.0 / 24.0 / 1.4142135623730951).toFloat()
private const val SECRET_RING = 1.7f
private const val SECRET_RING_ALPHA = 0.55f
private const val SECRET_RING_DEGREES_PER_SECOND = 15f
private const val CARTO_LANE_REACH = 1_400f
private const val CARTO_LANE_OFFSET = 60f
private const val CARTO_MARK_SECONDS = 1f

/**
 * Presentation copies of the first kinds' telegraph timings: how long a Shooter's aim, a Weaver's wall, a Charger's
 * wind-up, a Lancer's aim and a Sniper's track and lock show, and how far ahead an Interceptor leads.
 */
private const val SHOOTER_AIM_SECONDS = 0.42f
private const val WEAVER_WALL_SECONDS = 0.34f
private const val CHARGER_WINDUP_SECONDS = 0.45f
private const val LANCER_AIM_SECONDS = 0.7f
private const val SNIPER_AIM_SECONDS = 1.1f
private const val SNIPER_LOCK_SECONDS = 0.22f
private const val INTERCEPT_LEAD_SECONDS = 0.45f
/** How far a sight line runs past the Core. */
private const val SIGHT_OVERSHOOT = 120f

/**
 * Gameplay telegraphs show the timing and geometry of windups and attack areas
 * ([drawWindup], [drawVolley], [drawSight], [drawLane], [drawReach], [drawFuse], [drawTether]).
 */
internal fun DrawScope.drawEnemyTelegraph(
    engine: GameplayRenderModel,
    enemy: EnemyProjection,
    center: Offset,
    shakeX: Float,
    shakeY: Float,
    threat: androidx.compose.ui.graphics.Color,
) {
    drawTelegraphBody(engine, enemy, center, threat)
    val palette = PixelPalettes.of(threat)
    val timer = enemy.actionTimer
    val remaining = enemy.behaviorRemaining
    val aiming = enemy.behavior == EnemyBehavior.TELEGRAPH
    val primed = enemy.behavior == EnemyBehavior.PRIMED
    when (enemy.type) {
        EnemyType.SHOOTER -> if (timer in 0f..SHOOTER_AIM_SECONDS) {
            val share = heldShare(SHOOTER_AIM_SECONDS - timer, SHOOTER_AIM_SECONDS, PixelStrip.LINK_TELE_SIGHT.fps)
            drawSight(engine, center, coreOf(engine, shakeX, shakeY), share, false, enemy.radius + 15f * (1f - share), palette)
        }
        EnemyType.CHARGER -> if (timer < 0f) {
            drawWindup(engine, enemy, center, coreOf(engine, shakeX, shakeY), -timer, CHARGER_WINDUP_SECONDS, 28f, palette)
        }
        EnemyType.INTERCEPTOR -> {
            val predicted = world(engine, engine.renderCoreX + engine.velocityX * INTERCEPT_LEAD_SECONDS,
                engine.renderCoreY + engine.velocityY * INTERCEPT_LEAD_SECONDS, shakeX, shakeY)
            drawSight(engine, center, predicted, 0f, false, -1f, palette)
            drawInterceptMark(engine, predicted, palette)
        }
        EnemyType.WEAVER -> if (timer in 0f..WEAVER_WALL_SECONDS) drawWeaverWall(engine, enemy, center, palette)
        // The gravity well's reach, kept to faint dashes so several wardens never tint the arena; the volley's claws.
        EnemyType.WARDEN -> {
            drawReach(engine, center, 440f, reachBreath(engine, enemy), palette)
            drawVolley(engine, enemy, center, timer, 0.38f, 18f, 50f, palette)
        }
        EnemyType.ELITE -> drawVolley(engine, enemy, center, timer, 0.28f, 20f, 38f, palette)
        EnemyType.PYRE_HERALD, EnemyType.ANTECEDENT -> drawVolley(engine, enemy, center, timer, 0.3f, 22f, 40f, palette)
        EnemyType.BOMBER -> if (primed) drawFuse(engine, enemy, center, BOMBER_BLAST_REACH, 0.6f, palette, PixelPalettes.of(Kk.Bone))
        EnemyType.LANCER -> if (aiming) {
            val share = heldShare(LANCER_AIM_SECONDS - remaining, LANCER_AIM_SECONDS, PixelStrip.LINK_TELE_LANE.fps)
            drawLane(engine, center.x, center.y, enemy.facing, LANCE_LANE_LENGTH, telegraphStage(share), palette)
        }
        EnemyType.SNIPER -> if (aiming) drawSightLine(engine, enemy, center, shakeX, shakeY, SNIPER_LOCK_SECONDS, SNIPER_AIM_SECONDS, palette)
        EnemyType.BROOD_QUEEN -> drawVolley(engine, enemy, center, timer, 0.6f, 10f, 30f, palette)
        // The lane it is about to streak, firming as the mark runs out.
        EnemyType.COMET -> if (aiming) drawMarkedLane(engine, enemy, center, COMET_MARK_SECONDS, COMET_LANE_REACH, palette)
        // The Sniper's sight line on its own timings.
        EnemyType.PYLON -> if (aiming) drawSightLine(engine, enemy, center, shakeX, shakeY, PYLON_LOCK_SECONDS, PYLON_AIM_SECONDS, palette)
        // The corridor its bolt train will run down, firming as the aim holds.
        EnemyType.SENTRY -> if (aiming) drawMarkedLane(engine, enemy, center, SENTRY_AIM_SECONDS, SENTRY_LINE_REACH, palette)
        // The reach of its next mend, firming just before it pulses.
        EnemyType.SERAPH -> if (timer in 0f..SERAPH_MEND_LEAD) {
            val share = heldShare(SERAPH_MEND_LEAD - timer, SERAPH_MEND_LEAD, PixelStrip.TELE_REACH.fps)
            drawReach(engine, center, SERAPH_MEND_REACH, reachRamp(engine, share), PixelPalettes.of(Kk.Bone))
        }
        EnemyType.CHOIR -> if (aiming) drawChoir(engine, enemy, center, palette)
        // The Bomber's fuse look at the Imp's smaller reach.
        EnemyType.IMP -> if (primed) drawFuse(engine, enemy, center, IMP_BLAST_REACH, IMP_FUSE_SECONDS, palette, PixelPalettes.of(Kk.Bone))
        // The Charger's wind-up look over the Hound's shorter wind-up.
        EnemyType.HOUND -> if (timer < 0f) {
            drawWindup(engine, enemy, center, coreOf(engine, shakeX, shakeY), -timer, HOUND_WINDUP_SECONDS, 28f, palette)
        }
        EnemyType.CIPHER -> if (aiming) {
            val share = heldShare(CIPHER_AIM_SECONDS - remaining, CIPHER_AIM_SECONDS, PixelStrip.LINK_TELE_SIGHT.fps)
            drawSight(engine, center, coreOf(engine, shakeX, shakeY), share, false, -1f, palette)
        }
        // A hairline to the cursor it mirrors the Core through (the singularity's draw point).
        EnemyType.MIRROR -> drawTelegraphLink(PixelStrip.LINK_TELE_SIGHT, SIGHT_HAIRLINE, center.x, center.y,
            engine.pointerX + shakeX * 0.18f, engine.pointerY + shakeY * 0.18f, engine.worldScale, palette)
        EnemyType.WARDEN_PRIME -> drawPrime(engine, enemy, center, palette)
        // Judgement: the four lanes its bolt trains will run down, firming while the arcs stand still.
        EnemyType.THRONE -> if (aiming) drawJudgement(engine, enemy, center, palette)
        // Survey: the three parallel lanes its bolt trains will run down, firming while it holds the aim (its final
        // phase's short aim after a jump marks none).
        EnemyType.CARTOGRAPHER -> if (aiming && EnemyAffinity.phase(enemy.affinityBits) < 2) drawSurvey(engine, enemy, center, palette)
        EnemyType.DRIFTER, EnemyType.SPLITTER, EnemyType.ARCHITECT, EnemyType.SWARMLING, EnemyType.AEGIS,
        EnemyType.MINELAYER,
        EnemyType.CRAWLER,
        -> Unit
    }
    if (enemy.linkedTo >= 0) drawAegisTether(engine, enemy, center, shakeX, shakeY)
}

private fun DrawScope.coreOf(engine: GameplayRenderModel, shakeX: Float, shakeY: Float): Offset =
    world(engine, engine.renderCoreX, engine.renderCoreY, shakeX, shakeY)

/**
 * The parts of an enemy this file draws that are its body, not a warning: the Weaver's two orbiting pips and the
 * streaks of a striking Lancer and Comet (their bodies in motion). They stay vector, like the bodies.
 */
private fun DrawScope.drawTelegraphBody(engine: GameplayRenderModel, enemy: EnemyProjection, center: Offset, threat: androidx.compose.ui.graphics.Color) {
    if (enemy.type == EnemyType.WEAVER) {
        repeat(2) { index ->
            val orbitAngle = engine.renderElapsed * 2.7f + enemy.id + index * PI.toFloat()
            drawCircle(threat.copy(alpha = 0.8f), 2.5f, polar(center, enemy.radius + 10f, orbitAngle))
        }
    }
    if (enemy.behavior != EnemyBehavior.STRIKE) return
    if (enemy.type == EnemyType.LANCER) {
        val tail = polar(center, enemy.radius * 3.5f, enemy.facing + PI.toFloat())
        drawLine(Kk.Bone.copy(alpha = 0.7f), center, tail, enemy.radius * 0.8f)
    } else if (enemy.type == EnemyType.COMET) {
        // The streak: 7 radii long and 0.9 radii wide at the body, tapering to a blunt tip, its bone fading along it
        // (a shorter, brighter core over the full-length streak).
        val back = enemy.facing + PI.toFloat()
        withTransform({
            translate(center.x, center.y)
            rotate(back * RAD_TO_DEG, Offset.Zero)
            scale(enemy.radius * COMET_TAIL_LENGTH, enemy.radius, Offset.Zero)
        }) {
            drawPath(WorldUnitShapes.cometTail, Kk.Bone.copy(alpha = COMET_TAIL_ALPHA))
            drawPath(WorldUnitShapes.cometTailCore, Kk.Bone.copy(alpha = COMET_TAIL_CORE_ALPHA))
        }
        drawLine(threat, center, polar(center, enemy.radius * 4f, back), 1f)
    }
}

/**
 * The Sniper's sight line: marching dashes while it tracks the Core over [aimSeconds], a ruled bar once [lockSeconds]
 * remain, with muzzle brackets closing onto the body.
 */
private fun DrawScope.drawSightLine(
    engine: GameplayRenderModel,
    enemy: EnemyProjection,
    center: Offset,
    shakeX: Float,
    shakeY: Float,
    lockSeconds: Float,
    aimSeconds: Float,
    palette: androidx.compose.ui.graphics.ColorFilter,
) {
    val core = coreOf(engine, shakeX, shakeY)
    val reach = sqrt((core.x - center.x) * (core.x - center.x) + (core.y - center.y) * (core.y - center.y)) + SIGHT_OVERSHOOT
    val share = heldShare(aimSeconds - enemy.behaviorRemaining, aimSeconds, PixelStrip.LINK_TELE_SIGHT.fps)
    drawSight(engine, center, polar(center, reach, enemy.facing), share, enemy.behaviorRemaining <= lockSeconds,
        enemy.radius + 10f * (1f - share), palette)
}

/** A Weaver's wall: a short lane each way across its body, turning slowly on the lane's ticks, firming as it nears. */
private fun DrawScope.drawWeaverWall(engine: GameplayRenderModel, enemy: EnemyProjection, center: Offset, palette: androidx.compose.ui.graphics.ColorFilter) {
    val fps = PixelStrip.LINK_TELE_LANE.fps
    val angle = PixelFxClock.stepped(engine.renderElapsed, fps) * 0.2f + enemy.id
    val weight = telegraphStage(heldShare(WEAVER_WALL_SECONDS - enemy.actionTimer, WEAVER_WALL_SECONDS, fps), 2)
    drawLane(engine, center.x, center.y, angle, enemy.radius + 19f, weight, palette)
    drawLane(engine, center.x, center.y, angle + PI.toFloat(), enemy.radius + 19f, weight, palette)
}

/** A thin lane of [reach] along the facing, firming through three weights over a mark of [markSeconds]. */
private fun DrawScope.drawMarkedLane(
    engine: GameplayRenderModel,
    enemy: EnemyProjection,
    center: Offset,
    markSeconds: Float,
    reach: Float,
    palette: androidx.compose.ui.graphics.ColorFilter,
) {
    val share = heldShare(markSeconds - enemy.behaviorRemaining, markSeconds, PixelStrip.LINK_TELE_LANE.fps)
    drawLane(engine, center.x, center.y, enemy.facing, reach, telegraphStage(share, 2), palette)
}

/** Two rings contract onto a singing Choir; the faint ring is the reach of its shove. */
private fun DrawScope.drawChoir(engine: GameplayRenderModel, enemy: EnemyProjection, center: Offset, palette: androidx.compose.ui.graphics.ColorFilter) {
    val share = heldShare(CHOIR_SING_SECONDS - enemy.behaviorRemaining, CHOIR_SING_SECONDS, PixelStrip.TELE_REACH.fps)
    val ring = CHOIR_WAVE_REACH + (enemy.radius + 12f - CHOIR_WAVE_REACH) * share
    val live = reachLive(engine)
    drawReach(engine, center, CHOIR_WAVE_REACH, 1, palette)
    drawReach(engine, center, ring, live, palette)
    drawReach(engine, center, ring * 0.6f, live, palette)
}

/** Warden Prime's well: faint breathing dashes while it pulls, live ones while it pushes; then its volley's claws. */
private fun DrawScope.drawPrime(engine: GameplayRenderModel, enemy: EnemyProjection, center: Offset, palette: androidx.compose.ui.graphics.ColorFilter) {
    val pushing = enemy.behavior == EnemyBehavior.PRIMED
    drawReach(engine, center, PRIME_WELL_REACH, if (pushing) reachLive(engine) else reachBreath(engine, enemy), palette)
    // Its second phase fires at the half of a double cycle too.
    val doubled = EnemyAffinity.phase(enemy.affinityBits) == 1 && enemy.actionTimer > PRIME_PAIR_HALF
    drawVolley(engine, enemy, center, if (doubled) enemy.actionTimer - PRIME_PAIR_HALF else enemy.actionTimer, PRIME_VOLLEY_LEAD, 18f, 50f,
        palette)
}

/** The Throne's judgement: four lanes on the diagonals of its facing. */
private fun DrawScope.drawJudgement(engine: GameplayRenderModel, enemy: EnemyProjection, center: Offset, palette: androidx.compose.ui.graphics.ColorFilter) {
    val share = heldShare(THRONE_JUDGEMENT_SECONDS - enemy.behaviorRemaining, THRONE_JUDGEMENT_SECONDS, PixelStrip.LINK_TELE_LANE.fps)
    repeat(4) { index ->
        val angle = enemy.facing + PI.toFloat() * 0.25f + index * PI.toFloat() * 0.5f
        drawLane(engine, center.x, center.y, angle, THRONE_LANE_REACH, telegraphStage(share, 2), palette)
    }
}

/** The Cartographer's survey: three parallel lanes along its facing, [CARTO_LANE_OFFSET] apart. */
private fun DrawScope.drawSurvey(engine: GameplayRenderModel, enemy: EnemyProjection, center: Offset, palette: androidx.compose.ui.graphics.ColorFilter) {
    val share = heldShare(CARTO_MARK_SECONDS - enemy.behaviorRemaining, CARTO_MARK_SECONDS, PixelStrip.LINK_TELE_LANE.fps)
    val sideX = -sin(enemy.facing) * CARTO_LANE_OFFSET
    val sideY = cos(enemy.facing) * CARTO_LANE_OFFSET
    for (index in -1..1) {
        drawLane(engine, center.x + sideX * index, center.y + sideY * index, enemy.facing, CARTO_LANE_REACH, telegraphStage(share, 2), palette)
    }
}

/** The shield arcs of a Throne or the Antecedent: [EnemyProjection.charges] bone arcs of 80° spaced 120° from its facing. */
internal fun DrawScope.drawShieldArcs(enemy: EnemyProjection, center: Offset) {
    repeat(enemy.charges) { index ->
        val start = (enemy.facing + index * THRONE_ARC_SPACING) * RAD_TO_DEG - THRONE_ARC_DEGREES * 0.5f
        drawBoneArc(center, enemy.radius + THRONE_ARC_GAP, start, THRONE_ARC_DEGREES)
    }
}

/** A bone arc of [radius] around [center] (a front shield, a mount, a shield arc). */
internal fun DrawScope.drawBoneArc(center: Offset, radius: Float, startDegrees: Float, sweepDegrees: Float) {
    drawArc(Kk.Bone, startDegrees, sweepDegrees, false, Offset(center.x - radius, center.y - radius), Size(radius * 2f, radius * 2f),
        style = kkStroke(KkStrokeWidth.Heavy))
}

/**
 * A secret boss's identity: the anomaly glyph at its centre and a dashed bone ring turning 15° a second around it. Where
 * threats are hatched (Mono) the glyph sits on an ink plate of its own diamond, so its threat strokes never merge into the
 * body's threat hatch.
 */
internal fun DrawScope.drawSecretBossMark(
    engine: GameplayRenderModel,
    enemy: EnemyProjection,
    center: Offset,
    threat: androidx.compose.ui.graphics.Color,
    roles: KkRolePalette,
) {
    val glyph = enemy.radius * SECRET_GLYPH
    if (roles.hatchThreats) {
        // The glyph's diamond (24-grid half extent 10) as a square turned 45°.
        val half = glyph * SECRET_GLYPH_PLATE
        rotate(45f, center) { drawRect(Kk.Ink, Offset(center.x - half, center.y - half), Size(half * 2f, half * 2f)) }
    }
    drawKkIcon(KkIcon.SYSTEM_ANOMALY, center, glyph, threat)
    rotate(engine.renderElapsed * SECRET_RING_DEGREES_PER_SECOND, center) {
        drawCircle(Kk.Bone, enemy.radius * SECRET_RING, center, alpha = SECRET_RING_ALPHA, style = WorldStrokes.dashedThin)
    }
}

/** A tethered ally: a bone chain back to its Aegis and bone studs round the ally. */
private fun DrawScope.drawAegisTether(engine: GameplayRenderModel, enemy: EnemyProjection, center: Offset, shakeX: Float, shakeY: Float) {
    val enemies = engine.enemies
    for (index in enemies.indices) {
        val aegis = enemies[index]
        if (aegis.id != enemy.linkedTo || aegis.dead) continue
        val from = world(engine, engine.interpolated(aegis.previousX, aegis.x), engine.interpolated(aegis.previousY, aegis.y), shakeX, shakeY)
        drawTether(engine, enemy, from, center, PixelPalettes.of(Kk.Bone))
        return
    }
}

/** A glitched Cipher: its outline stroked twice, offset sideways, in faint dashed threat. */
internal fun DrawScope.drawCipherGlitch(
    engine: GameplayRenderModel,
    enemy: EnemyProjection,
    center: Offset,
    threat: androidx.compose.ui.graphics.Color,
) {
    val path = WorldPaths.silhouette(enemySilhouette(enemy.type), enemy.radius)
    val degrees = enemyRotation(enemy.type, engine.renderElapsed, enemy.id)
    val ghost = threat.copy(alpha = CIPHER_GLITCH_ALPHA)
    repeat(2) { index ->
        withTransform({
            translate(center.x + (index * 2 - 1) * CIPHER_GLITCH_OFFSET, center.y)
            rotate(degrees, Offset.Zero)
        }) {
            drawPath(path, ghost, style = WorldStrokes.dashedThin)
        }
    }
}

/**
 * The Architect: a triangle with a bone inner triangle and threat eye inside two counter-rotating
 * frames, which take the hue of its aspect (threat while it has none). While it shifts between
 * phases the plates of its shift shield stand round it ([ShieldLook.SHIFT]).
 */
internal fun DrawScope.drawArchitect(
    engine: GameplayRenderModel,
    enemy: EnemyProjection,
    center: Offset,
    fill: androidx.compose.ui.graphics.Color,
    roles: KkRolePalette,
    dominant: Int,
    statusFill: Float,
) {
    val threat = roles.threat
    val radius = enemy.radius
    val outer = radius * 1.62f
    val inner = radius * 1.22f
    val aspect = EnemyAffinity.aspect(enemy.affinityBits)
    val frame = if (aspect >= 0) roles.element(aspect) else threat
    rotate(-engine.renderElapsed * spin(40f), center) {
        drawCircle(threat.copy(alpha = 0.4f), radius * 2.57f, center, style = WorldStrokes.dashedHair)
    }
    rotate(engine.renderElapsed * spin(18f) + 12f, center) {
        drawRect(frame, Offset(center.x - outer, center.y - outer), Size(outer * 2f, outer * 2f), style = kkStroke(KkStrokeWidth.Icon))
    }
    rotate(-engine.renderElapsed * spin(12f) - 6f, center) {
        drawRect(frame.copy(alpha = 0.6f), Offset(center.x - inner, center.y - inner), Size(inner * 2f, inner * 2f), style = kkStroke(KkStrokeWidth.Icon))
    }
    val triangle = WorldPaths.architectTriangle(radius)
    // The triangle's visual center sits below its box center (`M50 4 96 84H4Z`), as on the board.
    translate(center.x, center.y) {
        drawPath(triangle, fill)
        if (dominant >= 0) drawStatusFill(triangle, dominant, statusFill, patterned = true, roles = roles)
        drawKkThreatHatch(triangle, roles)
        drawPath(triangle, threat, style = kkStroke(2.5f))
        drawPath(WorldPaths.architectInnerTriangle(radius), Kk.Bone, style = kkStroke(KkStrokeWidth.Line2))
        drawCircle(threat, radius * 0.174f, Offset(0f, radius * 0.174f))
    }
    if (EnemyAffinity.shifting(enemy.affinityBits)) drawShieldPlates(engine, enemy, center, ShieldLook.SHIFT)
}
