package com.example.work_calendar.widget

import android.content.Context
import androidx.glance.appwidget.updateAll

class WorkCalendarWidgetUpdater(private val context: Context) {
    suspend fun update() {
        // entries가 바뀌었으니 다음 갱신에서 DataStore를 다시 읽도록 캐시 무효화
        WidgetEntriesCache.invalidate()
        WorkCalendarWidget().updateAll(context)
    }
}
