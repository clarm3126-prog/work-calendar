package com.example.work_calendar.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
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

private fun defaultPickerTime(type: ShiftType): LocalTime = when (type) {
    ShiftType.D -> LocalTime.of(7, 30)
    ShiftType.DA -> LocalTime.of(7, 30)
    ShiftType.A -> LocalTime.of(14, 0)
    ShiftType.N -> LocalTime.of(19, 0)
    ShiftType.W -> LocalTime.of(8, 0)
    ShiftType.OFF -> LocalTime.of(9, 0)
}
