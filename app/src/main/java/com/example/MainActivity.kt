package com.example

import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.ui.theme.MyApplicationTheme
import com.example.watchface.ui.WatchFaceScreen
import com.example.watchface.ui.WatchFaceViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: WatchFaceViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setupWatchWindowFlags()

        viewModel.initialize(this)

        setContent {
            MyApplicationTheme {
                val uiState by viewModel.uiState.collectAsState()

                WatchFaceScreen(
                    uiState = uiState,
                    onWake = { reason -> viewModel.wakeUp(reason) },
                    onForceDim = { viewModel.enterDim("手动强制压暗") },
                    onNextWallpaper = { viewModel.nextWallpaper() },
                    onSetRotationMode = { mode -> viewModel.setRotationMode(mode) },
                    onSetActiveTimeout = { timeoutMs -> viewModel.setActiveTimeout(timeoutMs) },
                    onSelectWallpaper = { index -> viewModel.selectWallpaperIndex(index) },
                    onRefreshWallpapers = { viewModel.refreshWallpapers() },
                    onSetManualLux = { lux -> viewModel.setManualLuxOverride(lux) },
                    onSetDimImageScale = { scale -> viewModel.setDimImageScale(scale) },
                    onSetShowImageInDim = { enabled -> viewModel.setShowImageInDim(enabled) },
                    onToggleSettings = { open -> viewModel.toggleSettings(open) }
                )
            }
        }

        applyImmersiveFullscreen()
    }

    override fun onResume() {
        super.onResume()
        applyImmersiveFullscreen()
        viewModel.wakeUp("页面恢复前台")
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            viewModel.wakeUp("屏幕触摸")
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun setupWatchWindowFlags() {
        // Keep screen alive physically (never let AMOLED screen turn off)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
    }

    private fun applyImmersiveFullscreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        try {
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } catch (_: Exception) {
            // Fallback for older legacy APIs
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )
        }
    }
}
