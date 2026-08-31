package com.example.work_calendar.data

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * 한국 공휴일 데이터.
 * - 양력 고정 공휴일은 매년 자동 생성
 * - 음력 공휴일(설날/부처님오신날/추석)은 연도별로 하드코딩 (2025~2030)
 * - 대체공휴일은 「관공서의 공휴일에 관한 규정」 제3조에 따라 자동 계산
 *
 * 새 연도가 다가오면 음력 공휴일을 추가해 줘야 함. 음력 공휴일이 없는 연도는
 * 설날·추석·부처님오신날과 그에 딸린 대체공휴일이 통째로 비게 된다.
 */
object HolidayProvider {

    /** 설날·추석 연휴 — 대체공휴일 규칙이 나머지와 다르다(일요일 또는 겹침, 연휴 단위). */
    private val SEOLLAL_CHUSEOK = setOf("설날", "설날 연휴", "추석", "추석 연휴")

    /** 토·일과 겹치면 대체공휴일이 붙는 공휴일. */
    private val WEEKEND_SUBSTITUTED = setOf(
        "삼일절", "광복절", "개천절", "한글날", "부처님오신날", "크리스마스",
    )

    /**
     * 날짜별 공휴일 이름 목록.
     *
     * 한 날짜에 공휴일이 둘 겹칠 수 있어(2025-05-05 어린이날+부처님오신날,
     * 2028-10-03 추석+개천절) 값을 리스트로 둔다. 이 겹침 자체가 대체공휴일
     * 발생 요건이라 단일 문자열로 뭉개면 대체공휴일을 계산할 수 없다.
     */
    private val baseHolidays: Map<LocalDate, List<String>> = buildList {
        // 음력 공휴일 - 연도별 하드코딩. 겹칠 때 "추석/개천절" 순으로 보이도록 먼저 넣는다.
        add(LocalDate.of(2025, 1, 28) to "설날 연휴")
        add(LocalDate.of(2025, 1, 29) to "설날")
        add(LocalDate.of(2025, 1, 30) to "설날 연휴")
        add(LocalDate.of(2025, 5, 5) to "부처님오신날")
        add(LocalDate.of(2025, 10, 5) to "추석 연휴")
        add(LocalDate.of(2025, 10, 6) to "추석")
        add(LocalDate.of(2025, 10, 7) to "추석 연휴")

        add(LocalDate.of(2026, 2, 16) to "설날 연휴")
        add(LocalDate.of(2026, 2, 17) to "설날")
        add(LocalDate.of(2026, 2, 18) to "설날 연휴")
        add(LocalDate.of(2026, 5, 24) to "부처님오신날")
        add(LocalDate.of(2026, 9, 24) to "추석 연휴")
        add(LocalDate.of(2026, 9, 25) to "추석")
        add(LocalDate.of(2026, 9, 26) to "추석 연휴")

        add(LocalDate.of(2027, 2, 6) to "설날 연휴")
        add(LocalDate.of(2027, 2, 7) to "설날")
        add(LocalDate.of(2027, 2, 8) to "설날 연휴")
        add(LocalDate.of(2027, 5, 13) to "부처님오신날")
        add(LocalDate.of(2027, 9, 14) to "추석 연휴")
        add(LocalDate.of(2027, 9, 15) to "추석")
        add(LocalDate.of(2027, 9, 16) to "추석 연휴")

        add(LocalDate.of(2028, 1, 26) to "설날 연휴")
        add(LocalDate.of(2028, 1, 27) to "설날")
        add(LocalDate.of(2028, 1, 28) to "설날 연휴")
        add(LocalDate.of(2028, 5, 2) to "부처님오신날")
        add(LocalDate.of(2028, 10, 2) to "추석 연휴")
        // 10/3은 개천절과 겹친다 — 겹침이 곧 대체공휴일 요건이므로 따로 넣어 둔다.
        add(LocalDate.of(2028, 10, 3) to "추석")
        add(LocalDate.of(2028, 10, 4) to "추석 연휴")

        add(LocalDate.of(2029, 2, 12) to "설날 연휴")
        add(LocalDate.of(2029, 2, 13) to "설날")
        add(LocalDate.of(2029, 2, 14) to "설날 연휴")
        add(LocalDate.of(2029, 5, 20) to "부처님오신날")
        add(LocalDate.of(2029, 9, 21) to "추석 연휴")
        add(LocalDate.of(2029, 9, 22) to "추석")
        add(LocalDate.of(2029, 9, 23) to "추석 연휴")

        add(LocalDate.of(2030, 2, 2) to "설날 연휴")
        add(LocalDate.of(2030, 2, 3) to "설날")
        add(LocalDate.of(2030, 2, 4) to "설날 연휴")
        add(LocalDate.of(2030, 5, 9) to "부처님오신날")
        add(LocalDate.of(2030, 9, 11) to "추석 연휴")
        add(LocalDate.of(2030, 9, 12) to "추석")
        add(LocalDate.of(2030, 9, 13) to "추석 연휴")

        // 양력 고정 공휴일 (2025~2035 일괄 생성)
        for (year in 2025..2035) {
            add(LocalDate.of(year, 1, 1) to "신정")
            add(LocalDate.of(year, 3, 1) to "삼일절")
            add(LocalDate.of(year, 5, 5) to "어린이날")
            add(LocalDate.of(year, 6, 6) to "현충일")
            add(LocalDate.of(year, 8, 15) to "광복절")
            add(LocalDate.of(year, 10, 3) to "개천절")
            add(LocalDate.of(year, 10, 9) to "한글날")
            add(LocalDate.of(year, 12, 25) to "크리스마스")
        }
    }.groupBy({ it.first }, { it.second })

