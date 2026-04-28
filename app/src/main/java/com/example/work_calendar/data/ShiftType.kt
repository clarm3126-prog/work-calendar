package com.example.work_calendar.data

import androidx.compose.ui.graphics.Color
import java.time.LocalTime

enum class ShiftType(
    val label: String,
    val displayName: String,
    val start: LocalTime?,
    val end: LocalTime?,
    val endsNextDay: Boolean,
    val color: Long,
    val onColor: Long,
) {
    D(
        label = "D",
        displayName = "주간 (Day)",
        start = LocalTime.of(8, 30),
        end = LocalTime.of(15, 0),
        endsNextDay = false,
        color = 0xFF1E88E5,
        onColor = 0xFFFFFFFF,
    ),
    DA(
        label = "DA",
        displayName = "주간+오후 (D+A)",
        start = LocalTime.of(8, 30),
        end = LocalTime.of(20, 30),
        endsNextDay = false,
        color = 0xFF00897B,
        onColor = 0xFFFFFFFF,
    ),
    A(
        label = "A",
        displayName = "오후 (Afternoon)",
        start = LocalTime.of(15, 0),
        end = LocalTime.of(20, 30),
        endsNextDay = false,
        color = 0xFFEF6C00,
        onColor = 0xFFFFFFFF,
    ),
    N(
        label = "N",
        displayName = "야간 (Night)",
        start = LocalTime.of(20, 30),
        end = LocalTime.of(8, 30),
        endsNextDay = true,
        color = 0xFF5E35B1,
        onColor = 0xFFFFFFFF,
    ),
    OFF(
        label = "휴",
        displayName = "휴무",
        start = null,
        end = null,
        endsNextDay = false,
        color = 0xFFBDBDBD,
        onColor = 0xFF424242,
    ),
    W(
        label = "W",
        displayName = "지정근무",
        start = LocalTime.of(9, 0),
        end = LocalTime.of(18, 0),
        endsNextDay = false,
        color = 0xFF2E7D32,
        onColor = 0xFFFFFFFF,
    );

    val composeColor: Color get() = Color(color)
    val composeOnColor: Color get() = Color(onColor)

    fun timeRangeText(): String = when {
        start == null || end == null -> "-"
        endsNextDay -> "%s ~ %s (익일)".format(start, end)
        else -> "%s ~ %s".format(start, end)
    }
}
