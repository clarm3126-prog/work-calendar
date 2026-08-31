package com.example.work_calendar.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.work_calendar.MainActivity
import com.example.work_calendar.data.DayEntry
import com.example.work_calendar.data.HolidayProvider
import com.example.work_calendar.data.ShiftRepository
import com.example.work_calendar.data.ShiftSchedule
import com.example.work_calendar.data.ShiftType
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 위젯에서 표시 중인 월(年/月)은 Glance state(PreferencesGlanceStateDefinition)에
 * 저장한다. SharedPreferences를 직접 쓰던 이전 방식은 변경 시마다 세션을 통째로
 * 재시작(`updateAll`)해야 해서 클릭 후 반영까지 체감 0.5s+ 지연이 발생했다.
 * Glance state로 옮기면 같은 세션 안에서 recomposition만 일어나 IPC 외 오버헤드가
 * 거의 사라진다.
 *
 * 키는 둘 다 0(또는 부재)이면 "오늘 기준 월"로 해석한다 — '오늘' 버튼 누르면
 * 두 키를 제거해 이 의미를 다시 띤다.
 */
internal val ViewedYearKey = intPreferencesKey("viewed_year")
internal val ViewedMonthKey = intPreferencesKey("viewed_month")

internal fun monthFromPrefs(prefs: Preferences): YearMonth {
    val y = prefs[ViewedYearKey] ?: 0
    val m = prefs[ViewedMonthKey] ?: 0
    return if (y > 0 && m in 1..12) YearMonth.of(y, m) else YearMonth.now()
}

internal fun MutablePreferences.setViewedMonth(ym: YearMonth) {
    this[ViewedYearKey] = ym.year
    this[ViewedMonthKey] = ym.monthValue
}

internal fun MutablePreferences.clearViewedMonth() {
    remove(ViewedYearKey)
    remove(ViewedMonthKey)
}

class WorkCalendarWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WorkCalendarWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetMidnightRefresher.scheduleNext(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetMidnightRefresher.cancel(context)
    }
}

class PrevMonthAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        updateAppWidgetState(context, glanceId) { prefs ->
            prefs.setViewedMonth(monthFromPrefs(prefs).minusMonths(1))
        }
    }
}

class NextMonthAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        updateAppWidgetState(context, glanceId) { prefs ->
            prefs.setViewedMonth(monthFromPrefs(prefs).plusMonths(1))
        }
    }
}

class TodayMonthAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        updateAppWidgetState(context, glanceId) { prefs ->
            prefs.clearViewedMonth()
        }
    }
}

/** 이번 달이 아닌 날짜(앞뒤로 붙는 이웃 달)의 불투명도 */
private const val OTHER_MONTH_ALPHA = 0.40f

/** [inMonth]가 false면 알파를 낮춰 반투명 색을 돌려준다. */
private fun Color.dim(inMonth: Boolean): Color =
    if (inMonth) this else copy(alpha = alpha * OTHER_MONTH_ALPHA)

class WorkCalendarWidget : GlanceAppWidget() {

    // 월(year/month)을 Glance state(Preferences)로 관리. action callback에서
    // updateAppWidgetState로 쓰면 같은 세션 안에서 currentState가 새 값으로
    // recompose되어 세션 재시작 없이 RemoteViews만 갱신된다.
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // 메모/근무 변경(앱쪽 DataStore)은 entriesFlow를 collectAsState로 구독해 반응.
        // first()로 초기값을 잡아두면 첫 컴포지션에서 emptyMap 깜빡임이 없다.
        val repo = ShiftRepository(context)
        val initialEntries = repo.entriesFlow.first()

