package com.example.work_calendar.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.work_calendar.alarm.AlarmScheduler
import com.example.work_calendar.data.DayEntry
import com.example.work_calendar.data.RepoState
import com.example.work_calendar.data.ShiftAlarmDefault
import com.example.work_calendar.data.ShiftAlarmDefaults
import com.example.work_calendar.data.ShiftRepository
import com.example.work_calendar.data.ShiftSchedule
import com.example.work_calendar.data.ShiftType
import com.example.work_calendar.widget.WorkCalendarWidgetUpdater
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

private const val RESCHEDULE_WINDOW_DAYS = 90L

data class CalendarUiState(
    val month: YearMonth,
    val entries: Map<String, DayEntry>,
    val defaults: ShiftAlarmDefaults,
)

class CalendarViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = ShiftRepository(application)
    private val widgetUpdater = WorkCalendarWidgetUpdater(application)

    private val monthState = MutableStateFlow(YearMonth.now())

    val uiState: StateFlow<CalendarUiState> = combine(monthState, repo.stateFlow) { month, repoState ->
        CalendarUiState(month, repoState.entries, repoState.defaults)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        CalendarUiState(YearMonth.now(), emptyMap(), ShiftAlarmDefaults()),
    )

    fun showMonth(month: YearMonth) { monthState.value = month }

    fun resolvedShift(date: LocalDate, entries: Map<String, DayEntry>): ShiftType =
        repo.resolvedShift(date, entries)

    fun setOverride(date: LocalDate, type: ShiftType?) {
        viewModelScope.launch {
            repo.setOverride(date, type)
            rescheduleUpcoming()
            widgetUpdater.update()
        }
    }

    /** 이 날짜가 속한 AA 짝을 DA로 변경 (첫 A → D). A가 아니면 무시. */
    fun convertAaPairToDa(date: LocalDate) {
        val firstA = ShiftSchedule.firstAOfPair(date) ?: return
        viewModelScope.launch {
            repo.setOverride(firstA, ShiftType.D)
            rescheduleUpcoming()
            widgetUpdater.update()
        }
    }

    fun setMemo(date: LocalDate, memo: String) {
        viewModelScope.launch {
            repo.setMemo(date, memo)
            widgetUpdater.update()
        }
    }

    fun setAlarmDisabled(date: LocalDate, disabled: Boolean) {
        viewModelScope.launch {
            repo.setAlarmDisabled(date, disabled)
            rescheduleUpcoming()
        }
    }

    fun setShiftAlarmDefault(type: ShiftType, value: ShiftAlarmDefault) {
        viewModelScope.launch {
            repo.setShiftAlarmDefault(type, value)
            rescheduleUpcoming()
        }
    }

    private suspend fun rescheduleUpcoming() {
        val ctx = getApplication<Application>()
        val state: RepoState = repo.snapshot()
        val today = LocalDate.now()
        var date = today.minusDays(1) // N근무 전날 알람을 잡으려면 어제까지 포함
        val end = today.plusDays(RESCHEDULE_WINDOW_DAYS)
        while (date.isBefore(end)) {
            // shiftDate 입장에서 cancel
            AlarmScheduler.cancel(ctx, date)
            val effective = repo.effectiveAlarmTime(date, state.entries, state.defaults)
            if (effective != null) {
                val (shift, time) = effective
                AlarmScheduler.schedule(ctx, date, time, shift)
            }
            date = date.plusDays(1)
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                CalendarViewModel(app)
            }
        }
    }
}
