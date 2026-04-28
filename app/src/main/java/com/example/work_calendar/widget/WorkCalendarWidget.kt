package com.example.work_calendar.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.work_calendar.MainActivity
import com.example.work_calendar.data.DayEntry
import com.example.work_calendar.data.ShiftRepository
import com.example.work_calendar.data.ShiftSchedule
import com.example.work_calendar.data.ShiftType
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

class WorkCalendarWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WorkCalendarWidget()
}

class WorkCalendarWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = ShiftRepository(context)
        val entries = repo.entriesFlow.first()
        val month = YearMonth.now()

        provideContent {
            GlanceTheme {
                WidgetContent(month = month, entries = entries)
            }
        }
    }

    @Composable
    private fun WidgetContent(month: YearMonth, entries: Map<String, DayEntry>) {
        val today = LocalDate.now()
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.background)
                .padding(8.dp)
                .clickable(actionStartActivity<MainActivity>()),
        ) {
            Header(month = month)
            WeekHeader()
            MonthGrid(month = month, entries = entries, today = today)
        }
    }

    @Composable
    private fun Header(month: YearMonth) {
        val text = month.format(DateTimeFormatter.ofPattern("yyyy년 M월", Locale.KOREAN))
        Row(
            modifier = GlanceModifier.fillMaxWidth().padding(bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                style = TextStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = GlanceTheme.colors.onBackground,
                ),
            )
        }
    }

    @Composable
    private fun WeekHeader() {
        val labels = listOf("일", "월", "화", "수", "목", "금", "토")
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            labels.forEachIndexed { index, label ->
                val color = when (index) {
                    0 -> ColorProvider(Color(0xFFD32F2F))
                    6 -> ColorProvider(Color(0xFF1976D2))
                    else -> GlanceTheme.colors.onSurfaceVariant
                }
                Box(
                    modifier = GlanceModifier.defaultWeight(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = TextStyle(fontSize = 9.sp, color = color, fontWeight = FontWeight.Medium),
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

        Column(modifier = GlanceModifier.fillMaxWidth()) {
            for (row in 0 until rows) {
                Row(modifier = GlanceModifier.fillMaxWidth().padding(vertical = 1.dp)) {
                    for (col in 0 until 7) {
                        val cell = row * 7 + col
                        val dayNumber = cell - leadingEmpty + 1
                        Box(
                            modifier = GlanceModifier.defaultWeight().padding(1.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (dayNumber in 1..daysInMonth) {
                                val date = month.atDay(dayNumber)
                                val shift = entries[date.toString()]?.overrideShift()
                                    ?: ShiftSchedule.cycleShift(date)
                                WidgetDayCell(date = date, shift = shift, isToday = date == today)
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun WidgetDayCell(date: LocalDate, shift: ShiftType, isToday: Boolean) {
        val numColor = when (date.dayOfWeek) {
            DayOfWeek.SUNDAY -> Color(0xFFD32F2F)
            DayOfWeek.SATURDAY -> Color(0xFF1976D2)
            else -> Color(0xFF212121)
        }
        Column(
            modifier = GlanceModifier
                .fillMaxWidth()
                .cornerRadius(6.dp)
                .background(if (isToday) Color(0x331976D2) else Color.Transparent)
                .padding(2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                style = TextStyle(
                    fontSize = 10.sp,
                    color = ColorProvider(numColor),
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                ),
            )
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .cornerRadius(4.dp)
                    .background(shift.composeColor)
                    .padding(vertical = 1.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = shift.label,
                    style = TextStyle(
                        fontSize = 9.sp,
                        color = ColorProvider(shift.composeOnColor),
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
        }
    }
}
