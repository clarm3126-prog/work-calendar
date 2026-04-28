package com.example.work_calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.work_calendar.data.DayEntry
import com.example.work_calendar.data.ShiftType
import kotlinx.coroutines.delay
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private const val PAGER_BASE_INDEX = 600   // ~50년 양방향 = 1200개 페이지
private const val PAGER_TOTAL_PAGES = 1200

private fun pageToMonth(base: YearMonth, page: Int): YearMonth =
    base.plusMonths((page - PAGER_BASE_INDEX).toLong())

private fun monthsBetween(from: YearMonth, to: YearMonth): Int =
    ChronoUnit.MONTHS.between(from, to).toInt()

private val MonthFormatter = DateTimeFormatter.ofPattern("yyyy년 M월", Locale.KOREAN)
private val TodayDateFormatter = DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN)
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

    val baseMonth = remember { YearMonth.now() }
    val pagerState = rememberPagerState(
        initialPage = PAGER_BASE_INDEX + monthsBetween(baseMonth, state.month),
        pageCount = { PAGER_TOTAL_PAGES },
    )

    // 사용자가 스와이프해 페이지가 정착하면 ViewModel에 반영
    LaunchedEffect(pagerState.settledPage) {
        val swipedMonth = pageToMonth(baseMonth, pagerState.settledPage)
        if (swipedMonth != state.month) viewModel.showMonth(swipedMonth)
    }

    // 외부(화살표/오늘 버튼)에서 month가 바뀌면 페이저를 그쪽으로 애니메이트
    LaunchedEffect(state.month) {
        val targetPage = PAGER_BASE_INDEX + monthsBetween(baseMonth, state.month)
        if (targetPage != pagerState.currentPage) {
            pagerState.animateScrollToPage(targetPage)
        }
    }

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
        TodayShiftCard(
            today = LocalDate.now(),
            shift = viewModel.resolvedShift(LocalDate.now(), state.entries),
            onClick = { selectedDate = LocalDate.now() },
        )
        WeekHeaderRow()
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            val pageMonth = pageToMonth(baseMonth, page)
            MonthGrid(
                month = pageMonth,
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
                    fontWeight = FontWeight.Bold,
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
private fun TodayShiftCard(today: LocalDate, shift: ShiftType, onClick: () -> Unit) {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = LocalDateTime.now()
        }
    }
    val countdown = remember(now, shift) { computeShiftStatus(today, shift, now) }
    val tint = shift.composeColor.copy(alpha = 0.14f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(tint)
            .clickable { onClick() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(shift.composeColor),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = shift.label,
                color = shift.composeOnColor,
                fontWeight = FontWeight.Bold,
                fontSize = if (shift.label.length > 1) 20.sp else 26.sp,
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "오늘 · ${today.format(TodayDateFormatter)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = shift.displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = if (shift == ShiftType.OFF) "오늘은 휴무입니다." else shift.timeRangeText(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (countdown.isNotEmpty()) {
                Text(
                    text = countdown,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = shift.composeColor,
                )
            }
        }
    }
}

private fun computeShiftStatus(today: LocalDate, shift: ShiftType, now: LocalDateTime): String {
    if (shift.start == null || shift.end == null) return ""
    val start = LocalDateTime.of(today, shift.start)
    val end = if (shift.endsNextDay) {
        LocalDateTime.of(today.plusDays(1), shift.end)
    } else {
        LocalDateTime.of(today, shift.end)
    }
    return when {
        now.isBefore(start) -> "시작까지 ${formatDuration(Duration.between(now, start))}"
        now.isAfter(end) -> "오늘 근무 종료"
        else -> "근무 중 · ${formatDuration(Duration.between(now, end))} 후 종료"
    }
}

private fun formatDuration(d: Duration): String {
    val total = d.toMinutes().coerceAtLeast(0)
    val hours = total / 60
    val minutes = total % 60
    return when {
        hours > 0 && minutes > 0 -> "${hours}시간 ${minutes}분"
        hours > 0 -> "${hours}시간"
        else -> "${minutes}분"
    }
}

@Composable
private fun WeekHeaderRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
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

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
        var cell = 0
        while (cell < totalCells) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (col in 0 until 7) {
                    val dayNumber = cell - leadingEmpty + 1
                    val date = if (dayNumber in 1..daysInMonth) month.atDay(dayNumber) else null
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(0.7f)
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
    val cellShape = RoundedCornerShape(8.dp)
    val isOff = shift == ShiftType.OFF
    val highlightMod = if (isToday) {
        Modifier
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), cellShape)
            .border(1.5.dp, MaterialTheme.colorScheme.primary, cellShape)
    } else Modifier

    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(cellShape)
            .then(highlightMod)
            .clickable { onClick() }
            .padding(top = 4.dp, bottom = 3.dp, start = 1.dp, end = 1.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            color = dayColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
        if (isOff) {
            Text(
                text = "휴",
                color = shift.composeColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
        } else {
            ShiftBadge(shift = shift)
        }
        val memo = entry?.memo?.takeIf { it.isNotBlank() }
        if (memo != null) {
            MemoChip(memo)
        }
        if (hasAlarm) {
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFB8C00)),
            )
        }
    }
}

@Composable
private fun ShiftBadge(shift: ShiftType) {
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(shift.composeColor),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = shift.label,
            color = shift.composeOnColor,
            fontWeight = FontWeight.Bold,
            fontSize = if (shift.label.length > 1) 11.sp else 13.sp,
        )
    }
}

@Composable
private fun MemoChip(memo: String) {
    Text(
        text = memo,
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(horizontal = 3.dp, vertical = 1.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 9.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
