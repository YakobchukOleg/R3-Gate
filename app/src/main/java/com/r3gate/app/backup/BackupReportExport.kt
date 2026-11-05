package com.r3gate.app.backup

import android.content.Context
import android.net.Uri
import java.io.File
import java.time.LocalDate

/** Read-only, offline reports. Validate and render privately before opening the chosen document. */
object BackupReportExport {
    data class Request(
        val kind: BackupFilePicker.Kind,
        val report: JournalCsvExport.Report,
        val period: JournalCsvExport.Period = JournalCsvExport.Period()
    ) {
        init { require(kind == BackupFilePicker.Kind.CSV || kind == BackupFilePicker.Kind.PDF) }
        fun suggestedName(date: LocalDate = LocalDate.now()): String {
            val slug=when(report) {
                JournalCsvExport.Report.WORK_TIME -> "SOLO-przedzialy-pracy"
                JournalCsvExport.Report.BY_OBJECT -> "SOLO-czas-na-obiektach"
                JournalCsvExport.Report.MATERIALS -> "SOLO-materialy"
                JournalCsvExport.Report.STAGES -> "SOLO-etapy-pracy"
            }
            return BackupFilePicker.suggestName(kind,date,slug)
        }
    }

    fun title(report: JournalCsvExport.Report): String = when(report) {
        JournalCsvExport.Report.WORK_TIME -> "Przedziały dni pracy (brutto)"
        JournalCsvExport.Report.BY_OBJECT -> "Czas na obiektach"
        JournalCsvExport.Report.MATERIALS -> "Materiały"
        JournalCsvExport.Report.STAGES -> "Etapy pracy i przejazdy"
    }

    fun write(context: Context, destination: Uri, request: Request) {
        val journal=context.getSharedPreferences("r3_gate",Context.MODE_PRIVATE).getString("data","{}")
            ?: error("Missing journal")
        val temporary=File.createTempFile("workmeter-report-",".part",context.cacheDir)
        try {
            temporary.outputStream().use { output ->
                when(request.kind) {
                    BackupFilePicker.Kind.CSV -> JournalCsvExport.write(journal,request.report,request.period,output)
                    BackupFilePicker.Kind.PDF -> JournalPdfExport.write(journal,request.report,request.period,output)
                    BackupFilePicker.Kind.BACKUP -> error("ZIP is not a report format")
                }
            }
            BackupFilePicker.write(context.contentResolver,destination) { output ->
                temporary.inputStream().use { input -> input.copyTo(output) }
            }
        } finally { temporary.delete() }
    }
}
