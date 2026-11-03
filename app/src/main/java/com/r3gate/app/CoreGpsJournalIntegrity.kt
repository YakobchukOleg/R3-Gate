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
            if (row == null || row.event !in setOf("ARRIVAL", "DEPARTURE") ||
                row.timeMs <= 0L || row.objectId.isNullOrBlank() ||
                row.eventId?.isBlank() == true) return null
            clean.add(row)
        }
        return clean
    }

    /** Validate exact integral timestamps without accepting truncated floating values. */
    fun exactTime(value: Number?): Long? {
        if (value == null) return null
        val number = value.toDouble()
        if (!number.isFinite() || number <= 0.0 || number > Long.MAX_VALUE.toDouble()) return null
        val whole = value.toLong()
        return whole.takeIf { it > 0L && number == whole.toDouble() }
    }
}
