package com.example.work_calendar.alarm

import android.content.Context
import com.example.work_calendar.data.ShiftRepository
import java.time.LocalDate

/**
 * 향후 일정 분의 알람을 일괄 재등록한다.
 * 부팅 직후 / 앱 업데이트 / 콜드스타트 / 사용자 변경 후 호출.
 */
object AlarmRescheduler {
    private const val WINDOW_DAYS = 90L

    suspend fun rescheduleUpcoming(context: Context) {
        val repo = ShiftRepository(context)
        val state = repo.snapshot()
        val today = LocalDate.now()
        var date = today.minusDays(1)
        val end = today.plusDays(WINDOW_DAYS)
        while (date.isBefore(end)) {
            AlarmScheduler.cancel(context, date)
            val effective = repo.effectiveAlarmTime(date, state.entries, state.defaults)
            if (effective != null) {
                val (shift, time) = effective
                AlarmScheduler.schedule(context, date, time, shift)
            }
            date = date.plusDays(1)
        }
    }
}
