package com.r3gate.app

import org.junit.Assert.*
import org.junit.Test

class CoreGpsStageReviewQueueTest {
    private val ids = setOf("obj-a", "obj-b", "obj-c")
    private val traveling = CoreGpsStageRules.Shift(true, "Przejazd", "")
    private val working = CoreGpsStageRules.Shift(true, "Obiekt", "obj-a")

    private fun arrival(id: String, time: Long = 1_000L, eventId: String = "$id-$time") =
        CoreGpsProposalInbox.Event(
            eventId, CoreGpsProposalInbox.Kind.ARRIVAL, time, id,
            confidence = CoreGpsProposalInbox.Confidence.HIGH
        )
    private fun departure(time: Long = 1_000L, eventId: String = "depart-$time") =
        CoreGpsProposalInbox.Event(
            eventId, CoreGpsProposalInbox.Kind.DEPARTURE, time, "obj-a",
            confidence = CoreGpsProposalInbox.Confidence.HIGH
        )
    private fun pick(
        events: List<CoreGpsProposalInbox.Event>,
        shift: CoreGpsStageRules.Shift = traveling,
        start: Long = 500L,
        now: Long = 1_200L,
        activeIds: Set<String> = ids
    ) = CoreGpsStageReviewQueue.choose(events, shift, activeIds, start, now)

    @Test fun emptyQueueDoesNotPropose() {
        assertNull(pick(emptyList()).event)
        assertEquals(0, pick(emptyList()).eligibleCount)
    }

    @Test fun newestArrivalWinsRegardlessOfJournalOrder() {
        val older=arrival("obj-a", 800)
        val newer=arrival("obj-b", 1000)
        assertEquals(newer, pick(listOf(older,newer)).event)
        assertEquals(newer, pick(listOf(newer,older)).event)
    }

    @Test fun identicalReplayedEventHasOneEligibleIdentity() {
        val event=arrival("obj-a")
        assertEquals(event, pick(listOf(event,event)).event)
        assertEquals(1,pick(listOf(event,event)).eligibleCount)
    }

    @Test fun simultaneousDifferentDestinationsAreAmbiguous() {
        val result=pick(listOf(arrival("obj-a"),arrival("obj-b")))
        assertNull(result.event)
        assertTrue(result.ambiguous)
        assertEquals(2,result.eligibleCount)
    }

    @Test fun simultaneousDifferentIdsForSameDestinationAreAlsoAmbiguous() {
        val result=pick(listOf(arrival("obj-a",eventId="one"),arrival("obj-a",eventId="two")))
        assertNull(result.event)
        assertTrue(result.ambiguous)
    }

    @Test fun conflictingOldEventsDoNotOverrideUniqueLatestEvent() {
        val newest=arrival("obj-c",1000)
        assertEquals(newest,pick(listOf(arrival("obj-a",800),arrival("obj-b",800),newest)).event)
    }

    @Test fun lowConfidenceIsNotAnActionableSuggestion() {
        val low=arrival("obj-a").copy(confidence=CoreGpsProposalInbox.Confidence.LOW)
        val medium=arrival("obj-b").copy(confidence=CoreGpsProposalInbox.Confidence.MEDIUM)
        assertNull(pick(listOf(low,medium)).event)
    }

    @Test fun rejectedOrConfirmedProposalsStayHidden() {
        val e=arrival("obj-a")
        val events=listOf(
            e.copy(decision=CoreGpsProposalInbox.Decision.USER_CONFIRMED),
            arrival("obj-b").copy(decision=CoreGpsProposalInbox.Decision.USER_REJECTED)
        )
        assertNull(pick(events).event)
    }

    @Test fun staleAndFutureDatedEventsNeverNotify() {
        assertNull(pick(listOf(arrival("obj-a",1_000)),now=1_000+CoreGpsStageRules.MAX_REVIEW_AGE_MS+1).event)
        assertNull(pick(listOf(arrival("obj-a",1_000)),now=999).event)
    }

    @Test fun eventsBeforeCurrentStageDoNotNotify() {
        assertNull(pick(listOf(arrival("obj-a",999)),start=1_000).event)
    }

    @Test fun missingOrArchivedObjectIsExcluded() {
        assertNull(pick(listOf(arrival("obj-c")),activeIds=setOf("obj-a","obj-b")).event)
    }

    @Test fun noActionableArrivalWhileWorkingAtObject() {
        assertNull(pick(listOf(arrival("obj-b")),shift=working).event)
    }

    @Test fun stableDepartureCanStartTravel() {
        val e=departure()
        assertEquals(e,pick(listOf(e),shift=working).event)
    }

