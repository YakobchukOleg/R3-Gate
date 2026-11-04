package com.r3gate.app

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class TimeAnalyticsOverlapTest {
    private val day = LocalDate.of(2026, 11, 4)
    private val zone = ZoneId.of("Europe/Warsaw")
    private val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
    private val hour = 3_600_000L

    @Test fun duplicateImportedShiftCountsOnce() {
        val shift = TimeAnalytics.Shift(start, start + hour,
            listOf(TimeAnalytics.Stage(start, start + hour, 0)))
        val result = TimeAnalytics.calculate(listOf(shift, shift), day, day, zone)
        assertArrayEquals(longArrayOf(hour, 0, 0, 0, 0), result.totals)
        assertEquals(hour, result.hours.sumOf { it.sum() })
    }

    @Test fun laterStageTakesPrecedenceAcrossShiftsAndGapsStayUnclassified() {
        val first = TimeAnalytics.Shift(start, start + 4 * hour,
            listOf(TimeAnalytics.Stage(start, start + 3 * hour, 0)))
        val second = TimeAnalytics.Shift(start + hour, start + 5 * hour,
            listOf(TimeAnalytics.Stage(start + 2 * hour, start + 3 * hour, 2)))
        val result = TimeAnalytics.calculate(listOf(first, second), day, day, zone)
        assertArrayEquals(longArrayOf(2 * hour, 0, hour, 2 * hour, 0), result.totals)
        assertEquals(5 * hour, result.hours.sumOf { it.sum() })
    }

    @Test fun damagedIntervalsCannotContributeClassifiedTime() {
        val shift = TimeAnalytics.Shift(start, start + hour,
            listOf(TimeAnalytics.Stage(-1, start + hour, 0), TimeAnalytics.Stage(start, start - 1, 1)))
        val invalid = TimeAnalytics.Shift(-1, start + hour, emptyList())
        val result = TimeAnalytics.calculate(listOf(shift, invalid), day, day, zone)
        assertArrayEquals(longArrayOf(0, 0, 0, hour, 0), result.totals)
    }

    @Test fun thirtyMinuteClockChangeSplitsLocalHourBuckets() {
        val zone = ZoneId.of("Australia/Lord_Howe")
        val date = LocalDate.of(2026, 10, 4)
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val result = TimeAnalytics.calculate(listOf(TimeAnalytics.Shift(start, end, emptyList())), date, date, zone)
        assertEquals(23 * hour + hour / 2, result.totals[3])
        assertEquals(hour / 2, result.hours[2][3])
        assertEquals(hour, result.hours[3][3])
    }
}
