package com.example.work_calendar.data

import kotlinx.serialization.Serializable
import java.time.LocalTime

@Serializable
data class ShiftAlarmDefault(
    val time: String? = null,
    val enabled: Boolean = false,
) {
    fun localTime(): LocalTime? = time?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
}

@Serializable
data class ShiftAlarmDefaults(
    val byShift: Map<String, ShiftAlarmDefault> = emptyMap(),
) {
    fun get(type: ShiftType): ShiftAlarmDefault = byShift[type.name] ?: ShiftAlarmDefault()

    fun with(type: ShiftType, value: ShiftAlarmDefault): ShiftAlarmDefaults {
        val next = byShift.toMutableMap()
        if (value.time == null && !value.enabled) next.remove(type.name)
        else next[type.name] = value
        return ShiftAlarmDefaults(next)
    }

    companion object {
        /** 알림 기본값을 설정할 수 있는 근무 종류 (휴무 제외) */
        val configurableTypes: List<ShiftType> = listOf(ShiftType.D, ShiftType.A, ShiftType.N, ShiftType.W)
    }
}
