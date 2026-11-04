package com.r3gate.app

import org.junit.Assert.*
import org.junit.Test

class CoreGpsObservationsTest {
    private fun row(kind: String, time: Long, objectId: String? = null) =
        CoreGpsJournalAdapter.Raw(kind,time,objectId,null)

    @Test fun observationsSupportTripVisitAndCoverageGapKinds() {
        val observations=CoreGpsJournalAdapter.observations(listOf(row("TRIP_START",1000),
            row("TRIP_STOP",2000), row("OBJECT_VISIT",2100,"site"), row("GPS_GAP",3000)),500,4000)
        assertEquals(listOf("TRIP_START","TRIP_STOP","OBJECT_VISIT","GPS_GAP"),
            observations.map { it.kind.name })
        assertEquals(CoreGpsProposalInbox.Confidence.LOW, observations.last().confidence)
        val shift=CoreGpsStageRules.Shift(true,"Przejazd",null)
        assertTrue(observations.none { CoreGpsStageRules.canApply(it,shift,setOf("site")) })
    }

    @Test fun replayKeepsOneStableObservationAndTripIdentity() {
        val metadata=CoreGpsJournalAdapter.Metadata(2000,"gps-v1","PROVISIONAL",
            CoreGpsProposalInbox.Confidence.MEDIUM,1900,2000,"trip-id")
        val event=row("TRIP_STOP",2000).copy(metadata=metadata)
        val proposal=CoreGpsJournalAdapter.observations(listOf(event,event),1000,3000).single()
        assertEquals("trip-id",proposal.tripId)
        assertEquals(CoreGpsJournalAdapter.eventId("TRIP_STOP",2000,""),proposal.eventId)
    }

    @Test fun unrelatedOrIncompleteEvidenceIsNotImported() {
        val input=listOf(row("TRIP_START",999),row("TRIP_STOP",3001),
            row("OBJECT_VISIT",2000),row("LONG_STOP",2000))
        assertTrue(CoreGpsJournalAdapter.observations(input,1000,3000).isEmpty())
    }

    @Test fun conflictingEventIdentityDoesNotProduceAnObservation() {
        val a=row("TRIP_START",1000).copy(eventId="same")
        val b=row("TRIP_STOP",2000).copy(eventId="same")
        assertTrue(CoreGpsJournalAdapter.observations(listOf(a,b),500,3000).isEmpty())
    }

    @Test fun arrivalAndTripStopCreateOneStableReviewOnlyVisit() {
        val data=listOf(row("ARRIVAL",1000,"site"),row("TRIP_STOP",2000))
        val visit=CoreGpsJournalAdapter.visitFromEvidence(data+data,setOf("site"),500,3000)!!
        assertEquals(CoreGpsProposalInbox.Kind.OBJECT_VISIT,visit.kind)
        assertEquals(CoreGpsProposalInbox.Confidence.MEDIUM,visit.confidence)
        assertEquals(CoreGpsJournalAdapter.eventId("OBJECT_VISIT",2000,"site"),visit.eventId)
        assertEquals(visit,CoreGpsJournalAdapter.visitFromEvidence(data,setOf("site"),500,3100))
    }

    @Test fun travelEvidenceAcrossAGapCannotProduceVisit() {
        val data=listOf(row("ARRIVAL",1000,"site"),row("GPS_GAP",1500),row("TRIP_STOP",2000))
        assertNull(CoreGpsJournalAdapter.visitFromEvidence(data,setOf("site"),500,3000))
    }

    @Test fun unknownOrArchivedDestinationCannotProduceVisit() {
        val data=listOf(row("ARRIVAL",1000,"archived"),row("TRIP_STOP",2000))
        assertNull(CoreGpsJournalAdapter.visitFromEvidence(data,setOf("site"),500,3000))
    }
}
