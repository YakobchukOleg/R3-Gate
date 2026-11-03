package com.r3gate.app

/**
 * Read-only model for the native Obiekty details screen.
 * Time and archive state are calculated by the existing owner, not here.
 */
object ObjectDetailsUiModel {
    data class Day(val date: String, val duration: String)

    data class Content(
        val tag: String,
        val name: String,
        val city: String,
        val address: String,
        val organization: String,
        val notes: String,
        val archived: Boolean,
        val duration: String,
        val days: List<Day>,
        val hasUnassignedLegacyShifts: Boolean
    )

    data class ViewState(
        val heading: String,
        val title: String,
        val location: String?,
        val organization: String?,
        val notes: String?,
        val archiveStatus: String,
        val totalTime: String,
        val historyHeading: String,
        val days: List<Day>,
        val emptyHistory: String?,
        val legacyNote: String?,
        val archiveHint: String
    )

    fun display(content: Content): ViewState {
        val name = content.name.trim().ifEmpty { content.organization.trim() }
            .ifEmpty { content.tag.trim() }.ifEmpty { "Obiekt" }
        val location = listOf(content.city.trim(), content.address.trim())
            .filter { it.isNotEmpty() }.joinToString(", ").takeIf { it.isNotBlank() }
        return ViewState(
            heading = ObjectDetailsUiText.heading(content.tag),
            title = name,
            location = location,
            organization = content.organization.trim().takeIf { it.isNotEmpty() && it != name },
            notes = content.notes.trim().takeIf { it.isNotEmpty() },
            archiveStatus = ObjectUiPresentation.status(content.archived),
            totalTime = ObjectDetailsUiText.totalTimeLabel(content.duration),
            historyHeading = "Czas na obiekcie",
            days = content.days.toList(),
            emptyHistory = if (content.days.isEmpty()) ObjectDetailsUiText.emptyHistoryLabel() else null,
            legacyNote = if (content.hasUnassignedLegacyShifts)
                ObjectDetailsUiText.legacyUnassignedLabel() else null,
            archiveHint = ObjectDetailsUiText.archiveHint(content.archived)
        )
    }
}
