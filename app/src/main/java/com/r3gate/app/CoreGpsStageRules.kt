package com.r3gate.app

/**
 * GPS is evidence for suggesting a change, never permission to mutate work time.
 * Only a matching, live, manually opened shift can accept the suggestion.
 */
object CoreGpsStageRules {
    data class Shift(val active: Boolean, val kind: String, val objectId: String?)
    enum class Proposal { START_TRAVEL, ARRIVE_OBJECT }

    fun proposal(kind: CoreGpsProposalInbox.Kind, gpsObjectId: String?,
                 shift: Shift, activeObjectIds: Set<String>): Proposal? {
        if (!shift.active || gpsObjectId.isNullOrBlank() || gpsObjectId !in activeObjectIds) return null
        return when {
            kind == CoreGpsProposalInbox.Kind.DEPARTURE &&
                shift.kind == "Obiekt" && shift.objectId == gpsObjectId -> Proposal.START_TRAVEL
            kind == CoreGpsProposalInbox.Kind.ARRIVAL &&
                shift.kind == "Przejazd" -> Proposal.ARRIVE_OBJECT
            else -> null
        }
    }

    fun canApply(event: CoreGpsProposalInbox.Event, shift: Shift,
                 activeObjectIds: Set<String>): Boolean =
        event.decision == CoreGpsProposalInbox.Decision.PROVISIONAL &&
            proposal(event.kind, event.objectId, shift, activeObjectIds) != null

    fun shouldNotify(event: CoreGpsProposalInbox.Event, shift: Shift,
                     activeObjectIds: Set<String>, shiftStartMs: Long): Boolean =
        event.timestampMs >= shiftStartMs && shiftStartMs > 0 &&
            canApply(event, shift, activeObjectIds)
}
