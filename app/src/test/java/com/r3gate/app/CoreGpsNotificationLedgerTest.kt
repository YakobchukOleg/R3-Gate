package com.r3gate.app

import org.junit.Assert.*
import org.junit.Test

class CoreGpsNotificationLedgerTest {
    private fun event(id: String, at: Long = 1000L,
                      decision: CoreGpsProposalInbox.Decision =
                          CoreGpsProposalInbox.Decision.PROVISIONAL) =
        CoreGpsProposalInbox.Event(
            id, CoreGpsProposalInbox.Kind.ARRIVAL, at, "site-a",
            confidence = CoreGpsProposalInbox.Confidence.HIGH,
            decision = decision
        )

    @Test fun emptyHistoryClearsOldNotificationFlags() {
        assertEquals(emptySet<String>(),
            CoreGpsNotificationLedger.reconcile(setOf("old-1","old-2"), emptyList()))
    }

    @Test fun retainedHistoryKeepsDeliveredIdentity() {
        assertEquals(setOf("id"),CoreGpsNotificationLedger.reconcile(
            setOf("id"),listOf(event("id"))))
    }

    @Test fun missingHistoryIdsArePruned() {
        assertEquals(setOf("live"),CoreGpsNotificationLedger.reconcile(
            setOf("old","live"),listOf(event("live"))))
    }

    @Test fun blankDeliveredIdsArePruned() {
        assertEquals(emptySet<String>(),CoreGpsNotificationLedger.reconcile(
            setOf(""),listOf(event("id"))))
    }

    @Test fun emptyDeliveredSetRemainsEmpty() {
        assertEquals(emptySet<String>(),CoreGpsNotificationLedger.reconcile(
            emptySet(),listOf(event("id"))))
    }

    @Test fun deliveredEventIsNotShownAgainAfterRestart() {
        val events=listOf(event("id"))
        val marked=CoreGpsNotificationLedger.markDelivered(emptySet(),"id",events)
        assertFalse(CoreGpsNotificationLedger.shouldNotify("id",marked,events))
    }

    @Test fun unseenPendingEventCanNotify() {
        assertTrue(CoreGpsNotificationLedger.shouldNotify(
            "id",emptySet(),listOf(event("id"))))
    }

    @Test fun reviewedEventCannotNotifyAgain() {
        val reviewed=event("id",decision=CoreGpsProposalInbox.Decision.USER_CONFIRMED)
        assertFalse(CoreGpsNotificationLedger.shouldNotify("id",emptySet(),listOf(reviewed)))
    }

    @Test fun rejectedEventCannotNotifyAgain() {
        val rejected=event("id",decision=CoreGpsProposalInbox.Decision.USER_REJECTED)
        assertFalse(CoreGpsNotificationLedger.shouldNotify("id",emptySet(),listOf(rejected)))
    }

    @Test fun unknownEventCannotBeMarkedDelivered() {
        assertEquals(emptySet<String>(),CoreGpsNotificationLedger.markDelivered(
            emptySet(),"missing",listOf(event("id"))))
    }

    @Test fun blankEventCannotBeMarkedDelivered() {
        assertEquals(emptySet<String>(),CoreGpsNotificationLedger.markDelivered(
            emptySet(),"",listOf(event("id"))))
    }

    @Test fun deliveryMarkDoesNotLoseOtherRetainedIds() {
        assertEquals(setOf("old","new"),CoreGpsNotificationLedger.markDelivered(
            setOf("old"),"new",listOf(event("old"),event("new"))))
    }

    @Test fun compactionPrunesOlderNotificationFlags() {
        val events=(0 until 700).map { event("id-$it",at=1000L+it) }
        val retained=CoreGpsProposalStore.compact(events)
        val delivered=events.mapTo(HashSet()) { it.eventId }
        val flags=CoreGpsNotificationLedger.reconcile(delivered,retained)
        assertEquals(CoreGpsProposalStore.MAX_EVENTS,flags.size)
        assertFalse("id-0" in flags)
        assertTrue("id-699" in flags)
    }

    @Test fun repeatedCompactionIsIdempotent() {
        val events=(0 until 700).map { event("id-$it",at=1000L+it) }
        val retained=CoreGpsProposalStore.compact(events)
        val first=CoreGpsNotificationLedger.reconcile(events.mapTo(HashSet()) { it.eventId },retained)
        assertEquals(first,CoreGpsNotificationLedger.reconcile(first,retained))
    }

    @Test fun replayOfOldNotificationDoesNotEvictCurrentDeliveryRecord() {
        val retained=listOf(event("latest",1000))
        assertEquals(setOf("latest"),CoreGpsNotificationLedger.markDelivered(
            setOf("old","latest"),"old",retained))
    }

    @Test fun emptyIdentityCannotNotify() {
        assertFalse(CoreGpsNotificationLedger.shouldNotify(
            "",emptySet(),listOf(event("id"))))
    }
}
