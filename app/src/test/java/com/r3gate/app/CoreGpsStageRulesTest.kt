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
}
