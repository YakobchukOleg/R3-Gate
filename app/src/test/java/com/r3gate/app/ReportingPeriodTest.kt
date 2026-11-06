package com.r3gate.app

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class ReportingPeriodTest {
    @Test fun handlesBothBoundariesAndYearRollover() {
        for(day in listOf("2026-12-30","2026-12-31","2027-01-01","2027-01-05")) {
            assertEquals(LocalDate.parse("2026-12-30") to LocalDate.parse("2027-01-05"),ReportingPeriod.wednesdayToTuesday(LocalDate.parse(day)))
        }
        assertEquals(LocalDate.parse("2027-01-06") to LocalDate.parse("2027-01-12"),ReportingPeriod.wednesdayToTuesday(LocalDate.parse("2027-01-06")))
    }
}
