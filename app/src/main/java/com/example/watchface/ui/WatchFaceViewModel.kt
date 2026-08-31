package com.example.watchface.ui

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.watchface.Config
import com.example.watchface.data.ImageRotationMode
import com.example.watchface.data.LuxTier
import com.example.watchface.data.WatchFaceState
import com.example.watchface.domain.BrightnessController
import com.example.watchface.domain.BurnInGuard
import com.example.watchface.domain.BurnInOffset
import com.example.watchface.domain.CircadianProfile
import com.example.watchface.domain.ImageProvider
import com.example.watchface.domain.NotificationTracker
import com.example.watchface.domain.TimeSnapshot
import com.example.watchface.domain.TimeTicker
import com.example.watchface.domain.WakeDetector
import com.example.watchface.domain.WatchWallpaper
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WatchFaceUiState(
    val state: WatchFaceState = WatchFaceState.ACTIVE,
    val timeSnapshot: TimeSnapshot = TimeSnapshot("10:32", ":00", "周一 8月31日", 78, false, 10, 32),
    val currentBitmap: Bitmap? = null,
    val currentWallpaper: WatchWallpaper? = null,
    val allWallpapers: List<WatchWallpaper> = emptyList(),
    val rotationMode: ImageRotationMode = ImageRotationMode.ON_WAKE,
    val customImageDir: String = Config.IMAGE_DIR,
    val dirImageCount: Int = 0,
    val dirExists: Boolean = true,
    val overlayAlpha: Float = 0f,
    val screenBrightness: Float = Config.BRIGHTNESS_ACTIVE,
    val rawLux: Float = 50f,
    val smoothedLux: Float = 50f,
    val luxTier: LuxTier = LuxTier.ROOM,
    val burnInOffset: BurnInOffset = BurnInOffset(),
    val isBurnInPixelShiftEnabled: Boolean = true,
    val isCircadianBrightnessEnabled: Boolean = true,
    val circadianProfile: CircadianProfile = CircadianProfile(),
    val activeTimeoutMs: Long = Config.ACTIVE_TIMEOUT_MS,
    val isSettingsOpen: Boolean = false,
    val lastWakeReason: String = "启动初始化",
    val pitchAngle: Float = 0f,
    val motionMagnitudeDev: Float = 0f,
    val manualLuxOverride: Float? = null,
    val dimImageScale: Float = Config.IMAGE_DIM_SCALE,
    val showImageInDim: Boolean = true,
    val unreadNotificationCount: Int = 0,
    val hasUnreadNotifications: Boolean = false,
    val isNotificationAccessGranted: Boolean = false,
    val isNotificationDotEnabled: Boolean = true,
    val isStoragePermissionGranted: Boolean = true
)

class WatchFaceViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(WatchFaceUiState())
    val uiState: StateFlow<WatchFaceUiState> = _uiState.asStateFlow()

    private var brightnessController: BrightnessController? = null
    private var wakeDetector: WakeDetector? = null
    private var timeTicker: TimeTicker? = null
    private var imageProvider: ImageProvider? = null
    private val burnInGuard = BurnInGuard()
    private var sharedPrefs: android.content.SharedPreferences? = null

    private var dimTimerJob: Job? = null

    fun initialize(activity: Activity) {
        val appContext = activity.applicationContext
        sharedPrefs = appContext.getSharedPreferences("watchface_prefs", Context.MODE_PRIVATE)

        // Load persisted settings
        val savedModeName = sharedPrefs?.getString("pref_rotation_mode", ImageRotationMode.ON_WAKE.name)
        val savedMode = try {
            ImageRotationMode.valueOf(savedModeName ?: ImageRotationMode.ON_WAKE.name)
        } catch (_: Exception) {
            ImageRotationMode.ON_WAKE
        }
        val savedTimeout = sharedPrefs?.getLong("pref_active_timeout", Config.ACTIVE_TIMEOUT_MS) ?: Config.ACTIVE_TIMEOUT_MS
        val savedScale = sharedPrefs?.getFloat("pref_dim_scale", Config.IMAGE_DIM_SCALE) ?: Config.IMAGE_DIM_SCALE
        val savedShowDim = sharedPrefs?.getBoolean("pref_show_dim_img", true) ?: true
        val savedIndex = sharedPrefs?.getInt("pref_wallpaper_index", 0) ?: 0
        val savedPixelShift = sharedPrefs?.getBoolean("pref_pixel_shift", true) ?: true
        val savedCircadian = sharedPrefs?.getBoolean("pref_circadian_brightness", true) ?: true
        val savedCustomDir = sharedPrefs?.getString("pref_custom_image_dir", Config.IMAGE_DIR) ?: Config.IMAGE_DIR
        val savedNotifDot = sharedPrefs?.getBoolean("pref_notif_dot", true) ?: true

        burnInGuard.setEnabled(savedPixelShift)
        NotificationTracker.setDotEnabled(savedNotifDot)
        NotificationTracker.checkPermission(appContext)

        brightnessController = BrightnessController(activity)
        val initialCircadian = brightnessController!!.calculateCircadianProfile(10, 32)

        _uiState.update {
            it.copy(
                rotationMode = savedMode,
                customImageDir = savedCustomDir,
                activeTimeoutMs = savedTimeout,
                dimImageScale = savedScale,
                showImageInDim = savedShowDim,
                isBurnInPixelShiftEnabled = savedPixelShift,
                isCircadianBrightnessEnabled = savedCircadian,
                circadianProfile = initialCircadian,
                isNotificationDotEnabled = savedNotifDot,
                isNotificationAccessGranted = NotificationTracker.state.value.isPermissionGranted,
                unreadNotificationCount = NotificationTracker.state.value.unreadCount,
                hasUnreadNotifications = NotificationTracker.state.value.hasUnread
            )
        }

        // Collect NotificationTracker changes
        viewModelScope.launch {
            NotificationTracker.state.collect { nState ->
                _uiState.update {
                    it.copy(
                        unreadNotificationCount = nState.unreadCount,
                        hasUnreadNotifications = nState.hasUnread,
                        isNotificationAccessGranted = nState.isPermissionGranted,
                        isNotificationDotEnabled = nState.isDotEnabled
                    )
                }
            }
        }

        imageProvider = ImageProvider(appContext).apply {
            setRotationMode(savedMode)
            setDirectory(savedCustomDir)
        }

        val (count, exists) = imageProvider!!.scanDirectoryStats(savedCustomDir)
        val wallpapers = imageProvider!!.refreshWallpapers(savedCustomDir)
        val initialBitmap = if (savedMode == ImageRotationMode.FIXED && wallpapers.isNotEmpty()) {
            imageProvider!!.selectWallpaperIndex(savedIndex)
        } else {
            imageProvider!!.getWallpaperForMode(triggerWake = false)
        }
        val initialWp = imageProvider!!.getCurrentWallpaper()

        _uiState.update {
            it.copy(
                allWallpapers = wallpapers,
                dirImageCount = count,
                dirExists = exists,
                currentBitmap = initialBitmap,
                currentWallpaper = initialWp
            )
        }

        // Initialize WakeDetector
        wakeDetector = WakeDetector(
            context = appContext,
            onWakeRequest = { reason ->
                wakeUp(reason)
            },
            onLuxChanged = { smoothed, raw ->
                handleLuxUpdate(smoothed, raw)
            },
            onMotionTelemetry = { pitch, magDev ->
                _uiState.update { it.copy(pitchAngle = pitch, motionMagnitudeDev = magDev) }
            }
        ).also {
            it.startListening()
        }

        // Initialize TimeTicker
        timeTicker = TimeTicker(
            context = appContext,
            onTimeUpdate = { snapshot ->
                handleTimeTick(snapshot)
            }
        ).also {
            it.start()
        }

        resetDimTimer()
    }

    private fun handleTimeTick(snapshot: TimeSnapshot) {
        val density = 1.0f
        val offset = if (_uiState.value.state == WatchFaceState.DIM) {
            burnInGuard.getNextOffset(density, _uiState.value.isBurnInPixelShiftEnabled)
        } else {
            burnInGuard.getCurrentOffset(density, _uiState.value.isBurnInPixelShiftEnabled)
        }

        val circadian = brightnessController?.calculateCircadianProfile(
            snapshot.hourOfDay,
            snapshot.minuteOfHour
        ) ?: CircadianProfile()

        // Check periodic wallpaper rotation (Hourly or 15m intervals)
        val shouldRotateHourly = _uiState.value.rotationMode == ImageRotationMode.HOURLY && snapshot.minuteOfHour == 0
        val shouldRotate15m = _uiState.value.rotationMode == ImageRotationMode.INTERVAL_15M && (snapshot.minuteOfHour % 15 == 0)

        if (shouldRotateHourly || shouldRotate15m) {
            val bmp = imageProvider?.getWallpaperForMode(
                triggerWake = false,
                currentHour = snapshot.hourOfDay,
                currentMinute = snapshot.minuteOfHour
            )
            val wp = imageProvider?.getCurrentWallpaper()
            _uiState.update { it.copy(currentBitmap = bmp, currentWallpaper = wp) }
        }

        _uiState.update {
            it.copy(
                timeSnapshot = snapshot,
                burnInOffset = offset,
                circadianProfile = circadian
            )
        }

        // If in DIM state, dynamically refresh dim overlay based on updated circadian profile
        if (_uiState.value.state == WatchFaceState.DIM) {
            brightnessController?.animateToState(
                state = WatchFaceState.DIM,
                tier = _uiState.value.luxTier,
                circadianFactor = circadian.circadianFactor,
                isCircadianEnabled = _uiState.value.isCircadianBrightnessEnabled
            ) { alpha ->
                _uiState.update { it.copy(overlayAlpha = alpha) }
            }
        }
    }

    private fun handleLuxUpdate(smoothed: Float, raw: Float) {
        val effectiveLux = _uiState.value.manualLuxOverride ?: smoothed
        val tier = brightnessController?.evaluateLuxTier(effectiveLux) ?: LuxTier.ROOM

        _uiState.update {
            it.copy(
                rawLux = raw,
                smoothedLux = effectiveLux,
                luxTier = tier
            )
        }

        // If in DIM state, dynamically adjust dimming
        if (_uiState.value.state == WatchFaceState.DIM) {
            brightnessController?.animateToState(
                state = WatchFaceState.DIM,
                tier = tier,
                circadianFactor = _uiState.value.circadianProfile.circadianFactor,
                isCircadianEnabled = _uiState.value.isCircadianBrightnessEnabled
            ) { alpha ->
                _uiState.update { it.copy(overlayAlpha = alpha) }
            }
        }
    }

    fun wakeUp(reason: String = "触摸唤醒") {
        dimTimerJob?.cancel()

        val isStateChange = _uiState.value.state != WatchFaceState.ACTIVE
        if (isStateChange) {
            _uiState.update { it.copy(state = WatchFaceState.ACTIVE, lastWakeReason = reason) }
            timeTicker?.setState(WatchFaceState.ACTIVE)
            wakeDetector?.setDimState(false)

            // Wallpaper rotation on wake
            if (_uiState.value.rotationMode == ImageRotationMode.ON_WAKE) {
                val newBitmap = imageProvider?.getWallpaperForMode(triggerWake = true)
                val newWp = imageProvider?.getCurrentWallpaper()
                if (newBitmap != null) {
                    _uiState.update { it.copy(currentBitmap = newBitmap, currentWallpaper = newWp) }
                }
            }

            brightnessController?.animateToState(
                state = WatchFaceState.ACTIVE,
                tier = _uiState.value.luxTier,
                circadianFactor = _uiState.value.circadianProfile.circadianFactor,
                isCircadianEnabled = _uiState.value.isCircadianBrightnessEnabled
            ) { alpha ->
                _uiState.update { it.copy(overlayAlpha = alpha) }
            }
        }

        resetDimTimer()
    }

    fun enterDim(reason: String = "超时自动压暗") {
        dimTimerJob?.cancel()
        _uiState.update { it.copy(state = WatchFaceState.DIM, lastWakeReason = reason) }
        timeTicker?.setState(WatchFaceState.DIM)
        wakeDetector?.setDimState(true)

        brightnessController?.animateToState(
            state = WatchFaceState.DIM,
            tier = _uiState.value.luxTier,
            circadianFactor = _uiState.value.circadianProfile.circadianFactor,
            isCircadianEnabled = _uiState.value.isCircadianBrightnessEnabled
        ) { alpha ->
            _uiState.update { it.copy(overlayAlpha = alpha) }
        }
    }

    fun resetDimTimer() {
        dimTimerJob?.cancel()
        dimTimerJob = viewModelScope.launch {
            delay(_uiState.value.activeTimeoutMs)
            enterDim("15秒超时压暗")
        }
    }

    fun setRotationMode(mode: ImageRotationMode) {
        imageProvider?.setRotationMode(mode)
        _uiState.update { it.copy(rotationMode = mode) }
        sharedPrefs?.edit()?.putString("pref_rotation_mode", mode.name)?.apply()
    }

    fun setActiveTimeout(timeoutMs: Long) {
        _uiState.update { it.copy(activeTimeoutMs = timeoutMs) }
        sharedPrefs?.edit()?.putLong("pref_active_timeout", timeoutMs)?.apply()
        resetDimTimer()
    }

    fun setBurnInPixelShiftEnabled(enabled: Boolean) {
        burnInGuard.setEnabled(enabled)
        _uiState.update {
            it.copy(
                isBurnInPixelShiftEnabled = enabled,
                burnInOffset = burnInGuard.getCurrentOffset(1.0f, enabled)
            )
        }
        sharedPrefs?.edit()?.putBoolean("pref_pixel_shift", enabled)?.apply()
    }

    fun setCircadianBrightnessEnabled(enabled: Boolean) {
        _uiState.update { it.copy(isCircadianBrightnessEnabled = enabled) }
        sharedPrefs?.edit()?.putBoolean("pref_circadian_brightness", enabled)?.apply()
        if (_uiState.value.state == WatchFaceState.DIM) {
            brightnessController?.animateToState(
                state = WatchFaceState.DIM,
                tier = _uiState.value.luxTier,
                circadianFactor = _uiState.value.circadianProfile.circadianFactor,
                isCircadianEnabled = enabled
            ) { alpha ->
                _uiState.update { it.copy(overlayAlpha = alpha) }
            }
        }
    }

    fun nextWallpaper() {
        val bmp = imageProvider?.nextWallpaper()
        val wp = imageProvider?.getCurrentWallpaper()
        if (bmp != null) {
            _uiState.update {
                it.copy(
                    currentBitmap = bmp,
                    currentWallpaper = wp
                )
            }
        }
        wakeUp("双击切换壁纸")
    }

    fun selectWallpaperIndex(index: Int) {
        val bmp = imageProvider?.selectWallpaperIndex(index)
        val wp = imageProvider?.getCurrentWallpaper()
        _uiState.update {
            it.copy(
                currentBitmap = bmp,
                currentWallpaper = wp
            )
        }
        sharedPrefs?.edit()?.putInt("pref_wallpaper_index", index)?.apply()
    }

    fun refreshWallpapers() {
        val currentDir = _uiState.value.customImageDir
        val (count, exists) = imageProvider?.scanDirectoryStats(currentDir) ?: Pair(0, false)
        val list = imageProvider?.refreshWallpapers(currentDir) ?: emptyList()
        val bmp = imageProvider?.getWallpaperForMode(triggerWake = false)
        val wp = imageProvider?.getCurrentWallpaper()
        _uiState.update {
            it.copy(
                allWallpapers = list,
                dirImageCount = count,
                dirExists = exists,
                currentBitmap = bmp,
                currentWallpaper = wp
            )
        }
    }

    fun setCustomImageDir(path: String) {
        val cleanPath = path.trim()
        imageProvider?.setDirectory(cleanPath)
        val (count, exists) = imageProvider?.scanDirectoryStats(cleanPath) ?: Pair(0, false)
        val list = imageProvider?.refreshWallpapers(cleanPath) ?: emptyList()
        val bmp = imageProvider?.getWallpaperForMode(triggerWake = false)
        val wp = imageProvider?.getCurrentWallpaper()
        _uiState.update {
            it.copy(
                customImageDir = cleanPath,
                allWallpapers = list,
                dirImageCount = count,
                dirExists = exists,
                currentBitmap = bmp,
                currentWallpaper = wp
            )
        }
        sharedPrefs?.edit()?.putString("pref_custom_image_dir", cleanPath)?.apply()
    }

    fun setManualLuxOverride(lux: Float?) {
        _uiState.update { it.copy(manualLuxOverride = lux) }
        val effective = lux ?: _uiState.value.rawLux
        handleLuxUpdate(effective, _uiState.value.rawLux)
    }

    fun setDimImageScale(scale: Float) {
        val clamped = scale.coerceIn(0.2f, 1.0f)
        _uiState.update { it.copy(dimImageScale = clamped) }
        sharedPrefs?.edit()?.putFloat("pref_dim_scale", clamped)?.apply()
    }

    fun setShowImageInDim(enabled: Boolean) {
        _uiState.update { it.copy(showImageInDim = enabled) }
        sharedPrefs?.edit()?.putBoolean("pref_show_dim_img", enabled)?.apply()
    }

    fun toggleSettings(open: Boolean? = null) {
        _uiState.update { it.copy(isSettingsOpen = open ?: !it.isSettingsOpen) }
        if (_uiState.value.isSettingsOpen) {
            wakeUp("打开设置")
        }
    }

    fun checkStoragePermission(context: Context): Boolean {
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
        _uiState.update { it.copy(isStoragePermissionGranted = granted) }
        return granted
    }

    fun onStoragePermissionResult(granted: Boolean) {
        _uiState.update { it.copy(isStoragePermissionGranted = granted) }
        if (granted) {
            refreshWallpapers()
        }
    }

    fun setNotificationDotEnabled(enabled: Boolean) {
        NotificationTracker.setDotEnabled(enabled)
        _uiState.update { it.copy(isNotificationDotEnabled = enabled) }
        sharedPrefs?.edit()?.putBoolean("pref_notif_dot", enabled)?.apply()
    }

    fun checkNotificationPermission(context: Context) {
        val granted = NotificationTracker.checkPermission(context)
        _uiState.update { it.copy(isNotificationAccessGranted = granted) }
    }

    fun openNotificationAccessSettings(context: Context) {
        NotificationTracker.openNotificationAccessSettings(context)
    }

    fun expandStatusBar(context: Context): Boolean {
        wakeUp("手势下拉状态栏")
        return NotificationTracker.expandStatusBar(context)
    }

    fun expandNotificationsPanel(context: Context): Boolean {
        wakeUp("手势上拉通知栏")
        return NotificationTracker.expandNotificationsPanel(context)
    }

    fun toggleTestNotification() {
        NotificationTracker.toggleTestNotification()
    }

    override fun onCleared() {
        super.onCleared()
        dimTimerJob?.cancel()
        wakeDetector?.stopListening()
        timeTicker?.release()
        brightnessController?.release()
    }
}
