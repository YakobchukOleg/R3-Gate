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
    @Test fun unresolvedBorderNeverRearmsForever() { assertFalse(r.shouldBegin(130_000,100_000,true,true)) }
    @Test fun futureProbeTimestampCannotRearm() { assertFalse(r.shouldBegin(90_000,100_000,true,true)) }
    @Test fun zeroNowCannotProbe() { assertFalse(r.shouldBegin(0,0,true,true)) }

    @Test fun firstEvidenceOfEnteringAnObjectTriggersProbe() {
        assertTrue(r.shouldBegin(100_000,0,true,true))
    }
    @Test fun firstEvidenceOfLeavingAnObjectTriggersProbe() {
        assertTrue(r.shouldBegin(100_000,0,true,true))
    }
    @Test fun unresolvedCandidateIsNotResolved() {
        assertFalse(r.resolved(false,100_000))
    }
    @Test fun uncertainFixCannotRearm() {
        assertFalse(r.resolved(true,0))
    }
    @Test fun confirmedInsideFixRearmsFutureVisits() {
        assertTrue(r.resolved(false,0))
    }
    @Test fun resolvedTransitionCanBeginNewProbe() {
        assertTrue(r.shouldBegin(200_000,0,true,true))
    }

    @Test fun largeClockRollbackRearmsUnresolvedProbe() {
        assertTrue(GpsGeofenceProbeRules.clockRollbackRequiresReset(400_000L,460_000L))
        assertTrue(GpsGeofenceProbeRules.shouldBegin(400_000L,0L,true,true))
    }
    @Test fun minorClockReorderingDoesNotRearm() {
        assertFalse(GpsGeofenceProbeRules.clockRollbackRequiresReset(400_000L,459_999L))
        assertFalse(GpsGeofenceProbeRules.shouldBegin(400_000L,459_999L,true,true))
    }
    @Test fun normalForwardTimeDoesNotReset() {
        assertFalse(GpsGeofenceProbeRules.clockRollbackRequiresReset(400_000L,390_000L))
    }
    @Test fun zeroClockCannotReset() {
        assertFalse(GpsGeofenceProbeRules.clockRollbackRequiresReset(0L,460_000L))
    }
    @Test fun zeroCheckpointCannotReset() {
        assertFalse(GpsGeofenceProbeRules.clockRollbackRequiresReset(400_000L,0L))
    }
    @Test fun extremeFutureCheckpointRequiresResetWithoutOverflow() {
        assertTrue(GpsGeofenceProbeRules.clockRollbackRequiresReset(400_000L,Long.MAX_VALUE))
    }
    @Test fun unresolvedProbeAtRollbackStillUsesFiniteWindow() {
        assertEquals(15_000L,GpsGeofenceProbeRules.interval(400_000L,400_000L,90_000L))
        assertEquals(90_000L,GpsGeofenceProbeRules.interval(490_001L,400_000L,90_000L))
    }
    @Test fun resetDoesNotStartWithoutRelevantZone() {
        assertFalse(GpsGeofenceProbeRules.shouldBegin(400_000L,0L,true,false))
    }
}