    @Test fun departureFromDifferentObjectIsIgnored() {
        val e=departure().copy(objectId="obj-b")
        assertNull(pick(listOf(e),shift=working).event)
    }

    @Test fun gpsGapAndTripStopAreNotStageChanges() {
        val e=arrival("obj-a")
        val samples=listOf(e.copy(kind=CoreGpsProposalInbox.Kind.GPS_GAP),
            e.copy(eventId="stop",kind=CoreGpsProposalInbox.Kind.TRIP_STOP))
        assertNull(pick(samples).event)
    }

    @Test fun shiftMustBeOpen() {
        assertNull(pick(listOf(departure()),shift=working.copy(active=false)).event)
    }

    @Test fun allCandidatesAmbiguousAtLatestTimestampYieldNoSuggestion() {
        val events=listOf(arrival("obj-a",700),arrival("obj-a",1000,"latest-1"),
            arrival("obj-b",1000,"latest-2"))
        assertNull(pick(events).event)
        assertTrue(pick(events).ambiguous)
    }

    private fun current(
        id: String, events: List<CoreGpsProposalInbox.Event>,
        shift: CoreGpsStageRules.Shift = traveling,
        start: Long = 500L, now: Long = 1_200L,
        activeIds: Set<String> = ids
    ) = CoreGpsStageReviewQueue.isCurrent(id,events,shift,activeIds,start,now)

    @Test fun currentUniqueNotificationCanBeApproved() {
        assertTrue(current("obj-a-1000",listOf(arrival("obj-a"))))
    }

    @Test fun emptyNotificationIdentityIsAlwaysRejected() {
        assertFalse(current("",listOf(arrival("obj-a"))))
    }

    @Test fun oldNotificationCannotOverrideNewerArrival() {
        assertFalse(current("obj-a-800",listOf(arrival("obj-a",800),arrival("obj-b",1000))))
    }

    @Test fun newestNotificationCanBeApprovedAfterOlderEvidence() {
        assertTrue(current("obj-b-1000",listOf(arrival("obj-a",800),arrival("obj-b",1000))))
    }

    @Test fun simultaneousCompetingArrivalNotificationsAreBothRejected() {
        val events=listOf(arrival("obj-a",1000),arrival("obj-b",1000))
        assertFalse(current("obj-a-1000",events))
        assertFalse(current("obj-b-1000",events))
    }

    @Test fun replayingIdenticalNotificationDoesNotDisableApproval() {
        val e=arrival("obj-a")
        assertTrue(current(e.eventId,listOf(e,e)))
    }

    @Test fun dismissedNotificationFromPreviousStageCannotReopen() {
        assertFalse(current("obj-a-1000",listOf(arrival("obj-a")),start=1001))
    }

    @Test fun closedShiftRejectsNotificationTap() {
        assertFalse(current("depart-1000",listOf(departure()),shift=working.copy(active=false)))
    }

    @Test fun manualStageChangeRejectsOldDepartureNotification() {
        assertFalse(current("depart-1000",listOf(departure()),shift=traveling))
    }

    @Test fun manualArrivalRejectsOldArrivalNotification() {
        assertFalse(current("obj-a-1000",listOf(arrival("obj-a")),shift=working))
    }

    @Test fun rejectedNotificationCannotBeApprovedByOldPendingIntent() {
        val e=arrival("obj-a").copy(decision=CoreGpsProposalInbox.Decision.USER_REJECTED)
        assertFalse(current(e.eventId,listOf(e)))
    }

    @Test fun confirmedNotificationCannotApplyTwiceAfterRestart() {
        val e=departure().copy(decision=CoreGpsProposalInbox.Decision.USER_CONFIRMED)
        assertFalse(current(e.eventId,listOf(e),shift=working))
    }

    @Test fun oldNotificationExpiresWhilePhoneIsLocked() {
        val e=arrival("obj-a")
        assertFalse(current(e.eventId,listOf(e),now=e.timestampMs+
            CoreGpsStageRules.MAX_REVIEW_AGE_MS+1))
    }

    @Test fun notificationFromFutureClockIsNotActionable() {
        val e=arrival("obj-a")
        assertFalse(current(e.eventId,listOf(e),now=999))
    }

    @Test fun archivedDestinationInvalidatesPreviouslyShownNotification() {
        val e=arrival("obj-a")
        assertFalse(current(e.eventId,listOf(e),activeIds=setOf("obj-b","obj-c")))
    }

    @Test fun unresolvedGpsGapCannotBecomeActionableByReplayingEventId() {
        val gap=arrival("obj-a").copy(kind=CoreGpsProposalInbox.Kind.GPS_GAP)
        assertFalse(current(gap.eventId,listOf(gap)))
    }
}
