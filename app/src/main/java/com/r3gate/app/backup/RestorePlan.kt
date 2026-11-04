package com.r3gate.app.backup

import java.io.InputStream
import java.security.MessageDigest

/**
 * Produces a restore decision without mutating storage.
 * The UI must present the summary and obtain explicit confirmation before any write.
 * Replacement is the only supported policy: no implicit merge or duplicate creation.
 */
object RestorePlan {
    enum class Decision { NO_CHANGES, REPLACE_REQUIRES_CONFIRMATION }
    data class Preview(
        val decision: Decision,
        val currentSha256: String,
        val incomingSha256: String,
        val objects: Int,
        val archivedObjects: Int,
        val warnings: List<String>,
        val journalJson: String
    )

    private fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    fun prepare(openArchive: () -> InputStream, currentJournal: String, stagedJournal: String? = null): Preview {
        val current = JournalValidator.inspect(currentJournal)
        require(current.canRestore) { "Current journal is invalid; replacement requires recovery review" }
        val incoming = ArchivePreflight.verify(openArchive)
        val oldHash = digest(currentJournal)
        val journal = stagedJournal ?: incoming.journalJson
        require(JournalValidator.inspect(journal).canRestore) { "Invalid staged journal" }
        val newHash = digest(journal)
        return Preview(
            if (oldHash == newHash) Decision.NO_CHANGES else Decision.REPLACE_REQUIRES_CONFIRMATION,
            oldHash, newHash,
            incoming.validation.objectCount,
            incoming.validation.archivedCount,
            incoming.validation.warnings,
            journal
        )
    }

    /**
     * Call only after the user explicitly confirms replacement in the UI.
     * A concurrent journal change prevents applying an outdated plan.
     * Returns JSON for the storage adapter; intentionally performs no write.
     */
    fun confirmedReplacement(preview: Preview, currentJournal: String, userConfirmed: Boolean): String {
        require(userConfirmed) { "Explicit user confirmation required" }
        require(digest(currentJournal) == preview.currentSha256) { "Journal changed since preview" }
        require(digest(preview.journalJson) == preview.incomingSha256) { "Restore payload changed" }
        require(JournalValidator.inspect(preview.journalJson).canRestore) { "Invalid restore payload" }
        return if (preview.decision == Decision.NO_CHANGES) currentJournal else preview.journalJson
    }
}
