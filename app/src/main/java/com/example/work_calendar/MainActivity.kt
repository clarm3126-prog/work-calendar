package com.example.work_calendar

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.work_calendar.alarm.NotificationChannels
import com.example.work_calendar.ui.AlarmSettingsScreen
import com.example.work_calendar.ui.CalendarScreen
import com.example.work_calendar.ui.CalendarViewModel
import com.example.work_calendar.ui.theme.WorkcalendarTheme
import com.example.work_calendar.widget.WIDGET_EXTRA_OPEN_DATE
import java.time.LocalDate

class MainActivity : ComponentActivity() {

    private val viewModel: CalendarViewModel by viewModels { CalendarViewModel.Factory }

    private val pendingOpenDate = mutableStateOf<LocalDate?>(null)

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* implicit */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        NotificationChannels.ensure(this)
        requestNotificationPermissionIfNeeded()
        pendingOpenDate.value = readOpenDate(intent)
        setContent {
            WorkcalendarTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    var showSettings by remember { mutableStateOf(false) }
                    val pending = pendingOpenDate.value
                    LaunchedEffect(pending) {
                        if (pending != null) showSettings = false
                    }
                    if (showSettings) {
                        BackHandler { showSettings = false }
                        AlarmSettingsScreen(
                            viewModel = viewModel,
                            onBack = { showSettings = false },
                            modifier = Modifier.padding(innerPadding),
                        )
                    } else {
                        CalendarScreen(
                            viewModel = viewModel,
                            onOpenSettings = { showSettings = true },
                            pendingOpenDate = pending,
                            onConsumePendingOpenDate = { pendingOpenDate.value = null },
                            modifier = Modifier.padding(innerPadding),
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readOpenDate(intent)?.let { pendingOpenDate.value = it }
    }

    private fun readOpenDate(intent: Intent?): LocalDate? {
        val raw = intent?.getStringExtra(WIDGET_EXTRA_OPEN_DATE) ?: return null
        // 한 번 읽고 제거 — 회전(액티비티 재생성) 시 시트가 다시 열리는 것을 방지
        intent.removeExtra(WIDGET_EXTRA_OPEN_DATE)
        return runCatching { LocalDate.parse(raw) }.getOrNull()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
