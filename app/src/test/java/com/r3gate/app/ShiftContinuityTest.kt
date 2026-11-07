package com.r3gate.app

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ShiftContinuityTest {
    private fun at(hour: Int, minute: Int) = (hour * 60L + minute) * 60_000L

    private fun shift(id: String, start: Long, end: Long, objectId: String = id) =
        JSONObject().put("id", id).put("start", start).put("end", end)
            .put("stages", JSONArray().put(JSONObject().put("start", start).put("end", end)
                .put("kind", "Obiekt").put("objectId", objectId)))

    @Test fun resumesWhenRoundedBoundariesMatchEvenBeforeRoundedEnd() {
        val closed = shift("first", at(8, 30), at(17, 35)).put("recordedEnd", at(17, 32))
        val resumed = ShiftContinuity.reopen(closed, at(17, 34))!!
        assertEquals("first", resumed.getString("id"))
        assertEquals(at(8, 30), resumed.getLong("start"))
        assertEquals(at(17, 34), resumed.getLong("stageStart"))
        assertEquals(at(17, 34), resumed.getJSONArray("stages").getJSONObject(0).getLong("end"))
        assertFalse(resumed.has("end"))
        assertEquals(at(17, 35), closed.getLong("end"))
    }

    @Test fun rejectsOpeningBeforeActualCloseAndDoesNotJoinSeparateBuckets() {
        val closed = shift("first", at(8, 30), at(17, 35)).put("recordedEnd", at(17, 32))
        assertNull(ShiftContinuity.reopen(closed, at(17, 31)))
        assertNull(ShiftContinuity.reopen(closed, at(17, 36)))
        assertNotNull(ShiftContinuity.reopen(closed, at(17, 35)))
    }

    @Test fun repairsLegacyOverlapWithoutDoubleCountingStages() {
        val first = shift("first", at(8, 29), at(17, 35))
        val second = shift("second", at(17, 30), at(18, 5))
        val repaired = ShiftContinuity.normalizeHistory(listOf(second, first), null).single()
        assertEquals(WorkShiftIntegrity.Interval(at(8, 30), at(18, 5)), ShiftContinuity.bounds(repaired))
        val stages = repaired.getJSONArray("stages")
        assertEquals(2, stages.length())
        assertEquals("first", stages.getJSONObject(0).getString("objectId"))
        assertEquals("second", stages.getJSONObject(1).getString("objectId"))
        assertEquals(at(17, 30), stages.getJSONObject(0).getLong("end"))
        assertEquals(stages.getJSONObject(0).getLong("end"), stages.getJSONObject(1).getLong("start"))
        assertEquals(repaired.toString(), ShiftContinuity.normalizeHistory(listOf(repaired), null).single().toString())
    }

    @Test fun editJoinsBothTouchingNeighboursAndKeepsEarliestIdentity() {
        val first = shift("first", at(8, 30), at(12, 0))
        val original = shift("middle", at(12, 15), at(14, 0))
        val last = shift("last", at(14, 15), at(18, 0))
        val edited = JSONObject(original.toString()).put("start", at(12, 0)).put("end", at(14, 15))
        val result = ShiftContinuity.replace(listOf(first, original, last), edited, null)!!.single()
        assertEquals("first", result.getString("id"))
        assertEquals(WorkShiftIntegrity.Interval(at(8, 30), at(18, 0)), ShiftContinuity.bounds(result))
        assertEquals(3, result.getJSONArray("stages").length())
    }

    @Test fun rejectsSubstantialOverlapAndPreservesDifferentRates() {
        val first = shift("first", at(8, 30), at(17, 35)).put("rate", "30.00")
        val overlap = shift("second", at(16, 0), at(18, 0))
        assertNull(ShiftContinuity.replace(listOf(first), overlap, null))
        val differentRate = shift("third", at(17, 35), at(18, 0)).put("rate", "40.00")
        assertEquals(2, ShiftContinuity.normalizeHistory(listOf(first, differentRate), null).size)
    }

    @Test fun veryShortShiftKeepsPositiveDurationWhenBothEndsRoundToSameTime() {
        val rounded = ShiftContinuity.rounded(shift("short", at(8, 31), at(8, 32)))
        assertEquals(WorkShiftIntegrity.Interval(at(8, 31), at(8, 35)), ShiftContinuity.bounds(rounded))
    }
}
