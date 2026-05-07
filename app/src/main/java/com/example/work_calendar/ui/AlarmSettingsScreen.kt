package com.example.work_calendar.ui

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.work_calendar.data.ShiftAlarmDefault
import com.example.work_calendar.data.ShiftAlarmDefaults
import com.example.work_calendar.data.ShiftType
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmSettingsScreen(
    viewModel: CalendarViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("알림 설정") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Text("‹", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        )
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "근무 종류별 기본 알람 시각을 설정하면 모든 해당 근무일에 자동 적용됩니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "N 근무는 시작일 *전날* 이 시각에 알람이 울립니다.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF5E35B1),
            )
            HorizontalDivider()
            AlarmPermissionsCard()
            HorizontalDivider()
            ShiftAlarmDefaults.configurableTypes.forEach { type ->
                ShiftDefaultRow(
                    type = type,
                    value = state.defaults.get(type),
                    onChange = { viewModel.setShiftAlarmDefault(type, it) },
                )
            }
        }
    }
}

@Composable
private fun ShiftDefaultRow(
    type: ShiftType,
    value: ShiftAlarmDefault,
    onChange: (ShiftAlarmDefault) -> Unit,
) {
    val context = LocalContext.current
    val time = value.localTime()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(type.composeColor),
                contentAlignment = Alignment.Center,
            ) {
                Text(type.label, color = type.composeOnColor, fontWeight = FontWeight.Bold)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(type.displayName, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "근무 시간 ${type.timeRangeText()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = value.enabled,
                onCheckedChange = { enabled ->
                    onChange(value.copy(enabled = enabled && value.time != null))
                },
                enabled = value.time != null,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("알람 시각", modifier = Modifier.weight(1f))
            Button(onClick = {
                val initial = time ?: defaultPickerTime(type)
                TimePickerDialog(
                    context,
                    { _, hour, minute ->
                        val picked = LocalTime.of(hour, minute)
                        onChange(value.copy(time = picked.toString(), enabled = true))
                    },
                    initial.hour,
                    initial.minute,
                    false,
                ).show()
            }) {
                Text(time?.toString() ?: "시간 선택")
            }
            if (time != null) {
                OutlinedButton(onClick = { onChange(ShiftAlarmDefault()) }) { Text("지움") }
            }
        }
    }
}

@Composable
private fun AlarmPermissionsCard() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var refreshTick by remember { mutableStateOf(0) }
    DisposableEffectOnResume(lifecycleOwner) { refreshTick++ }

    val canExact = remember(refreshTick) { canScheduleExactAlarms(context) }
    val canFullScreen = remember(refreshTick) { canUseFullScreenIntent(context) }

    if (canExact && canFullScreen) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFFFF3E0))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "시계앱처럼 알람이 울리려면 권한이 필요해요",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF6D4C00),
        )
        if (!canExact) {
            Text(
                "• 정확한 알람: 정해진 시각에 정확히 울리도록 허용해 주세요.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF6D4C00),
            )
            Button(
                onClick = { openExactAlarmSettings(context) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("정확한 알람 권한 열기") }
        }
        if (!canFullScreen) {
            Spacer(Modifier.height(2.dp))
            Text(
                "• 잠금화면 알람: 잠금화면 위에 알람 화면을 띄우려면 허용해 주세요.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF6D4C00),
            )
            Button(
                onClick = { openFullScreenIntentSettings(context) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("잠금화면 알람 권한 열기") }
        }
    }
}

@Composable
private fun DisposableEffectOnResume(
    owner: androidx.lifecycle.LifecycleOwner,
    onResume: () -> Unit,
) {
    androidx.compose.runtime.DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) onResume()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}

private fun canScheduleExactAlarms(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
    val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    return am.canScheduleExactAlarms()
}

private fun canUseFullScreenIntent(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
    val nm = context.getSystemService(NotificationManager::class.java) ?: return true
    return nm.canUseFullScreenIntent()
}

private fun openExactAlarmSettings(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
        data = Uri.fromParts("package", context.packageName, null)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
    runCatching { context.startActivity(intent) }
}

private fun openFullScreenIntentSettings(context: Context) {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
    }
    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
    runCatching { context.startActivity(intent) }
}

private fun defaultPickerTime(type: ShiftType): LocalTime = when (type) {
    ShiftType.D -> LocalTime.of(7, 30)
    ShiftType.DA -> LocalTime.of(7, 30)
    ShiftType.A -> LocalTime.of(14, 0)
    ShiftType.N -> LocalTime.of(19, 0)
    ShiftType.W -> LocalTime.of(8, 0)
    ShiftType.OFF -> LocalTime.of(9, 0)
}
