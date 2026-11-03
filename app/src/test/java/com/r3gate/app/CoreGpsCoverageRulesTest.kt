package com.r3gate.app

import org.junit.Assert.*
import org.junit.Test

class CoreGpsCoverageRulesTest {
    private fun safe(event:Long=1000,now:Long=2000,outages:List<Long> = emptyList(),
                     gaps:List<CoreGpsCoverageRules.Gap> = emptyList()) =
        CoreGpsCoverageRules.trustworthy(event,now,outages,gaps)

    @Test fun cleanGpsCoverageAllowsRecentSuggestion() { assertTrue(safe()) }
    @Test fun outageAfterArrivalInvalidatesIt() { assertFalse(safe(outages=listOf(1500))) }
    @Test fun outageAtArrivalInvalidatesIt() { assertFalse(safe(outages=listOf(1000))) }
    @Test fun outageAtApprovalInvalidatesIt() { assertFalse(safe(outages=listOf(2000))) }
    @Test fun outageBeforeArrivalDoesNotInvalidateFreshProof() { assertTrue(safe(outages=listOf(999))) }
    @Test fun outageAfterApprovalDoesNotInvalidatePreviousWindow() { assertTrue(safe(outages=listOf(2001))) }
    @Test fun overlappingGapInvalidatesGeofenceObservation() {
        assertFalse(safe(gaps=listOf(CoreGpsCoverageRules.Gap(800,1200))))
    }
    @Test fun gapInsideObservationWindowInvalidates() {
        assertFalse(safe(gaps=listOf(CoreGpsCoverageRules.Gap(1200,1500))))
    }
    @Test fun gapAtCurrentTimeInvalidates() {
        assertFalse(safe(gaps=listOf(CoreGpsCoverageRules.Gap(2000,2100))))
    }
    @Test fun earlierResolvedGapDoesNotInvalidate() {
        assertTrue(safe(gaps=listOf(CoreGpsCoverageRules.Gap(100,999))))
    }
    @Test fun laterGapDoesNotInvalidateCurrentEvidence() {
        assertTrue(safe(gaps=listOf(CoreGpsCoverageRules.Gap(2001,2100))))
    }
    @Test fun invalidGapRangeFailsClosed() {
        assertFalse(safe(gaps=listOf(CoreGpsCoverageRules.Gap(2000,1999))))
    }
    @Test fun zeroGapStartFailsClosed() {
        assertFalse(safe(gaps=listOf(CoreGpsCoverageRules.Gap(0,100))))
    }
    @Test fun invalidEventTimeFailsClosed() { assertFalse(safe(event=0)) }
    @Test fun futureEventTimeFailsClosed() { assertFalse(safe(event=2001)) }
    @Test fun independentOldAndNewGpsOutagesUseAnyOverlap() {
        assertFalse(safe(outages=listOf(100,1800,3000)))
    }
    @Test fun multipleNonoverlappingIntervalsRemainSafe() {
        assertTrue(safe(gaps=listOf(CoreGpsCoverageRules.Gap(100,500),
            CoreGpsCoverageRules.Gap(2500,2800))))
    }
    @Test fun restartReplayOfGapKeepsProposalBlocked() {
        val gaps=listOf(CoreGpsCoverageRules.Gap(1100,1300))
        assertFalse(safe(gaps=gaps))
        assertFalse(safe(gaps=gaps))
    }
}
