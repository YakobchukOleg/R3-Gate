package com.r3gate.app.backup

import com.r3gate.app.ShiftContinuity
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ObjectHistoryExportTest {
    private val zone = ZoneId.of("Europe/Warsaw")
    private val day = LocalDate.of(2026, 11, 6)
    private fun at(hour: Int, minute: Int) = day.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
    private fun roundedShift(): JSONObject {
        val start = at(8, 29)
        val end = at(9, 30)
        val stage = JSONObject().put("start", start).put("end", end).put("kind", "Obiekt").put("objectId", "site")
        return ShiftContinuity.rounded(JSONObject().put("id", "shift").put("start", start).put("end", end)
            .put("stages", JSONArray().put(stage)))
    }
    private fun table(report: JournalCsvExport.Report) = JournalCsvExport.table(
        JSONObject().put("shifts", JSONArray().put(roundedShift())).toString(), report, JournalCsvExport.Period())

    @Test fun objectTotalsAcceptActualStagesBeforeTheRoundedShiftStart() {
        assertEquals("1.00", table(JournalCsvExport.Report.BY_OBJECT).rows.single()[2])
    }

    @Test fun stageHistoryUsesAccountingBoundsWithoutRejectingRecordedEvidence() {
        val row = table(JournalCsvExport.Report.STAGES).rows.single()
        assertEquals("2026-11-06 08:30", row[1])
        assertEquals("2026-11-06 09:30", row[2])
        assertEquals("1.00", row[3])
    }
    @Test fun detailedHistoryContainsVisitsToAllSitesIncludingArchivedOffice() {
        val office = JSONObject().put("id", "office").put("TAG", "BIU-01").put("city", "Tczew")
            .put("Adres", "Lipowa 12").put("archived", true)
        val site = JSONObject().put("id", "site").put("TAG", "TCZ-01").put("name", "Serwis")
        val second = JSONObject().put("start", at(10, 0)).put("end", at(12, 0)).put("stages", JSONArray()
            .put(JSONObject().put("start", at(10, 0)).put("end", at(11, 0)).put("kind", "Biuro").put("objectId", "office"))
            .put(JSONObject().put("start", at(11, 0)).put("end", at(12, 0)).put("kind", "Przejazd")))
        val data = JSONObject().put("objects", JSONArray().put(site).put(office))
            .put("shifts", JSONArray().put(roundedShift()).put(second))
            .put("active", JSONObject().put("start", at(13, 0)).put("objectId", "site"))
        val before = data.toString()
        val result = JournalCsvExport.table(before, JournalCsvExport.Report.OBJECT_HISTORY, JournalCsvExport.Period())
        assertEquals(2, result.rows.size)
        assertEquals(listOf("site", "office"), result.rows.map { it[4] })
        assertEquals(listOf("TCZ-01 · Serwis", "BIU-01"), result.rows.map { it[5] })
        assertEquals("Tczew, Lipowa 12", result.rows[1][6])
        assertEquals("Biuro", result.rows[1][0])
        assertEquals(listOf("1.00", "1.00"), result.rows.map { it[3] })
        assertEquals(before, data.toString())
        val summary = JournalCsvExport.table(before, JournalCsvExport.Report.BY_OBJECT, JournalCsvExport.Period())
        assertEquals(2, summary.rows.size)
    }

    @Test fun fullHistoryKeepsAllYearsAndPeriodSelectionStillClipsVisits() {
        val oldStart = day.minusYears(1).atTime(8, 0).atZone(zone).toInstant().toEpochMilli()
        val old = JSONObject().put("start", oldStart).put("end", oldStart + 3_600_000).put("objectId", "old")
            .put("object", "Old site")
        val data = JSONObject().put("shifts", JSONArray().put(old).put(roundedShift())).toString()
        assertEquals(2, JournalCsvExport.table(data, JournalCsvExport.Report.OBJECT_HISTORY, JournalCsvExport.Period()).rows.size)
        val selected = JournalCsvExport.table(data, JournalCsvExport.Report.OBJECT_HISTORY,
            JournalCsvExport.Period(day, day)).rows.single()
        assertEquals("site", selected[4])
        assertEquals("2026-11-06 08:30", selected[1])
    }

    @Test fun legacyUnassignedDayIsNotInventedAsAnObjectVisit() {
        val data = JSONObject().put("shifts", JSONArray().put(JSONObject()
            .put("start", at(8, 0)).put("end", at(9, 0)))).toString()
        assertTrue(JournalCsvExport.table(data, JournalCsvExport.Report.OBJECT_HISTORY, JournalCsvExport.Period()).rows.isEmpty())
    }

    @Test fun stageBeforeRecordedStartOrAfterShiftEndStillFails() {
        for ((start, end) in listOf(at(8, 28) to at(9, 0), at(8, 29) to at(10, 0))) {
            val shift = roundedShift()
            shift.getJSONArray("stages").getJSONObject(0).put("start", start).put("end", end)
            val data = JSONObject().put("shifts", JSONArray().put(shift)).toString()
            for (report in listOf(JournalCsvExport.Report.BY_OBJECT, JournalCsvExport.Report.STAGES, JournalCsvExport.Report.OBJECT_HISTORY)) {
                assertThrows(IllegalArgumentException::class.java) {
                    JournalCsvExport.table(data, report, JournalCsvExport.Period())
                }
            }
        }
    }

    @Test fun aStageFullyBeforeTheRoundedBoundaryAddsNoObjectTime() {
        val shift = roundedShift()
        shift.getJSONArray("stages").getJSONObject(0).put("end", at(8, 30))
        val data = JSONObject().put("shifts", JSONArray().put(shift)).toString()
        assertTrue(JournalCsvExport.table(data, JournalCsvExport.Report.BY_OBJECT, JournalCsvExport.Period()).rows.isEmpty())
        assertTrue(JournalCsvExport.table(data, JournalCsvExport.Report.OBJECT_HISTORY, JournalCsvExport.Period()).rows.isEmpty())
    }

    @Test fun arbitraryRecordedStartCannotHideAnInvalidStage() {
        val shift = roundedShift().put("recordedStart", at(7, 0))
        val data = JSONObject().put("shifts", JSONArray().put(shift)).toString()
        assertThrows(IllegalArgumentException::class.java) {
            JournalCsvExport.table(data, JournalCsvExport.Report.OBJECT_HISTORY, JournalCsvExport.Period())
        }
    }

}
