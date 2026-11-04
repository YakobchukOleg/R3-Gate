package com.r3gate.app

/**
 * A damaged producer journal cannot be treated as a partial source of
 * authoritative location evidence. Reject the entire batch instead of
 * silently skipping malformed rows.
 */
object CoreGpsJournalIntegrity {
    fun validated(rows: Collection<CoreGpsJournalAdapter.Raw?>): List<CoreGpsJournalAdapter.Raw>? {
        val clean = ArrayList<CoreGpsJournalAdapter.Raw>()
        for (row in rows) {
            if (row == null || row.event !in setOf("ARRIVAL", "DEPARTURE", "OBJECT_VISIT") ||
                row.timeMs <= 0L || row.objectId.isNullOrBlank() ||
                row.eventId?.isBlank() == true || !validMetadata(row)) return null
            clean.add(row)
        }
        return clean
    }

    fun validMetadata(row: CoreGpsJournalAdapter.Raw): Boolean {
        val metadata = row.metadata ?: return true
        val start = metadata.evidenceStartMs
        val end = metadata.evidenceEndMs
        return metadata.timestampMs == row.timeMs && metadata.source == "gps-v1" &&
            metadata.confirmation == "PROVISIONAL" && metadata.tripId?.isBlank() != true &&
            ((start == null && end == null) ||
                (start != null && end != null && start > 0L && end in start..row.timeMs))
    }

    /** Validate exact integral timestamps without accepting truncated floating values. */
    fun exactTime(value: Number?): Long? {
        if (value == null) return null
        if (value is Long || value is Int || value is Short || value is Byte)
            return value.toLong().takeIf { it > 0L }
        val number = value.toDouble()
        if (!number.isFinite() || number <= 0.0 || number >= Long.MAX_VALUE.toDouble()) return null
        val whole = value.toLong()
        return whole.takeIf { it > 0L && number == whole.toDouble() }
    }
}
