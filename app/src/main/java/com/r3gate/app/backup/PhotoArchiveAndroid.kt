package com.r3gate.app.backup

import android.content.ContentResolver
import android.net.Uri
import java.io.InputStream
import java.io.OutputStream

/** ContentResolver adapter. Unreadable photos abort creation of a supposedly complete backup. */
object PhotoArchiveAndroid {
    fun write(journal: String, resolver: ContentResolver, destination: OutputStream): WorkMeterArchive.Inspection {
        val references = PhotoArchiveJournal.references(journal)
        return PhotoArchivePackage.write(journal, references, { value ->
            val uri = Uri.parse(value)
            require(uri.scheme == "content" || uri.scheme == "file") { "Unsupported photo URI scheme" }
            resolver.openInputStream(uri) ?: throw java.io.FileNotFoundException("Photo is unavailable")
        }, destination)
    }

    fun verify(open: () -> InputStream): PhotoArchivePackage.Report {
        val preview = ArchivePreflight.verify(open)
        return PhotoArchivePackage.verifyComplete(open, PhotoArchiveJournal.references(preview.journalJson))
    }
}
