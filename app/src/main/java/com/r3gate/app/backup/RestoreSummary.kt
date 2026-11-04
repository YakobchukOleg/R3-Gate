package com.r3gate.app.backup

import org.json.JSONObject

/** Counts from the verified archive, for the pre-confirmation screen. */
data class RestoreSummary(
    val objects: Int,
    val archivedObjects: Int,
    val closedShifts: Int,
    val activeShift: Boolean,
    val photos: Int,
    val materials: Int,
    val notes: Int,
    val payments: Int,
    val expenses: Int,
    val referenceWarnings: Int,
    val replacesJournal: Boolean
) {
    companion object {
        internal fun from(preview: RestorePlan.Preview, photos: Int): RestoreSummary {
            val journal = JSONObject(preview.journalJson)
            fun count(key: String) = journal.optJSONArray(key)?.length() ?: 0
            return RestoreSummary(
                preview.objects, preview.archivedObjects, count("shifts"), journal.optJSONObject("active") != null,
                photos, count("materials"), count("notes"), count("payments"), count("expenses"),
                preview.warnings.size, preview.decision == RestorePlan.Decision.REPLACE_REQUIRES_CONFIRMATION
            )
        }
    }
}
