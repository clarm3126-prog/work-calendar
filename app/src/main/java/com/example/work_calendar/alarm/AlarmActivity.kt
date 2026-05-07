package com.example.work_calendar.alarm

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.work_calendar.data.ShiftType
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class AlarmActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureLockScreenWindow()

        val date = intent?.getStringExtra(EXTRA_DATE).orEmpty()
        val label = intent?.getStringExtra(EXTRA_LABEL) ?: "근무"
        val text = intent?.getStringExtra(EXTRA_TEXT).orEmpty()

        setContent {
            AlarmScreen(
                label = label,
                text = text,
                onDismiss = {
                    sendBroadcast(AlarmActionReceiver.dismissIntent(this, date))
                    finishAndRemoveTask()
                },
                onSnooze = {
                    sendBroadcast(AlarmActionReceiver.snoozeIntent(this, date, label, text))
                    finishAndRemoveTask()
                },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recreate()
    }

    private fun configureLockScreenWindow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            (getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager)
                ?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD,
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    companion object {
        const val EXTRA_DATE = "date"
        const val EXTRA_LABEL = "label"
        const val EXTRA_TEXT = "text"

        fun intent(context: Context, date: String, label: String, text: String): Intent =
            Intent(context, AlarmActivity::class.java).apply {
                putExtra(EXTRA_DATE, date)
                putExtra(EXTRA_LABEL, label)
                putExtra(EXTRA_TEXT, text)
            }
    }
}

@Composable
private fun AlarmScreen(
    label: String,
    text: String,
    onDismiss: () -> Unit,
    onSnooze: () -> Unit,
) {
    val shift = remember(label) {
        ShiftType.entries.firstOrNull { it.label == label }
    }
    val accent = shift?.composeColor ?: Color(0xFF4FC3F7)
    val onAccent = shift?.composeOnColor ?: Color.Black

    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(1000)
        }
    }
    val timeText = now.format(DateTimeFormatter.ofPattern("HH:mm", Locale.KOREAN))
    val dateText = now.format(DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN))

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0E0E10))
            .padding(horizontal = 28.dp, vertical = 56.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = dateText,
                    color = Color(0xFFB0B0B0),
                    fontSize = 18.sp,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = timeText,
                    color = Color.White,
                    fontSize = 92.sp,
                    fontWeight = FontWeight.Light,
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        color = onAccent,
                        fontSize = 56.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    text = if (label == "N") "내일 N 근무 시작 알람" else "$label 근무 알람",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                if (text.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = text,
                        color = Color(0xFFCFCFCF),
                        fontSize = 16.sp,
                    )
                }
            }

            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clip(RoundedCornerShape(32.dp)),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accent,
                        contentColor = onAccent,
                    ),
                ) {
                    Text("정지", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = onSnooze,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .clip(RoundedCornerShape(28.dp)),
                    ) {
                        Text("5분 뒤 다시 울림", fontSize = 16.sp, color = Color.White)
                    }
                }
            }
        }
    }
}
