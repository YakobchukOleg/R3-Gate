package com.r3gate.app

import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * Versioned, local-only persistence for Core GPS proposals.
 *
 * This does not touch confirmed shifts, payments or the GPS service journal.
 * The caller supplies a small key/value adapter; corrupted or unknown formats
 * are rejected without overwriting the saved original.
 */
object CoreGpsProposalStore {
    const val KEY = "coreGpsProposalsV1"
    const val MAX_EVENTS = 512
    private const val HEADER = "workmeter-core-gps-proposals-v1"
    private const val FIELD_COUNT = 11

    interface Storage {
        fun read(key: String): String?
        /** Return false if the write could not be durably committed. */
        fun write(key: String, value: String): Boolean
    }

    sealed class ReadResult {
        data class Success(val events: List<CoreGpsProposalInbox.Event>) : ReadResult()
        data class Corrupt(val reason: String) : ReadResult()
    }

    data class ChangeResult(
        val changed: Boolean,
        val state: ReadResult,
        val imported: CoreGpsProposalInbox.ImportResult? = null
    )

    /**
     * Bound local history without rewriting timestamps or review decisions.
     * The newest observations always win; a repeated old event cannot evict
     * a newer confirmed/rejected decision.
     */
    fun compact(events: Collection<CoreGpsProposalInbox.Event>): List<CoreGpsProposalInbox.Event> =
        events.sortedWith(compareBy<CoreGpsProposalInbox.Event> { it.timestampMs }.thenBy { it.eventId })
            .takeLast(MAX_EVENTS)

    fun read(storage: Storage): ReadResult = decode(storage.read(KEY))

    fun import(
        storage: Storage,
        incoming: Collection<CoreGpsProposalInbox.Event>,
        knownObjectIds: Set<String>
    ): ChangeResult {
        val current = read(storage)
        if (current !is ReadResult.Success) return ChangeResult(false, current)
        val result = CoreGpsProposalInbox.import(current.events, incoming, knownObjectIds)
        if (result.added == 0) return ChangeResult(false, current, result)
        val retained = compact(result.events)
        val persisted = result.copy(events = retained)
        // Old evidence that falls outside the retention window must not
        // rewrite the journal or falsely report a durable state change.
        if (retained == current.events) return ChangeResult(false, current, persisted)
        if (!storage.write(KEY, encode(retained)))
            return ChangeResult(false, ReadResult.Corrupt("Storage write failed"), persisted)
        return ChangeResult(true, ReadResult.Success(retained), persisted)
    }

    fun decide(
        storage: Storage,
        eventId: String,
        decision: CoreGpsProposalInbox.Decision
    ): ChangeResult {
        val current = read(storage)
        if (current !is ReadResult.Success) return ChangeResult(false, current)
        val updated = CoreGpsProposalInbox.decide(current.events, eventId, decision)
        if (updated == current.events) return ChangeResult(false, current)
        val retained = compact(updated)
        if (!storage.write(KEY, encode(retained)))
            return ChangeResult(false, ReadResult.Corrupt("Storage write failed"))
        return ChangeResult(true, ReadResult.Success(retained))
    }

    fun encode(events: Collection<CoreGpsProposalInbox.Event>): String {
        require(events.map { it.eventId }.distinct().size == events.size) { "Duplicate event ID" }
        val lines = events.sortedWith(compareBy<CoreGpsProposalInbox.Event> { it.timestampMs }.thenBy { it.eventId })
            .map { event ->
                require(event.eventId.isNotBlank() && event.timestampMs > 0L &&
                    event.evidenceStartMs == null == (event.evidenceEndMs == null) &&
                    (event.evidenceStartMs == null ||
                        (event.evidenceStartMs > 0L && event.evidenceEndMs!! >= event.evidenceStartMs)))
                listOf(
                    field(event.eventId), event.kind.name, event.timestampMs.toString(),
                    field(event.objectId), field(event.tripId), event.confidence.name,
                    event.decision.name, field(event.source), number(event.evidenceStartMs),
                    number(event.evidenceEndMs), "1"
                ).joinToString("\t")
            }
        return (listOf(HEADER) + lines).joinToString("\n")
    }

    fun decode(raw: String?): ReadResult {
        if (raw.isNullOrEmpty()) return ReadResult.Success(emptyList())
        val lines = raw.split("\n")
        if (lines.firstOrNull() != HEADER) return ReadResult.Corrupt("Unsupported format")
        val events = ArrayList<CoreGpsProposalInbox.Event>()
        val ids = HashSet<String>()
        for (line in lines.drop(1)) {
            if (line.isEmpty()) return ReadResult.Corrupt("Empty row")
            try {
                val values = line.split("\t")
                if (values.size != FIELD_COUNT || values[10] != "1") return ReadResult.Corrupt("Invalid row")
                val event = CoreGpsProposalInbox.Event(
                    eventId = text(values[0]) ?: return ReadResult.Corrupt("Null eventId"),
                    kind = CoreGpsProposalInbox.Kind.valueOf(values[1]),
                    timestampMs = values[2].toLong(),
                    objectId = text(values[3]),
                    tripId = text(values[4]),
                    confidence = CoreGpsProposalInbox.Confidence.valueOf(values[5]),
                    decision = CoreGpsProposalInbox.Decision.valueOf(values[6]),
                    source = text(values[7]) ?: return ReadResult.Corrupt("Null source"),
                    evidenceStartMs = parseNumber(values[8]),
                    evidenceEndMs = parseNumber(values[9])
                )
                if (event.eventId.isBlank() || event.timestampMs <= 0L ||
                    event.source != "gps-v1" ||
                    event.evidenceStartMs == null != (event.evidenceEndMs == null) ||
                    (event.evidenceStartMs != null &&
                        (event.evidenceStartMs <= 0L || event.evidenceEndMs!! < event.evidenceStartMs)) ||
                    !ids.add(event.eventId)) return ReadResult.Corrupt("Invalid event")
                events.add(event)
            } catch (_: IllegalArgumentException) {
                return ReadResult.Corrupt("Unrecognized field")
            }
        }
        return ReadResult.Success(events.sortedWith(compareBy<CoreGpsProposalInbox.Event> { it.timestampMs }.thenBy { it.eventId }))
    }

    private fun field(value: String?): String =
        if (value == null) "-" else "~" + Base64.getUrlEncoder().withoutPadding()
            .encodeToString(value.toByteArray(StandardCharsets.UTF_8))

    private fun text(value: String): String? {
        if (value == "-") return null
        require(value.startsWith("~"))
        return String(Base64.getUrlDecoder().decode(value.substring(1)), StandardCharsets.UTF_8)
    }

    private fun number(value: Long?): String = value?.toString() ?: "-"
    private fun parseNumber(value: String): Long? = if (value == "-") null else value.toLong()
}
