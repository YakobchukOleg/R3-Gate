package com.r3gate.app

/**
 * Bounded notification replay ledger. Delivery IDs are retained only while
 * their corresponding proposal still exists in the durable Core inbox.
 * It never changes a user's decision or the work-time journal.
 */
object CoreGpsNotificationLedger {
    fun reconcile(
        delivered: Set<String>,
        retainedEvents: Collection<CoreGpsProposalInbox.Event>
    ): Set<String> {
        if (delivered.isEmpty() || retainedEvents.isEmpty()) return emptySet()
        val retainedIds = retainedEvents.mapTo(HashSet()) { it.eventId }
        return delivered.filterTo(LinkedHashSet()) { it.isNotBlank() && it in retainedIds }
    }

    fun markDelivered(
        delivered: Set<String>,
        eventId: String,
        retainedEvents: Collection<CoreGpsProposalInbox.Event>
    ): Set<String> {
        val active = reconcile(delivered, retainedEvents)
        if (eventId.isBlank() || retainedEvents.none { it.eventId == eventId }) return active
        return active + eventId
    }

    fun shouldNotify(
        eventId: String,
        delivered: Set<String>,
        retainedEvents: Collection<CoreGpsProposalInbox.Event>
    ): Boolean =
        eventId.isNotBlank() &&
            eventId !in delivered &&
            retainedEvents.any { it.eventId == eventId &&
                it.decision == CoreGpsProposalInbox.Decision.PROVISIONAL }
}
