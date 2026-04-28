package com.example.work_calendar.alarm

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.work_calendar.MainActivity
import com.example.work_calendar.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmScheduler.ACTION_SHIFT_ALARM) return
        NotificationChannels.ensure(context)

        val dateStr = intent.getStringExtra(AlarmScheduler.EXTRA_SHIFT_DATE) ?: return
        val label = intent.getStringExtra(AlarmScheduler.EXTRA_SHIFT_LABEL) ?: "근무"
        val range = intent.getStringExtra(AlarmScheduler.EXTRA_SHIFT_TIME_RANGE) ?: ""

        val date = runCatching { LocalDate.parse(dateStr) }.getOrNull() ?: return
        val isNightAlarm = label == "N"
        val title = if (isNightAlarm) "내일 N 근무 알림" else "$label 근무 알림"
        val dateFormat = DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN)
        val text = buildString {
            append(date.format(dateFormat))
            if (range.isNotBlank()) append(" · ").append(range)
        }

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentPi = PendingIntent.getActivity(
            context,
            AlarmScheduler.requestCode(date),
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, NotificationChannels.SHIFT_ALARM_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentPi)
            .build()

        val canPost = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!canPost) return

        NotificationManagerCompat.from(context)
            .notify(AlarmScheduler.requestCode(date), notification)
    }
}
