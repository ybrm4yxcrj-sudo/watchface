package com.example.watchface.domain

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.lang.reflect.Method

data class NotificationState(
    val unreadCount: Int = 0,
    val hasUnread: Boolean = false,
    val isPermissionGranted: Boolean = false,
    val isDotEnabled: Boolean = true
)

object NotificationTracker {
    private const val TAG = "NotificationTracker"

    private val _state = MutableStateFlow(NotificationState())
    val state: StateFlow<NotificationState> = _state.asStateFlow()

    fun updateUnreadCount(count: Int) {
        _state.update {
            it.copy(
                unreadCount = count.coerceAtLeast(0),
                hasUnread = count > 0
            )
        }
    }

    fun setDotEnabled(enabled: Boolean) {
        _state.update { it.copy(isDotEnabled = enabled) }
    }

    fun checkPermission(context: Context): Boolean {
        val packageName = context.packageName
        val granted = try {
            val enabledListeners = NotificationManagerCompat.getEnabledListenerPackages(context)
            if (enabledListeners.contains(packageName)) {
                true
            } else {
                val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
                flat != null && flat.contains(packageName)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to query notification listener status", e)
            false
        }

        _state.update { it.copy(isPermissionGranted = granted) }
        return granted
    }

    fun openNotificationAccessSettings(context: Context) {
        try {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            } else {
                Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to launch notification listener settings, opening main settings", e)
            try {
                val fallbackIntent = Intent(Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
            } catch (_: Exception) {}
        }
    }

    /**
     * Attempts to expand the system Quick Settings / Status Bar (下拉状态栏/控制中心).
     */
    @SuppressLint("WrongConstant")
    fun expandStatusBar(context: Context): Boolean {
        return try {
            val statusBarService = context.getSystemService("statusbar") ?: return false
            val statusBarManagerClass = Class.forName("android.app.StatusBarManager")
            
            // Prefer expandSettingsPanel on modern devices, fallback to expandNotificationsPanel
            val method: Method = try {
                statusBarManagerClass.getMethod("expandSettingsPanel")
            } catch (_: NoSuchMethodException) {
                try {
                    statusBarManagerClass.getMethod("expandNotificationsPanel")
                } catch (_: NoSuchMethodException) {
                    statusBarManagerClass.getMethod("expandStatusBar")
                }
            }
            method.isAccessible = true
            method.invoke(statusBarService)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Reflection call to expand status bar failed: ${e.message}")
            false
        }
    }

    /**
     * Attempts to expand the system Notification Panel (上拉通知中心).
     */
    @SuppressLint("WrongConstant")
    fun expandNotificationsPanel(context: Context): Boolean {
        return try {
            val statusBarService = context.getSystemService("statusbar") ?: return false
            val statusBarManagerClass = Class.forName("android.app.StatusBarManager")
            val method: Method = try {
                statusBarManagerClass.getMethod("expandNotificationsPanel")
            } catch (_: NoSuchMethodException) {
                statusBarManagerClass.getMethod("expandStatusBar")
            }
            method.isAccessible = true
            method.invoke(statusBarService)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Reflection call to expand notification panel failed: ${e.message}")
            false
        }
    }

    /**
     * For testing/demo mode: manually toggle unread dot.
     */
    fun toggleTestNotification() {
        _state.update {
            val newCount = if (it.unreadCount > 0) 0 else 3
            it.copy(unreadCount = newCount, hasUnread = newCount > 0)
        }
    }
}
