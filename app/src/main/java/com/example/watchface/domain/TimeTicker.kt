package com.example.watchface.domain

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import com.example.watchface.data.WatchFaceState
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class TimeSnapshot(
    val timeText: String,        // "10:32"
    val secondsText: String,     // ":45" or "" in DIM
    val dateText: String,        // "周一 8月31日"
    val batteryPct: Int,         // 78
    val hourOfDay: Int,          // 10
    val minuteOfHour: Int        // 32
)

class TimeTicker(
    private val context: Context,
    private val onTimeUpdate: (TimeSnapshot) -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private var currentState = WatchFaceState.ACTIVE
    private var isReceiverRegistered = false

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("E M月d日", Locale.CHINESE)

    private val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager

    private val timeTickReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            dispatchSnapshot()
        }
    }

    private val minuteRunnable = object : Runnable {
        override fun run() {
            dispatchSnapshot()
            val now = System.currentTimeMillis()
            val delay = (60_000L - (now % 60_000L)) + 50L
            handler.postDelayed(this, delay)
        }
    }

    fun start() {
        safelyRegisterReceiver()
        setState(currentState)
    }

    fun setState(state: WatchFaceState) {
        currentState = state
        dispatchSnapshot()
        handler.removeCallbacks(minuteRunnable)
        if (state == WatchFaceState.ACTIVE) {
            val now = System.currentTimeMillis()
            val delay = (60_000L - (now % 60_000L)) + 50L
            handler.postDelayed(minuteRunnable, delay)
        }
    }

    private fun dispatchSnapshot() {
        val cal = Calendar.getInstance()
        val now = cal.time
        val timeStr = timeFormat.format(now)
        val dateStr = dateFormat.format(now)
        val batteryPct = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 85

        val snapshot = TimeSnapshot(
            timeText = timeStr,
            secondsText = "",
            dateText = dateStr,
            batteryPct = batteryPct,
            hourOfDay = cal.get(Calendar.HOUR_OF_DAY),
            minuteOfHour = cal.get(Calendar.MINUTE)
        )
        onTimeUpdate(snapshot)
    }

    private fun safelyRegisterReceiver() {
        if (!isReceiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_TIME_TICK)
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
            }
            context.registerReceiver(timeTickReceiver, filter)
            isReceiverRegistered = true
        }
    }

    private fun safelyUnregisterReceiver() {
        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(timeTickReceiver)
            } catch (_: Exception) { }
            isReceiverRegistered = false
        }
    }

    fun release() {
        handler.removeCallbacks(minuteRunnable)
        safelyUnregisterReceiver()
    }
}
