package com.example.work_calendar

import android.Manifest
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

class MainActivity : ComponentActivity() {

    private val viewModel: CalendarViewModel by viewModels { CalendarViewModel.Factory }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* implicit */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        NotificationChannels.ensure(this)
        requestNotificationPermissionIfNeeded()
        setContent {
            WorkcalendarTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    var showSettings by remember { mutableStateOf(false) }
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
                            modifier = Modifier.padding(innerPadding),
                        )
                    }
                }
            }
        }
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
