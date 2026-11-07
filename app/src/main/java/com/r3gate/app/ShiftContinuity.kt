package com.r3gate.app

import org.json.JSONArray
import org.json.JSONObject

internal object ShiftContinuity {
    fun bounds(shift: JSONObject) =
        WorkShiftIntegrity.Interval(shift.optLong("start", -1), shift.optLong("end", -1))

    private fun rawEnd(shift: JSONObject): Long {
        val (start, end) = bounds(shift)
        return shift.optLong("recordedEnd", end).takeIf { it in start..end } ?: end
    }

    fun rounded(shift: JSONObject): JSONObject {
        val (start, end) = bounds(shift)
        if (!WorkShiftIntegrity.isValid(start, end)) return shift
        val roundedEnd = WorkTimeRounding.ceil(end)
        // Keep a positive shift when both timestamps land in the same bucket.
        val roundedStart = WorkTimeRounding.ceil(start).takeIf { it < roundedEnd } ?: start
        if (roundedStart == start && roundedEnd == end) return shift
        return JSONObject(shift.toString())
            .put("recordedStart", shift.optLong("recordedStart", start))
            .put("recordedEnd", rawEnd(shift))
            .put("start", roundedStart)
            .put("end", roundedEnd)
    }

    fun reopen(previous: JSONObject, now: Long): JSONObject? {
        val (start, end) = bounds(previous)
        if (!WorkShiftIntegrity.isValid(start, end) || now < rawEnd(previous) ||
            WorkTimeRounding.ceil(now) != WorkTimeRounding.ceil(end)) return null
        return JSONObject(previous.toString()).apply {
            remove("end")
            remove("recordedEnd")
            remove("stageMeta")
            put("stages", stagesBetween(previous, start, now))
            put("stageStart", now)
            put("kind", "Nieprzypisane")
            put("objectId", "")
            put("object", "")
        }
    }

    private fun stagesBetween(shift: JSONObject, lower: Long, upper: Long): JSONArray {
        val result = JSONArray()
        val stages = shift.optJSONArray("stages") ?: return result
        for (index in 0 until stages.length()) {
            val stage = stages.optJSONObject(index) ?: continue
            val start = maxOf(stage.optLong("start", -1), lower)
            val end = minOf(stage.optLong("end", -1), upper)
            if (end > start) result.put(JSONObject(stage.toString()).put("start", start).put("end", end))
        }
        return result
    }

    private fun join(earlier: JSONObject, later: JSONObject, repairLegacyOverlap: Boolean = false): JSONObject? {
        val first = bounds(earlier)
        val second = bounds(later)
        val id = earlier.optString("id")
        if (id.isBlank() || later.optString("id").isBlank() || id == later.optString("id")) return null
        if (!WorkShiftIntegrity.isValid(first.start, first.end) ||
            !WorkShiftIntegrity.isValid(second.start, second.end) ||
            second.start <= first.start || second.end < first.end) return null
        val roundedEnd = WorkTimeRounding.ceil(first.end)
        val sameBoundary = WorkTimeRounding.ceil(second.start) == roundedEnd
        val legacyOverlap = repairLegacyOverlap &&
            second.start in (roundedEnd - WorkTimeRounding.STEP_MILLIS)..roundedEnd
        if (!sameBoundary && !legacyOverlap) return null
        val firstRate = WalletRules.rate(earlier.optString("rate", "30.00")) ?: return null
        val secondRate = WalletRules.rate(later.optString("rate", "30.00")) ?: return null
        if (firstRate.compareTo(secondRate) != 0) return null

        // Give the second shift ownership from its start. This removes overlap
        // caused by rounding without double-counting the first shift's last stage.
        val stages = stagesBetween(earlier, first.start, second.start)
        val nextStages = stagesBetween(later, second.start, second.end)
        for (index in 0 until nextStages.length()) stages.put(nextStages.getJSONObject(index))
        return JSONObject(later.toString()).apply {
            put("id", id)
            put("start", first.start)
            put("recordedStart", earlier.optLong("recordedStart", first.start))
            put("stages", stages)
            for (key in listOf("edits", "stageEdits")) {
                val combined = JSONArray()
                for (source in listOf(earlier, later)) {
                    val entries = source.optJSONArray(key) ?: continue
                    for (index in 0 until entries.length()) combined.put(entries.get(index))
                }
                if (combined.length() > 0) put(key, combined)
            }
        }
    }

    fun normalizeHistory(shifts: List<JSONObject>, active: JSONObject?): List<JSONObject> {
        val joined = mutableListOf<JSONObject>()
        for (shift in shifts.sortedBy { bounds(it).start }) {
            val merged = joined.lastOrNull()?.let { join(it, shift, repairLegacyOverlap = true) }
            if (merged == null) joined.add(shift) else joined[joined.lastIndex] = merged
        }
        return joined.mapIndexed { index, shift ->
            val rounded = rounded(shift)
            val proposed = bounds(rounded)
            val nextStart = joined.getOrNull(index + 1)?.let { bounds(it).start }
            val activeStart = active?.optLong("start", -1)
            if ((nextStart != null && proposed.end > nextStart) ||
                (activeStart != null && proposed.end > activeStart)) shift else rounded
        }
    }

    /** Apply a proposed replacement and merge only its immediate neighbours. */
    fun replace(
        shifts: List<JSONObject>, proposed: JSONObject, active: JSONObject?
    ): List<JSONObject>? {
        val id = proposed.optString("id")
        val sorted = shifts.filter { it.optString("id") != id }.plus(proposed)
            .sortedBy { bounds(it).start }.toMutableList()
        var index = sorted.indexOf(proposed)
        while (index > 0) {
            val merged = join(sorted[index - 1], sorted[index]) ?: break
            sorted.removeAt(index)
            sorted[--index] = merged
        }
        while (index + 1 < sorted.size) {
            val merged = join(sorted[index], sorted[index + 1]) ?: break
            sorted[index] = merged
            sorted.removeAt(index + 1)
        }
        val shift = rounded(sorted[index])
        val other = sorted.filterIndexed { i, _ -> i != index }.map(::bounds).toMutableList()
        active?.let { other.add(WorkShiftIntegrity.Interval(it.optLong("start", -1), Long.MAX_VALUE)) }
        if (!WorkShiftIntegrity.canSave(bounds(shift), other)) return null
        sorted[index] = shift
        return sorted
    }
}
