package com.example.work_calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.example.work_calendar.data.DayEntry
import com.example.work_calendar.data.HolidayProvider
import com.example.work_calendar.data.ShiftAlarmDefaults
import com.example.work_calendar.data.ShiftSchedule
import com.example.work_calendar.data.ShiftType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DateFormatter = DateTimeFormatter.ofPattern("yyyy년 M월 d일 (E)", Locale.KOREAN)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayDetailSheet(
    date: LocalDate,
    shift: ShiftType,
    entry: DayEntry,
    defaults: ShiftAlarmDefaults,
    onDismiss: () -> Unit,
    onOverrideChange: (ShiftType?) -> Unit,
    onConvertAaToDa: () -> Unit,
    onMemoChange: (String) -> Unit,
    onAlarmDisabledChange: (Boolean) -> Unit,
    onOpenAlarmSettings: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var memoDraft by remember(date, entry.memo) { mutableStateOf(entry.memo) }
    LaunchedEffect(memoDraft) {
        if (memoDraft != entry.memo) onMemoChange(memoDraft)
    }

    val cycleShift = ShiftSchedule.cycleShift(date)
    val isOverridden = entry.overrideShift() != null
    val cycleIndex = ShiftSchedule.cycleIndex(date)
    val canConvertAaToDa = cycleShift == ShiftType.A && (cycleIndex == 0 || cycleIndex == 1)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            HeaderSection(date = date, shift = shift, isOverridden = isOverridden, cycleShift = cycleShift)

            HorizontalDivider()

            SectionTitle("근무 변경")
            if (canConvertAaToDa) {
                Button(
                    onClick = onConvertAaToDa,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("이번 AA 짝을 DA로 (첫날 D + 둘째날 A)")
                }
            }
            ShiftPicker(
                current = shift,
                cycleShift = cycleShift,
                onSelect = { selected ->
                    if (selected == cycleShift) onOverrideChange(null)
                    else onOverrideChange(selected)
                },
                onReset = { onOverrideChange(null) },
                isOverridden = isOverridden,
            )

            HorizontalDivider()

            SectionTitle("메모")
            OutlinedTextField(
                value = memoDraft,
                onValueChange = { memoDraft = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("메모를 입력하세요") },
                minLines = 2,
                maxLines = 6,
            )

            HorizontalDivider()

            SectionTitle("알림")
            AlarmStatusSection(
                shift = shift,
                date = date,
                defaults = defaults,
                disabledForThisDay = entry.alarmDisabled,
                onAlarmDisabledChange = onAlarmDisabledChange,
                onOpenAlarmSettings = onOpenAlarmSettings,
            )
        }
    }
}

@Composable
private fun HeaderSection(date: LocalDate, shift: ShiftType, isOverridden: Boolean, cycleShift: ShiftType) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(shift.composeColor),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = shift.label,
                color = shift.composeOnColor,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge,
            )
        }
        Column {
            Text(text = date.format(DateFormatter), style = MaterialTheme.typography.titleMedium)
            HolidayProvider.nameOf(date)?.let { name ->
                Text(
                    text = name,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFEF5350),
                )
            }
            Text(
                text = "${shift.displayName} · ${shift.timeRangeText()}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (isOverridden) {
                Text(
                    text = "원래 사이클: ${cycleShift.displayName}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShiftPicker(
    current: ShiftType,
    cycleShift: ShiftType,
    onSelect: (ShiftType) -> Unit,
    onReset: () -> Unit,
    isOverridden: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            maxItemsInEachRow = 3,
        ) {
            ShiftType.values().forEach { type ->
                ShiftChip(
                    type = type,
                    selected = type == current,
                    isCycleDefault = type == cycleShift,
                    onClick = { onSelect(type) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (isOverridden) {
            OutlinedButton(onClick = onReset, modifier = Modifier.fillMaxWidth()) {
                Text("사이클 기본값(${cycleShift.label})으로 되돌리기")
            }
        }
    }
}

@Composable
private fun ShiftChip(
    type: ShiftType,
    selected: Boolean,
    isCycleDefault: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg = if (selected) type.composeColor else type.composeColor.copy(alpha = 0.2f)
    val fg = if (selected) type.composeOnColor else MaterialTheme.colorScheme.onSurface
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = type.label, color = fg, fontWeight = FontWeight.Bold)
        if (isCycleDefault) {
            Text(text = "기본", color = fg.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun AlarmStatusSection(
    shift: ShiftType,
    date: LocalDate,
    defaults: ShiftAlarmDefaults,
    disabledForThisDay: Boolean,
    onAlarmDisabledChange: (Boolean) -> Unit,
    onOpenAlarmSettings: () -> Unit,
) {
    val def = defaults.get(shift)
    val time = def.localTime()
    val isNight = shift == ShiftType.N
    val fireDate = if (isNight) date.minusDays(1) else date

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when {
            shift == ShiftType.OFF -> {
                InfoBanner(color = Color(0xFF757575), text = "휴무일에는 알림이 울리지 않습니다.")
            }
            !def.enabled || time == null -> {
                InfoBanner(
                    color = Color(0xFFEF6C00),
                    text = "${shift.label} 근무 기본 알람이 설정되어 있지 않습니다.",
                )
                Button(onClick = onOpenAlarmSettings, modifier = Modifier.fillMaxWidth()) {
                    Text("알림 설정 열기")
                }
            }
            else -> {
                val description = if (isNight) {
                    "전날(${fireDate.format(DateFormatter)}) ${time}에 울림"
                } else {
                    "이 날 ${time}에 울림"
                }
                InfoBanner(color = shift.composeColor, text = description)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("이 날만 알림 끄기", modifier = Modifier.weight(1f))
                    Switch(checked = disabledForThisDay, onCheckedChange = onAlarmDisabledChange)
                }
                OutlinedButton(onClick = onOpenAlarmSettings, modifier = Modifier.fillMaxWidth()) {
                    Text("기본 알람 시각 변경")
                }
            }
        }
    }
}

@Composable
private fun InfoBanner(color: Color, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(
            text = text,
            modifier = Modifier.padding(start = 8.dp),
            color = color,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
