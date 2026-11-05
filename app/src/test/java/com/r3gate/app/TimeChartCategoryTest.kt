package com.r3gate.app

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class TimeChartCategoryTest {
    @Test fun privateTimeAndUnassignedTimeHaveSeparateBuckets() {
        val date = LocalDate.of(2026, 11, 4)
        val zone = ZoneId.of("Europe/Warsaw")
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val hour = 3_600_000L
        val personal = TimeChartCategory.from("Prywatnie", false)
        val shift = TimeAnalytics.Shift(start, start + 2 * hour,
            listOf(TimeAnalytics.Stage(start, start + hour, personal.ordinal)))
        val report = TimeAnalytics.calculate(listOf(shift), date, date, zone)
        assertEquals(TimeChartCategory.PERSONAL, personal)
        assertEquals(hour, report.personalMillis)
        assertEquals(hour, report.totals[TimeChartCategory.UNASSIGNED.ordinal])
        assertEquals(hour, report.totals.sum())
        assertEquals(report.totals.sum(), report.hours.sumOf { it.sum() })
    }

    @Test fun usesApprovedStageColors() {
        assertEquals(WorkMeterTheme.ON_SITE, TimeChartCategory.from("Obiekt", true).color)
        assertEquals(WorkMeterTheme.SHOPPING, TimeChartCategory.from("Zakupy", false).color)
        assertEquals(WorkMeterTheme.TRAVEL, TimeChartCategory.from("Przejazd", false).color)
        assertEquals(WorkMeterTheme.PERSONAL, TimeChartCategory.from("Prywatnie", false).color)
        assertEquals(TimeChartCategory.UNASSIGNED, TimeChartCategory.from("Obiekt", false))
    }
}
