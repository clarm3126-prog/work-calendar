package com.example.work_calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.work_calendar.data.DayEntry
import com.example.work_calendar.data.ShiftType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val MonthFormatter = DateTimeFormatter.ofPattern("yyyy년 M월", Locale.KOREAN)
private val WeekHeaders = listOf("일", "월", "화", "수", "목", "금", "토")

@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    val updateState by viewModel.updateState.collectAsState()
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }

    Column(modifier = modifier.fillMaxSize()) {
        CalendarTopBar(
            month = state.month,
            onPrev = { viewModel.showMonth(state.month.minusMonths(1)) },
            onNext = { viewModel.showMonth(state.month.plusMonths(1)) },
            onToday = {
                viewModel.showMonth(YearMonth.now())
                selectedDate = LocalDate.now()
            },
            onOpenSettings = onOpenSettings,
        )
        UpdateBanner(
            state = updateState,
            onUpdate = { viewModel.startUpdate() },
            onDismiss = { viewModel.dismissUpdate() },
        )
        WeekHeaderRow()
        MonthGrid(
            month = state.month,
            entries = state.entries,
            resolveShift = { date -> viewModel.resolvedShift(date, state.entries) },
            hasActiveAlarm = { date, shift ->
                val entry = state.entries[date.toString()]
                if (entry?.alarmDisabled == true) false
                else state.defaults.get(shift).enabled && state.defaults.get(shift).time != null
            },
            onClick = { selectedDate = it },
        )
    }

    selectedDate?.let { date ->
        val entry = state.entries[date.toString()] ?: DayEntry()
        val shift = viewModel.resolvedShift(date, state.entries)
        DayDetailSheet(
            date = date,
            shift = shift,
            entry = entry,
            defaults = state.defaults,
            onDismiss = { selectedDate = null },
            onOverrideChange = { viewModel.setOverride(date, it) },
            onConvertAaToDa = { viewModel.convertAaPairToDa(date) },
            onMemoChange = { viewModel.setMemo(date, it) },
            onAlarmDisabledChange = { disabled -> viewModel.setAlarmDisabled(date, disabled) },
            onOpenAlarmSettings = onOpenSettings,
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun CalendarTopBar(
    month: YearMonth,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = month.format(MonthFormatter),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = "오늘",
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onToday() }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onPrev) {
                Text("‹", fontSize = 26.sp, fontWeight = FontWeight.Bold)
            }
        },
        actions = {
            IconButton(onClick = onNext) {
                Text("›", fontSize = 26.sp, fontWeight = FontWeight.Bold)
            }
            IconButton(onClick = onOpenSettings) {
                Text("⚙", fontSize = 20.sp)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    )
}

@Composable
private fun WeekHeaderRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
    ) {
        WeekHeaders.forEachIndexed { index, label ->
            val color = when (index) {
                0 -> Color(0xFFD32F2F)
                6 -> Color(0xFF1976D2)
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                color = color,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    entries: Map<String, DayEntry>,
    resolveShift: (LocalDate) -> ShiftType,
    hasActiveAlarm: (LocalDate, ShiftType) -> Boolean,
    onClick: (LocalDate) -> Unit,
) {
    val firstOfMonth = month.atDay(1)
    val leadingEmpty = firstOfMonth.dayOfWeek.value % 7 // Sunday = 0
    val daysInMonth = month.lengthOfMonth()
    val totalCells = ((leadingEmpty + daysInMonth + 6) / 7) * 7
    val today = LocalDate.now()

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        var cell = 0
        while (cell < totalCells) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (col in 0 until 7) {
                    val dayNumber = cell - leadingEmpty + 1
                    val date = if (dayNumber in 1..daysInMonth) month.atDay(dayNumber) else null
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(0.85f)
                            .padding(2.dp),
                    ) {
                        if (date != null) {
                            val shift = resolveShift(date)
                            DayCell(
                                date = date,
                                shift = shift,
                                entry = entries[date.toString()],
                                hasAlarm = hasActiveAlarm(date, shift),
                                isToday = date == today,
                                onClick = { onClick(date) },
                            )
                        }
                    }
                    cell++
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    shift: ShiftType,
    entry: DayEntry?,
    hasAlarm: Boolean,
    isToday: Boolean,
    onClick: () -> Unit,
) {
    val dayColor = when (date.dayOfWeek) {
        DayOfWeek.SUNDAY -> Color(0xFFD32F2F)
        DayOfWeek.SATURDAY -> Color(0xFF1976D2)
        else -> MaterialTheme.colorScheme.onSurface
    }
    val borderShape = RoundedCornerShape(10.dp)
    val highlight = if (isToday) {
        Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), borderShape)
    } else Modifier
    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(borderShape)
            .then(highlight)
            .clickable { onClick() }
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            color = dayColor,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
            fontSize = 13.sp,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(shift.composeColor)
                .padding(vertical = 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = shift.label,
                color = shift.composeOnColor,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            if (entry?.memo?.isNotBlank() == true) {
                Dot(color = Color(0xFF8E24AA))
            }
            if (hasAlarm) {
                Dot(color = Color(0xFFFB8C00))
            }
            Spacer(modifier = Modifier.size(0.dp))
        }
    }
}

@Composable
private fun Dot(color: Color) {
    Box(
        modifier = Modifier
            .size(5.dp)
            .clip(CircleShape)
            .background(color),
    )
}
