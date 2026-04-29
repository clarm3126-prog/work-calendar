package com.example.work_calendar.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import androidx.glance.appwidget.updateAll
import androidx.glance.background
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
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
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

private const val WIDGET_PREFS = "work_calendar_widget"
private const val KEY_VIEWED_YEAR = "viewed_year"
private const val KEY_VIEWED_MONTH = "viewed_month"
private const val KEY_ENTRIES_REVISION = "entries_revision"

/**
 * 위젯 receiver 프로세스에 entries를 캐싱.
 * 월 이동 같은 단순 갱신은 DataStore를 다시 읽지 않고 캐시를 그대로 쓴다.
 * 메모/오버라이드가 변경되면 WorkCalendarWidgetUpdater가 revision을 올려 다음 갱신에서 재로드.
 */
internal object WidgetEntriesCache {
    @Volatile private var cached: Map<String, DayEntry>? = null
    @Volatile private var cachedRevision: Int = -1

    suspend fun getOrLoad(context: Context): Map<String, DayEntry> {
        val current = readRevision(context)
        cached?.let { if (cachedRevision == current) return it }
        val fresh = ShiftRepository(context).entriesFlow.first()
        cached = fresh
        cachedRevision = current
        return fresh
    }

    fun bumpRevision(context: Context) {
        val prefs = context.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE)
        prefs.edit()
            .putInt(KEY_ENTRIES_REVISION, prefs.getInt(KEY_ENTRIES_REVISION, 0) + 1)
            .apply()
    }

    private fun readRevision(context: Context): Int =
        context.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_ENTRIES_REVISION, 0)
}

private fun Context.viewedMonth(): YearMonth {
    val prefs = getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE)
    val year = prefs.getInt(KEY_VIEWED_YEAR, 0)
    val month = prefs.getInt(KEY_VIEWED_MONTH, 0)
    return if (year > 0 && month in 1..12) YearMonth.of(year, month) else YearMonth.now()
}

private fun Context.setViewedMonth(yearMonth: YearMonth) {
    getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE).edit()
        .putInt(KEY_VIEWED_YEAR, yearMonth.year)
        .putInt(KEY_VIEWED_MONTH, yearMonth.monthValue)
        .apply()
}

private fun Context.clearViewedMonth() {
    getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE).edit()
        .remove(KEY_VIEWED_YEAR)
        .remove(KEY_VIEWED_MONTH)
        .apply()
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
        context.setViewedMonth(context.viewedMonth().minusMonths(1))
        WorkCalendarWidget().updateAll(context)
    }
}

class NextMonthAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        context.setViewedMonth(context.viewedMonth().plusMonths(1))
        WorkCalendarWidget().updateAll(context)
    }
}

class TodayMonthAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        context.clearViewedMonth()
        WorkCalendarWidget().updateAll(context)
    }
}

class WorkCalendarWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entries = WidgetEntriesCache.getOrLoad(context)
        val month = context.viewedMonth()

        provideContent {
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
                .padding(horizontal = 8.dp, vertical = 10.dp),
        ) {
            Header(month = month, today = today)
            WeekHeader()
            MonthGrid(month = month, entries = entries, today = today)
        }
    }

    @Composable
    private fun Header(month: YearMonth, today: LocalDate) {
        val monthText = month.format(DateTimeFormatter.ofPattern("yyyy년 M월", Locale.KOREAN))
        val todayText = today.format(DateTimeFormatter.ofPattern("M.d (E)", Locale.KOREAN))
        Row(
            modifier = GlanceModifier.fillMaxWidth().padding(bottom = 8.dp),
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
                        .padding(horizontal = 14.dp, vertical = 6.dp)
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
                        .padding(horizontal = 14.dp, vertical = 6.dp)
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
        Row(modifier = GlanceModifier.fillMaxWidth().padding(bottom = 4.dp)) {
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
    private fun MonthGrid(month: YearMonth, entries: Map<String, DayEntry>, today: LocalDate) {
        val firstOfMonth = month.atDay(1)
        val leadingEmpty = firstOfMonth.dayOfWeek.value % 7
        val daysInMonth = month.lengthOfMonth()
        val totalCells = ((leadingEmpty + daysInMonth + 6) / 7) * 7
        val rows = totalCells / 7

        // 주(週) 사이에만 가로 구분선을 그어 행을 시각적으로 분리한다 (세로선 없음)
        // MonthGrid 전체에 단일 clickable을 부여 — 셀 단위로 두면 PendingIntent가 30~40개 생겨 위젯 갱신이 느려진다
        val gridLineColor = Color(0xFF333333)
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .clickable(actionStartActivity<MainActivity>()),
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
                        val dayNumber = cell - leadingEmpty + 1
                        Box(
                            modifier = GlanceModifier.defaultWeight(),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (dayNumber in 1..daysInMonth) {
                                val date = month.atDay(dayNumber)
                                val shift = entries[date.toString()]?.overrideShift()
                                    ?: ShiftSchedule.cycleShift(date)
                                WidgetDayCell(date = date, shift = shift, isToday = date == today)
                            } else {
                                Spacer(modifier = GlanceModifier.fillMaxSize())
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun WidgetDayCell(date: LocalDate, shift: ShiftType, isToday: Boolean) {
        val isHoliday = HolidayProvider.isHoliday(date)
        val numColor = when {
            isHoliday || date.dayOfWeek == DayOfWeek.SUNDAY -> Color(0xFFEF5350)
            date.dayOfWeek == DayOfWeek.SATURDAY -> Color(0xFF42A5F5)
            else -> Color(0xFFEFEFEF)
        }
        val isOff = shift == ShiftType.OFF

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .cornerRadius(8.dp)
                .background(if (isToday) Color(0x3342A5F5) else Color.Transparent)
                .padding(horizontal = 2.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                style = TextStyle(
                    fontSize = 12.sp,
                    color = ColorProvider(numColor),
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                ),
            )
            if (isOff) {
                Text(
                    text = "휴",
                    style = TextStyle(
                        fontSize = 13.sp,
                        color = ColorProvider(shift.composeColor),
                        fontWeight = FontWeight.Medium,
                    ),
                )
            } else {
                Box(
                    modifier = GlanceModifier
                        .width(26.dp)
                        .height(26.dp)
                        .cornerRadius(13.dp)
                        .background(shift.composeColor),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = shift.label,
                        style = TextStyle(
                            fontSize = if (shift.label.length > 1) 11.sp else 13.sp,
                            color = ColorProvider(shift.composeOnColor),
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                }
            }
        }
    }
}