    private val holidays: Map<LocalDate, String> =
        (baseHolidays + computeSubstitutes(baseHolidays))
            .mapValues { (_, names) -> names.joinToString("/") }

    fun nameOf(date: LocalDate): String? = holidays[date]
    fun isHoliday(date: LocalDate): Boolean = holidays.containsKey(date)

    /**
     * 「관공서의 공휴일에 관한 규정」 제3조 기준 대체공휴일 자동 계산.
     * - 설날·추석 연휴 : 연휴 중 **일요일 또는 다른 공휴일과 겹치는 날 수**만큼,
     *   연휴가 끝난 뒤의 비공휴일 평일에 순차 배정
     * - 어린이날 : 토·일 **또는 다른 공휴일**과 겹치는 경우
     * - 삼일절·광복절·개천절·한글날·부처님오신날·크리스마스 : 토·일과 겹치는 경우
     * - 신정·현충일 : 대체공휴일 대상 아님
     *
     * 날짜 순서대로 처리해 결과가 항상 같도록 한다(같은 대체일을 여러 공휴일이
     * 노릴 때 배정 순서가 뒤집히지 않게).
     */
    private fun computeSubstitutes(
        raw: Map<LocalDate, List<String>>,
    ): Map<LocalDate, List<String>> {
        val subs = mutableMapOf<LocalDate, List<String>>()
        fun occupied() = raw.keys + subs.keys
        fun addSub(after: LocalDate) {
            subs[nextNonHolidayWeekday(after, occupied())] = listOf("대체공휴일")
        }

        // 1) 설날·추석 연휴 — 연휴 단위로 묶어서 처리
        val clusterDates = raw
            .filterValues { names -> names.any { it in SEOLLAL_CHUSEOK } }
            .keys.sorted()
        val inCluster = clusterDates.toSet()
        for (cluster in groupConsecutive(clusterDates)) {
            val overlapping = cluster.count { date ->
                date.dayOfWeek == DayOfWeek.SUNDAY || raw.getValue(date).size > 1
            }
            repeat(overlapping) { addSub(cluster.last()) }
        }

        // 2) 나머지 공휴일 — 연휴에 속한 날은 위에서 이미 셈했으므로 건너뛴다
        for (date in raw.keys.sorted()) {
            if (date in inCluster) continue
            val names = raw.getValue(date)
            val dow = date.dayOfWeek
            val isWeekend = dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY
            val overlapsOther = names.size > 1
            val needsSub = names.any { name ->
                when (name) {
                    "어린이날" -> isWeekend || overlapsOther
                    in WEEKEND_SUBSTITUTED -> isWeekend
                    else -> false
                }
            }
            if (needsSub) addSub(date)
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
