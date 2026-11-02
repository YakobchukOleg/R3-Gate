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
}
