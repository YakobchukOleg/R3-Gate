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
    @Test fun localBackupPreviewDoesNotReplaceJournalAndCanBeDiscarded() {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("r3_gate", android.content.Context.MODE_PRIVATE)
        val original = prefs.getString("data", null)
        val directory = java.io.File(context.filesDir, LocalBackupWorker.DIRECTORY)
        val backup = LocalBackupStore.create(directory, """{"objects":[{"id":"backup-test"}]}""",
            { error("No attachments in fixture") }, retain = 30)
        try {
            val session = LocalBackups.prepareRestore(context, backup.name)
            assertEquals(1, session.preview.objects)
            assertEquals(original, prefs.getString("data", null))
            session.discard()
            assertFalse(session.stagingDirectory.exists())
            assertEquals(original, prefs.getString("data", null))
        } finally {
            backup.delete()
        }
    }

    @Test fun freshLocalExportRoundTripsJournalAndFinance() {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("r3_gate", android.content.Context.MODE_PRIVATE)
        val original = prefs.getString("data", null)
        val backupPrefs = context.getSharedPreferences(LocalBackupWorker.PREFERENCES, android.content.Context.MODE_PRIVATE)
        val lastFile = backupPrefs.getString(LocalBackupWorker.LAST_FILE, null)
        val lastSuccess = backupPrefs.getString(LocalBackupWorker.LAST_SUCCESS, null)
        val oldError = backupPrefs.getString(LocalBackupWorker.STATUS_ERROR, null)
        val target = java.io.File.createTempFile("backup-output-", ".zip", context.cacheDir)
        var copy: LocalBackups.Copy? = null
        val journal = """{"objects":[{"id":"archived","archived":true}],"shifts":[{"id":"shift","objectId":"archived","start":100,"end":200,"rate":"30"}],"payments":[{"id":"payment","amount":"120.50"}],"expenses":[{"id":"expense","amount":"42.15"}]}"""
        try {
            assertTrue(prefs.edit().putString("data", journal).commit())
            val created = LocalBackups.exportCurrent(context, android.net.Uri.fromFile(target))
            copy = created
            assertEquals(created, LocalBackups.status(context).latestCopy)
            assertEquals(created.sizeBytes, target.length())
            val originalData = org.json.JSONObject(journal)
            val archived = org.json.JSONObject(ArchivePreflight.verify { target.inputStream() }.journalJson)
            for (field in listOf("objects", "shifts", "payments", "expenses")) {
                assertEquals(originalData.getJSONArray(field).toString(), archived.getJSONArray(field).toString())
            }

            assertTrue(prefs.edit().putString("data", "{}").commit())
            val session = LocalBackups.prepareImport(context, android.net.Uri.fromFile(target))
            assertEquals(1, session.summary.closedShifts)
            assertEquals(1, session.summary.payments)
            assertEquals(1, session.summary.expenses)
            assertEquals("{}", prefs.getString("data", null))
            assertEquals(JournalRestoreStore.Outcome.RESTORED, session.commit(context, confirmed = true))
            assertEquals(archived.toString(), prefs.getString("data", null))
            session.stagingDirectory.delete()
        } finally {
            prefs.edit().putString("data", original).commit()
            backupPrefs.edit().putString(LocalBackupWorker.LAST_FILE, lastFile)
                .putString(LocalBackupWorker.LAST_SUCCESS, lastSuccess).putString(LocalBackupWorker.STATUS_ERROR, oldError).commit()
            copy?.let { java.io.File(context.filesDir, LocalBackupWorker.DIRECTORY + "/" + it.name).delete() }
            target.delete()
        }
    }

    @Test fun confirmingIdenticalPhotoFreeArchiveCleansStaging() {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("r3_gate", android.content.Context.MODE_PRIVATE)
        val original = prefs.getString("data", null)
        val output = ByteArrayOutputStream()
        PhotoArchivePackage.write("{}", emptyList(), { error("Unexpected photo") }, output)
        try {
            assertTrue(prefs.edit().putString("data", "{}").commit())
            val session = PhotoRestoreCoordinator.prepare(context) { output.toByteArray().inputStream() }
            assertFalse(session.summary.replacesJournal)
            assertEquals(JournalRestoreStore.Outcome.UNCHANGED, session.commit(context, confirmed = true))
            assertFalse(session.stagingDirectory.exists())
        } finally {
            prefs.edit().putString("data", original).commit()
        }
    }

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

    @Test fun oneLongPdfRowContinuesAcrossPagesWithoutEllipsis() {
        val context=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val file=java.io.File(context.getExternalFilesDir(null),"backup-pdf-wrapped-row.pdf")
        val body=(1..100).joinToString("\n") { "Łódź · materiał $it" }+"\nKONIEC-żółć-100"
        file.outputStream().use { output ->
            WorkMeterPdf.write(output,"Raport materiałów",listOf("Obiekt i opis"),sequenceOf(listOf(body)))
        }
        android.os.ParcelFileDescriptor.open(file,android.os.ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            android.graphics.pdf.PdfRenderer(descriptor).use { renderer ->
                assertTrue("A single long row must span pages",renderer.pageCount>=3)
                renderer.openPage(renderer.pageCount-1).use { page -> assertEquals(595,page.width) }
            }
        }
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("cp "+file.absolutePath+" /sdcard/Download/backup-pdf-wrapped-row.pdf").use {
                java.io.FileInputStream(it.fileDescriptor).use { input -> input.readBytes() }
            }
    }
    @Test fun pdfWrapsLongPolishHeadersAndTitle() {
        val output=ByteArrayOutputStream()
        WorkMeterPdf.write(output,"Raport czasu pracy oraz materiałów na wszystkich obiektach w wybranym okresie",
            listOf("Pełny adres i nazwa obiektu", "Opis zużytych materiałów oraz jednostka"),
            sequenceOf(listOf("Łódź, Jana Stanisławskiego 5", "Przewód elektryczny 3 × 2,5 mm²")))
        assertTrue(output.size()>100)
        assertTrue(output.toString(Charsets.ISO_8859_1.name()).startsWith("%PDF-"))
    }
    @Test fun oversizedPdfHeaderIsRejectedBeforeWriting() {
        val output=ByteArrayOutputStream()
        try {
            WorkMeterPdf.write(output,"Raport",listOf("nagłówek\n".repeat(100)),emptySequence())
            fail("Header consumed the entire page")
        } catch (_:IllegalArgumentException) { assertEquals(0,output.size()) }
    }

}
