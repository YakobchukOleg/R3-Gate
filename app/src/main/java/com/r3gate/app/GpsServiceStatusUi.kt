package com.r3gate.app

import android.content.Context
import android.content.pm.PackageManager
import org.json.JSONObject

internal object GpsServiceStatusUi {
    fun read(context: Context, now: Long): GpsServiceStatusRules.Status {
        val prefs = context.getSharedPreferences("r3_gate", Context.MODE_PRIVATE)
        val permission = context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val lastFix = try {
            JSONObject(prefs.getString("gps_last_fix", "{}") ?: "{}").optLong("time")
        } catch (_: org.json.JSONException) {
            0L
        }
        return GpsServiceStatusRules.status(prefs.getBoolean("gps_enabled", false), permission,
            prefs.getBoolean("gps_resume_pending", false), lastFix, now)
    }

    fun label(status: GpsServiceStatusRules.Status): String = when (status) {
        GpsServiceStatusRules.Status.OFF -> "GPS: wyłączony"
        GpsServiceStatusRules.Status.PERMISSION_REQUIRED -> "GPS: brak pozwolenia"
        GpsServiceStatusRules.Status.RESUME_AFTER_REBOOT -> "GPS: wymaga wznowienia"
        GpsServiceStatusRules.Status.NO_RECENT_FIX -> "GPS: brak aktualnego pomiaru"
        GpsServiceStatusRules.Status.RECENT_FIX -> "GPS: aktualny pomiar"
    }
}
