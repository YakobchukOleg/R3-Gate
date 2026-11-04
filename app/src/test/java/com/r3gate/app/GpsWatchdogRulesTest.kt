package com.r3gate.app

import org.junit.Assert.*
import org.junit.Test

class GpsWatchdogRulesTest {
    private val r=GpsWatchdogRules
    private fun check(now:Long=400_000,started:Long=1_000,last:Long=1_000,probe:Long=0,
                      enabled:Boolean=true,provider:Boolean=true) =
        r.decide(now,started,last,probe,enabled,provider)

    @Test fun stalledFixTriggersReacquisition() { assertEquals(GpsWatchdogRules.Action.PROBE,check()) }
    @Test fun healthyStreamDoesNotTrigger() { assertEquals(GpsWatchdogRules.Action.NONE,check(last=350_000)) }
    @Test fun atExactStallThresholdTriggers() { assertEquals(GpsWatchdogRules.Action.PROBE,check(now=301_000)) }
    @Test fun oneMsBeforeStallDoesNotTrigger() { assertEquals(GpsWatchdogRules.Action.NONE,check(now=300_999)) }
    @Test fun startWithoutFirstFixCanStall() { assertEquals(GpsWatchdogRules.Action.PROBE,check(last=0)) }
    @Test fun freshSessionWithoutFirstFixDoesNotProbe() { assertEquals(GpsWatchdogRules.Action.NONE,check(now=250_000,last=0)) }
    @Test fun trackingDisabledNeverProbes() { assertEquals(GpsWatchdogRules.Action.NONE,check(enabled=false)) }
    @Test fun unavailableProviderNeverProbes() { assertEquals(GpsWatchdogRules.Action.NONE,check(provider=false)) }
    @Test fun zeroStartTimestampDoesNotProbe() { assertEquals(GpsWatchdogRules.Action.NONE,check(started=0)) }
    @Test fun clockRollbackDoesNotProbe() { assertEquals(GpsWatchdogRules.Action.NONE,check(now=500,started=1000)) }
    @Test fun recentWatchdogProbeThrottlesRetries() { assertEquals(GpsWatchdogRules.Action.NONE,check(probe=390_000)) }
    @Test fun retryAtCooldownBoundaryIsAllowed() { assertEquals(GpsWatchdogRules.Action.PROBE,check(now=500_000,probe=320_000)) }
    @Test fun slightlyFutureProbeTimestampStillThrottles() { assertEquals(GpsWatchdogRules.Action.NONE,check(probe=401_000)) }
    @Test fun maxTimestampWithoutOverflowCanProbe() { assertEquals(GpsWatchdogRules.Action.PROBE,check(now=Long.MAX_VALUE,started=1,last=1)) }
}
