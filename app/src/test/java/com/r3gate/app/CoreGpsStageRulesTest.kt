package com.r3gate.app

import org.junit.Assert.*
import org.junit.Test

class CoreGpsStageRulesTest {
    private val ids = setOf("site-a", "site-b")
    private fun shift(kind: String, objectId: String? = "site-a", active: Boolean = true) =
        CoreGpsStageRules.Shift(active, kind, objectId)
    private fun event(kind: CoreGpsProposalInbox.Kind, id: String = "site-a", at: Long = 200L) =
        CoreGpsProposalInbox.Event("gps:$kind:$at:$id", kind, at, id,
            confidence = CoreGpsProposalInbox.Confidence.HIGH)

    @Test fun confirmedDepartureOffersTravelOnlyFromCurrentObject() {
        assertEquals(CoreGpsStageRules.Proposal.START_TRAVEL,
            CoreGpsStageRules.proposal(CoreGpsProposalInbox.Kind.DEPARTURE, "site-a",
                shift("Obiekt"), ids))
        assertNull(CoreGpsStageRules.proposal(CoreGpsProposalInbox.Kind.DEPARTURE,
            "site-b", shift("Obiekt"), ids))
    }

    @Test fun arrivalOffersObjectOnlyDuringTravel() {
        assertEquals(CoreGpsStageRules.Proposal.ARRIVE_OBJECT,
            CoreGpsStageRules.proposal(CoreGpsProposalInbox.Kind.ARRIVAL, "site-b",
                shift("Przejazd", ""), ids))
        assertNull(CoreGpsStageRules.proposal(CoreGpsProposalInbox.Kind.ARRIVAL,
            "site-a", shift("Obiekt"), ids))
    }

    @Test fun inaccurateOrIncompleteEventsNeverOfferTransition() {
        for (kind in listOf(CoreGpsProposalInbox.Kind.GPS_GAP,
                CoreGpsProposalInbox.Kind.TRIP_STOP, CoreGpsProposalInbox.Kind.TRIP_START)) {
            assertNull(CoreGpsStageRules.proposal(kind, "site-a", shift("Obiekt"), ids))
        }
        assertNull(CoreGpsStageRules.proposal(CoreGpsProposalInbox.Kind.ARRIVAL,
            null, shift("Przejazd"), ids))
    }

    @Test fun closedShiftCannotReceiveStageSuggestions() {
        assertNull(CoreGpsStageRules.proposal(CoreGpsProposalInbox.Kind.DEPARTURE,
            "site-a", shift("Obiekt", active = false), ids))
    }

    @Test fun archivedOrMissingObjectIsNotEligible() {
        assertNull(CoreGpsStageRules.proposal(CoreGpsProposalInbox.Kind.ARRIVAL,
            "site-archived", shift("Przejazd"), ids))
        assertNull(CoreGpsStageRules.proposal(CoreGpsProposalInbox.Kind.DEPARTURE,
            "site-archived", shift("Obiekt", "site-archived"), ids))
    }

    @Test fun repeatedDepartureDoesNotStartDuplicateTravelStage() {
        assertNull(CoreGpsStageRules.proposal(CoreGpsProposalInbox.Kind.DEPARTURE,
            "site-a", shift("Przejazd", ""), ids))
    }

    @Test fun repeatedArrivalDoesNotStartDuplicateObjectStage() {
        assertNull(CoreGpsStageRules.proposal(CoreGpsProposalInbox.Kind.ARRIVAL,
            "site-b", shift("Obiekt", "site-b"), ids))
    }

    @Test fun rejectedAndConfirmedProposalsDoNotOfferAgain() {
        val e = event(CoreGpsProposalInbox.Kind.DEPARTURE)
        assertTrue(CoreGpsStageRules.canApply(e, shift("Obiekt"), ids))
        assertFalse(CoreGpsStageRules.canApply(
            e.copy(decision = CoreGpsProposalInbox.Decision.USER_REJECTED), shift("Obiekt"), ids))
        assertFalse(CoreGpsStageRules.canApply(
            e.copy(decision = CoreGpsProposalInbox.Decision.USER_CONFIRMED), shift("Obiekt"), ids))
    }

