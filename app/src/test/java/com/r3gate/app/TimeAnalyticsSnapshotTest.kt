package com.r3gate.app
import java.time.*
import org.json.*
import org.junit.Assert.*
import org.junit.Test
class TimeAnalyticsSnapshotTest {
    private val zone=ZoneId.of("Europe/Warsaw")
    private val day=LocalDate.of(2026,11,5)
    private fun at(date: LocalDate,hour: Int)=date.atTime(hour,0).atZone(zone).toInstant().toEpochMilli()
    @Test fun severalDaysAccumulateTheSameHourBeyondOneHour() {
        val shifts=JSONArray()
        for(date in listOf(day,day.minusDays(1))) shifts.put(JSONObject().put("start",at(date,9)).put("end",at(date,10))
            .put("stages",JSONArray().put(JSONObject().put("start",at(date,9)).put("end",at(date,10)).put("kind","Obiekt").put("objectId","site"))))
        val result=TimeAnalytics.calculate(TimeAnalyticsSnapshot.shifts(JSONObject().put("shifts",shifts),at(day,11)),day.minusDays(1),day,zone)
        assertEquals(7_200_000,result.hours[9].sum()); assertEquals(7_200_000,result.workMillis)
    }
    @Test fun activePersonalTimeDoesNotIncreaseWorkOrChangeTheSource() {
        val source=JSONObject().put("active",JSONObject().put("start",at(day,8)).put("stageStart",at(day,9)).put("kind","Prywatnie")
            .put("stages",JSONArray().put(JSONObject().put("start",at(day,8)).put("end",at(day,9)).put("kind","Obiekt").put("objectId","site"))))
        val before=source.toString()
        val result=TimeAnalytics.calculate(TimeAnalyticsSnapshot.shifts(source,at(day,10)),day,day,zone)
        assertEquals(3_600_000,result.workMillis); assertEquals(3_600_000,result.personalMillis); assertEquals(before,source.toString())
    }
}
