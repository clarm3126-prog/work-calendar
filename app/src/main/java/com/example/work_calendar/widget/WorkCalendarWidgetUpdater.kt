package com.example.work_calendar.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WorkCalendarWidgetUpdater(private val context: Context) {
    suspend fun update() = withContext(Dispatchers.IO) {
        val appCtx = context.applicationContext
        // 캐시 무효화 — 다음 provideGlance에서 DataStore를 다시 읽도록.
        WidgetEntriesCache.invalidate()
        // 1차: Glance 표준 갱신.
        WorkCalendarWidget().updateAll(appCtx)
        // 2차 안전망: AppWidgetManager 브로드캐스트로 직접 widget 갱신을 강제.
        // updateAll만으로 RemoteViews가 실제 위젯 호스트까지 전달 안 되는 케이스를 보강.
        val manager = AppWidgetManager.getInstance(appCtx)
        val component = ComponentName(appCtx, WorkCalendarWidgetReceiver::class.java)
        val ids = manager.getAppWidgetIds(component)
        if (ids.isNotEmpty()) {
            val intent = Intent(appCtx, WorkCalendarWidgetReceiver::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
            appCtx.sendBroadcast(intent)
        }
    }
}
