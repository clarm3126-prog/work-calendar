package com.example.work_calendar.widget

import android.content.Context
import androidx.glance.appwidget.updateAll

class WorkCalendarWidgetUpdater(private val context: Context) {
    suspend fun update() {
        // entries가 바뀌었을 수 있으니 receiver 프로세스가 다음 갱신에서 캐시를 다시 로드하도록 표시
        WidgetEntriesCache.bumpRevision(context)
        WorkCalendarWidget().updateAll(context)
    }
}
