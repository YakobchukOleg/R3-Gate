package com.r3gate.app

import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class PrivateTimeRulesTest {
    private val hour = 3_600_000L
    private fun interval(start: Long, end: Long) = PrivateTimeRules.Interval(start, end)

    @Test fun overlappingPausesAreDeductedOnce() {
        assertEquals(2 * hour, PrivateTimeRules.worked(0, 5 * hour,
            listOf(interval(hour, 3 * hour), interval(2 * hour, 4 * hour))))
    }

    @Test fun workWindowClipsPrivateTimeAndIgnoresDamagedIntervals() {
        assertEquals(hour, PrivateTimeRules.worked(hour, 3 * hour,
            listOf(interval(0, 2 * hour), interval(-1, 3 * hour), interval(3 * hour, 0))))
    }

    @Test fun privateTimeIsNotPaid() {
        val shift = WalletRules.Shift(0, 3 * hour, BigDecimal("30"), listOf(interval(hour, 2 * hour)))
        assertEquals(6000L, WalletRules.earned(listOf(shift)))
    }

    @Test fun walletClipsPrivateTimeAcrossMidnight() {
        val zone = ZoneId.of("Europe/Warsaw")
        val day = LocalDate.of(2026, 11, 4)
        val midnight = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val shift = WalletRules.Shift(midnight - 2 * hour, midnight + 2 * hour, BigDecimal("30"),
            listOf(interval(midnight - hour, midnight + hour)))
        assertEquals(3000L, WalletRules.earned(listOf(shift), day, day, zone))
    }

    @Test fun legacyShiftWithoutPrivateStagesKeepsOriginalPay() {
        assertEquals(6000L, WalletRules.earned(listOf(WalletRules.Shift(0, 2 * hour, BigDecimal("30")))))
    }
}
