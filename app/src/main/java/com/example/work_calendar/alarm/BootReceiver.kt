package com.example.work_calendar.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 기기 부팅 또는 앱 업데이트 직후 알람을 다시 등록한다.
 * setExactAndAllowWhileIdle 알람은 재부팅/강제종료 시 사라지므로 필수.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return

        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                AlarmRescheduler.rescheduleUpcoming(appContext)
            } finally {
                pending.finish()
            }
        }
    }
}