    @Test fun eventsPredatingShiftAreIgnored() {
        assertFalse(CoreGpsStageRules.shouldNotify(event(CoreGpsProposalInbox.Kind.DEPARTURE, at = 199L),
            shift("Obiekt"), ids, 200L))
        assertTrue(CoreGpsStageRules.shouldNotify(event(CoreGpsProposalInbox.Kind.DEPARTURE, at = 200L),
            shift("Obiekt"), ids, 200L))
    }

    @Test fun legacyActiveShiftWithoutStartDoesNotNotify() {
        assertFalse(CoreGpsStageRules.shouldNotify(event(CoreGpsProposalInbox.Kind.DEPARTURE),
            shift("Obiekt"), ids, 0L))
    }

    @Test fun staleDepartureIsNotEligibleForNotification() {
        val departure=event(CoreGpsProposalInbox.Kind.DEPARTURE,at=1_000L)
        assertFalse(CoreGpsStageRules.shouldNotify(departure,shift("Obiekt"),ids,500L,
            1_000L+CoreGpsStageRules.MAX_REVIEW_AGE_MS+1))
    }

    @Test fun recentDepartureCanStillBeReviewed() {
        val departure=event(CoreGpsProposalInbox.Kind.DEPARTURE,at=1_000L)
        assertTrue(CoreGpsStageRules.shouldNotify(departure,shift("Obiekt"),ids,500L,
            1_000L+CoreGpsStageRules.MAX_REVIEW_AGE_MS))
    }

    @Test fun clockRollbackCannotTreatFutureEvidenceAsValid() {
        val departure=event(CoreGpsProposalInbox.Kind.DEPARTURE,at=1_000L)
        assertFalse(CoreGpsStageRules.shouldNotify(departure,shift("Obiekt"),ids,500L,999L))
    }

    @Test fun staleArrivalCannotChangeTripAfterLongParking() {
        val arrival=event(CoreGpsProposalInbox.Kind.ARRIVAL,at=1_000L)
        assertFalse(CoreGpsStageRules.shouldNotify(arrival,shift("Przejazd",""),ids,500L,
            1_000L+CoreGpsStageRules.MAX_REVIEW_AGE_MS+10))
    }

    @Test fun unchangedCurrentStageCanReviewAtEventTime() {
        val arrival=event(CoreGpsProposalInbox.Kind.ARRIVAL,at=1_000L)
        assertTrue(CoreGpsStageRules.shouldNotify(arrival,shift("Przejazd",""),ids,500L,1_000L))
    }

    @Test fun lowConfidenceNeverReachesFinalStageApproval() {
        val sample=event(CoreGpsProposalInbox.Kind.DEPARTURE)
        assertFalse(CoreGpsStageRules.canApply(sample.copy(
            confidence=CoreGpsProposalInbox.Confidence.LOW),shift("Obiekt"),ids))
    }

    @Test fun mediumConfidenceNeverReachesFinalStageApproval() {
        val sample=event(CoreGpsProposalInbox.Kind.DEPARTURE)
        assertFalse(CoreGpsStageRules.canApply(sample.copy(
            confidence=CoreGpsProposalInbox.Confidence.MEDIUM),shift("Obiekt"),ids))
    }

    @Test fun unknownSourceNeverReachesFinalStageApproval() {
        val sample=event(CoreGpsProposalInbox.Kind.DEPARTURE)
        assertFalse(CoreGpsStageRules.canApply(sample.copy(source="untrusted"),
            shift("Obiekt"),ids))
    }

    @Test fun missingIdentityAndInvalidTimestampCannotBeApproved() {
        val sample=event(CoreGpsProposalInbox.Kind.DEPARTURE)
        assertFalse(CoreGpsStageRules.canApply(sample.copy(eventId=""),shift("Obiekt"),ids))
        assertFalse(CoreGpsStageRules.canApply(sample.copy(timestampMs=0L),shift("Obiekt"),ids))
    }
}
