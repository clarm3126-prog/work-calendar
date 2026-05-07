package com.example.work_calendar.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager

object NotificationChannels {
    // 시계앱 스타일의 풀스크린 알람용 채널.
    const val SHIFT_ALARM_ID = "shift_alarm_v2"

    fun ensure(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        // 이전 버전 채널이 남아있으면 정리한다 (사운드/중요도 변경 반영을 위해).
        manager.getNotificationChannel("shift_alarm")?.let {
            manager.deleteNotificationChannel("shift_alarm")
        }

        if (manager.getNotificationChannel(SHIFT_ALARM_ID) == null) {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            val channel = NotificationChannel(
                SHIFT_ALARM_ID,
                "근무 알람",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "교대 근무 시작 시각 알람"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 800, 400, 800, 400, 800)
                setSound(sound, attrs)
                setBypassDnd(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            manager.createNotificationChannel(channel)
        }
    }
}
