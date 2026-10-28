package com.r3gate.app

import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class WalletRulesTest {
    private val zone=ZoneId.of("Europe/Warsaw")
    private fun ms(date:String)=java.time.LocalDateTime.parse(date).atZone(zone).toInstant().toEpochMilli()
    @Test fun partialPaymentsHaveIndependentMethods() {
        val shift=WalletRules.Shift(ms("2026-10-28T08:00"),ms("2026-10-28T16:00"),BigDecimal("30"))
        val payments=listOf(WalletRules.Payment("2026-10-01","2026-10-31",10000,"cash"),WalletRules.Payment("2026-10-01","2026-10-31",5000,"card"))
        val s=WalletRules.summary(listOf(shift),payments,LocalDate.parse("2026-10-01"),LocalDate.parse("2026-10-31"),zone)
        assertEquals(24000L,s.earned); assertEquals(10000L,s.cash); assertEquals(5000L,s.card); assertEquals(9000L,s.due)
    }
    @Test fun crossingMonthClipsHoursAndKeepsRateSnapshot() {
        val shifts=listOf(WalletRules.Shift(ms("2026-10-31T23:00"),ms("2026-11-01T01:00"),BigDecimal("40")))
        assertEquals(4000L,WalletRules.earned(shifts,LocalDate.parse("2026-10-01"),LocalDate.parse("2026-10-31"),zone))
        assertEquals(8000L,WalletRules.earned(shifts))
    }
    @Test fun broadPaymentsAreNotSilentlyProratedOrDoubleCounted() {
        val payment=WalletRules.Payment("2026-10-01","2026-10-31",100000,"cash")
        val s=WalletRules.summary(emptyList(),listOf(payment),LocalDate.parse("2026-10-01"),LocalDate.parse("2026-10-07"),zone)
        assertEquals(0L,s.paid); assertEquals(1,s.overlapping)
    }
    @Test fun moneyUsesCentsAndRejectsExcessDecimals() {
        assertEquals(12345L,WalletRules.money("123,45")); assertNull(WalletRules.money("1.001")); assertNull(WalletRules.money("-1"))
        assertEquals(-100L,WalletRules.money("-1",true)); assertNull(WalletRules.rate("0"))
    }
    @Test fun aggregateRoundingAndOverpayment() {
        val shifts=List(3) { WalletRules.Shift(0,60000,BigDecimal("1")) }
        assertEquals(5L,WalletRules.earned(shifts))
        val s=WalletRules.summary(emptyList(),listOf(WalletRules.Payment("2026-10-01","2026-10-31",500,"card")),LocalDate.parse("2026-10-01"),LocalDate.parse("2026-10-31"),zone)
        assertEquals(-500L,s.due)
    }
}
