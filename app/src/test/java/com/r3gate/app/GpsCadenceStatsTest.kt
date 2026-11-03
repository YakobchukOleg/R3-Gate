package com.r3gate.app
import org.junit.Assert.*
import org.junit.Test

class GpsCadenceStatsTest {
    @Test fun ignoresFixesOutsideCurrentSession() {
        val s=GpsCadenceStats.assess(listOf(5,100,130,160,500),100,200,30)
        assertEquals(3,s.samples)
        assertEquals(30L,s.medianIntervalMs)
        assertEquals(0,s.delayedIntervals)
    }
    @Test fun reportsLargeRealGaps() {
        val s=GpsCadenceStats.assess(listOf(100,130,500),100,600,30)
        assertEquals(370L,s.longestGapMs)
        assertEquals(1,s.delayedIntervals)
    }
    @Test fun duplicateAndUnorderedCallbacksAreNotExtraMeasurements() {
        val s=GpsCadenceStats.assess(listOf(160,100,100,130),100,200,30)
        assertEquals(3,s.samples)
        assertEquals(2,s.intervals)
    }
    @Test fun neverInventsIntervalsWhenNoFixes() {
        val s=GpsCadenceStats.assess(emptyList(),100,500,90)
        assertNull(s.medianIntervalMs)
        assertNull(s.longestGapMs)
        assertEquals(0,s.delayedIntervals)
    }
    @Test fun rejectsInvalidSessions() {
        assertEquals(0,GpsCadenceStats.assess(listOf(100,200),300,200,30).samples)
    }
    @Test fun countsOnlyGapsLongerThanGpsEvidenceLimit() {
        val threshold=GpsTripRules.MAX_EVIDENCE_GAP_MS
        val stats=GpsCadenceStats.assess(
            listOf(1_000L,1_000L+threshold,1_000L+threshold*2+1),
            1_000L,1_000L+threshold*2+1,30_000L)
        assertEquals(1,stats.coverageInterruptions)
        assertEquals(2,stats.intervals)
    }

    @Test fun normalQuietSamplingIsNotCoverageInterruption() {
        val stats=GpsCadenceStats.assess(
            listOf(1_000L,91_000L,181_000L,271_000L),1_000,280_000,90_000)
        assertEquals(0,stats.coverageInterruptions)
        assertEquals(0,stats.delayedIntervals)
    }

    @Test fun twoLongOutagesAreReportedSeparately() {
        val stats=GpsCadenceStats.assess(
            listOf(1_000L,400_000L,430_000L,800_000L),1_000,900_000,30_000)
        assertEquals(2,stats.coverageInterruptions)
        assertEquals(2,stats.delayedIntervals)
    }

    @Test fun duplicateFixesDoNotAddOutages() {
        val stats=GpsCadenceStats.assess(
            listOf(1_000L,400_000L,400_000L,1_000L),1_000,500_000,30_000)
        assertEquals(1,stats.coverageInterruptions)
        assertEquals(1,stats.intervals)
    }

    @Test fun invalidSessionHasNoCoverageInterruptions() {
        val stats=GpsCadenceStats.assess(listOf(100L,500_000L),700_000,600_000,30_000)
        assertEquals(0,stats.coverageInterruptions)
    }

    @Test fun hugeExpectedIntervalDoesNotOverflowToNegative() {
        val stats=GpsCadenceStats.assess(
            listOf(1_000L,2_000L),1_000L,2_000L,Long.MAX_VALUE)
        assertEquals(0,stats.delayedIntervals)
    }

    @Test fun delayBoundaryIsStrictlyMoreThanTwiceExpected() {
        val stats=GpsCadenceStats.assess(
            listOf(1_000L,61_000L,121_001L),1_000,130_000,30_000)
        assertEquals(1,stats.delayedIntervals)
    }

}
