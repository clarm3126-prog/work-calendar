package com.example.work_calendar.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * 풀스크린 알람 화면 / 헤드업 알림의 "정지" 와 "스누즈" 버튼을 처리한다.
 */
class AlarmActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_DISMISS -> stopSound(context)
            ACTION_SNOOZE -> {
                val date = intent.getStringExtra(EXTRA_DATE).orEmpty()
                val label = intent.getStringExtra(EXTRA_LABEL) ?: "근무"
                val text = intent.getStringExtra(EXTRA_TEXT).orEmpty()
                stopSound(context)
                scheduleSnooze(context, date, label, text)
            }
        }
    }

    private fun stopSound(context: Context) {
        // 서비스에 정지 액션 전달.
        val stop = AlarmSoundService.stopIntent(context)
        // 서비스가 떠있지 않을 수도 있으니 startService 만 호출 (포그라운드 진입은 STOP 분기에서 처리하지 않음).
        runCatching { context.startService(stop) }
    }

    private fun scheduleSnooze(
        context: Context,
        date: String,
        label: String,
        text: String,
    ) {
        val triggerAt = System.currentTimeMillis() + SNOOZE_MS
        val fire = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmScheduler.ACTION_SHIFT_ALARM
            putExtra(AlarmScheduler.EXTRA_SHIFT_DATE, date)
            putExtra(AlarmScheduler.EXTRA_SHIFT_LABEL, label)
            putExtra(AlarmScheduler.EXTRA_SHIFT_TIME_RANGE, text)
            putExtra(EXTRA_SNOOZED, true)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            SNOOZE_REQUEST_CODE,
            fire,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            am.canScheduleExactAlarms()
        } else true

        if (canExact) {
            val showPi = PendingIntent.getActivity(
                context,
                SNOOZE_REQUEST_CODE,
                Intent(context, com.example.work_calendar.MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, showPi), pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    companion object {
        const val ACTION_DISMISS = "com.example.work_calendar.action.ALARM_DISMISS"
        const val ACTION_SNOOZE = "com.example.work_calendar.action.ALARM_SNOOZE"
        const val EXTRA_DATE = "date"
        const val EXTRA_LABEL = "label"
        const val EXTRA_TEXT = "text"
        const val EXTRA_SNOOZED = "snoozed"

        private const val SNOOZE_MS = 5L * 60L * 1000L
        private const val SNOOZE_REQUEST_CODE = 0x5305

        fun dismissIntent(context: Context, date: String): Intent =
            Intent(context, AlarmActionReceiver::class.java).apply {
                action = ACTION_DISMISS
                putExtra(EXTRA_DATE, date)
                // 명시적 컴포넌트 (브로드캐스트 보안).
                setPackage(context.packageName)
            }

        fun snoozeIntent(context: Context, date: String, label: String, text: String): Intent =
            Intent(context, AlarmActionReceiver::class.java).apply {
                action = ACTION_SNOOZE
                putExtra(EXTRA_DATE, date)
                putExtra(EXTRA_LABEL, label)
                putExtra(EXTRA_TEXT, text)
                setPackage(context.packageName)
            }
    }
}

