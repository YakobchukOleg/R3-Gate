package com.r3gate.app

/**
 * Core-side, read-only inbox for provisional GPS observations.
 *
 * This is deliberately independent from GPS service classes and saved work
 * shifts. Importing an observation never creates or edits working time, stages,
 * hourly rates or payments. User decisions survive repeated delivery.
 */
object CoreGpsProposalInbox {
    enum class Kind { TRIP_START, TRIP_STOP, ARRIVAL, DEPARTURE, OBJECT_VISIT, GPS_GAP }
    enum class Confidence { LOW, MEDIUM, HIGH }
    enum class Decision { PROVISIONAL, USER_CONFIRMED, USER_REJECTED }

    data class Event(
        val eventId: String,
        val kind: Kind,
        val timestampMs: Long,
        val objectId: String? = null,
        val tripId: String? = null,
        val confidence: Confidence,
        val decision: Decision = Decision.PROVISIONAL,
        val source: String = "gps-v1",
        val evidenceStartMs: Long? = null,
        val evidenceEndMs: Long? = null
    )

    data class ImportResult(
        val events: List<Event>,
        val added: Int,
        val ignoredDuplicates: Int,
        val rejectedInvalid: Int,
        val rejectedConflicts: Int
    )

    private val ordering = compareBy<Event> { it.timestampMs }.thenBy { it.eventId }

    fun valid(event: Event, knownObjectIds: Set<String>): Boolean {
        if (event.eventId.isBlank() || event.timestampMs <= 0L || event.source != "gps-v1") return false
        if (event.objectId != null &&
            (event.objectId.isBlank() || event.objectId !in knownObjectIds)) return false
        if (event.evidenceStartMs != null || event.evidenceEndMs != null) {
            val start = event.evidenceStartMs ?: return false
            val end = event.evidenceEndMs ?: return false
            if (start <= 0L || end < start) return false
        }
        return true
    }

    /**
     * On replay, the first stored event and its explicit decision win.
     * An eventId reused for different evidence is a conflict, not an update.
     */
    fun import(
        existing: Collection<Event>,
        incoming: Collection<Event>,
        knownObjectIds: Set<String>
    ): ImportResult {
        val byId = LinkedHashMap<String, Event>()
        for (event in existing) if (event.eventId.isNotBlank()) {
            byId.putIfAbsent(event.eventId, event)
        }
        var added = 0
        var duplicates = 0
        var invalid = 0
        var conflicts = 0
        for (event in incoming) {
            if (event.decision != Decision.PROVISIONAL ||
                !valid(event, knownObjectIds)) {
                invalid++
                continue
            }
            val previous = byId[event.eventId]
            if (previous == null) {
                byId[event.eventId] = event
                added++
            } else if (sameEvidence(previous, event)) {
                duplicates++
            } else {
                conflicts++
            }
        }
        return ImportResult(byId.values.sortedWith(ordering), added, duplicates, invalid, conflicts)
    }

    /** Changes only the review decision, never the shift or GPS evidence. */
    fun decide(events: List<Event>, eventId: String, decision: Decision): List<Event> {
        require(eventId.isNotBlank() && decision != Decision.PROVISIONAL)
        require(events.any { it.eventId == eventId }) { "Unknown GPS event" }
        return events.map { event ->
            if (event.eventId == eventId && event.decision == Decision.PROVISIONAL)
                event.copy(decision = decision)
            else event
        }
    }

    private fun sameEvidence(a: Event, b: Event): Boolean =
        a.eventId == b.eventId && a.kind == b.kind && a.timestampMs == b.timestampMs &&
            a.objectId == b.objectId && a.tripId == b.tripId &&
            a.confidence == b.confidence && a.source == b.source &&
            a.evidenceStartMs == b.evidenceStartMs && a.evidenceEndMs == b.evidenceEndMs
}
