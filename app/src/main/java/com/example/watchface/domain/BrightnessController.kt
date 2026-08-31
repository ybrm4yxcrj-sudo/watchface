package com.example.watchface.domain

import android.animation.ValueAnimator
import android.app.Activity
import android.view.animation.DecelerateInterpolator
import com.example.watchface.Config
import com.example.watchface.data.LuxTier
import com.example.watchface.data.WatchFaceState

class BrightnessController(private val activity: Activity) {

    private var currentTier = LuxTier.ROOM
    private var brightnessAnim: ValueAnimator? = null
    private var overlayAnim: ValueAnimator? = null

    var currentScreenBrightness: Float = Config.BRIGHTNESS_ACTIVE
        private set
    var currentOverlayAlpha: Float = 0f
        private set

    /**
     * Maps smoothed lux value to LuxTier applying ±20% hysteresis to prevent flickering
     */
    fun evaluateLuxTier(smoothedLux: Float): LuxTier {
        val nightUpper = Config.LUX_NIGHT_MAX
        val roomUpper = Config.LUX_ROOM_MAX
        val hyst = Config.LUX_HYSTERESIS

        currentTier = when (currentTier) {
            LuxTier.NIGHT -> {
                if (smoothedLux > nightUpper * (1f + hyst)) {
                    if (smoothedLux > roomUpper * (1f + hyst)) LuxTier.SUN else LuxTier.ROOM
                } else {
                    LuxTier.NIGHT
                }
            }
            LuxTier.ROOM -> {
                if (smoothedLux < nightUpper * (1f - hyst)) {
                    LuxTier.NIGHT
                } else if (smoothedLux > roomUpper * (1f + hyst)) {
                    LuxTier.SUN
                } else {
                    LuxTier.ROOM
                }
            }
            LuxTier.SUN -> {
                if (smoothedLux < roomUpper * (1f - hyst)) {
                    if (smoothedLux < nightUpper * (1f - hyst)) LuxTier.NIGHT else LuxTier.ROOM
                } else {
                    LuxTier.SUN
                }
            }
        }
        return currentTier
    }

    /**
     * Compute target (screenBrightness, overlayAlpha) for state and lux tier
     */
    fun getTargetDimProfile(state: WatchFaceState, tier: LuxTier): Pair<Float, Float> {
        if (state == WatchFaceState.ACTIVE) {
            return Pair(Config.BRIGHTNESS_ACTIVE, 0f)
        }

        return when (tier) {
            LuxTier.NIGHT -> Pair(Config.BRIGHTNESS_DIM_NIGHT, Config.OVERLAY_ALPHA_NIGHT)
            LuxTier.ROOM -> Pair(Config.BRIGHTNESS_DIM_ROOM, Config.OVERLAY_ALPHA_ROOM)
            LuxTier.SUN -> Pair(Config.BRIGHTNESS_DIM_SUN, Config.OVERLAY_ALPHA_SUN)
        }
    }

    /**
     * Smoothly animate screen brightness and dim overlay
     */
    fun animateToState(
        state: WatchFaceState,
        tier: LuxTier,
        onAlphaUpdate: (Float) -> Unit
    ) {
        val (targetBrightness, targetAlpha) = getTargetDimProfile(state, tier)
        val duration = if (state == WatchFaceState.DIM) Config.FADE_TO_DIM_MS else Config.FADE_TO_ACTIVE_MS

        // Screen Brightness animation
        val fromBrightness = if (currentScreenBrightness < 0f) 0.5f else currentScreenBrightness
        val toBrightness = if (targetBrightness < 0f) 0.5f else targetBrightness

        brightnessAnim?.cancel()
        brightnessAnim = ValueAnimator.ofFloat(fromBrightness, toBrightness).apply {
            this.duration = duration
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                val v = anim.animatedValue as Float
                applyWindowBrightness(if (state == WatchFaceState.ACTIVE) Config.BRIGHTNESS_ACTIVE else v)
            }
            start()
        }

        // Dim Overlay Alpha animation
        overlayAnim?.cancel()
        overlayAnim = ValueAnimator.ofFloat(currentOverlayAlpha, targetAlpha).apply {
            this.duration = duration
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                val alpha = anim.animatedValue as Float
                currentOverlayAlpha = alpha
                onAlphaUpdate(alpha)
            }
            start()
        }
    }

    private fun applyWindowBrightness(brightness: Float) {
        currentScreenBrightness = brightness
        activity.window?.let { win ->
            val lp = win.attributes
            lp.screenBrightness = brightness
            win.attributes = lp
        }
    }

    fun release() {
        brightnessAnim?.cancel()
        overlayAnim?.cancel()
    }
}
