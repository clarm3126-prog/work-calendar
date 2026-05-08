package com.example.work_calendar.widget

import android.content.Context
import androidx.glance.appwidget.updateAll

class WorkCalendarWidgetUpdater(private val context: Context) {
    suspend fun update() {
        // entries가 바뀌었으므로 캐시를 비워 다음 갱신에서 DataStore를 다시 읽도록 함.
        // mutex로 동기화돼 있어 in-flight 로드와 race가 나지 않음.
        WidgetEntriesCache.invalidate()
        WorkCalendarWidget().updateAll(context)
    }
}
