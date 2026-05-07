package com.example.work_calendar.ui

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
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
    val canIgnoreBattery = remember(refreshTick) { isIgnoringBatteryOptimizations(context) }

    val allGranted = canExact && canFullScreen && canIgnoreBattery
    val manufacturerHint = remember { manufacturerSpecificHint() }

    val bg = if (allGranted) Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
    val fg = if (allGranted) Color(0xFF2E7D32) else Color(0xFF6D4C00)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            if (allGranted) "알람 권한 모두 허용됨" else "근무 알람을 놓치지 않으려면 권한이 필요해요",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = fg,
        )
        if (!canExact) {
            Text(
                "• 정확한 알람: 정해진 시각에 정확히 울리도록 허용해 주세요.",
                style = MaterialTheme.typography.bodySmall,
                color = fg,
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
                color = fg,
            )
            Button(
                onClick = { openFullScreenIntentSettings(context) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("잠금화면 알람 권한 열기") }
        }
        if (!canIgnoreBattery) {
            Spacer(Modifier.height(2.dp))
            Text(
                "• 배터리 최적화 제외: 폰이 절전 모드에 들어가도 알람이 울리도록 해주세요. " +
                    "이게 없으면 알람이 누락되거나 늦게 울릴 수 있어요.",
                style = MaterialTheme.typography.bodySmall,
                color = fg,
            )
            Button(
                onClick = { requestIgnoreBatteryOptimizations(context) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("배터리 최적화 제외 허용") }
        }
        if (manufacturerHint != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                manufacturerHint,
                style = MaterialTheme.typography.bodySmall,
                color = fg,
            )
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

private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

@SuppressLint("BatteryLife")
private fun requestIgnoreBatteryOptimizations(context: Context) {
    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
        data = Uri.parse("package:${context.packageName}")
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
    val handled = runCatching { context.startActivity(intent) }.isSuccess
    if (!handled) {
        // 일부 ROM 은 위 인텐트를 막아둠. 배터리 최적화 목록 화면으로 폴백.
        val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        runCatching { context.startActivity(fallback) }
    }
}

/**
 * 표준 안드로이드 배터리 최적화 외에 제조사가 별도로 운영하는 절전 정책 안내문.
 * 자동 처리 불가능한 영역이므로 사용자에게 직접 설정하도록 알려준다.
 */
private fun manufacturerSpecificHint(): String? {
    val m = Build.MANUFACTURER.lowercase()
    return when {
        m.contains("samsung") ->
            "삼성 단말은 추가로 [설정 → 디바이스 케어 → 배터리 → 백그라운드 사용 제한] " +
                "에서 이 앱을 '잠자기/딥 슬립' 목록에서 제외해야 안전합니다."
        m.contains("xiaomi") || m.contains("redmi") || m.contains("poco") ->
            "샤오미/MIUI 단말은 [설정 → 앱 → 권한 → 자동 시작]을 켜고, " +
                "[배터리 → 앱 배터리 절약 → 제한 없음]으로 설정하세요."
        m.contains("huawei") || m.contains("honor") ->
            "화웨이/Honor 단말은 [설정 → 배터리 → 앱 시작 관리]에서 이 앱을 '수동 관리'로 두고 " +
                "자동 시작/보조 시작/백그라운드 실행을 모두 켜세요."
        m.contains("oppo") || m.contains("realme") || m.contains("oneplus") ->
            "오포/리얼미/원플러스(ColorOS) 단말은 [설정 → 배터리 → 앱 배터리 관리]에서 " +
                "이 앱의 백그라운드 실행을 허용하세요."
        m.contains("vivo") ->
            "비보(FuntouchOS) 단말은 [설정 → 배터리 → 백그라운드 전력 소모 관리]에서 " +
                "이 앱을 허용으로 두세요."
        else -> null
    }
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
