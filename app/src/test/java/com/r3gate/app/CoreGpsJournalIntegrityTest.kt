package com.r3gate.app

import org.junit.Assert.*
import org.junit.Test

class CoreGpsJournalIntegrityTest {
    private fun row(event: String = "ARRIVAL", time: Long = 1000L,
                    site: String? = "site-1", id: String? = "stable") =
        CoreGpsJournalAdapter.Raw(event, time, site, id)

    @Test fun emptyJournalIsValid() {
        assertEquals(emptyList<CoreGpsJournalAdapter.Raw>(),
            CoreGpsJournalIntegrity.validated(emptyList()))
    }
    @Test fun validArrivalIsRetained() {
        assertEquals(listOf(row()),CoreGpsJournalIntegrity.validated(listOf(row())))
    }
    @Test fun validDepartureIsRetained() {
        val departure=row(event="DEPARTURE")
        assertEquals(listOf(departure),CoreGpsJournalIntegrity.validated(listOf(departure)))
    }
    @Test fun legacyMissingEventIdIsAccepted() {
        val legacy=row(id=null)
        assertEquals(listOf(legacy),CoreGpsJournalIntegrity.validated(listOf(legacy)))
    }
    @Test fun emptyExplicitEventIdBlocksWholeBatch() {
        assertNull(CoreGpsJournalIntegrity.validated(listOf(row(),row(id=""))))
    }
    @Test fun missingObjectBlocksWholeBatch() {
        assertNull(CoreGpsJournalIntegrity.validated(listOf(row(),row(site=null))))
    }
    @Test fun blankObjectBlocksWholeBatch() {
        assertNull(CoreGpsJournalIntegrity.validated(listOf(row(),row(site=""))))
    }
    @Test fun whitespaceObjectBlocksWholeBatch() {
        assertNull(CoreGpsJournalIntegrity.validated(listOf(row(),row(site="   "))))
    }
    @Test fun unknownEventBlocksWholeBatch() {
        assertNull(CoreGpsJournalIntegrity.validated(listOf(row(),row(event="TRIP_START"))))
    }
    @Test fun missingTimestampBlocksWholeBatch() {
        assertNull(CoreGpsJournalIntegrity.validated(listOf(row(),row(time=0))))
    }
    @Test fun negativeTimestampBlocksWholeBatch() {
        assertNull(CoreGpsJournalIntegrity.validated(listOf(row(),row(time=-10))))
    }
    @Test fun nullRowBlocksWholeBatch() {
        assertNull(CoreGpsJournalIntegrity.validated(listOf(row(),null)))
    }
    @Test fun repeatedValidEventIsPreservedForCollisionCheck() {
        assertEquals(2,CoreGpsJournalIntegrity.validated(listOf(row(),row()))!!.size)
    }
    @Test fun journalOrderIsPreserved() {
        val entries=listOf(row(time=2000),row(time=1000))
        assertEquals(entries,CoreGpsJournalIntegrity.validated(entries))
    }
    @Test fun mixedValidArrivalDepartureAreAccepted() {
        val entries=listOf(row(),row(event="DEPARTURE",time=2000))
        assertEquals(entries,CoreGpsJournalIntegrity.validated(entries))
    }
    @Test fun noTimestampValueFails() {
        assertNull(CoreGpsJournalIntegrity.exactTime(null))
    }
    @Test fun zeroTimestampValueFails() {
        assertNull(CoreGpsJournalIntegrity.exactTime(0))
    }
    @Test fun negativeTimestampValueFails() {
        assertNull(CoreGpsJournalIntegrity.exactTime(-1))
    }
    @Test fun integralLongTimestampPasses() {
        assertEquals(1000L,CoreGpsJournalIntegrity.exactTime(1000L))
    }
    @Test fun integralDoubleTimestampPasses() {
        assertEquals(1000L,CoreGpsJournalIntegrity.exactTime(1000.0))
    }
    @Test fun fractionalTimestampCannotBeTruncated() {
        assertNull(CoreGpsJournalIntegrity.exactTime(1000.5))
    }
    @Test fun tinyFractionalTimestampCannotBeTruncated() {
        assertNull(CoreGpsJournalIntegrity.exactTime(1.5))
    }
    @Test fun infiniteTimestampFails() {
        assertNull(CoreGpsJournalIntegrity.exactTime(Double.POSITIVE_INFINITY))
    }
    @Test fun nanTimestampFails() {
        assertNull(CoreGpsJournalIntegrity.exactTime(Double.NaN))
    }
    @Test fun negativeInfiniteTimestampFails() {
        assertNull(CoreGpsJournalIntegrity.exactTime(Double.NEGATIVE_INFINITY))
    }
    @Test fun oversizedTimestampFails() {
        assertNull(CoreGpsJournalIntegrity.exactTime(Double.MAX_VALUE))
    }
    @Test fun exactBoundaryAtOneMillisecondPasses() {
        assertEquals(1L,CoreGpsJournalIntegrity.exactTime(1L))
    }
}
