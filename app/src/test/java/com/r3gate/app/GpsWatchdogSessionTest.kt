package com.r3gate.app

import org.junit.Assert.assertEquals
import org.junit.Test

class GpsWatchdogSessionTest {
    private val rules=GpsWatchdogRules
    private fun outcome(now:Long,started:Long,lastFix:Long,probe:Long=0L,
        enabled:Boolean=true,provider:Boolean=true)=rules.decide(now,started,lastFix,probe,enabled,provider)

    @Test fun oldSessionFixDoesNotCauseImmediateProbe() {
        assertEquals(GpsWatchdogRules.Action.NONE,outcome(1_050_000L,1_000_000L,100_000L))
    }
    @Test fun previousSessionFixCannotSuppressNewSessionTimeout() {
        assertEquals(GpsWatchdogRules.Action.PROBE,outcome(1_301_000L,1_000_000L,100_000L))
    }
    @Test fun newFixAfterSessionStartResetsWatchdogClock() {
        assertEquals(GpsWatchdogRules.Action.NONE,outcome(1_350_000L,1_000_000L,1_300_000L))
    }
    @Test fun newFixEventuallyStalls() {
        assertEquals(GpsWatchdogRules.Action.PROBE,outcome(1_601_000L,1_000_000L,1_300_000L))
    }
    @Test fun newSessionStartsAtLargeEpochWithoutOverflow() {
        assertEquals(GpsWatchdogRules.Action.NONE,
            outcome(Long.MAX_VALUE,Long.MAX_VALUE-1000L,1L))
    }
    @Test fun noPreviousFixUsesCurrentSessionStart() {
        assertEquals(GpsWatchdogRules.Action.NONE,outcome(1_050_000L,1_000_000L,0L))
    }
    @Test fun noPreviousFixEventuallyProbes() {
        assertEquals(GpsWatchdogRules.Action.PROBE,outcome(1_400_000L,1_000_000L,0L))
    }
    @Test fun futureLastFixCannotSuppressSessionStall() {
        assertEquals(GpsWatchdogRules.Action.PROBE,outcome(1_400_000L,1_000_000L,1_500_000L))
    }
    @Test fun futureLastProbeDisablesRetry() {
        assertEquals(GpsWatchdogRules.Action.NONE,
            outcome(1_400_000L,1_000_000L,1L,1_500_000L))
    }
    @Test fun disabledGpsSuppressesProbeEvenOnStalePreviousFix() {
        assertEquals(GpsWatchdogRules.Action.NONE,
            outcome(1_400_000L,1_000_000L,1L,enabled=false))
    }
    @Test fun missingProviderSuppressesProbeEvenOnStalePreviousFix() {
        assertEquals(GpsWatchdogRules.Action.NONE,
            outcome(1_400_000L,1_000_000L,1L,provider=false))
    }
    @Test fun retryCooldownRespectedAcrossServiceEpochs() {
        assertEquals(GpsWatchdogRules.Action.NONE,
            outcome(1_400_000L,1_000_000L,1L,1_350_000L))
    }
}
