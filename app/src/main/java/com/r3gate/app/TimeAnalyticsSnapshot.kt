package com.r3gate.app

import org.json.JSONObject

/** Read-only adapter to the shared analytics engine, used by charts and widgets. */
internal object TimeAnalyticsSnapshot {
    private fun rows(data: JSONObject, key: String): List<JSONObject> {
        val array=data.optJSONArray(key) ?: return emptyList()
        return (0 until array.length()).mapNotNull(array::optJSONObject)
    }
    fun shifts(data: JSONObject, now: Long): List<TimeAnalytics.Shift> =
        (rows(data,"shifts")+listOfNotNull(data.optJSONObject("active"))).map { shift ->
            val end=shift.optLong("end",now)
            val stages=rows(shift,"stages").toMutableList()
            if(!shift.has("end")) stages.add(JSONObject().put("start",shift.optLong("stageStart",shift.optLong("start")))
                .put("end",end).put("kind",shift.optString("kind","Obiekt"))
                .put("objectId",shift.optString("objectId")).put("object",shift.optString("object")))
            TimeAnalytics.Shift(shift.optLong("start"),end,stages.map { stage ->
                TimeAnalytics.Stage(stage.optLong("start"),stage.optLong("end"),
                    TimeChartCategory.from(stage.optString("kind"),stage.optString("objectId").isNotBlank() || stage.optString("object").isNotBlank()).ordinal)
            })
        }
}
