package com.r3gate.app

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class WorkTimeRoundingTest {
    private fun at(value: String) = LocalDateTime.parse(value).atZone(ZoneId.of("Europe/Warsaw")).toInstant().toEpochMilli()
    @Test fun closingAt1732Records1735() {
        assertEquals(at("2026-11-06T17:35:00"), WorkTimeRounding.ceil(at("2026-11-06T17:32:00")))
    }
    @Test fun exactFiveMinuteBoundaryDoesNotAdvance() {
        val time=at("2026-11-06T17:35:00")
        assertEquals(time,WorkTimeRounding.ceil(time)); assertEquals("09:05",WorkTimeRounding.format(545L*60_000L))
    }
    @Test fun hiddenSecondsCannotTruncateTheUsers0905To0904() {
        val elapsed=at("2026-11-06T17:30:00")-at("2026-11-06T08:25:45")
        assertEquals("09:05",WorkTimeRounding.format(elapsed))
    }
    @Test fun closingAcrossMidnightAdvancesTheDate() {
        assertEquals(at("2026-11-07T00:00:00"),WorkTimeRounding.ceil(at("2026-11-06T23:59:01")))
    }
    @Test fun totalsAlwaysCeilToFiveMinutes() {
        assertEquals("09:05",WorkTimeRounding.format(544L*60_000L))
        assertEquals("09:10",WorkTimeRounding.format(546L*60_000L))
        assertEquals("10:20",WorkTimeRounding.format(619L*60_000L))
        assertEquals("01:00",WorkTimeRounding.format(59L*60_000L))
    }
    @Test fun positiveFractionRoundsUpAndNonpositiveDurationStaysZero() {
        assertEquals("00:05",WorkTimeRounding.format(1L)); assertEquals("00:10",WorkTimeRounding.format(300_001L))
        assertEquals("00:00",WorkTimeRounding.format(0L));assertEquals("00:00",WorkTimeRounding.format(-1L))
    }
    @Test fun longTotalsAreNotLimitedToOneDay() { assertEquals("125:05",WorkTimeRounding.format(125L*3_600_000L+1L)) }
    @Test fun roundingIsIdempotentAndNeverBeforeTheClick() {
        for(second in 0L..600L) {
            val input=at("2026-11-06T17:30:00")+second*1000L
            val end=WorkTimeRounding.ceil(input)
            assertTrue(end>=input);assertTrue(end-input<300_000L);assertEquals(end,WorkTimeRounding.ceil(end))
        }
    }
}
