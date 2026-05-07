package com.example.work_calendar.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.work_calendar.MainActivity
import com.example.work_calendar.data.ShiftType
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

object AlarmScheduler {

    const val ACTION_SHIFT_ALARM = "com.example.work_calendar.action.SHIFT_ALARM"
    const val EXTRA_SHIFT_DATE = "shift_date"
    const val EXTRA_SHIFT_LABEL = "shift_label"
    const val EXTRA_SHIFT_TIME_RANGE = "shift_time_range"

    fun requestCode(date: LocalDate): Int = date.toEpochDay().toInt()

    fun schedule(
        context: Context,
        shiftDate: LocalDate,
        time: LocalTime,
        shift: ShiftType,
    ) {
        val fireDate = if (shift == ShiftType.N) shiftDate.minusDays(1) else shiftDate
        val triggerMillis = LocalDateTime.of(fireDate, time)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        // 이미 지난 시각의 알람은 등록하지 않는다.
        if (triggerMillis <= System.currentTimeMillis()) return

        val operationPi = buildOperationPendingIntent(context, shiftDate, shift)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else true

        if (canExact) {
            // 시계앱 알람과 동일하게 도즈/절전 모드 무시 + 상태바 다음알람 아이콘 표시.
            val showPi = buildShowPendingIntent(context, shiftDate)
            val info = AlarmManager.AlarmClockInfo(triggerMillis, showPi)
            alarmManager.setAlarmClock(info, operationPi)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, operationPi)
        }
    }

    fun cancel(context: Context, shiftDate: LocalDate) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_SHIFT_ALARM
        }
        val pi = PendingIntent.getBroadcast(
            context,
            requestCode(shiftDate),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pi)
        pi.cancel()
    }

    private fun buildOperationPendingIntent(
        context: Context,
        shiftDate: LocalDate,
        shift: ShiftType,
    ): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_SHIFT_ALARM
            putExtra(EXTRA_SHIFT_DATE, shiftDate.toString())
            putExtra(EXTRA_SHIFT_LABEL, shift.label)
            putExtra(EXTRA_SHIFT_TIME_RANGE, shift.timeRangeText())
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode(shiftDate),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun buildShowPendingIntent(
        context: Context,
        shiftDate: LocalDate,
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context,
            requestCode(shiftDate),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
