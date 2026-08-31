package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.ui.theme.MyApplicationTheme
import com.example.watchface.ui.WatchFaceScreen
import com.example.watchface.ui.WatchFaceViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: WatchFaceViewModel by viewModels()

    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val isGranted = permissions.values.any { it } || checkStoragePermission()
        viewModel.onStoragePermissionResult(isGranted)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setupWatchWindowFlags()

        viewModel.initialize(this)

        // Automatically prompt user for storage / media permissions if not yet granted
        if (!checkStoragePermission()) {
            requestStoragePermission()
        }

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
                    onSetCustomImageDir = { path -> viewModel.setCustomImageDir(path) },
                    onSetManualLux = { lux -> viewModel.setManualLuxOverride(lux) },
                    onSetDimImageScale = { scale -> viewModel.setDimImageScale(scale) },
                    onSetShowImageInDim = { enabled -> viewModel.setShowImageInDim(enabled) },
                    onSetBurnInPixelShift = { enabled -> viewModel.setBurnInPixelShiftEnabled(enabled) },
                    onSetCircadianBrightness = { enabled -> viewModel.setCircadianBrightnessEnabled(enabled) },
                    onToggleSettings = { open -> viewModel.toggleSettings(open) },
                    onExpandStatusBar = { viewModel.expandStatusBar(this@MainActivity) },
                    onExpandNotifications = { viewModel.expandNotificationsPanel(this@MainActivity) },
                    onSetNotificationDotEnabled = { enabled -> viewModel.setNotificationDotEnabled(enabled) },
                    onOpenNotificationAccess = { viewModel.openNotificationAccessSettings(this@MainActivity) },
                    onToggleTestNotification = { viewModel.toggleTestNotification() },
                    onRequestStoragePermission = { requestStoragePermission() }
                )
            }
        }

        applyImmersiveFullscreen()
    }

    override fun onResume() {
        super.onResume()
        applyImmersiveFullscreen()
        viewModel.checkNotificationPermission(this)
        viewModel.checkStoragePermission(this)
        viewModel.wakeUp("页面恢复前台")
    }

    private fun checkStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun getRequiredStoragePermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        }
    }

    private fun requestStoragePermission() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:$packageName")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                    return
                } catch (_: Exception) {
                    // Fallback to standard request
                }
            }
            storagePermissionLauncher.launch(getRequiredStoragePermissions())
        } catch (e: Exception) {
            try {
                storagePermissionLauncher.launch(getRequiredStoragePermissions())
            } catch (_: Exception) {}
        }
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
