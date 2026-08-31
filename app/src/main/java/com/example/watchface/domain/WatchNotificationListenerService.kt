package com.example.watchface.domain

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class WatchNotificationListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "WatchNotifService"
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "Notification listener connected successfully")
        recalculateUnreadCount()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        recalculateUnreadCount()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        recalculateUnreadCount()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.i(TAG, "Notification listener disconnected")
    }

    private fun recalculateUnreadCount() {
        try {
            val notifications = activeNotifications ?: emptyArray()
            // Filter out ongoing background service notifications (like media player or foreground tasks) if they are not user alerts
            val userAlertCount = notifications.count { sbn ->
                val notif = sbn.notification
                val isOngoing = (notif.flags and Notification.FLAG_ONGOING_EVENT) != 0
                val isGroupSummary = (notif.flags and Notification.FLAG_GROUP_SUMMARY) != 0
                // We count clearable and non-summary notifications as user-visible unread notifications
                !isOngoing && !isGroupSummary
            }

            NotificationTracker.updateUnreadCount(userAlertCount)
        } catch (e: Exception) {
            Log.w(TAG, "Error calculating active notifications", e)
        }
    }
}
