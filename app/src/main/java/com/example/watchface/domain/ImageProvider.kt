package com.example.watchface.domain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorFilter
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.util.LruCache
import com.example.R
import com.example.watchface.Config
import com.example.watchface.data.ImageRotationMode
import java.io.File
import kotlin.random.Random

data class WatchWallpaper(
    val id: String,
    val title: String,
    val isExternal: Boolean,
    val filePath: String? = null,
    val drawableRes: Int? = null
)

class ImageProvider(
    private val context: Context,
    private val targetWidth: Int = Config.SCREEN_WIDTH_PX,
    private val targetHeight: Int = Config.SCREEN_HEIGHT_PX
) {
    private var rotationMode = ImageRotationMode.ON_WAKE
    private var currentWallpaperList: List<WatchWallpaper> = emptyList()
    private var currentActiveIndex = 0

    // Memory-safe LRU Cache (max 3 bitmaps ~ 1.2MB total in RGB_565)
    private val bitmapCache = object : LruCache<String, Bitmap>(Config.IMAGE_CACHE_SIZE) {
        override fun entryRemoved(evicted: Boolean, key: String, oldValue: Bitmap, newValue: Bitmap?) {
            if (evicted && !oldValue.isRecycled) {
                oldValue.recycle()
            }
        }
    }

    // Built-in presets in case /sdcard/WatchFace is empty or before adb push
    private val fallbackWallpapers = listOf(
        WatchWallpaper(
            id = "preset_space",
            title = "深空星云 (AMOLED Nebula)",
            isExternal = false,
            drawableRes = R.drawable.watch_bg_space_1788157274537
        ),
        WatchWallpaper(
            id = "preset_aurora",
            title = "极光夜空 (Arctic Aurora)",
            isExternal = false,
            drawableRes = R.drawable.watch_bg_aurora_1788165103087
        ),
        WatchWallpaper(
            id = "preset_sunset_dune",
            title = "暮色沙丘 (Dusk Horizon)",
            isExternal = false,
            drawableRes = R.drawable.watch_bg_sunset_dune_1788165113666
        ),
        WatchWallpaper(
            id = "preset_geo_dark",
            title = "暗晶多面 (Dark Crystal)",
            isExternal = false,
            drawableRes = R.drawable.watch_bg_geo_dark_1788165125954
        ),
        WatchWallpaper(
            id = "preset_cyber",
            title = "赛博霓虹 (Cyber Glow)",
            isExternal = false,
            drawableRes = R.drawable.watch_bg_cyber_1788157287366
        )
    )

    fun refreshWallpapers(externalDir: String = Config.IMAGE_DIR): List<WatchWallpaper> {
        val list = mutableListOf<WatchWallpaper>()
        try {
            val dir = File(externalDir)
            if (dir.exists() && dir.isDirectory) {
                val files = dir.listFiles { file ->
                    val name = file.name.lowercase()
                    name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".webp")
                }
                files?.sortedBy { it.name }?.forEach { f ->
                    list.add(
                        WatchWallpaper(
                            id = f.absolutePath,
                            title = f.name,
                            isExternal = true,
                            filePath = f.absolutePath
                        )
                    )
                }
            }
        } catch (_: Exception) { }

        // Fallback to presets if external dir has no images
        if (list.isEmpty()) {
            list.addAll(fallbackWallpapers)
        } else {
            // Also append presets for user testing
            list.addAll(fallbackWallpapers)
        }

        currentWallpaperList = list
        return list
    }

    fun setRotationMode(mode: ImageRotationMode) {
        rotationMode = mode
    }

    fun getRotationMode(): ImageRotationMode = rotationMode

    fun selectWallpaperIndex(index: Int): Bitmap? {
        if (currentWallpaperList.isEmpty()) refreshWallpapers()
        if (currentWallpaperList.isEmpty()) return null
        currentActiveIndex = index.coerceIn(0, currentWallpaperList.size - 1)
        val target = currentWallpaperList[currentActiveIndex]
        return loadBitmap(target)
    }

    fun getCurrentWallpaper(): WatchWallpaper? {
        if (currentWallpaperList.isEmpty()) refreshWallpapers()
        if (currentWallpaperList.isEmpty()) return null
        return currentWallpaperList.getOrNull(currentActiveIndex)
    }

    fun nextWallpaper(): Bitmap? {
        if (currentWallpaperList.isEmpty()) refreshWallpapers()
        if (currentWallpaperList.isEmpty()) return null
        currentActiveIndex = (currentActiveIndex + 1) % currentWallpaperList.size
        val target = currentWallpaperList[currentActiveIndex]
        return loadBitmap(target)
    }

    fun getWallpaperForMode(triggerWake: Boolean = false, currentHour: Int = 0, currentMinute: Int = 0): Bitmap? {
        if (currentWallpaperList.isEmpty()) {
            refreshWallpapers()
        }
        if (currentWallpaperList.isEmpty()) return null

        when (rotationMode) {
            ImageRotationMode.FIXED -> {
                // Keep currentActiveIndex unchanged
            }
            ImageRotationMode.HOURLY -> {
                currentActiveIndex = (currentHour % currentWallpaperList.size).coerceIn(0, currentWallpaperList.size - 1)
            }
            ImageRotationMode.INTERVAL_15M -> {
                val slot = (currentHour * 4 + (currentMinute / 15))
                currentActiveIndex = (slot % currentWallpaperList.size).coerceIn(0, currentWallpaperList.size - 1)
            }
            ImageRotationMode.ON_WAKE -> {
                if (triggerWake && currentWallpaperList.size > 1) {
                    var nextIndex = Random.nextInt(currentWallpaperList.size)
                    if (nextIndex == currentActiveIndex) {
                        nextIndex = (currentActiveIndex + 1) % currentWallpaperList.size
                    }
                    currentActiveIndex = nextIndex
                }
            }
        }

        val wp = currentWallpaperList.getOrNull(currentActiveIndex) ?: return null
        return loadBitmap(wp)
    }

    private fun loadBitmap(wallpaper: WatchWallpaper): Bitmap? {
        val cached = bitmapCache.get(wallpaper.id)
        if (cached != null && !cached.isRecycled) {
            return cached
        }

        val bitmap = if (wallpaper.isExternal && wallpaper.filePath != null) {
            decodeSampledFromFile(wallpaper.filePath, targetWidth, targetHeight)
        } else if (wallpaper.drawableRes != null) {
            decodeSampledFromResource(wallpaper.drawableRes, targetWidth, targetHeight)
        } else {
            null
        }

        if (bitmap != null) {
            bitmapCache.put(wallpaper.id, bitmap)
        }
        return bitmap
    }

    /**
     * Strict 2-pass decoding for 1GB RAM memory safety
     * Uses RGB_565 (383 KB per 402x476 image)
     */
    private fun decodeSampledFromFile(path: String, reqW: Int, reqH: Int): Bitmap? {
        return try {
            val opts = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(path, opts)

            var sample = 1
            while (opts.outWidth / sample > reqW * 2 && opts.outHeight / sample > reqH * 2) {
                sample *= 2
            }

            val realOpts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.RGB_565
                inDither = true
            }
            BitmapFactory.decodeFile(path, realOpts)
        } catch (_: Exception) {
            null
        }
    }

    private fun decodeSampledFromResource(resId: Int, reqW: Int, reqH: Int): Bitmap? {
        return try {
            val opts = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeResource(context.resources, resId, opts)

            var sample = 1
            while (opts.outWidth / sample > reqW * 2 && opts.outHeight / sample > reqH * 2) {
                sample *= 2
            }

            val realOpts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.RGB_565
                inDither = true
            }
            BitmapFactory.decodeResource(context.resources, resId, realOpts)
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        /**
         * GPU-accelerated ColorMatrixColorFilter for DIM state dimming (25%)
         */
        fun createDimFilter(scale: Float = Config.IMAGE_DIM_SCALE): ColorFilter {
            val cm = ColorMatrix(
                floatArrayOf(
                    scale, 0f, 0f, 0f, 0f,
                    0f, scale, 0f, 0f, 0f,
                    0f, 0f, scale, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            return ColorMatrixColorFilter(cm)
        }
    }
}
