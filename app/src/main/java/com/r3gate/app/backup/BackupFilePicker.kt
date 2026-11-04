package com.r3gate.app.backup

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import java.io.OutputStream
import java.time.LocalDate

/** Storage Access Framework entry points, independent of any Activity/navigation. */
object BackupFilePicker {
    private const val ARCHIVE_MIME = "application/zip"
    private const val CSV_MIME = "text/csv"
    private const val PDF_MIME = "application/pdf"

    enum class Kind(val mime: String, val extension: String) {
        BACKUP(ARCHIVE_MIME, "zip"),
        CSV(CSV_MIME, "csv"),
        PDF(PDF_MIME, "pdf")
    }

    fun suggestName(kind: Kind, date: LocalDate = LocalDate.now(), report: String = "backup"): String {
        require(report.matches(Regex("[A-Za-z0-9_-]{1,40}"))) { "Invalid report name" }
        return "WorkMeter-${report}-${date}.${kind.extension}"
    }

    /** The hosting Activity must launch this intent and handle its result URI. */
    fun createIntent(kind: Kind, name: String): Intent {
        require(name.endsWith(".${kind.extension}", ignoreCase = true)) { "Invalid extension" }
        require(!name.contains('/') && !name.contains('\\')) { "Unsafe filename" }
        return Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = kind.mime
            putExtra(Intent.EXTRA_TITLE, name)
        }
    }

    fun openBackupIntent(): Intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = "*/*"
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    /** Never handles filesystem paths and never creates a public temporary file. */
    fun write(resolver: ContentResolver, uri: Uri, producer: (OutputStream) -> Unit) {
        val stream = resolver.openOutputStream(uri, "wt")
            ?: throw IllegalStateException("Cannot open selected document")
        stream.use { producer(it) }
    }
}
