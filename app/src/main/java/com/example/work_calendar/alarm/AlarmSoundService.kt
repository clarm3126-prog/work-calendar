package com.example.work_calendar.alarm

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.example.work_calendar.MainActivity
import com.example.work_calendar.R

/**
 * 사용자가 정지/스누즈를 누를 때까지 알람음 + 진동을 유지하는 포그라운드 서비스.
 * - AudioAttributes.USAGE_ALARM 으로 무음/방해금지 모드에서도 알람 볼륨으로 울림.
 * - 화면을 깨워 잠금화면 위 AlarmActivity 가 뜰 수 있도록 WakeLock 보유.
 */
class AlarmSoundService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopAndRelease()
            return START_NOT_STICKY
        }

        val date = intent?.getStringExtra(EXTRA_DATE).orEmpty()
        val label = intent?.getStringExtra(EXTRA_LABEL) ?: "근무"
        val text = intent?.getStringExtra(EXTRA_TEXT).orEmpty()

        startForegroundWithNotification(label, text, date)
        acquireWakeLock()
        startSound()
        startVibration()
        return START_STICKY
    }

    override fun onDestroy() {
        stopAndRelease()
        super.onDestroy()
    }

    private fun startForegroundWithNotification(label: String, text: String, date: String) {
        NotificationChannels.ensure(this)

        val openAlarmIntent = AlarmActivity.intent(this, date, label, text).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TASK or
                Intent.FLAG_ACTIVITY_NO_USER_ACTION
        }
        val openAlarmPi = PendingIntent.getActivity(
            this,
            REQUEST_OPEN,
            openAlarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val dismissPi = PendingIntent.getBroadcast(
            this,
            REQUEST_DISMISS,
            AlarmActionReceiver.dismissIntent(this, date),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val snoozePi = PendingIntent.getBroadcast(
            this,
            REQUEST_SNOOZE,
            AlarmActionReceiver.snoozeIntent(this, date, label, text),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val title = if (label == "N") "내일 N 근무 알람" else "$label 근무 알람"
        val notification = NotificationCompat.Builder(this, NotificationChannels.SHIFT_ALARM_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(openAlarmPi)
            .setFullScreenIntent(openAlarmPi, true)
            .addAction(0, "스누즈 5분", snoozePi)
            .addAction(0, "정지", dismissPi)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val lock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "workcalendar:alarm",
        )
        lock.setReferenceCounted(false)
        // 안전장치: 10분 안에는 무조건 해제.
        lock.acquire(10 * 60 * 1000L)
        wakeLock = lock
    }

    private fun startSound() {
        val uri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: return

        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(attrs)
            setDataSource(this@AlarmSoundService, uri)
            isLooping = true
            // 알람 스트림에 직접 출력 — 미디어/벨소리 볼륨이 아니라 알람 볼륨이 적용됨.
            @Suppress("DEPRECATION")
            setAudioStreamType(AudioManager.STREAM_ALARM)
            setOnPreparedListener { it.start() }
            setOnErrorListener { _, _, _ -> true }
            prepareAsync()
        }
    }

    private fun startVibration() {
        val v: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        vibrator = v
        val pattern = longArrayOf(0, 800, 400, 800, 400, 800, 400)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            v?.vibrate(pattern, 0)
        }
    }

    private fun stopAndRelease() {
        runCatching { mediaPlayer?.stop() }
        runCatching { mediaPlayer?.release() }
        mediaPlayer = null
        runCatching { vibrator?.cancel() }
        vibrator = null
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        wakeLock = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    companion object {
        const val NOTIFICATION_ID = 0xA1A2

        const val ACTION_STOP = "com.example.work_calendar.action.ALARM_SOUND_STOP"
        const val EXTRA_DATE = "date"
        const val EXTRA_LABEL = "label"
        const val EXTRA_TEXT = "text"

        private const val REQUEST_OPEN = 1
        private const val REQUEST_DISMISS = 2
        private const val REQUEST_SNOOZE = 3

        fun startIntent(context: Context, date: String, label: String, text: String): Intent =
            Intent(context, AlarmSoundService::class.java).apply {
                putExtra(EXTRA_DATE, date)
                putExtra(EXTRA_LABEL, label)
                putExtra(EXTRA_TEXT, text)
            }

        fun stopIntent(context: Context): Intent =
            Intent(context, AlarmSoundService::class.java).apply { action = ACTION_STOP }

        // MainActivity 사용 (Compose 화면 전환 트리거 가능). 풀스크린 인텐트의 fallback.
        fun mainActivityIntent(context: Context): Intent =
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
    }
}
