package com.example.work_calendar.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/**
 * 매 자정에 위젯을 한 번 갱신해서 "오늘" 강조와 헤더 우측 날짜를 최신화한다.
 * 위젯 추가 시(onEnabled), 부팅 시(BootReceiver), 자정 이후 매번 다음 자정을 다시 스케줄.
 */
object WidgetMidnightRefresher {
    private const val ACTION_REFRESH = "com.example.work_calendar.action.WIDGET_MIDNIGHT_REFRESH"
    private const val REQUEST_CODE = 0xCAFE

    fun scheduleNext(context: Context) {
        val triggerMillis = LocalDate.now().plusDays(1).atStartOfDay()
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        val pi = buildPendingIntent(context, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.setAndAllowWhileIdle(AlarmManager.RTC, triggerMillis, pi)
    }

    fun cancel(context: Context) {
        val pi = buildPendingIntent(context, PendingIntent.FLAG_NO_CREATE) ?: return
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pi)
        pi.cancel()
    }

    internal const val MATCHED_ACTION = ACTION_REFRESH

    private fun buildPendingIntent(context: Context, flags: Int): PendingIntent? {
        val intent = Intent(context, WidgetMidnightReceiver::class.java).apply {
            action = ACTION_REFRESH
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            flags or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

class WidgetMidnightReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != WidgetMidnightRefresher.MATCHED_ACTION) return
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                WorkCalendarWidget().updateAll(app)
            } finally {
                WidgetMidnightRefresher.scheduleNext(app)
                pending.finish()
            }
        }
    }
}
