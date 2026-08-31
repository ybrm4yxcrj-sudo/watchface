package com.example.watchface.domain

import android.animation.ValueAnimator
import android.app.Activity
import android.view.animation.DecelerateInterpolator
import com.example.watchface.Config
import com.example.watchface.data.LuxTier
import com.example.watchface.data.WatchFaceState

data class CircadianProfile(
    val circadianFactor: Float = 1.0f,
    val periodName: String = "日间标准",
    val description: String = "8:30 - 17:30 标准白昼"
)

class BrightnessController(private val activity: Activity) {

    private var currentTier = LuxTier.ROOM
    private var brightnessAnim: ValueAnimator? = null
    private var overlayAnim: ValueAnimator? = null

    var currentScreenBrightness: Float = Config.BRIGHTNESS_ACTIVE
        private set
    var currentOverlayAlpha: Float = 0f
        private set

    /**
     * Compute diurnal / circadian brightness factor based on time of day (0.0 to 24.0h)
     */
    fun calculateCircadianProfile(hour: Int, minute: Int): CircadianProfile {
        val t = hour + (minute / 60.0f)

        return when {
            // 23:00 - 05:30 深夜深度护眼与防烧屏
            t >= 23.0f || t < 5.5f -> {
                CircadianProfile(
                    circadianFactor = Config.CIRCADIAN_NIGHT_FACTOR,
                    periodName = "深夜舒适护眼",
                    description = "23:00 - 05:30 舒适护眼 (${(Config.CIRCADIAN_NIGHT_FACTOR * 100).toInt()}%亮度)"
                )
            }
            // 05:30 - 08:30 清晨日出平滑升温
            t in 5.5f..<8.5f -> {
                val progress = (t - 5.5f) / 3.0f
                val factor = Config.CIRCADIAN_NIGHT_FACTOR + progress * (0.95f - Config.CIRCADIAN_NIGHT_FACTOR)
                CircadianProfile(
                    circadianFactor = factor,
                    periodName = "清晨柔和升温",
                    description = "05:30 - 08:30 晨光过渡 (${(factor * 100).toInt()}%)"
                )
            }
            // 08:30 - 17:30 日间白昼清晰
            t in 8.5f..<17.5f -> {
                CircadianProfile(
                    circadianFactor = Config.CIRCADIAN_DAY_FACTOR,
                    periodName = "日间白昼清晰",
                    description = "08:30 - 17:30 黄金可视度 (100%)"
                )
            }
            // 17:30 - 21:00 傍晚日落缓降
            t in 17.5f..<21.0f -> {
                val progress = (t - 17.5f) / 3.5f
                val factor = 1.0f - progress * (1.0f - Config.CIRCADIAN_DUSK_FACTOR)
                CircadianProfile(
                    circadianFactor = factor,
                    periodName = "傍晚日落柔光",
                    description = "17:30 - 21:00 暮色过渡 (${(factor * 100).toInt()}%)"
                )
            }
            // 21:00 - 23:00 入夜睡前微暗
            else -> {
                val progress = (t - 21.0f) / 2.0f
                val factor = Config.CIRCADIAN_DUSK_FACTOR - progress * (Config.CIRCADIAN_DUSK_FACTOR - Config.CIRCADIAN_NIGHT_FACTOR)
                CircadianProfile(
                    circadianFactor = factor,
                    periodName = "睡前舒适护眼",
                    description = "21:00 - 23:00 入夜保护 (${(factor * 100).toInt()}%)"
                )
            }
        }
    }

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
     * Compute target (screenBrightness, overlayAlpha) taking into account state, lux tier,
     * and optional 24-hour Circadian Diurnal Protection factor.
     */
    fun getTargetDimProfile(
        state: WatchFaceState,
        tier: LuxTier,
        circadianFactor: Float = 1.0f,
        isCircadianEnabled: Boolean = true
    ): Pair<Float, Float> {
        val effectiveCircadian = if (isCircadianEnabled) circadianFactor.coerceIn(0.60f, 1.0f) else 1.0f

        if (state == WatchFaceState.ACTIVE) {
            // In ACTIVE mode, preserve bright and responsive screen
            return Pair(Config.BRIGHTNESS_ACTIVE, 0f)
        }

        // DIM (AOD Mode) - apply combined Ambient Lux Tier + Circadian Diurnal Attenuation
        val (baseBrightness, baseAlpha) = when (tier) {
            LuxTier.NIGHT -> Pair(Config.BRIGHTNESS_DIM_NIGHT, Config.OVERLAY_ALPHA_NIGHT)
            LuxTier.ROOM -> Pair(Config.BRIGHTNESS_DIM_ROOM, Config.OVERLAY_ALPHA_ROOM)
            LuxTier.SUN -> Pair(Config.BRIGHTNESS_DIM_SUN, Config.OVERLAY_ALPHA_SUN)
        }

        val targetBrightness = (baseBrightness * effectiveCircadian).coerceAtLeast(0.05f)
        val targetAlpha = (baseAlpha + (1.0f - effectiveCircadian) * 0.10f).coerceIn(0f, 0.40f)

        return Pair(targetBrightness, targetAlpha)
    }

    /**
     * Smoothly animate screen brightness and dim overlay
     */
    fun animateToState(
        state: WatchFaceState,
        tier: LuxTier,
        circadianFactor: Float = 1.0f,
        isCircadianEnabled: Boolean = true,
        onAlphaUpdate: (Float) -> Unit
    ) {
        val (targetBrightness, targetAlpha) = getTargetDimProfile(state, tier, circadianFactor, isCircadianEnabled)
        val duration = if (state == WatchFaceState.DIM) Config.FADE_TO_DIM_MS else Config.FADE_TO_ACTIVE_MS

        // Dim Overlay Alpha animation (Software AMOLED Dimming)
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

        // Window Brightness update (Avoid rapid PWM/IPC stepping that causes backlight flicker)
        brightnessAnim?.cancel()
        if (state == WatchFaceState.ACTIVE) {
            // Restore default system brightness smoothly
            applyWindowBrightness(Config.BRIGHTNESS_ACTIVE)
        } else {
            // Apply dimmed target brightness without arbitrary intermediate jumps
            applyWindowBrightness(targetBrightness)
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
