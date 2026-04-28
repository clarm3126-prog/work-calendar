package com.example.work_calendar.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.LocalTime

private val Context.workCalendarStore by preferencesDataStore(name = "work_calendar_entries")

private const val DEFAULTS_KEY = "__shift_alarm_defaults"
private const val DAY_KEY_PREFIX = "day_"

class ShiftRepository(private val context: Context) {

    private val store get() = context.workCalendarStore

    val entriesFlow: Flow<Map<String, DayEntry>> = store.data.map { prefs ->
        prefs.asMap().mapNotNull { (key, value) ->
            val name = key.name
            if (!name.startsWith(DAY_KEY_PREFIX)) return@mapNotNull null
            val text = value as? String ?: return@mapNotNull null
            val parsed = runCatching { Json.decodeFromString<DayEntry>(text) }.getOrNull()
                ?: return@mapNotNull null
            name.removePrefix(DAY_KEY_PREFIX) to parsed
        }.toMap()
    }

    val defaultsFlow: Flow<ShiftAlarmDefaults> = store.data.map { prefs ->
        val raw = prefs[stringPreferencesKey(DEFAULTS_KEY)] ?: return@map ShiftAlarmDefaults()
        runCatching { Json.decodeFromString<ShiftAlarmDefaults>(raw) }.getOrNull()
            ?: ShiftAlarmDefaults()
    }

    /** 화면에서 한 번에 쓰기 좋은 결합 흐름 */
    val stateFlow: Flow<RepoState> = combine(entriesFlow, defaultsFlow) { entries, defaults ->
        RepoState(entries, defaults)
    }

    suspend fun snapshot(): RepoState = stateFlow.first()

    fun resolvedShift(date: LocalDate, entries: Map<String, DayEntry>): ShiftType {
        val override = entries[date.toString()]?.overrideShift()
        return override ?: ShiftSchedule.cycleShift(date)
    }

    /** 일자별 메모/오버라이드 등 갱신. 어느 데이터도 없으면 키 자체를 삭제. */
    suspend fun updateDay(date: LocalDate, transform: (DayEntry) -> DayEntry) {
        val key = stringPreferencesKey(DAY_KEY_PREFIX + date.toString())
        store.edit { prefs ->
            val current = prefs[key]
                ?.let { runCatching { Json.decodeFromString<DayEntry>(it) }.getOrNull() }
                ?: DayEntry()
            val next = transform(current)
            if (!next.hasAnyData) prefs.remove(key)
            else prefs[key] = Json.encodeToString(next)
        }
    }

    suspend fun setOverride(date: LocalDate, type: ShiftType?) {
        updateDay(date) { it.copy(shiftOverride = type?.name) }
    }

    suspend fun setMemo(date: LocalDate, memo: String) {
        updateDay(date) { it.copy(memo = memo) }
    }

    suspend fun setAlarmDisabled(date: LocalDate, disabled: Boolean) {
        updateDay(date) { it.copy(alarmDisabled = disabled) }
    }

    suspend fun setShiftAlarmDefault(type: ShiftType, value: ShiftAlarmDefault) {
        val key = stringPreferencesKey(DEFAULTS_KEY)
        store.edit { prefs ->
            val current = prefs[key]
                ?.let { runCatching { Json.decodeFromString<ShiftAlarmDefaults>(it) }.getOrNull() }
                ?: ShiftAlarmDefaults()
            val next = current.with(type, value)
            if (next.byShift.isEmpty()) prefs.remove(key)
            else prefs[key] = Json.encodeToString(next)
        }
    }

    /**
     * 특정 날짜에 실제로 울려야 할 알람 시각.
     * 우선순위: 일자별 비활성 > 근무종류 기본값.
     */
    fun effectiveAlarmTime(
        date: LocalDate,
        entries: Map<String, DayEntry>,
        defaults: ShiftAlarmDefaults,
    ): Pair<ShiftType, LocalTime>? {
        val entry = entries[date.toString()] ?: DayEntry()
        if (entry.alarmDisabled) return null
        val shift = entry.overrideShift() ?: ShiftSchedule.cycleShift(date)
        val def = defaults.get(shift)
        if (!def.enabled) return null
        val time = def.localTime() ?: return null
        return shift to time
    }
}

data class RepoState(
    val entries: Map<String, DayEntry>,
    val defaults: ShiftAlarmDefaults,
)
