// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.nucleus.model

import kinetickk.ball.gameplay.nucleus.render.WeaponEntityKind

/** A Gyre Disc or a mine well, with previous positions for interpolation and swept hits. */
internal class WeaponEntity(
    val kind: WeaponEntityKind,
    var x: Float,
    var y: Float,
    var radius: Float,
    var life: Float,
    val maxLife: Float,
    var vx: Float = 0f,
    var vy: Float = 0f,
    var damage: Float = 0f,
    var angle: Float = 0f,
    /** Reach of a disc's outward pass. */
    var extent: Float = 0f,
    /** Outward travel distance or elapsed time until a returning disc's next deflection spark. */
    var timer: Float = 0f,
    /** Zero on the disc's outward pass, one on return. */
    var phase: Int = 0,
    var procKey: Int = WeaponRuntime.PROC_ALWAYS,
) {
    var previousX: Float = x
    var previousY: Float = y
    var previousAngle: Float = angle
    private val hitIds = IntArray(MAX_ENTITY_HITS)
    private var hitCount = 0

    /** Starts a fixed step: the current positions become the previous ones. */
    fun capturePrevious() {
        previousX = x
        previousY = y
        previousAngle = angle
    }

    fun hasHit(enemyId: Int): Boolean {
        for (index in 0 until hitCount) if (hitIds[index] == enemyId) return true
        return false
    }

    /** Remembers [enemyId] as already hit on this pass; the oldest entry gives way when full. */
    fun recordHit(enemyId: Int) {
        if (hasHit(enemyId)) return
        if (hitCount == MAX_ENTITY_HITS) {
            for (index in 1 until MAX_ENTITY_HITS) hitIds[index - 1] = hitIds[index]
            hitCount--
        }
        hitIds[hitCount++] = enemyId
    }

    fun clearHits() {
        hitCount = 0
    }

    fun shift(shiftX: Float, shiftY: Float) {
        x -= shiftX
        y -= shiftY
        previousX -= shiftX
        previousY -= shiftY
    }

    fun copy(): WeaponEntity = WeaponEntity(
        kind = kind, x = x, y = y, radius = radius, life = life, maxLife = maxLife, vx = vx, vy = vy,
        damage = damage, angle = angle, extent = extent, timer = timer, phase = phase, procKey = procKey,
    ).also { copy ->
        copy.previousX = previousX
        copy.previousY = previousY
        copy.previousAngle = previousAngle
        hitIds.copyInto(copy.hitIds, 0, 0, hitCount)
        copy.hitCount = hitCount
    }

    override fun toString(): String = buildString {
        append(kind).append('(')
        append(x).append(',').append(y).append(',').append(previousX).append(',').append(previousY).append(',')
        append(radius).append(',').append(life).append(',').append(maxLife).append(',').append(vx).append(',')
        append(vy).append(',').append(damage)
        append(',').append(angle).append(',')
        append(previousAngle).append(',').append(extent).append(',').append(timer).append(',').append(phase).append(',')
        append(procKey).append(";hits=")
        for (index in 0 until hitCount) append(hitIds[index]).append(' ')
        append(')')
    }

    companion object {
        /** Enemies one pass remembers; a disc meets each enemy once per pass. */
        const val MAX_ENTITY_HITS: Int = 24
    }
}

/**
 * Owned state of the equipped weapon beyond its clocks: world entities, the proc gates, element and
 * infusion cadences and the inputs the scaling stats read. A deep copy forks it (entities are
 * copied, at most [MAX_WEAPON_ENTITIES]).
 *
 * Proc rule: a discrete activation takes the next [activationSerial]; only its first enemy hit qualifies
 * ([procMask] bit k marks activation `serial − k` as spent). Persistent sources (continuous
 * and the separate Morningstar contact points) qualify at most once per [SOURCE_INTERVAL] per source slot.
 */