        provideContent {
            val entries by repo.entriesFlow.collectAsState(initial = initialEntries)
            val prefs = currentState<Preferences>()
            val month = monthFromPrefs(prefs)
            GlanceTheme {
                WidgetContent(month = month, entries = entries)
            }
        }
    }

    @Composable
    private fun WidgetContent(month: YearMonth, entries: Map<String, DayEntry>) {
        val today = LocalDate.now()
        // 외곽 Column에는 clickable을 두지 않는다.
        // 화살표/오늘 버튼이 자식 클릭이고, 부모 clickable이 있으면 Glance/RemoteViews에서
        // 자식 클릭을 부모가 가로채 앱이 열리는 현상이 발생할 수 있다.
        // 셀 영역의 앱 열기는 WidgetDayCell에서 직접 부착한다.
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(Color(0xFF000000))
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            Header(month = month, today = today)
            WeekHeader()
            // defaultWeight()는 ColumnScope 확장이라 호출부(= 이 Column 안)에서만 쓸 수 있다.
            MonthGrid(
                month = month,
                entries = entries,
                today = today,
                modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
            )
        }
    }

    @Composable
    private fun Header(month: YearMonth, today: LocalDate) {
        val monthText = month.format(DateTimeFormatter.ofPattern("yyyy년 M월", Locale.KOREAN))
        val todayText = today.format(DateTimeFormatter.ofPattern("M.d (E)", Locale.KOREAN))
        Row(
            modifier = GlanceModifier.fillMaxWidth().padding(bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 좌측: '오늘' 버튼 → 현재 월로 점프
            Box(
                modifier = GlanceModifier.defaultWeight(),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = "오늘",
                    modifier = GlanceModifier
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                        .clickable(actionRunCallback<TodayMonthAction>()),
                    style = TextStyle(
                        fontSize = 14.sp,
                        color = ColorProvider(Color(0xFF42A5F5)),
                        fontWeight = FontWeight.Medium,
                    ),
                )
            }
            // 중앙: ‹  YYYY년 M월  ›  — 폰트와 터치 영역을 키워서 누르기 쉽게
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = GlanceModifier
                        .padding(horizontal = 14.dp, vertical = 4.dp)
                        .clickable(actionRunCallback<PrevMonthAction>()),
                ) {
                    Text(
                        text = "‹",
                        style = TextStyle(
                            fontSize = 28.sp,
                            color = ColorProvider(Color(0xFFEFEFEF)),
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                }
                Text(
                    text = monthText,
                    style = TextStyle(
                        fontSize = 19.sp,
                        color = ColorProvider(Color(0xFFEFEFEF)),
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Box(
                    modifier = GlanceModifier
                        .padding(horizontal = 14.dp, vertical = 4.dp)
                        .clickable(actionRunCallback<NextMonthAction>()),
                ) {
                    Text(
                        text = "›",
                        style = TextStyle(
                            fontSize = 28.sp,
                            color = ColorProvider(Color(0xFFEFEFEF)),
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                }
            }
            // 우측: 오늘 날짜 (정보용)
            Box(
                modifier = GlanceModifier.defaultWeight(),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Text(
                    text = todayText,
                    style = TextStyle(
                        fontSize = 12.sp,
                        color = ColorProvider(Color(0xFFBDBDBD)),
                        fontWeight = FontWeight.Medium,
                    ),
                )
            }
        }
    }

    @Composable
    private fun WeekHeader() {
        val labels = listOf("일", "월", "화", "수", "목", "금", "토")
        Row(modifier = GlanceModifier.fillMaxWidth().padding(bottom = 2.dp)) {
            labels.forEachIndexed { index, label ->
                val color = when (index) {
                    0 -> ColorProvider(Color(0xFFEF5350))
                    6 -> ColorProvider(Color(0xFF42A5F5))
                    else -> ColorProvider(Color(0xFFBDBDBD))
                }
                Box(
                    modifier = GlanceModifier.defaultWeight(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = TextStyle(fontSize = 11.sp, color = color, fontWeight = FontWeight.Medium),
                    )
                }
            }
        }
    }

    @Composable
    private fun MonthGrid(
        month: YearMonth,
        entries: Map<String, DayEntry>,
        today: LocalDate,
        modifier: GlanceModifier,
    ) {
        val firstOfMonth = month.atDay(1)
        val leadingEmpty = firstOfMonth.dayOfWeek.value % 7
        val daysInMonth = month.lengthOfMonth()
        val totalCells = ((leadingEmpty + daysInMonth + 6) / 7) * 7
        val rows = totalCells / 7
        // 1일이 금/토인 달(예: 2026-08)은 6주 = 42칸이 된다. 셀 내용이 5주 기준
        // 고정 크기(26dp 배지 + 여백)라 6주일 때 마지막 주가 위젯 밖으로 밀려 잘렸다.
        // 6주인 달만 한 단계 조밀하게 그려 마지막 주(8/31 등)까지 들어오게 한다.
        val compact = rows >= 6

        // 주(週) 사이에만 가로 구분선을 그어 행을 시각적으로 분리한다 (세로선 없음)
        // MonthGrid 전체에 단일 clickable을 부여 — 셀 단위로 두면 PendingIntent가 30~40개 생겨 위젯 갱신이 느려진다
        val gridLineColor = Color(0xFF333333)
        // fillMaxSize()는 match_parent로 번역돼 헤더가 차지한 높이까지 요구하게 된다.
        // defaultWeight()로 "남은 높이만" 가져가야 마지막 주가 아래로 잘려나가지 않는다.
        Column(
            modifier = modifier.clickable(actionStartActivity<MainActivity>()),
        ) {
            for (row in 0 until rows) {
                if (row > 0) {
                    Spacer(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(gridLineColor),
                    )
                }
                Row(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .defaultWeight(),
                ) {
                    for (col in 0 until 7) {
                        val cell = row * 7 + col
                        // 앞/뒤 빈칸도 이웃 달의 실제 날짜로 채운다 — 반투명으로 구분.
                        val date = firstOfMonth.plusDays((cell - leadingEmpty).toLong())
                        val inMonth = YearMonth.from(date) == month
                        val entry = entries[date.toString()]
                        val shift = entry?.overrideShift()
                            ?: ShiftSchedule.cycleShift(date)
                        val memo = entry?.memo?.takeIf { it.isNotBlank() }
                        Box(
                            modifier = GlanceModifier.defaultWeight(),
                            contentAlignment = Alignment.Center,
                        ) {
                            WidgetDayCell(
                                date = date,
                                shift = shift,
                                isToday = inMonth && date == today,
                                memo = memo,
                                inMonth = inMonth,
                                compact = compact,
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun WidgetDayCell(
        date: LocalDate,
        shift: ShiftType,
        isToday: Boolean,
        memo: String?,
        inMonth: Boolean,
        compact: Boolean,
    ) {
        val holidayName = HolidayProvider.nameOf(date)
        val baseNumColor = when {
            holidayName != null || date.dayOfWeek == DayOfWeek.SUNDAY -> Color(0xFFEF5350)
            date.dayOfWeek == DayOfWeek.SATURDAY -> Color(0xFF42A5F5)
            else -> Color(0xFFEFEFEF)
        }
        // Glance에는 Modifier.alpha가 없어 색 자체의 알파를 낮춰 반투명을 만든다.
        val numColor = baseNumColor.dim(inMonth)
        val isOff = shift == ShiftType.OFF

        // 6주짜리 달에서만 한 단계 조밀하게 — 5주 달은 기존 크기를 그대로 유지한다.
        val slot = if (compact) 22.dp else 26.dp
        val slotRadius = if (compact) 11.dp else 13.dp
        val numSize = if (compact) 11.sp else 12.sp
        val labelSize = if (compact) 8.sp else 9.sp
        val cellPadding = if (compact) 2.dp else 4.dp

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .cornerRadius(8.dp)
                .background(if (isToday) Color(0x3342A5F5) else Color.Transparent)
                .padding(horizontal = 2.dp, vertical = cellPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                style = TextStyle(
                    fontSize = numSize,
                    color = ColorProvider(numColor),
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                ),
            )
            // 휴(텍스트)와 근무 원형의 높이가 달라 셀마다 메모 시작 y가 어긋난다.
            // 슬롯을 고정 크기로 통일해 메모/공휴일 라벨이 항상 같은 위치에 오도록 함.
            Box(
                modifier = GlanceModifier
                    .height(slot)
                    .width(slot),
                contentAlignment = Alignment.Center,
            ) {
                if (isOff) {
                    Text(
                        text = "휴",
                        style = TextStyle(
                            fontSize = if (compact) 12.sp else 13.sp,
                            color = ColorProvider(shift.composeColor.dim(inMonth)),
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                        ),
                    )
                } else {
                    Box(
                        modifier = GlanceModifier
                            .width(slot)
                            .height(slot)
                            .cornerRadius(slotRadius)
                            .background(shift.composeColor.dim(inMonth)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = shift.label,
                            style = TextStyle(
                                fontSize = when {
                                    shift.label.length > 1 && compact -> 10.sp
                                    shift.label.length > 1 -> 11.sp
                                    compact -> 12.sp
                                    else -> 13.sp
                                },
                                color = ColorProvider(shift.composeOnColor.dim(inMonth)),
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                            ),
                        )
                    }
                }
            }
            // 셀 자연 배치 — Spacer로 하단에 밀어내지 않아 메모가 중앙쯤에 옴.
            // 공휴일과 메모 둘 다 있으면 공휴일 라벨 위, 메모 아래로 스택.
            if (holidayName != null) {
                Text(
                    text = holidayName,
                    style = TextStyle(
                        fontSize = labelSize,
                        color = ColorProvider(Color(0xFFEF5350).dim(inMonth)),
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                    ),
                    maxLines = 1,
                )
            }
            if (memo != null) {
                WidgetMemoChip(memo = memo, inMonth = inMonth, fontSize = labelSize)
            }
        }
    }

    @Composable
    private fun WidgetMemoChip(memo: String, inMonth: Boolean, fontSize: TextUnit) {
        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .cornerRadius(3.dp)
                // 이번 달이 아니면 흰 칩을 반투명으로 — 검은 배경 위에서 회색 칩이 된다.
                .background(if (inMonth) Color.White else Color(0x8AFFFFFF))
                .padding(horizontal = 2.dp, vertical = 1.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = memo,
                style = TextStyle(
                    fontSize = fontSize,
                    color = ColorProvider(Color(0xFF000000)),
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                ),
                maxLines = 2,
            )
        }
    }
}
