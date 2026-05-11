package com.example.work_calendar.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.work_calendar.alarm.AlarmRescheduler
import com.example.work_calendar.data.DayEntry
import com.example.work_calendar.data.ShiftAlarmDefault
import com.example.work_calendar.data.ShiftAlarmDefaults
import com.example.work_calendar.data.ShiftRepository
import com.example.work_calendar.data.ShiftSchedule
import com.example.work_calendar.data.ShiftType
import com.example.work_calendar.update.DownloadEvent
import com.example.work_calendar.update.UpdateChecker
import com.example.work_calendar.update.UpdateInfo
import com.example.work_calendar.update.UpdateInstaller
import com.example.work_calendar.widget.WorkCalendarWidgetUpdater
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.YearMonth

data class CalendarUiState(
    val month: YearMonth,
    val entries: Map<String, DayEntry>,
    val defaults: ShiftAlarmDefaults,
)

sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data class Available(val info: UpdateInfo) : UpdateUiState
    data class Downloading(val info: UpdateInfo, val downloaded: Long, val total: Long) : UpdateUiState
    data class ReadyToInstall(val file: File, val info: UpdateInfo) : UpdateUiState
    data class Failed(val message: String) : UpdateUiState
}

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

    private val _updateState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val updateState: StateFlow<UpdateUiState> = _updateState.asStateFlow()

    init {
        checkForUpdate()
        // 콜드스타트마다 향후 알람을 다시 등록 — 부팅 후 BootReceiver가 못 미친 경우 보정
        viewModelScope.launch { AlarmRescheduler.rescheduleUpcoming(application) }
        // 위젯이 마지막으로 보고 있던 월을 가져와 앱 시작 월에 반영.
        // 위젯에서 6월을 보던 사용자가 앱을 열면 앱도 6월에서 시작 → 일관성.
        viewModelScope.launch {
            val widgetMonth = widgetUpdater.readViewedMonth()
            // showMonth 콜백을 거치면 다시 위젯에 쓰면서 불필요한 round-trip이 발생.
            // 초기 동기화는 monthState만 직접 갱신해 한쪽 방향으로만 흐르게 한다.
            monthState.value = widgetMonth
        }
    }

    // 위젯 월 동기화 작업 — 빠르게 여러 번 호출되면(빠른 스와이프/연타) 이전 sync는
    // 취소하고 가장 최신 월만 위젯에 쓰도록 한다. DataStore 쓰기는 멀티스레드 IO이라
    // 순서가 뒤집힐 수 있어 단순 launch로는 마지막 월이 보장되지 않는다.
    private var widgetMonthSyncJob: Job? = null

    fun showMonth(month: YearMonth) {
        if (monthState.value == month) return
        monthState.value = month
        widgetMonthSyncJob?.cancel()
        widgetMonthSyncJob = viewModelScope.launch {
            widgetUpdater.setViewedMonth(month)
        }
    }

    fun resolvedShift(date: LocalDate, entries: Map<String, DayEntry>): ShiftType =
        repo.resolvedShift(date, entries)

    fun setOverride(date: LocalDate, type: ShiftType?) {
        viewModelScope.launch {
            // 시트/액티비티가 닫혀 viewModelScope가 취소돼도 쓰기·위젯 갱신이 누락되지 않도록 보호.
            withContext(NonCancellable) {
                repo.setOverride(date, type)
                widgetUpdater.update()
            }
            rescheduleUpcoming()
        }
    }

    fun convertAaPairToDa(date: LocalDate) {
        val firstA = ShiftSchedule.firstAOfPair(date) ?: return
        viewModelScope.launch {
            withContext(NonCancellable) {
                repo.setOverride(firstA, ShiftType.D)
                widgetUpdater.update()
            }
            rescheduleUpcoming()
        }
    }

    fun setMemo(date: LocalDate, memo: String) {
        viewModelScope.launch {
            withContext(NonCancellable) {
                repo.setMemo(date, memo)
                widgetUpdater.update()
            }
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

    fun checkForUpdate() {
        viewModelScope.launch {
            val current = currentVersionCode()
            val info = UpdateChecker.fetchLatestUpdate(current) ?: return@launch
            _updateState.value = UpdateUiState.Available(info)
        }
    }

    fun startUpdate() {
        val available = _updateState.value as? UpdateUiState.Available ?: return
        val app = getApplication<Application>()
        viewModelScope.launch {
            UpdateInstaller.download(app, available.info.apkUrl)
                .catch { error ->
                    _updateState.value = UpdateUiState.Failed(
                        error.message ?: "다운로드 실패"
                    )
                }
                .collect { event ->
                    when (event) {
                        is DownloadEvent.Progress -> {
                            _updateState.value = UpdateUiState.Downloading(
                                info = available.info,
                                downloaded = event.downloaded,
                                total = event.total,
                            )
                        }
                        is DownloadEvent.Complete -> {
                            _updateState.value = UpdateUiState.ReadyToInstall(
                                file = event.file,
                                info = available.info,
                            )
                            UpdateInstaller.launchInstall(app, event.file)
                        }
                    }
                }
        }
    }

    fun dismissUpdate() {
        _updateState.value = UpdateUiState.Idle
    }

    private fun currentVersionCode(): Int {
        val ctx = getApplication<Application>()
        return runCatching {
            val info = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
            info.longVersionCode.toInt()
        }.getOrDefault(0)
    }

    private suspend fun rescheduleUpcoming() {
        AlarmRescheduler.rescheduleUpcoming(getApplication())
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
