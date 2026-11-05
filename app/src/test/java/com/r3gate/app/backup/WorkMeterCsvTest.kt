package com.r3gate.app.backup

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.time.LocalDate

class WorkMeterCsvTest {
    @Test fun polishCharactersAndExcelBom() {
        val output = ByteArrayOutputStream()
        WorkMeterCsv.write(output, listOf("Obiekt", "Opis"),
            sequenceOf(listOf("Łódź", "Zażółć gęślą jaźń")))
        assertEquals("\uFEFFObiekt;Opis\r\nŁódź;Zażółć gęślą jaźń\r\n",
            output.toString("UTF-8"))
    }

    @Test fun quotesDelimiterAndNewlines() {
        val output = ByteArrayOutputStream()
        WorkMeterCsv.write(output, listOf("Miasto", "Opis"),
            sequenceOf(listOf("Gdańsk;Północ", "Pierwsza \"praca\"\ndruga")), false)
        assertEquals("Miasto;Opis\r\n\"Gdańsk;Północ\";\"Pierwsza \"\"praca\"\"\ndruga\"\r\n",
            output.toString("UTF-8"))
    }

    @Test fun spreadsheetFormulasAreEscaped() {
        for (value in listOf("=1+1", "+cmd", "-3+2", "@NOW()", "  =SUM(A1)", "\t=2+2")) {
            assertTrue(WorkMeterCsv.escape(value).startsWith("'"))
        }
        assertEquals("Praca", WorkMeterCsv.escape("Praca"))
    }

    @Test fun refusesMalformedTables() {
        try {
            WorkMeterCsv.write(ByteArrayOutputStream(), listOf("A", "B"), sequenceOf(listOf("one")))
            fail("Missing column accepted")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun dateStampedSafeFilename() {
        assertEquals("WorkMeter-czas-2026-11-03.csv",
            WorkMeterCsv.suggestedName("czas", LocalDate.of(2026, 11, 3)))
        try {
            WorkMeterCsv.suggestedName("../escape", LocalDate.now())
            fail("Unsafe filename accepted")
        } catch (_: IllegalArgumentException) { }
    }
}
