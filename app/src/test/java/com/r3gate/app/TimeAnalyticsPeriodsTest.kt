package com.r3gate.app

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class TimeAnalyticsPeriodsTest {
    private val day = LocalDate.of(2026, 1, 1)
    private val zone = ZoneId.of("Europe/Warsaw")
    private val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
    private val hour = 3_600_000L

    @Test fun todayAndBothWeeksHaveCalendarBoundariesAcrossYearEnd() {
        assertEquals(day to day, TimeAnalytics.dateRange(TimeAnalytics.Period.TODAY, day))
        assertEquals(LocalDate.of(2025, 12, 29) to LocalDate.of(2026, 1, 4),
            TimeAnalytics.dateRange(TimeAnalytics.Period.CURRENT_WEEK, day))
        assertEquals(LocalDate.of(2025, 12, 22) to LocalDate.of(2025, 12, 28),
            TimeAnalytics.dateRange(TimeAnalytics.Period.PREVIOUS_WEEK, day))
    }

    @Test fun completedMonthUsesLeapDayAndCurrentMonthUsesItsOwnLength() {
        val march = LocalDate.of(2024, 3, 1)
        assertEquals(LocalDate.of(2024, 2, 1) to LocalDate.of(2024, 2, 29),
            TimeAnalytics.dateRange(TimeAnalytics.Period.PREVIOUS_MONTH, march))
        assertEquals(march to LocalDate.of(2024, 3, 31),
            TimeAnalytics.dateRange(TimeAnalytics.Period.CURRENT_MONTH, march))
    }

    @Test fun customPeriodRequiresOrderedInclusiveDates() {
        val custom = day to day.plusDays(1)
        assertEquals(custom, TimeAnalytics.dateRange(TimeAnalytics.Period.CUSTOM, day, custom))
        assertNull(TimeAnalytics.dateRange(TimeAnalytics.Period.CUSTOM, day, custom.second to custom.first))
        assertNull(TimeAnalytics.dateRange(TimeAnalytics.Period.CUSTOM, day))
    }

    @Test fun personalTimeIsSeparateFromUnassignedAndHourlyWorkTotals() {
        val shift = TimeAnalytics.Shift(start, start + 3 * hour, listOf(
            TimeAnalytics.Stage(start, start + hour, 0),
            TimeAnalytics.Stage(start + hour, start + 2 * hour, TimeAnalytics.PERSONAL)
        ))
        val result = TimeAnalytics.calculate(listOf(shift), day, day, zone)
        assertArrayEquals(longArrayOf(hour, 0, 0, hour), result.totals)
        assertEquals(hour, result.personalMillis)
        assertEquals(2 * hour, result.hours.sumOf { it.sum() })
    }

    @Test fun anOverlappingImportedWorkStageCannotHidePersonalTime() {
        val personal = TimeAnalytics.Shift(start, start + 2 * hour,
            listOf(TimeAnalytics.Stage(start, start + 2 * hour, TimeAnalytics.PERSONAL)))
        val work = TimeAnalytics.Shift(start + hour, start + 3 * hour,
            listOf(TimeAnalytics.Stage(start + hour, start + 3 * hour, 0)))
        val result = TimeAnalytics.calculate(listOf(work, personal), day, day, zone)
        assertArrayEquals(longArrayOf(hour, 0, 0, 0), result.totals)
        assertEquals(2 * hour, result.personalMillis)
    }
}
