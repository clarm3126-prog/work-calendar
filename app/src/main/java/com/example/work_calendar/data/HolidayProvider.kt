package com.example.work_calendar.data

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * 한국 공휴일 데이터.
 * - 양력 고정 공휴일은 매년 자동 생성
 * - 음력 공휴일(설날/부처님오신날/추석)은 연도별로 하드코딩 (2026~2030)
 * - 대체공휴일은 공휴일법 시행령 규칙에 따라 자동 계산
 *
 * 새 연도가 다가오면 음력 공휴일을 추가해 줘야 함.
 */
object HolidayProvider {
    private val baseHolidays: Map<LocalDate, String> = buildMap {
        // 양력 고정 공휴일 (2025~2035 일괄 생성)
        for (year in 2025..2035) {
            put(LocalDate.of(year, 1, 1), "신정")
            put(LocalDate.of(year, 3, 1), "삼일절")
            put(LocalDate.of(year, 5, 5), "어린이날")
            put(LocalDate.of(year, 6, 6), "현충일")
            put(LocalDate.of(year, 8, 15), "광복절")
            put(LocalDate.of(year, 10, 3), "개천절")
            put(LocalDate.of(year, 10, 9), "한글날")
            put(LocalDate.of(year, 12, 25), "크리스마스")
        }

        // 음력 공휴일 - 연도별 하드코딩
        // 2026
        put(LocalDate.of(2026, 2, 16), "설날 연휴")
        put(LocalDate.of(2026, 2, 17), "설날")
        put(LocalDate.of(2026, 2, 18), "설날 연휴")
        put(LocalDate.of(2026, 5, 24), "부처님오신날")
        put(LocalDate.of(2026, 9, 24), "추석 연휴")
        put(LocalDate.of(2026, 9, 25), "추석")
        put(LocalDate.of(2026, 9, 26), "추석 연휴")

        // 2027
        put(LocalDate.of(2027, 2, 6), "설날 연휴")
        put(LocalDate.of(2027, 2, 7), "설날")
        put(LocalDate.of(2027, 2, 8), "설날 연휴")
        put(LocalDate.of(2027, 5, 13), "부처님오신날")
        put(LocalDate.of(2027, 9, 14), "추석 연휴")
        put(LocalDate.of(2027, 9, 15), "추석")
        put(LocalDate.of(2027, 9, 16), "추석 연휴")

        // 2028
        put(LocalDate.of(2028, 1, 26), "설날 연휴")
        put(LocalDate.of(2028, 1, 27), "설날")
        put(LocalDate.of(2028, 1, 28), "설날 연휴")
        put(LocalDate.of(2028, 5, 2), "부처님오신날")
        put(LocalDate.of(2028, 10, 2), "추석 연휴")
        // 10/3은 개천절(이미 등록됨)과 추석이 겹침. 추석/개천절로 덮어씀
        put(LocalDate.of(2028, 10, 3), "추석/개천절")
        put(LocalDate.of(2028, 10, 4), "추석 연휴")

        // 2029
        put(LocalDate.of(2029, 2, 12), "설날 연휴")
        put(LocalDate.of(2029, 2, 13), "설날")
        put(LocalDate.of(2029, 2, 14), "설날 연휴")
        put(LocalDate.of(2029, 5, 20), "부처님오신날")
        put(LocalDate.of(2029, 9, 21), "추석 연휴")
        put(LocalDate.of(2029, 9, 22), "추석")
        put(LocalDate.of(2029, 9, 23), "추석 연휴")

        // 2030
        put(LocalDate.of(2030, 2, 2), "설날 연휴")
        put(LocalDate.of(2030, 2, 3), "설날")
        put(LocalDate.of(2030, 2, 4), "설날 연휴")
        put(LocalDate.of(2030, 5, 9), "부처님오신날")
        put(LocalDate.of(2030, 9, 11), "추석 연휴")
        put(LocalDate.of(2030, 9, 12), "추석")
        put(LocalDate.of(2030, 9, 13), "추석 연휴")
    }

    private val holidays: Map<LocalDate, String> = baseHolidays + computeSubstitutes(baseHolidays)

    fun nameOf(date: LocalDate): String? = holidays[date]
    fun isHoliday(date: LocalDate): Boolean = holidays.containsKey(date)

    /**
     * 공휴일법 시행령(제2조) 기준 대체공휴일 자동 계산.
     * - 삼일절·광복절·개천절·한글날·어린이날 : 토·일과 겹치는 경우
     * - 부처님오신날·크리스마스 : 일요일과 겹치는 경우
     * - 설날 연휴·추석 연휴 : 연휴 중 일요일과 겹치는 경우 마지막 연휴 다음 평일 1일
     * - 신정·현충일 : 대체공휴일 대상 아님
     *
     * 다른 공휴일끼리 같은 날 겹치는 케이스(예: 2028년 추석/개천절)는 데이터 구조상
     * 자동 추출이 어려워 별도 처리하지 않는다. 필요하면 baseHolidays에 직접 추가.
     */
    private fun computeSubstitutes(raw: Map<LocalDate, String>): Map<LocalDate, String> {
        val subs = mutableMapOf<LocalDate, String>()
        fun occupied() = raw.keys + subs.keys

        for ((date, name) in raw) {
            val dow = date.dayOfWeek
            val isWeekend = dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY
            val needsSub = when (name) {
                "삼일절", "광복절", "개천절", "한글날", "어린이날" -> isWeekend
                "부처님오신날", "크리스마스" -> dow == DayOfWeek.SUNDAY
                else -> false
            }
            if (needsSub) {
                subs[nextNonHolidayWeekday(date, occupied())] = "대체공휴일"
            }
        }

        // 설날/추석 연휴 클러스터 — 이름에 "설날" 또는 "추석"이 포함된 날짜를 그룹화
        val seollal = raw.entries.filter { it.value.contains("설날") }.map { it.key }.sorted()
        val chuseok = raw.entries.filter { it.value.contains("추석") }.map { it.key }.sorted()
        for (cluster in groupConsecutive(seollal) + groupConsecutive(chuseok)) {
            if (cluster.any { it.dayOfWeek == DayOfWeek.SUNDAY }) {
                subs[nextNonHolidayWeekday(cluster.last(), occupied())] = "대체공휴일"
            }
        }

        return subs
    }

    private fun nextNonHolidayWeekday(after: LocalDate, occupied: Set<LocalDate>): LocalDate {
        var d = after.plusDays(1)
        while (d.dayOfWeek == DayOfWeek.SATURDAY ||
            d.dayOfWeek == DayOfWeek.SUNDAY ||
            d in occupied
        ) {
            d = d.plusDays(1)
        }
        return d
    }

    private fun groupConsecutive(dates: List<LocalDate>): List<List<LocalDate>> {
        if (dates.isEmpty()) return emptyList()
        val groups = mutableListOf<MutableList<LocalDate>>()
        var current = mutableListOf(dates.first())
        for (i in 1 until dates.size) {
            if (dates[i] == dates[i - 1].plusDays(1)) current.add(dates[i])
            else {
                groups.add(current)
                current = mutableListOf(dates[i])
            }
        }
        groups.add(current)
        return groups
    }
}
