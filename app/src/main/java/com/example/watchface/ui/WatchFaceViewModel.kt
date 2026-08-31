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
import com.example.watchface.domain.ImageProvider
import com.example.watchface.domain.TimeSnapshot
import com.example.watchface.domain.TimeTicker
import com.example.watchface.domain.WakeDetector
import com.example.watchface.domain.WatchWallpaper
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WatchFaceUiState(
    val state: WatchFaceState = WatchFaceState.ACTIVE,
    val timeSnapshot: TimeSnapshot = TimeSnapshot("10:32", ":00", "周一 8月31日", 78, 10, 32),
    val currentBitmap: Bitmap? = null,
    val currentWallpaper: WatchWallpaper? = null,
    val allWallpapers: List<WatchWallpaper> = emptyList(),
    val rotationMode: ImageRotationMode = ImageRotationMode.ON_WAKE,
    val overlayAlpha: Float = 0f,
    val screenBrightness: Float = Config.BRIGHTNESS_ACTIVE,
    val rawLux: Float = 50f,
    val smoothedLux: Float = 50f,
    val luxTier: LuxTier = LuxTier.ROOM,
    val burnInOffset: BurnInOffset = BurnInOffset(0f, 0f, 0),
    val activeTimeoutMs: Long = Config.ACTIVE_TIMEOUT_MS,
    val isSettingsOpen: Boolean = false,
    val lastWakeReason: String = "启动初始化",
    val pitchAngle: Float = 0f,
    val motionMagnitudeDev: Float = 0f,
    val manualLuxOverride: Float? = null,
    val dimImageScale: Float = Config.IMAGE_DIM_SCALE,
    val showImageInDim: Boolean = true
)

class WatchFaceViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(WatchFaceUiState())
    val uiState: StateFlow<WatchFaceUiState> = _uiState.asStateFlow()

    private var brightnessController: BrightnessController? = null
    private var wakeDetector: WakeDetector? = null
    private var timeTicker: TimeTicker? = null
    private var imageProvider: ImageProvider? = null
    private val burnInGuard = BurnInGuard()

    private var dimTimerJob: Job? = null

    fun initialize(activity: Activity) {
        val appContext = activity.applicationContext

        brightnessController = BrightnessController(activity)
        imageProvider = ImageProvider(appContext).apply {
            setRotationMode(_uiState.value.rotationMode)
        }

        val wallpapers = imageProvider!!.refreshWallpapers()
        val initialBitmap = imageProvider!!.getWallpaperForMode(triggerWake = false)
        val initialWp = imageProvider!!.getCurrentWallpaper()

        _uiState.update {
            it.copy(
                allWallpapers = wallpapers,
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
            burnInGuard.getNextOffset(density)
        } else {
            burnInGuard.getCurrentOffset(density)
        }

        // Check hourly wallpaper rotation
        if (_uiState.value.rotationMode == ImageRotationMode.HOURLY && snapshot.minuteOfHour == 0) {
            val bmp = imageProvider?.getWallpaperForMode(triggerWake = false, currentHour = snapshot.hourOfDay)
            val wp = imageProvider?.getCurrentWallpaper()
            _uiState.update { it.copy(currentBitmap = bmp, currentWallpaper = wp) }
        }

        _uiState.update {
            it.copy(
                timeSnapshot = snapshot,
                burnInOffset = offset
            )
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
            brightnessController?.animateToState(WatchFaceState.DIM, tier) { alpha ->
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

            // Wallpaper rotation on wake
            if (_uiState.value.rotationMode == ImageRotationMode.ON_WAKE) {
                val newBitmap = imageProvider?.getWallpaperForMode(triggerWake = true)
                val newWp = imageProvider?.getCurrentWallpaper()
                if (newBitmap != null) {
                    _uiState.update { it.copy(currentBitmap = newBitmap, currentWallpaper = newWp) }
                }
            }

            brightnessController?.animateToState(WatchFaceState.ACTIVE, _uiState.value.luxTier) { alpha ->
                _uiState.update { it.copy(overlayAlpha = alpha) }
            }
        }

        resetDimTimer()
    }

    fun enterDim(reason: String = "超时自动压暗") {
        dimTimerJob?.cancel()
        _uiState.update { it.copy(state = WatchFaceState.DIM, lastWakeReason = reason) }
        timeTicker?.setState(WatchFaceState.DIM)

        brightnessController?.animateToState(WatchFaceState.DIM, _uiState.value.luxTier) { alpha ->
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
    }

    fun setActiveTimeout(timeoutMs: Long) {
        _uiState.update { it.copy(activeTimeoutMs = timeoutMs) }
        resetDimTimer()
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
    }

    fun refreshWallpapers() {
        val list = imageProvider?.refreshWallpapers() ?: emptyList()
        val bmp = imageProvider?.getWallpaperForMode(triggerWake = false)
        val wp = imageProvider?.getCurrentWallpaper()
        _uiState.update {
            it.copy(
                allWallpapers = list,
                currentBitmap = bmp,
                currentWallpaper = wp
            )
        }
    }

    fun setManualLuxOverride(lux: Float?) {
        _uiState.update { it.copy(manualLuxOverride = lux) }
        val effective = lux ?: _uiState.value.rawLux
        handleLuxUpdate(effective, _uiState.value.rawLux)
    }

    fun setDimImageScale(scale: Float) {
        _uiState.update { it.copy(dimImageScale = scale.coerceIn(0.2f, 1.0f)) }
    }

    fun setShowImageInDim(enabled: Boolean) {
        _uiState.update { it.copy(showImageInDim = enabled) }
    }

    fun toggleSettings(open: Boolean? = null) {
        _uiState.update { it.copy(isSettingsOpen = open ?: !it.isSettingsOpen) }
        if (_uiState.value.isSettingsOpen) {
            wakeUp("打开设置")
        }
    }

    override fun onCleared() {
        super.onCleared()
        dimTimerJob?.cancel()
        wakeDetector?.stopListening()
        timeTicker?.release()
        brightnessController?.release()
    }
}
