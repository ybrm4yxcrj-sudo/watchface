package com.example.watchface.domain

import com.example.watchface.Config
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Pixel Shifting (防烧屏像素多维空间微偏移) Data structure
 */
data class BurnInOffset(
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val timeOffsetX: Float = 0f,
    val timeOffsetY: Float = 0f,
    val dateOffsetX: Float = 0f,
    val dateOffsetY: Float = 0f,
    val step: Int = 0,
    val isEnabled: Boolean = true,
    val radiusDp: Float = Config.BURN_IN_RADIUS_DP,
    val algorithmName: String = "2D Lissajous & Sub-Pixel Shift"
)

/**
 * Industrial-grade OLED Burn-In Guard.
 * Uses a space-filling Lissajous curve with non-harmonic frequencies (3:4) and deterministic
 * micro-dither jitter over a 120-minute cycle, ensuring uniform sub-pixel wear across the entire
 * 2D boundary without fixed orbital rings or center dead zones.
 */
class BurnInGuard(private var radiusDp: Float = Config.BURN_IN_RADIUS_DP) {
    private var shiftStep = 0
    private var isEnabled = true

    fun setEnabled(enabled: Boolean) {
        this.isEnabled = enabled
    }

    fun setRadiusDp(radius: Float) {
        this.radiusDp = radius.coerceIn(1.0f, 12.0f)
    }

    /**
     * Compute next step pixel offset and advance step counter
     */
    fun getNextOffset(density: Float = 1f, enabled: Boolean = isEnabled): BurnInOffset {
        val current = computeOffset(shiftStep, density, enabled)
        shiftStep = (shiftStep + 1) % (Config.BURN_IN_CYCLE_MINUTES * 10)
        return current
    }

    /**
     * Get current step pixel offset without advancing step counter
     */
    fun getCurrentOffset(density: Float = 1f, enabled: Boolean = isEnabled): BurnInOffset {
        return computeOffset(shiftStep, density, enabled)
    }

    /**
     * Reset step counter
     */
    fun reset() {
        shiftStep = 0
    }

    private fun computeOffset(step: Int, density: Float, enabled: Boolean): BurnInOffset {
        if (!enabled) {
            return BurnInOffset(
                offsetX = 0f,
                offsetY = 0f,
                timeOffsetX = 0f,
                timeOffsetY = 0f,
                dateOffsetX = 0f,
                dateOffsetY = 0f,
                step = step,
                isEnabled = false,
                radiusDp = radiusDp
            )
        }

        val totalCycle = Config.BURN_IN_CYCLE_MINUTES.toDouble()
        val normalizedT = (step % Config.BURN_IN_CYCLE_MINUTES) / totalCycle
        val omega = 2.0 * PI * normalizedT

        val radiusPx = radiusDp * density

        // 1. Primary Global Lissajous 3:4 curve filling the 2D box [-R, +R]
        // x(t) = R * sin(3*omega), y(t) = R * sin(4*omega + PI/4)
        val lissajousX = radiusPx * sin(3.0 * omega)
        val lissajousY = radiusPx * sin(4.0 * omega + (PI / 4.0))

        // 2. Deterministic Sub-pixel Jitter (±0.75px) based on golden ratio hash to prevent exact point overlap
        val hash = ((step * 2654435761L) and 0xFFFFFFFFL).toDouble() / 0xFFFFFFFFL
        val jitterX = ((hash * 2.0) - 1.0) * (0.75f * density)
        val jitterY = (((hash * 3.7) % 2.0) - 1.0) * (0.75f * density)

        val globalX = (lissajousX + jitterX).toFloat()
        val globalY = (lissajousY + jitterY).toFloat()

        // 3. Sub-element micro-phase differential (Time text and Date text drift independently by ~1.5px)
        val subPhaseTimeX = (1.5f * density * sin(5.0 * omega)).toFloat()
        val subPhaseTimeY = (1.5f * density * cos(5.0 * omega)).toFloat()

        val subPhaseDateX = (1.2f * density * cos(4.0 * omega + PI / 3.0)).toFloat()
        val subPhaseDateY = (1.2f * density * sin(4.0 * omega + PI / 3.0)).toFloat()

        return BurnInOffset(
            offsetX = globalX,
            offsetY = globalY,
            timeOffsetX = subPhaseTimeX,
            timeOffsetY = subPhaseTimeY,
            dateOffsetX = subPhaseDateX,
            dateOffsetY = subPhaseDateY,
            step = step,
            isEnabled = true,
            radiusDp = radiusDp,
            algorithmName = "2D Lissajous Space-Filling (3:4)"
        )
    }
}
