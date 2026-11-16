// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.interaction.fx

/**
 * Stepped time of the pixel-art effects. A strip advances in whole ticks of its own frame rate and shows the frame
 * its holds give that tick, whatever the display's rate: at 30 or 240 FPS a 16 FPS strip changes frame at the same
 * moments and lasts as long. Tick maps are built once, when the catalog loads; a lookup allocates nothing.
 */
internal object PixelFxClock {
    /** Per strip, the frame shown on each tick of its life. */
    private val tickFrames: Array<ByteArray> = Array(PixelStrip.entries.size) { ordinal ->
        val strip = PixelStrip.entries[ordinal]
        require(strip.holds.length == strip.frames) { "${strip.name}: one hold per frame" }
        val ticks = ByteArray(strip.holds.sumOf { it - '0' })
        var tick = 0
        for (frame in 0 until strip.frames) repeat(strip.holds[frame] - '0') { ticks[tick++] = frame.toByte() }
        ticks
    }

    /** Whole ticks of a [fps] animation passed at [age] seconds (0 on the first tick; negative before it starts). */
    fun tick(age: Float, fps: Int): Int = if (age < 0f) -1 else (age * fps).toInt()

    /** [age] seconds held back to the start of its tick at [fps]: the time a stepped animation shows. */
    fun stepped(age: Float, fps: Int): Float = if (age <= 0f) 0f else (age * fps).toInt() / fps.toFloat()

    /** Ticks [strip] lasts. */
    fun ticks(strip: PixelStrip): Int = tickFrames[strip.ordinal].size

    /** Seconds [strip] lasts when it plays once. */
    fun seconds(strip: PixelStrip): Float = tickFrames[strip.ordinal].size / strip.fps.toFloat()

    /** The frame of [strip] at [age] seconds when it plays once; −1 before it starts and after it ends. */
    fun frame(strip: PixelStrip, age: Float): Int {
        val ticks = tickFrames[strip.ordinal]
        val tick = tick(age, strip.fps)
        return if (tick < 0 || tick >= ticks.size) -1 else ticks[tick].toInt()
    }

    /** The frame of [strip] at [age] seconds when it loops over its frames [first] until [end] (holds apply). */
    fun loopFrame(strip: PixelStrip, age: Float, first: Int = 0, end: Int = strip.frames, phase: Int = 0): Int {
        val span = end - first
        val tick = tick(if (age < 0f) 0f else age, strip.fps) + phase
        return first + ((tick % span) + span) % span
    }

    /**
     * The frame of [strip] when its [frames] (the whole strip by default) are spread over a life of [life] seconds
     * instead of its own holds, still changing only on its ticks: for looks whose life the simulation sets.
     */
    fun spreadFrame(strip: PixelStrip, age: Float, life: Float, frames: Int = strip.frames): Int {
        if (life <= 0f) return frames - 1
        val share = stepped(age, strip.fps) / life
        return (share * frames).toInt().coerceIn(0, frames - 1)
    }
}
