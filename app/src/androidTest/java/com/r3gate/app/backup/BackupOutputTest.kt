package com.r3gate.app.backup

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class BackupOutputTest {
    @Test fun pdfProducesRealPdfHeaderAndPages() {
        val output = ByteArrayOutputStream()
        WorkMeterPdf.write(output, "Raport czasu pracy",
            listOf("Obiekt", "Godziny"),
            (1..120).asSequence().map { listOf("Łódź Centrum $it", "1,50") })
        val bytes = output.toByteArray()
        assertTrue(bytes.size > 500)
        assertTrue(String(bytes.copyOfRange(0, 5), Charsets.US_ASCII).startsWith("%PDF"))
    }

    @Test fun pdfRejectsMismatchedColumns() {
        try {
            WorkMeterPdf.write(ByteArrayOutputStream(), "Raport",
                listOf("Obiekt", "Czas"), sequenceOf(listOf("only-one")))
            fail("Malformed PDF table accepted")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun filePickerUsesSystemCreateDocument() {
        val name = BackupFilePicker.suggestName(BackupFilePicker.Kind.BACKUP,
            LocalDate.of(2026, 11, 3))
        assertEquals("WorkMeter-backup-2026-11-03.zip", name)
        val intent = BackupFilePicker.createIntent(BackupFilePicker.Kind.BACKUP, name)
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, intent.action)
        assertEquals("application/zip", intent.type)
        assertTrue(intent.hasCategory(Intent.CATEGORY_OPENABLE))
    }

    @Test fun importUsesSystemOpenDocument() {
        val intent = BackupFilePicker.openBackupIntent()
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, intent.action)
        assertTrue(intent.hasCategory(Intent.CATEGORY_OPENABLE))
    }
    @Test fun pdfExportUsesSamePolishJournalDataAsCsv() {
        val journal = """{"objects":[{"id":"x","name":"Łódź"}],"shifts":[{"start":1793689200000,"end":1793692800000,"objectId":"x"}]}"""
        val report = JournalCsvExport.table(journal, JournalCsvExport.Report.WORK_TIME,
            JournalCsvExport.Period())
        assertEquals(1, report.rows.size)
        assertEquals("Łódź", report.rows[0].last())
        val output = ByteArrayOutputStream()
        JournalPdfExport.write(journal, JournalCsvExport.Report.WORK_TIME,
            JournalCsvExport.Period(), output)
        assertTrue(output.size() > 500)
        assertEquals("%PDF-", String(output.toByteArray().copyOfRange(0, 5), Charsets.US_ASCII))
    }

    @Test fun reportsRejectMalformedJournalWithoutWritingPdf() {
        val destination = ByteArrayOutputStream()
        try {
            JournalPdfExport.write("""{"shifts":[{"start":2000,"end":1000}]}""",
                JournalCsvExport.Report.WORK_TIME, JournalCsvExport.Period(), destination)
            fail("Invalid shift accepted")
        } catch (_: IllegalArgumentException) { }
        assertEquals(0, destination.size())
    }

}
