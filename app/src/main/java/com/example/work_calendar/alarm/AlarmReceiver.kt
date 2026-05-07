package com.example.work_calendar.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 예약된 알람 시각이 되면 호출된다.
 * 시계앱 알람과 동일하게 동작하도록:
 *  1) 사용자가 정지/스누즈 누를 때까지 알람음을 유지하는 포그라운드 서비스 시작.
 *     서비스가 풀스크린 인텐트가 달린 알림을 올려, 잠금화면 위로 AlarmActivity 가 표시된다.
 *  2) 화면이 켜진 상태(잠금 미동작)에서는 풀스크린 인텐트가 헤드업으로만 뜰 수 있어
 *     AlarmActivity 를 직접 시작해 즉시 풀스크린 화면을 보장한다.
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmScheduler.ACTION_SHIFT_ALARM) return
        NotificationChannels.ensure(context)

        val dateStr = intent.getStringExtra(AlarmScheduler.EXTRA_SHIFT_DATE) ?: return
        val label = intent.getStringExtra(AlarmScheduler.EXTRA_SHIFT_LABEL) ?: "근무"
        val range = intent.getStringExtra(AlarmScheduler.EXTRA_SHIFT_TIME_RANGE) ?: ""

        val date = runCatching { LocalDate.parse(dateStr) }.getOrNull() ?: return
        val dateFormat = DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN)
        val text = buildString {
            append(date.format(dateFormat))
            if (range.isNotBlank()) append(" · ").append(range)
        }

        val serviceIntent = AlarmSoundService.startIntent(context, dateStr, label, text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(context, serviceIntent)
        } else {
            context.startService(serviceIntent)
        }

        // 사용자 사용 중인 단말이라도 풀스크린 화면을 즉시 띄운다.
        val alarmActivity = AlarmActivity.intent(context, dateStr, label, text).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TASK or
                Intent.FLAG_ACTIVITY_NO_USER_ACTION
        }
        runCatching { context.startActivity(alarmActivity) }
    }
}
