package com.r3gate.app.backup

import android.content.Context
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class BackupReportExportTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs get()=context.getSharedPreferences("r3_gate",Context.MODE_PRIVATE)
    private var original: String?=null
    private val source="""{"objects":[{"id":"site","name":"Łódź"}],"shifts":[{"start":1793689200000,"end":1793692800000,"objectId":"site"}],"materials":[{"date":"2026-11-03","objectId":"site","name":"=SUM(A1)","quantity":"2,5","unit":"kg"}],"walletSettings":{"defaultRate":"98765"},"payments":[{"id":"SECRET_PAYMENT"}]}"""
    private fun parts()=context.cacheDir.listFiles().orEmpty().filter { it.name.startsWith("workmeter-report-") }.map { it.name }.toSet()
    @Before fun setup() { original=prefs.getString("data",null);prefs.edit().putString("data",source).commit() }
    @After fun restore() { prefs.edit().apply { if(original==null) remove("data") else putString("data",original) }.commit() }
    private fun csv(report: JournalCsvExport.Report=JournalCsvExport.Report.WORK_TIME)=BackupReportExport.Request(BackupFilePicker.Kind.CSV,report)
    @Test fun everyReportHasADatedSoloFilenameInBothFormats() {
        for(kind in listOf(BackupFilePicker.Kind.CSV,BackupFilePicker.Kind.PDF)) for(report in JournalCsvExport.Report.entries) {
            val name=BackupReportExport.Request(kind,report).suggestedName(LocalDate.of(2026,11,5))
            assertTrue(name.startsWith("WorkMeter-SOLO-"));assertTrue(name.endsWith("2026-11-05.${kind.extension}"));assertFalse(name.contains('/'))
        }
    }
    @Test fun zipCannotBeMislabeledAsAReport() { assertThrows(IllegalArgumentException::class.java) { BackupReportExport.Request(BackupFilePicker.Kind.BACKUP,JournalCsvExport.Report.WORK_TIME) } }
    @Test fun csvUsesActualPolishDataAndCleansPrivateTemporaryFile() {
        val file=File.createTempFile("report-destination-",".csv",context.cacheDir)
        val before=parts()
        try {
            BackupReportExport.write(context,Uri.fromFile(file),csv())
            val text=file.readText();assertTrue(text.startsWith("\uFEFF"));assertTrue(text.contains("Łódź"));assertTrue(text.contains("1.00"))
            assertEquals(before,parts());assertEquals(source,prefs.getString("data",null))
        } finally {file.delete()}
    }
    @Test fun pdfIsReadableByTheAndroidPdfRenderer() {
        val file=File.createTempFile("report-destination-",".pdf",context.cacheDir)
        try {
            BackupReportExport.write(context,Uri.fromFile(file),BackupReportExport.Request(BackupFilePicker.Kind.PDF,JournalCsvExport.Report.WORK_TIME))
            android.os.ParcelFileDescriptor.open(file,android.os.ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                android.graphics.pdf.PdfRenderer(fd).use { assertTrue(it.pageCount>=1) }
            }
            assertEquals(source,prefs.getString("data",null))
        } finally {file.delete()}
    }
    @Test fun invalidJournalDoesNotTruncateTheChosenExistingDocument() {
        val file=File.createTempFile("report-destination-",".csv",context.cacheDir).apply { writeText("KEEP ORIGINAL") }
        val before=parts();val invalid="""{"shifts":[{"start":2000,"end":1000}]}"""
        prefs.edit().putString("data",invalid).commit()
        try {
            assertThrows(IllegalArgumentException::class.java) { BackupReportExport.write(context,Uri.fromFile(file),csv()) }
            assertEquals("KEEP ORIGINAL",file.readText());assertEquals(before,parts());assertEquals(invalid,prefs.getString("data",null))
        } finally {file.delete()}
    }
    @Test fun failedDestinationLeavesJournalAndNoPrivateReportDebris() {
        val before=parts();val absent=File(context.cacheDir,"missing-${java.util.UUID.randomUUID()}/out.csv")
        assertThrows(Exception::class.java) { BackupReportExport.write(context,Uri.fromFile(absent),csv()) }
        assertEquals(before,parts());assertEquals(source,prefs.getString("data",null))
    }
    @Test fun spreadsheetFormulaInMaterialNameIsEscaped() {
        val file=File.createTempFile("report-destination-",".csv",context.cacheDir)
        try {BackupReportExport.write(context,Uri.fromFile(file),csv(JournalCsvExport.Report.MATERIALS));assertTrue(file.readText().contains("'=SUM(A1)"))}finally{file.delete()}
    }
    @Test fun reportsDoNotExportProtectedFinancialFields() {
        val file=File.createTempFile("report-destination-",".csv",context.cacheDir)
        try { for(report in JournalCsvExport.Report.entries) {
            BackupReportExport.write(context,Uri.fromFile(file),csv(report));val text=file.readText()
            assertFalse(text.contains("98765"));assertFalse(text.contains("SECRET_PAYMENT"))
        } } finally{file.delete()}
    }
}
