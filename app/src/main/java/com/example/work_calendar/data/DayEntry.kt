package com.example.work_calendar.data

import kotlinx.serialization.Serializable

@Serializable
data class DayEntry(
    val shiftOverride: String? = null,
    val memo: String = "",
    val alarmDisabled: Boolean = false,
) {
    val hasAnyData: Boolean
        get() = shiftOverride != null || memo.isNotBlank() || alarmDisabled

    fun overrideShift(): ShiftType? =
        shiftOverride?.let { runCatching { ShiftType.valueOf(it) }.getOrNull() }
}
