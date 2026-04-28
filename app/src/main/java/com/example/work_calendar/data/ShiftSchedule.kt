package com.example.work_calendar.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit

object ShiftSchedule {
    val anchorDate: LocalDate = LocalDate.of(2026, 4, 27)

    private val cycle: List<ShiftType> = listOf(
        ShiftType.A, ShiftType.A,
        ShiftType.D, ShiftType.D,
        ShiftType.OFF, ShiftType.OFF,
        ShiftType.N, ShiftType.N,
        ShiftType.OFF, ShiftType.OFF,
    )
    private val cycleSize = cycle.size

    fun cycleShift(date: LocalDate): ShiftType = cycle[cycleIndex(date)]

    fun cycleIndex(date: LocalDate): Int {
        val days = ChronoUnit.DAYS.between(anchorDate, date).toInt()
        return ((days % cycleSize) + cycleSize) % cycleSize
    }

    /**
     * 사이클이 만들어내는 AA 짝의 *첫째* A 날짜를 돌려준다.
     * 입력 날짜가 AA 짝 안의 A가 아니면 null.
     */
    fun firstAOfPair(date: LocalDate): LocalDate? = when (cycleIndex(date)) {
        0 -> date
        1 -> date.minusDays(1)
        else -> null
    }
}
