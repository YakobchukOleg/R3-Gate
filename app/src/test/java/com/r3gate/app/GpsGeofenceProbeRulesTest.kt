package com.r3gate.app

import org.junit.Assert.*
import org.junit.Test

class GpsGeofenceProbeRulesTest {
    private val r=GpsGeofenceProbeRules
    @Test fun ambiguousBorderUsesFastFix() { assertEquals(15_000L,r.interval(101_000,100_000,90_000)) }
    @Test fun exactProbeWindowStillUsesFastFix() { assertEquals(15_000L,r.interval(190_000,100_000,90_000)) }
    @Test fun probeExpiresAfterWindow() { assertEquals(90_000L,r.interval(190_001,100_000,90_000)) }
    @Test fun noProbeLeavesNormalInterval() { assertEquals(90_000L,r.interval(100_000,0,90_000)) }
    @Test fun invalidClockLeavesNormalInterval() { assertEquals(90_000L,r.interval(0,100_000,90_000)) }
    @Test fun futureProbeLeavesNormalInterval() { assertEquals(90_000L,r.interval(50_000,100_000,90_000)) }
    @Test fun alreadyFastScheduleIsNotSlowed() { assertEquals(10_000L,r.interval(101_000,100_000,10_000)) }
    @Test fun confirmedUncertaintyStartsProbe() { assertTrue(r.shouldBegin(100_000,0,true,true)) }
    @Test fun noConfirmedZoneDoesNotProbe() { assertFalse(r.shouldBegin(100_000,0,true,false)) }
    @Test fun clearEvidenceDoesNotProbe() { assertFalse(r.shouldBegin(100_000,0,false,true)) }
    @Test fun repeatedProbeIsRateLimited() { assertFalse(r.shouldBegin(110_000,100_000,true,true)) }
    @Test fun probeAllowedAtRearmBoundary() { assertTrue(r.shouldBegin(130_000,100_000,true,true)) }
    @Test fun futureProbeTimestampCannotRearm() { assertFalse(r.shouldBegin(90_000,100_000,true,true)) }
    @Test fun zeroNowCannotProbe() { assertFalse(r.shouldBegin(0,0,true,true)) }
}
