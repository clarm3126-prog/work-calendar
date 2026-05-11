package com.example.work_calendar.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WorkCalendarWidgetUpdater(private val context: Context) {
    /**
     * 위젯 즉시 갱신.
     *
     * 위젯이 실제로 살아있는 동안은 provideGlance 내부의 collectAsState가 DataStore
     * 변경을 자동으로 받아 재구성하지만, 위젯이 백그라운드에서 세션이 죽어 있는
     * 경우에는 리액티브 구독이 동작하지 않는다. 이 메서드는 세션을 강제로 다시
     * 만들어 최신 데이터로 RemoteViews를 갱신하는 안전망 역할이다.
     *
     * 구현 노트:
     *   ManifestComponent 기반 ACTION_APPWIDGET_UPDATE 브로드캐스트만 사용한다.
     *   이렇게 하면 시스템이 우리가 manifest에 등록한 WorkCalendarWidgetReceiver
     *   (= GlanceAppWidgetReceiver 서브클래스)를 호출하고, 받침 측에서 자신이
     *   보유한 단일 GlanceAppWidget 인스턴스로 세션을 새로 만든다.
     *
     *   과거에는 여기서 별도로 `WorkCalendarWidget().updateAll(...)`을 호출했으나,
     *   이 방식은 Receiver가 들고 있는 인스턴스와 별개의 GlanceAppWidget 인스턴스로
     *   세션을 시작하기 때문에 Glance 내부 SessionManager 입장에서 같은 widget id에
     *   대해 두 인스턴스가 경합한다. 그 결과 RemoteViews가 호스트까지 전달되지 못해
     *   "메모 저장해도 위젯에 바로 안 보임" 현상이 가끔 발생했다. 단일 경로로 단순화.
     */
    suspend fun update() = withContext(Dispatchers.IO) {
        val appCtx = context.applicationContext
        val manager = AppWidgetManager.getInstance(appCtx) ?: return@withContext
        val component = ComponentName(appCtx, WorkCalendarWidgetReceiver::class.java)
        val ids = manager.getAppWidgetIds(component)
        if (ids.isEmpty()) return@withContext
        val intent = Intent(appCtx, WorkCalendarWidgetReceiver::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        }
        appCtx.sendBroadcast(intent)
    }
}

