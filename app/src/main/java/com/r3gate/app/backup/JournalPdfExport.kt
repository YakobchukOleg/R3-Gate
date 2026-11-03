package com.r3gate.app.backup

import java.io.OutputStream

/**
 * Renders the same validated source rows used by CSV. Does not mutate the journal.
 * The calling Activity chooses the destination using the Android SAF.
 */
object JournalPdfExport {
    fun write(
        json: String,
        report: JournalCsvExport.Report,
        period: JournalCsvExport.Period,
        destination: OutputStream
    ) {
        val table = JournalCsvExport.table(json, report, period)
        val title = when (report) {
            JournalCsvExport.Report.WORK_TIME -> "Raport czasu pracy"
            JournalCsvExport.Report.BY_OBJECT -> "Zestawienie czasu na obiektach"
            JournalCsvExport.Report.MATERIALS -> "Zestawienie materiałów"
        }
        WorkMeterPdf.write(destination, title, table.headers, table.rows.asSequence())
    }
}
