package com.example.work_calendar.widget

import android.content.Context
import androidx.glance.appwidget.updateAll

class WorkCalendarWidgetUpdater(private val context: Context) {
    suspend fun update() {
        WorkCalendarWidget().updateAll(context)
    }
}
