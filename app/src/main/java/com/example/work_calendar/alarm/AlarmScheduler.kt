package com.example.work_calendar.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
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

        val pi = buildPendingIntent(context, shiftDate, shift)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else true

        if (canExact) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pi)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pi)
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

    private fun buildPendingIntent(
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
}
