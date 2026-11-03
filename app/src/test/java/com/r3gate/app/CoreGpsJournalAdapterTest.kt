package com.r3gate.app

import org.junit.Assert.*
import org.junit.Test

class CoreGpsJournalAdapterTest {
    private val sites=setOf("site-a","site-b")
    private val working=CoreGpsStageRules.Shift(true,"Obiekt","site-a")
    private val traveling=CoreGpsStageRules.Shift(true,"Przejazd","")
    private fun row(type:String="DEPARTURE",time:Long=1_000L,site:String?="site-a",id:String?=null) =
        CoreGpsJournalAdapter.Raw(type,time,site,id)
    private fun scan(rows:List<CoreGpsJournalAdapter.Raw>, shift:CoreGpsStageRules.Shift=working,
                     start:Long=500L,now:Long=1_200L,active:Set<String> = sites) =
        CoreGpsJournalAdapter.convert(rows,shift,active,start,now)

    @Test fun confirmedDepartureFromCurrentObjectCreatesOneProposal() {
        val result=scan(listOf(row()))
        assertEquals(1,result.size)
        assertEquals(CoreGpsProposalInbox.Kind.DEPARTURE,result.single().kind)
        assertEquals("site-a",result.single().objectId)
    }

    @Test fun confirmedArrivalDuringTravelCreatesOneProposal() {
        val result=scan(listOf(row("ARRIVAL",site="site-b")),traveling)
        assertEquals(CoreGpsProposalInbox.Kind.ARRIVAL,result.single().kind)
        assertEquals("site-b",result.single().objectId)
    }

    @Test fun motionEventsCannotChangeWorkStages() {
        assertTrue(scan(listOf(row("TRIP_START"),row("TRIP_STOP"),row("LONG_STOP"),
            row("GPS_GAP"))).isEmpty())
    }

    @Test fun noObjectIdentityCannotTriggerArrivalOrDeparture() {
        assertTrue(scan(listOf(row(site=null),row(site=""),row(site="   "))).isEmpty())
    }

    @Test fun archivedOrUnknownWorksiteIsNotActionable() {
        assertTrue(scan(listOf(row(site="site-b")),active=setOf("site-a")).isEmpty())
        assertTrue(scan(listOf(row(site="missing"))).isEmpty())
    }

    @Test fun departureFromAnotherObjectCannotStartTravel() {
        assertTrue(scan(listOf(row(site="site-b"))).isEmpty())
    }

    @Test fun arrivalCannotChangeCurrentObjectStage() {
        assertTrue(scan(listOf(row("ARRIVAL")),working).isEmpty())
    }

    @Test fun departureCannotChangeTravelStage() {
        assertTrue(scan(listOf(row()),traveling).isEmpty())
    }

    @Test fun eventBeforeCurrentStageIsIgnored() {
        assertTrue(scan(listOf(row(time=999)),start=1_000).isEmpty())
    }

    @Test fun eventAtStageBoundaryIsAllowed() {
        assertEquals(1,scan(listOf(row(time=1_000)),start=1_000).size)
    }

    @Test fun futureDatedGpsEvidenceCannotTriggerProposal() {
        assertTrue(scan(listOf(row(time=1_201))).isEmpty())
    }

    @Test fun staleGpsEvidenceCannotTriggerProposalAfterDoze() {
        assertTrue(scan(listOf(row()),now=1_000+CoreGpsStageRules.MAX_REVIEW_AGE_MS+1).isEmpty())
    }

    @Test fun duplicateJournalReplayDoesNotCreateDuplicateProposals() {
        val original=row(id="stable-event")
        assertEquals(1,scan(listOf(original,original,original)).size)
    }

    @Test fun reusedIdentityWithDifferentWorksiteSuppressesBoth() {
        val first=row("ARRIVAL",site="site-a",id="collision")
        val second=row("ARRIVAL",site="site-b",id="collision")
        assertTrue(scan(listOf(first,second),traveling).isEmpty())
    }

    @Test fun reusedIdentityWithDifferentTimestampSuppressesBoth() {
        assertTrue(scan(listOf(row(time=1_000,id="same"),row(time=1_001,id="same"))).isEmpty())
    }

    @Test fun explicitEventIdIsPreserved() {
        assertEquals("gps-stable-123",scan(listOf(row(id="gps-stable-123"))).single().eventId)
    }

    @Test fun missingLegacyIdentityIsDeterministicallyGenerated() {
        val one=scan(listOf(row())).single()
        val two=scan(listOf(row())).single()
        assertEquals(one.eventId,two.eventId)
        assertEquals(CoreGpsJournalAdapter.eventId("DEPARTURE",1_000,"site-a"),one.eventId)
    }

