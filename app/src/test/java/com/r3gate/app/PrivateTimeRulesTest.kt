package com.r3gate.app

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId

class PrivateTimeRulesTest {
    @Test fun privatePeriodDoesNotCount() {
        assertEquals(7_200_000L, PrivateTimeRules.worked(0, 10_800_000L,
            listOf(PrivateTimeRules.Interval(3_600_000L, 7_200_000L))))
    }
    @Test fun overlappingAndOutOfBoundsPeriodsAreNotDoubleSubtracted() {
        assertEquals(2_000L, PrivateTimeRules.worked(1000, 5000,
            listOf(PrivateTimeRules.Interval(0, 2500), PrivateTimeRules.Interval(2000, 3000))))
    }
    @Test fun legacyShiftWithoutPersonalPeriodsIsUnchanged() {
        assertEquals(3_600_000L, PrivateTimeRules.worked(0, 3_600_000L, emptyList()))
    }
    @Test fun walletExcludesPrivateTimeFromEarnedAmounts() {
        val rate = BigDecimal("30.00")
        val s = WalletRules.Shift(0, 10_800_000L, rate, listOf(PrivateTimeRules.Interval(3_600_000L,7_200_000L)))
        assertEquals(6000L, WalletRules.earned(listOf(s)))
    }
}
