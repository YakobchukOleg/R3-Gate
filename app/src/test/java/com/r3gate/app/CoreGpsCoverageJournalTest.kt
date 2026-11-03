package com.r3gate.app

import org.junit.Assert.*
import org.junit.Test

class CoreGpsCoverageJournalTest {
    private fun boundary(kind: String? = "GPS_GAP", at: Long? = 1500L) =
        CoreGpsCoverageJournal.Boundary(kind, at)
    private fun interval(start: Long? = 1200L, end: Long? = 1500L) =
        CoreGpsCoverageJournal.Interval(start, end)
    private fun valid(
        boundaries: List<CoreGpsCoverageJournal.Boundary?> = emptyList(),
        gaps: List<CoreGpsCoverageJournal.Interval?> = emptyList(),
        event: Long = 1000L,
        now: Long = 2000L
    ) = CoreGpsCoverageJournal.trustworthy(event, now, boundaries, gaps)

    @Test fun emptyJournalsAreSafeForOtherwiseFreshEvidence() {
        assertTrue(valid())
    }

    @Test fun validProviderGapBlocksSuggestion() {
        assertFalse(valid(boundaries=listOf(boundary())))
    }

    @Test fun invalidProviderGapTimeFailsClosed() {
        assertFalse(valid(boundaries=listOf(boundary(at=0))))
    }

    @Test fun missingProviderGapTimeFailsClosed() {
        assertFalse(valid(boundaries=listOf(boundary(at=null))))
    }

    @Test fun negativeProviderGapTimeFailsClosed() {
        assertFalse(valid(boundaries=listOf(boundary(at=-1))))
    }

    @Test fun nullProviderJournalRowFailsClosed() {
        assertFalse(valid(boundaries=listOf(null)))
    }

    @Test fun unrelatedValidMotionEventIsIgnored() {
        assertTrue(valid(boundaries=listOf(boundary(kind="TRIP_START",at=1500))))
    }

    @Test fun unrelatedEventWithoutTimestampFailsClosed() {
        assertFalse(valid(boundaries=listOf(boundary(kind="TRIP_STOP",at=null))))
    }

    @Test fun invalidGapRowFailsClosed() {
        assertFalse(valid(gaps=listOf(null)))
    }

    @Test fun missingGapStartFailsClosed() {
        assertFalse(valid(gaps=listOf(interval(start=null))))
    }

    @Test fun missingGapEndFailsClosed() {
        assertFalse(valid(gaps=listOf(interval(end=null))))
    }

    @Test fun zeroGapStartFailsClosed() {
        assertFalse(valid(gaps=listOf(interval(start=0))))
    }

    @Test fun negativeGapStartFailsClosed() {
        assertFalse(valid(gaps=listOf(interval(start=-1))))
    }

    @Test fun backwardsGapFailsClosed() {
        assertFalse(valid(gaps=listOf(interval(start=1600,end=1500))))
    }

    @Test fun actualCoverageGapBlocksPrompt() {
        assertFalse(valid(gaps=listOf(interval())))
    }

    @Test fun oldResolvedGapAllowsLaterEvent() {
        assertTrue(valid(gaps=listOf(interval(start=100,end=999))))
    }

    @Test fun futureGapDoesNotBlockCurrentEvidence() {
        assertTrue(valid(gaps=listOf(interval(start=2001,end=2100))))
    }

    @Test fun malformedRowAmongOtherValidRowsFailsClosed() {
        assertFalse(valid(
            boundaries=listOf(boundary(kind="TRIP_START"),null),
            gaps=listOf(interval(start=1,end=900))
        ))
    }

    @Test fun multipleHistoricalValidGapsRemainSafe() {
        assertTrue(valid(gaps=listOf(interval(10,50),interval(100,999))))
    }

    @Test fun equalStartEndGapInsideWindowBlocks() {
        assertFalse(valid(gaps=listOf(interval(1500,1500))))
    }

    @Test fun repeatedReopenKeepsMalformedJournalBlocked() {
        val b=listOf(boundary(at=null))
        assertFalse(valid(boundaries=b))
        assertFalse(valid(boundaries=b))
    }

    @Test fun invalidTimeOrderFailsClosedEvenWithoutGaps() {
        assertFalse(valid(event=2001,now=2000))
    }

    @Test fun missingMotionEventKindFailsClosed() {
        assertFalse(valid(boundaries=listOf(boundary(kind=null))))
    }

    @Test fun blankMotionEventKindFailsClosed() {
        assertFalse(valid(boundaries=listOf(boundary(kind=""))))
    }

    @Test fun nonGapEventWithInvalidTimeFailsClosed() {
        assertFalse(valid(boundaries=listOf(boundary(kind="TRIP_START",at=0))))
    }

    @Test fun nonGapEventWithNegativeTimeFailsClosed() {
        assertFalse(valid(boundaries=listOf(boundary(kind="TRIP_STOP",at=-1))))
    }

    @Test fun multipleValidMotionEventsPreserveCoverage() {
        assertTrue(valid(boundaries=listOf(
            boundary(kind="TRIP_START",at=1200),
            boundary(kind="TRIP_STOP",at=1600)
        )))
    }
}