    @Test fun legacyIdentitySeparatesKindsTimesAndObjects() {
        val first=CoreGpsJournalAdapter.eventId("ARRIVAL",1000,"site-a")
        assertNotEquals(first,CoreGpsJournalAdapter.eventId("DEPARTURE",1000,"site-a"))
        assertNotEquals(first,CoreGpsJournalAdapter.eventId("ARRIVAL",1001,"site-a"))
        assertNotEquals(first,CoreGpsJournalAdapter.eventId("ARRIVAL",1000,"site-b"))
    }

    @Test fun legacyIdentitySeparatesDelimiterAndLengthAmbiguity() {
        val first=CoreGpsJournalAdapter.eventId("ARRIVAL",1000,"a:b")
        val second=CoreGpsJournalAdapter.eventId("ARRIVAL",1000,"a:b:c")
        assertNotEquals(first,second)
    }

    @Test fun differentValidEventsHaveDeterministicChronologicalOrder() {
        val rows=listOf(row("ARRIVAL",1100,"site-b","new"),
            row("ARRIVAL",1000,"site-a","old"))
        assertEquals(listOf("old","new"),scan(rows,traveling).map { it.eventId })
    }

    @Test fun equalTimestampOrdersByEventId() {
        val rows=listOf(row("ARRIVAL",1000,"site-b","z"),
            row("ARRIVAL",1000,"site-a","a"))
        assertEquals(listOf("a","z"),scan(rows,traveling).map { it.eventId })
    }

    @Test fun invalidTimeDoesNotProduceWorkSuggestion() {
        assertTrue(scan(listOf(row(time=0),row(time=-1))).isEmpty())
    }

    @Test fun closedShiftRejectsAllGeofenceEvents() {
        assertTrue(scan(listOf(row()),working.copy(active=false)).isEmpty())
    }

    @Test fun unknownEventKindsCannotBeConvertedIntoTrustedObservations() {
        assertTrue(scan(listOf(row("OBJECT_VISIT"),row("GPS_PROVIDER_OFF_ALERT"),
            row("UNCONFIRMED_ARRIVAL"))).isEmpty())
    }

    @Test fun conflictingArchivedObjectRowInvalidatesSameEventId() {
        val rows=listOf(row("ARRIVAL",1000,"site-a","shared"),
            row("ARRIVAL",1000,"archived","shared"))
        assertTrue(scan(rows,traveling).isEmpty())
    }

    @Test fun conflictingNonActionableKindInvalidatesSameEventId() {
        val rows=listOf(row("DEPARTURE",1000,"site-a","shared"),
            row("TRIP_STOP",1000,"site-a","shared"))
        assertTrue(scan(rows).isEmpty())
    }

    @Test fun conflictingOldTimestampInvalidatesRecentSameId() {
        val rows=listOf(row("DEPARTURE",1000,"site-a","shared"),
            row("DEPARTURE",100,"site-a","shared"))
        assertTrue(scan(rows).isEmpty())
    }

    @Test fun conflictingFutureTimestampInvalidatesRecentSameId() {
        val rows=listOf(row("DEPARTURE",1000,"site-a","shared"),
            row("DEPARTURE",1300,"site-a","shared"))
        assertTrue(scan(rows).isEmpty())
    }

    @Test fun conflictingBlankObjectInvalidatesSameId() {
        val rows=listOf(row("DEPARTURE",1000,"site-a","shared"),
            row("DEPARTURE",1000,"","shared"))
        assertTrue(scan(rows).isEmpty())
    }

    @Test fun conflictingRowDoesNotSuppressIndependentTrustedEvent() {
        val rows=listOf(row("DEPARTURE",1000,"site-a","shared"),
            row("TRIP_STOP",1000,"site-a","shared"),
            row("DEPARTURE",1100,"site-a","independent"))
        assertEquals(listOf("independent"),scan(rows).map { it.eventId })
    }

    @Test fun identicalNonActionableReplayDoesNotAffectLegitimateEvent() {
        val invalid=row("TRIP_STOP",1000,"site-a","noise")
        val valid=row("DEPARTURE",1000,"site-a","trusted")
        assertEquals(listOf("trusted"),scan(listOf(invalid,invalid,valid)).map { it.eventId })
    }

    @Test fun conflictingArchivedObjectRowsAreOrderIndependent() {
        val rows=listOf(row("ARRIVAL",1000,"site-a","shared"),
            row("ARRIVAL",1000,"archived","shared"))
        assertTrue(scan(rows.reversed(),traveling).isEmpty())
    }
}
