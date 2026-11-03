package com.r3gate.app

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** Bounded device-local trace; only an explicit Android share action exports it. */
object GpsTraceJournal {
    private const val KEY="gps_diagnostic_trace"

    fun append(prefs:SharedPreferences,kind:String,nowMs:Long,details:JSONObject=JSONObject()) {
        if(nowMs<=0L) return
        val lastKey="gps_trace_last_"+kind
        if(!GpsTraceRules.shouldLog(kind,nowMs,prefs.getLong(lastKey,0L))) return
        val previous=try { JSONArray(prefs.getString(KEY,"[]") ?: "[]") }
            catch(_:Exception) { JSONArray() }
        previous.put(JSONObject().put("time",nowMs).put("kind",kind).put("details",details))
        val bounded=JSONArray()
        for(i in GpsTraceRules.retainedStart(previous.length()) until previous.length())
            bounded.put(previous.opt(i))
        prefs.edit().putString(KEY,bounded.toString()).putLong(lastKey,nowMs).apply()
    }

    fun recent(prefs:SharedPreferences,max:Int=12):List<String> {
        val entries=try { JSONArray(prefs.getString(KEY,"[]") ?: "[]") }
            catch(_:Exception) { JSONArray() }
        return (maxOf(0,entries.length()-max.coerceAtLeast(0)) until entries.length())
            .mapNotNull { entries.optJSONObject(it)?.let { entry ->
                entry.optLong("time").toString()+" "+entry.optString("kind")+" "+(entry.optJSONObject("details") ?: JSONObject())
            } }
    }

    fun report(prefs:SharedPreferences):String = buildString {
        appendLine("WorkMeter SOLO GPS diagnostic report")
        appendLine("Generated UTC epoch ms: "+System.currentTimeMillis())
        appendLine("GPS enabled: "+prefs.getBoolean("gps_enabled",false))
        appendLine("Service active flag: "+prefs.getBoolean("gps_service_active",false))
        appendLine("Service problem: "+prefs.getString("gps_service_problem","none"))
        appendLine("Requested interval ms: "+prefs.getLong("gps_requested_interval_ms",0))
        appendLine("Rejected low-quality fixes: "+prefs.getInt("gps_rejected_quality_count",0))
        appendLine("Rejected out-of-order fixes: "+prefs.getInt("gps_rejected_order_count",0))
        appendLine("Rejected jumps: "+prefs.getInt("gps_rejected_jump_count",0))
        for(key in listOf("gps_last_fix","gps_arrival_state","gps_trip_state",
            "gps_events","gps_trip_events","gps_coverage_gaps","gps_fix_times",KEY)) {
            appendLine("--- "+key+" ---")
            appendLine(prefs.getString(key,"[]") ?: "[]")
        }
        appendLine("NOTE: includes precise locations. Share only with people you trust.")
    }
}
