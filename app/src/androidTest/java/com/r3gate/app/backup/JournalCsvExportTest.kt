package com.r3gate.app.backup

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class JournalCsvExportTest {
    private val data = """{
      "objects":[{"id":"obj1","name":"Łódź Centrum","archived":true}],
      "shifts":[{"start":1793689200000,"end":1793696400000,"objectId":"obj1",
        "stages":[{"start":1793689200000,"end":1793692800000,"kind":"Obiekt","objectId":"obj1"},
                  {"start":1793692800000,"end":1793696400000,"kind":"Przejazd"}]}],
      "materials":[{"date":"2026-11-03","objectId":"obj1","name":"Śruby; M6",
                    "quantity":"2,5","unit":"kg"}]
    }"""
    private fun csv(report: JournalCsvExport.Report, period: JournalCsvExport.Period =
        JournalCsvExport.Period()): String {
        val output = ByteArrayOutputStream()
        JournalCsvExport.write(data, report, period, output)
        return output.toString("UTF-8").removePrefix("\uFEFF")
    }
    @Test fun shiftsIncludePolishNamesAndDate() {
        val text = csv(JournalCsvExport.Report.WORK_TIME)
        assertTrue(text.contains("Łódź Centrum"))
        assertTrue(text.contains("2.00"))
        assertTrue(text.contains("obj1"))
    }
    @Test fun objectSummaryExcludesDrivingTime() {
        val text = csv(JournalCsvExport.Report.BY_OBJECT)
        assertTrue(text.contains("1.00"))
        assertFalse(text.contains("2.00"))
    }
    @Test fun materialsQuoteSemicolon() {
        val text = csv(JournalCsvExport.Report.MATERIALS)
        assertTrue(text.contains("\"Śruby; M6\""))
        assertTrue(text.contains("2,5"))
    }
    @Test fun emptyPeriodReturnsHeadersOnly() {
        val text = csv(JournalCsvExport.Report.WORK_TIME,
            JournalCsvExport.Period(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31)))
        assertEquals(1, text.trim().lines().size)
    }
    @Test fun negativeDurationIsRejected() {
        val broken = """{"shifts":[{"start":2000,"end":1000}]}"""
        try {
            JournalCsvExport.write(broken, JournalCsvExport.Report.WORK_TIME,
                JournalCsvExport.Period(), ByteArrayOutputStream())
            fail("Invalid duration was accepted")
        } catch (_: IllegalArgumentException) { }
    }
}
