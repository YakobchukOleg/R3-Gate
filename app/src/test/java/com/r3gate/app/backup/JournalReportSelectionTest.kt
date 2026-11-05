package com.r3gate.app.backup
import org.json.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class JournalReportSelectionTest {
    private val day=LocalDate.of(2026,11,5)
    private val zone=ZoneId.of("Europe/Warsaw")
    private fun at(d: LocalDate,h: Int)=d.atTime(h,0).atZone(zone).toInstant().toEpochMilli()
    private fun shift(start: Long=at(day.minusDays(1),23),end: Long=at(day,2))=JSONObject().put("start",start).put("end",end).put("objectId","site").put("object","Saved name")
    private fun table(sh: JSONObject,report: JournalCsvExport.Report=JournalCsvExport.Report.WORK_TIME,period: JournalCsvExport.Period=JournalCsvExport.Period(day,day),objects: JSONArray=JSONArray())=
        JournalCsvExport.table(JSONObject().put("shifts",JSONArray().put(sh)).put("objects",objects).toString(),report,period)
    @Test fun shiftStartingYesterdayIsClippedIntoSelectedDay() {
        val row=table(shift()).rows.single()
        assertEquals("2026-11-05 00:00",row[1]); assertEquals("2.00",row[3])
    }
    @Test fun endOfSelectedDayIsExclusive() {
        val row=table(shift(at(day,23),at(day.plusDays(1),2))).rows.single()
        assertEquals("2026-11-06 00:00",row[2]); assertEquals("1.00",row[3])
    }
    @Test fun shiftEndingAtRangeStartDoesNotAppear() { assertTrue(table(shift(at(day.minusDays(1),23),at(day,0))).rows.isEmpty()) }
    @Test fun shiftStartingAfterTheRangeDoesNotAppear() { assertTrue(table(shift(at(day.plusDays(1),0),at(day.plusDays(1),1))).rows.isEmpty()) }
    @Test fun unboundedReportRetainsFullIntervalAndLabelsItGross() {
        val t=table(shift(),period=JournalCsvExport.Period())
        assertEquals("3.00",t.rows.single()[3]); assertTrue(t.headers[3].contains("brutto"))
    }
    @Test fun objectTotalCountsOnlyTheSelectedPartOfAStage() {
        val sh=shift().put("stages",JSONArray().put(shift().put("kind","Obiekt")))
        assertEquals("2.00",table(sh,JournalCsvExport.Report.BY_OBJECT).rows.single()[2])
    }
    @Test fun legacyTripDoesNotBecomeObjectWork() {
        assertTrue(table(shift().put("kind","Przejazd"),JournalCsvExport.Report.BY_OBJECT).rows.isEmpty())
    }
    @Test fun stageReportPreservesPrivateShoppingOfficeAndTripKinds() {
        for(kind in listOf("Prywatnie","Zakupy","Biuro","Przejazd")) {
            val sh=shift().put("stages",JSONArray().put(shift().put("kind",kind)))
            assertEquals(kind,table(sh,JournalCsvExport.Report.STAGES).rows.single()[0])
        }
    }
    @Test fun tripRouteUsesActualSavedEndpoints() {
        val sh=shift().put("kind","Przejazd").put("fromObject","Łódź").put("toObject","Gdańsk")
        assertEquals("Łódź → Gdańsk",table(sh,JournalCsvExport.Report.STAGES).rows.single().last())
    }
    @Test fun missingStageHistoryIsExplicitlyUnassigned() {
        assertEquals("Nieprzypisane",table(shift(),JournalCsvExport.Report.STAGES).rows.single()[0])
    }
    @Test fun ambiguousIdsKeepSavedLabelInsteadOfGuessing() {
        val objects=JSONArray().put(JSONObject().put("id","site").put("name","Wrong one"))
            .put(JSONObject().put("id","site").put("name","Wrong two"))
        assertEquals("Saved name",table(shift(),objects=objects).rows.single().last())
    }
    @Test fun archivedLegacyObjectKeepsTagAndPolishOrganization() {
        val objects=JSONArray().put(JSONObject().put("id","site").put("TAG","TCZ-01").put("organization","Łódź Centrum").put("archived",true))
        assertEquals("TCZ-01 · Łódź Centrum",table(shift(),objects=objects).rows.single().last())
    }
    @Test fun emptyObjectIdsDoNotBorrowAnUnrelatedName() {
        val sh=shift().put("objectId","")
        assertEquals("Saved name",table(sh,objects=JSONArray().put(JSONObject().put("id","").put("name","Wrong"))).rows.single().last())
    }
    @Test fun invalidStageBoundsAreRejected() {
        try { table(shift().put("stages",JSONArray().put(shift(at(day.minusDays(1),22)).put("kind","Obiekt"))),JournalCsvExport.Report.STAGES);fail("Invalid stage") }
        catch (_:IllegalArgumentException) { }
    }
    @Test fun daylightSavingDayUsesActualElapsedTime() {
        val folded=LocalDate.of(2026,10,25)
        val t=table(shift(at(folded,0),at(folded.plusDays(1),0)),period=JournalCsvExport.Period(folded,folded))
        assertEquals("25.00",t.rows.single()[3])
    }

}
