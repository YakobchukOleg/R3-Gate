package com.r3gate.app

import org.junit.Assert.assertEquals
import org.junit.Test

class GpsProbeCadenceRulesTest {
    private val rules=GpsProbeCadenceRules
    private fun check(now:Long=100_000L,probe:Long=95_000L,baseline:Long=90_000L,
        current:Long=15_000L,enabled:Boolean=true) =
        rules.evaluate(now,probe,baseline,current,enabled)

    @Test fun activeProbeWithAlreadyFastSubscriptionNeedsNoChange() {
        assertEquals(GpsProbeCadenceRules.Change(false,15_000L),check())
    }
    @Test fun firstActiveProbeRequestsFasterSampling() {
        assertEquals(GpsProbeCadenceRules.Change(true,15_000L),check(current=90_000L))
    }
    @Test fun expiredProbeRestoresQuietSampling() {
        assertEquals(GpsProbeCadenceRules.Change(true,90_000L),check(now=200_000L))
    }
    @Test fun boundaryWindowStillKeepsFastSampling() {
        assertEquals(GpsProbeCadenceRules.Change(false,15_000L),
            check(now=185_000L,probe=95_000L))
    }
    @Test fun firstMillisecondAfterWindowRestoresSampling() {
        assertEquals(GpsProbeCadenceRules.Change(true,90_000L),
            check(now=185_001L,probe=95_000L))
    }
    @Test fun noProbeUsesBaseline() {
        assertEquals(GpsProbeCadenceRules.Change(true,90_000L),check(probe=0L))
    }
    @Test fun baselineAlreadyFastIsNotSlowedByProbe() {
        assertEquals(GpsProbeCadenceRules.Change(false,10_000L),
            check(baseline=10_000L,current=10_000L))
    }
    @Test fun expiredProbePreservesBaselineThirtySeconds() {
        assertEquals(GpsProbeCadenceRules.Change(true,30_000L),
            check(now=200_000L,baseline=30_000L))
    }
    @Test fun noChangeIfCurrentAlreadyMatchesRestoredBaseline() {
        assertEquals(GpsProbeCadenceRules.Change(false,90_000L),
            check(now=200_000L,current=90_000L))
    }
    @Test fun disabledTrackingCannotUpdateProvider() {
        assertEquals(GpsProbeCadenceRules.Change(false,15_000L),check(enabled=false))
    }
    @Test fun invalidClockCannotUpdateProvider() {
        assertEquals(GpsProbeCadenceRules.Change(false,15_000L),check(now=0L))
    }
    @Test fun invalidBaselineCannotUpdateProvider() {
        assertEquals(GpsProbeCadenceRules.Change(false,15_000L),check(baseline=0L))
    }
    @Test fun futureProbeTimestampLeavesNormalCadence() {
        assertEquals(GpsProbeCadenceRules.Change(true,90_000L),
            check(now=90_000L,probe=95_000L))
    }
}
