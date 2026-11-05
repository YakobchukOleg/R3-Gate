package com.r3gate.app

import org.junit.Assert.*
import org.junit.Test

class GpsSensorBurstRulesTest {
    private val r=GpsSensorBurstRules
    @Test fun firstWakeStartsBurst() { assertTrue(r.mayStart(1_000L,0L,0L)) }
    @Test fun zeroTimeNeverStarts() { assertFalse(r.mayStart(0L,0L,0L)) }
    @Test fun negativeTimeNeverStarts() { assertFalse(r.mayStart(-1L,0L,0L)) }
    @Test fun activeBurstCannotBeExtended() { assertFalse(r.mayStart(61_000L,1_000L,1_000L)) }
    @Test fun atActiveWindowEndStillNoRestart() {
        assertFalse(r.mayStart(91_000L,1_000L,1_000L))
    }
    @Test fun expiredBurstButCoolingDownDoesNotRearm() {
        assertFalse(r.mayStart(200_000L,1_000L,1_000L))
    }
    @Test fun rearmAfterFiveMinutes() {
        assertTrue(r.mayStart(301_000L,1_000L,1_000L))
    }
    @Test fun rearmJustBeforeFiveMinutesIsRejected() {
        assertFalse(r.mayStart(300_999L,1_000L,1_000L))
    }
    @Test fun futureLastStartCannotRearm() {
        assertFalse(r.mayStart(2_000L,3_000L,0L))
    }
    @Test fun futureBurstCannotRearm() {
        assertFalse(r.mayStart(2_000L,0L,3_000L))
    }
    @Test fun noActiveBurstStillRespectsCooldown() {
        assertFalse(r.mayStart(150_000L,1_000L,0L))
    }
    @Test fun noActiveBurstCanRearmAfterCooldown() {
        assertTrue(r.mayStart(301_000L,1_000L,0L))
    }
    @Test fun repeatedContinuousMotionCannotExtendBurstAtOneMinute() {
        var lastStart=1_000L
        var current=1_000L
        for (now in listOf(61_000L,121_000L,181_000L,241_000L)) {
            val allow=r.mayStart(now,lastStart,current)
            assertFalse(allow)
            if(allow) { lastStart=now;current=now }
        }
        assertEquals(1_000L,lastStart)
    }
    @Test fun canStartNewBurstAtExactCooldown() {
        assertTrue(r.mayStart(301_000L,1_000L,1_000L))
    }

    @Test fun majorRollbackOfLastStartRequestsReset() {
        assertTrue(r.clockRollbackRequiresReset(400_000L,460_000L,0L))
    }
    @Test fun majorRollbackOfActiveBurstRequestsReset() {
        assertTrue(r.clockRollbackRequiresReset(400_000L,0L,460_000L))
    }
    @Test fun minorReorderingOfLastStartPreservesCooldown() {
        assertFalse(r.clockRollbackRequiresReset(400_000L,459_999L,0L))
        assertFalse(r.mayStart(400_000L,459_999L,0L))
    }
    @Test fun minorReorderingOfBurstPreservesProtection() {
        assertFalse(r.clockRollbackRequiresReset(400_000L,0L,459_999L))
        assertFalse(r.mayStart(400_000L,0L,459_999L))
    }
    @Test fun exactResetThresholdIsAccepted() {
        assertTrue(r.clockRollbackRequiresReset(400_000L,460_000L,0L))
        assertTrue(r.mayStart(400_000L,0L,0L))
    }
    @Test fun noRollbackDoesNotReset() {
        assertFalse(r.clockRollbackRequiresReset(400_000L,390_000L,390_000L))
    }
    @Test fun invalidClockDoesNotReset() {
        assertFalse(r.clockRollbackRequiresReset(0L,460_000L,460_000L))
    }
    @Test fun extremeFutureClockResetIsOverflowSafe() {
        assertTrue(r.clockRollbackRequiresReset(400_000L,Long.MAX_VALUE,0L))
    }
    @Test fun resetPreservesFiniteBurstDuration() {
        assertEquals(15_000L,GpsGeofenceProbeRules.interval(400_000L,400_000L,90_000L))
        assertEquals(90_000L,GpsGeofenceProbeRules.interval(490_001L,400_000L,90_000L))
    }
    @Test fun resetStillRequiresMotionWakeForStarting() {
        assertFalse(r.clockRollbackRequiresReset(400_000L,0L,0L))
    }
}
