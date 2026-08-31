package com.example.watchface

/**
 * Key parameters and device constants for OPPO Watch 2 AOD Watch Face.
 * Tuned for 402x476 1.91" AMOLED screen, 1GB RAM, Snapdragon Wear 4100 + ColorOS Watch (API 27).
 */
object Config {
    // State machine timeouts
    const val ACTIVE_TIMEOUT_MS = 15_000L

    // Brightness overrides (-1f = system default)
    const val BRIGHTNESS_ACTIVE = -1f
    const val BRIGHTNESS_DIM_NIGHT = 0.01f
    const val BRIGHTNESS_DIM_ROOM = 0.05f
    const val BRIGHTNESS_DIM_SUN = 0.25f

    // Dual-stage Dim overlay alpha (gentle contrast so images remain clearly visible)
    const val OVERLAY_ALPHA_NIGHT = 0.20f
    const val OVERLAY_ALPHA_ROOM = 0.10f
    const val OVERLAY_ALPHA_SUN = 0.00f

    // Smooth transition durations
    const val FADE_TO_DIM_MS = 800L
    const val FADE_TO_ACTIVE_MS = 150L

    // Image dimming factor in DIM mode (via ColorMatrix, keeping high clarity and rich colors)
    const val IMAGE_DIM_SCALE = 0.70f

    // Anti-burn-in multi-phase pixel shift algorithm
    const val BURN_IN_RADIUS_DP = 5.5f
    const val BURN_IN_CYCLE_MINUTES = 120

    // Time-based Circadian Diurnal Brightness curve parameters (OLED protection)
    const val CIRCADIAN_NIGHT_FACTOR = 0.45f // 23:00 - 05:30 deep night attenuation
    const val CIRCADIAN_DAWN_FACTOR = 0.85f  // 05:30 - 08:30 morning transition
    const val CIRCADIAN_DAY_FACTOR = 1.00f   // 08:30 - 17:30 peak day
    const val CIRCADIAN_DUSK_FACTOR = 0.70f  // 17:30 - 21:00 evening transition
    const val CIRCADIAN_EVENING_FACTOR = 0.55f // 21:00 - 23:00 pre-sleep dimmed

    // Memory budget for 1GB RAM
    const val IMAGE_CACHE_SIZE = 3

    // Light sensor thresholds & hysteresis
    const val LUX_NIGHT_MAX = 10f
    const val LUX_ROOM_MAX = 200f
    const val LUX_HYSTERESIS = 0.20f // ±20% buffer

    // Accelerometer wake thresholds (Optimized for low power & deliberate gestures)
    const val MOTION_THRESHOLD = 5.8f // Higher magnitude threshold to filter out inadvertent arm swings
    const val WAKE_COOLDOWN_MS = 4_000L

    // Default watch storage directory
    const val IMAGE_DIR = "/sdcard/WatchFace"

    // Reference screen dimensions (OPPO Watch 2 46mm)
    const val SCREEN_WIDTH_PX = 402
    const val SCREEN_HEIGHT_PX = 476
}
