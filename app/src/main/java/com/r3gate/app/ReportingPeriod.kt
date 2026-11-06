package com.r3gate.app

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** Explicit report shortcut; manual dates and accounting calculations are unchanged. */
internal object ReportingPeriod {
    fun wednesdayToTuesday(day: LocalDate): Pair<LocalDate,LocalDate> {
        val start=day.with(TemporalAdjusters.previousOrSame(DayOfWeek.WEDNESDAY))
        return start to start.plusDays(6)
    }
}