internal class WeaponRuntime private constructor(
    val entities: MutableList<WeaponEntity>,
    private val sourceReadyAt: FloatArray,
    private val shedBuckets: FloatArray,
) {
    constructor() : this(ArrayList(), FloatArray(SOURCE_SLOTS), FloatArray(SHED_BUCKETS))

    var activationSerial: Int = 0
    var procMask: Long = 0L
    /** Qualifying hits counted toward a kinetic weapon's adopted element (every third). */
    var infusionHits: Int = 0
    /** Qualifying weapon hits (each fired WEAPON_HIT once); telemetry for the proc bound. */
    var qualifiedHits: Int = 0
    var lastDashAt: Float = NEVER
    var seenDashSequence: Int = 0
    var previousSpeed: Float = 0f
    /** Velocity heading of the last step (Flux Wake's turning trail). */
    var previousHeading: Float = 0f
    var shedBucketIndex: Int = 0
    var shedBucketClock: Float = 0f
    /** Scatter Bloom's passive volley clock. */
    var passiveClock: Float = 0f

    /** Claims the next activation serial (bounded window, see [procMask]). */
    fun beginActivation(): Int {
        activationSerial = (activationSerial + 1) and ACTIVATION_MASK
        procMask = procMask shl 1
        return activationSerial
    }

    /** True when [procKey]'s source may fire on-hit effects now; nothing is spent. */
    fun canQualify(procKey: Int, elapsed: Float): Boolean = when {
        procKey == PROC_ALWAYS -> true
        procKey == PROC_NEVER || procKey == PROC_APEX -> false
        procKey >= 0 -> {
            val age = (activationSerial - procKey) and ACTIVATION_MASK
            age < ACTIVATION_WINDOW && procMask and (1L shl age) == 0L
        }
        else -> elapsed >= sourceReadyAt[sourceSlot(procKey)]
    }

    /** Spends [procKey]'s qualification after a hit that landed. */
    fun spendQualification(procKey: Int, elapsed: Float) {
        when {
            procKey == PROC_ALWAYS || procKey == PROC_NEVER || procKey == PROC_APEX -> Unit
            procKey >= 0 -> {
                val age = (activationSerial - procKey) and ACTIVATION_MASK
                if (age < ACTIVATION_WINDOW) procMask = procMask or (1L shl age)
            }
            else -> sourceReadyAt[sourceSlot(procKey)] = elapsed + SOURCE_INTERVAL
        }
        qualifiedHits++
    }

    /** Adds a step's Brake speed loss and returns the speed shed in about the last half second. */
    fun recordShed(shed: Float, delta: Float): Float {
        shedBucketClock += delta
        while (shedBucketClock >= SHED_BUCKET_SECONDS) {
            shedBucketClock -= SHED_BUCKET_SECONDS
            shedBucketIndex = (shedBucketIndex + 1) % SHED_BUCKETS
            shedBuckets[shedBucketIndex] = 0f
        }
        shedBuckets[shedBucketIndex] += shed
        return recentShed()
    }

    fun recentShed(): Float {
        var total = 0f
        for (value in shedBuckets) total += value
        return total
    }

    /** Clears everything a weapon change or a new run must not carry over. */
    fun reset() {
        entities.clear()
        sourceReadyAt.fill(0f)
        procMask = 0L
        infusionHits = 0
        passiveClock = 0f
    }

    fun rebase(shiftX: Float, shiftY: Float) {
        for (index in entities.indices) entities[index].shift(shiftX, shiftY)
    }

    fun fork(): WeaponRuntime = WeaponRuntime(
        entities = entities.mapTo(ArrayList(entities.size), WeaponEntity::copy),
        sourceReadyAt = sourceReadyAt.copyOf(),
        shedBuckets = shedBuckets.copyOf(),
    ).also { copy ->
        copy.activationSerial = activationSerial
        copy.procMask = procMask
        copy.infusionHits = infusionHits
        copy.qualifiedHits = qualifiedHits
        copy.lastDashAt = lastDashAt
        copy.seenDashSequence = seenDashSequence
        copy.previousSpeed = previousSpeed
        copy.previousHeading = previousHeading
        copy.shedBucketIndex = shedBucketIndex
        copy.shedBucketClock = shedBucketClock
        copy.passiveClock = passiveClock
    }

    /** Deterministic text of every field (the reduction isolation fingerprint reads it). */
    override fun toString(): String = buildString {
        append("WeaponRuntime(")
        append(activationSerial).append(',').append(procMask).append(',')
        append(infusionHits).append(',').append(qualifiedHits).append(',').append(lastDashAt).append(',')
        append(seenDashSequence).append(',').append(previousSpeed).append(',').append(previousHeading).append(',')
        append(shedBucketIndex).append(',')
        append(shedBucketClock).append(',').append(passiveClock)
        append(";sources=").append(sourceReadyAt.joinToString(","))
        append(";shed=").append(shedBuckets.joinToString(","))
        append(";entities=").append(entities.joinToString("|"))
        append(')')
    }

    companion object {
        /** Hits that always fire on-hit effects (relic shots and test fixtures). */
        const val PROC_ALWAYS: Int = Int.MIN_VALUE
        /** Hits that never do (derived bursts of an already counted hit). */
        const val PROC_NEVER: Int = Int.MIN_VALUE + 1
        /** Shots an Apex fired: strikes on boss-class enemies, character damage elsewhere, never an on-hit effect. */
        const val PROC_APEX: Int = Int.MIN_VALUE + 2
        const val SOURCE_SLOTS: Int = 4
        const val SOURCE_INTERVAL: Float = 0.25f
        const val ACTIVATION_WINDOW: Int = 63
        const val ACTIVATION_MASK: Int = 0x3FFF_FFFF
        const val SHED_BUCKETS: Int = 5
        const val SHED_BUCKET_SECONDS: Float = 0.1f
        const val NEVER: Float = -1_000_000f
        const val MAX_WEAPON_ENTITIES: Int = 32

        /** The proc key of persistent source [slot] (0 until [SOURCE_SLOTS]). */
        fun sourceKey(slot: Int): Int = -1 - slot

        private fun sourceSlot(procKey: Int): Int = (-1 - procKey).coerceIn(0, SOURCE_SLOTS - 1)
    }
}
