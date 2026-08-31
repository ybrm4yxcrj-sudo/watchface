package com.example.watchface.domain

import com.example.watchface.Config
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

data class BurnInOffset(
    val offsetX: Float,
    val offsetY: Float,
    val step: Int
)

class BurnInGuard(private val radiusDp: Float = Config.BURN_IN_RADIUS_DP) {
    private var shiftStep = 0

    fun getNextOffset(density: Float = 1f): BurnInOffset {
        val angle = (shiftStep % 60) / 60.0 * 2 * PI
        val radiusPx = radiusDp * density
        val x = (radiusPx * cos(angle)).toFloat()
        val y = (radiusPx * sin(angle)).toFloat()
        val currentStep = shiftStep
        shiftStep++
        return BurnInOffset(x, y, currentStep)
    }

    fun getCurrentOffset(density: Float = 1f): BurnInOffset {
        val angle = (shiftStep % 60) / 60.0 * 2 * PI
        val radiusPx = radiusDp * density
        val x = (radiusPx * cos(angle)).toFloat()
        val y = (radiusPx * sin(angle)).toFloat()
        return BurnInOffset(x, y, shiftStep)
    }
}
