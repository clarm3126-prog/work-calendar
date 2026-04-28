package com.example.work_calendar.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

object NotificationChannels {
    const val SHIFT_ALARM_ID = "shift_alarm"

    fun ensure(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(SHIFT_ALARM_ID) == null) {
            val channel = NotificationChannel(
                SHIFT_ALARM_ID,
                "근무 알림",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "교대 근무 일정 알림"
                enableVibration(true)
            }
            manager.createNotificationChannel(channel)
        }
    }
}
