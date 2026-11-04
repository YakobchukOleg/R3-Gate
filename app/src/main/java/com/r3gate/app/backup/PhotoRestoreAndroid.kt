package com.r3gate.app.backup

import android.content.Context
import androidx.core.content.FileProvider
import java.io.InputStream

/**
 * Prepares a journal with new, private FileProvider photo URIs.
 * No journal write occurs here. Caller must obtain confirmation and perform
 * a separately verified commit; discard() cleans failed/unwanted previews.
 */
object PhotoRestoreAndroid {
    data class Prepared(
        val journalJson: String,
        val staged: PhotoRestoreStage.Result,
        val remappedCount: Int
    ) {
        fun discard() = staged.discard()
    }

    fun prepare(context: Context, openArchive: () -> InputStream): Prepared {
        val preview = ArchivePreflight.verify(openArchive)
        val references = PhotoArchiveJournal.references(preview.journalJson)
        val parent = java.io.File(context.filesDir, "photos")
        val staged = PhotoRestoreStage.prepare(openArchive, references, parent)
        try {
            val changes = staged.files.mapValues { (_, file) ->
                FileProvider.getUriForFile(context, "com.r3gate.app.photos", file).toString()
            }
            val rewritten = PhotoRestoreJournal.remap(preview.journalJson, changes)
            require(JournalValidator.inspect(rewritten).canRestore) { "Remapped journal invalid" }
            return Prepared(rewritten, staged, changes.size)
        } catch (failure: Exception) {
            staged.discard()
            throw failure
        }
    }
}
