package com.r3gate.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import org.json.JSONObject

/** Read a single theme-neutral GPS snapshot for any app screen or widget. */
object GpsStatusReader {
    fun read(context: Context, pendingProposalCount: Int): GpsStatusContract.Snapshot {
        val prefs = context.getSharedPreferences("r3_gate", Context.MODE_PRIVATE)
        val locations = context.getSystemService(LocationManager::class.java)
        val fine = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val provider = GpsProviderRules.permitted(
            locations?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true,
            locations?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true, fine, coarse
        )
        val values = prefs.all
        fun objectValue(key: String): JSONObject = try {
            JSONObject(values[key] as? String ?: "{}")
        } catch (_: org.json.JSONException) {
            JSONObject()
        }
        val data = objectValue("data")
        val open = data.optJSONObject("active") != null
        val consent = GpsWorkdaySession.hasConsent(values[GpsWorkdaySession.CONSENT] as? Boolean,
            values["gps_enabled"] == true, values["gps_resume_pending"] == true)
        val confirmed = objectValue("gps_arrival_state").optString("confirmed")
        val objects = data.optJSONArray("objects")
        val matches = (0 until (objects?.length() ?: 0)).mapNotNull { objects?.optJSONObject(it) }
            .filter { it.optString("id") == confirmed }
        val objectId = confirmed.takeIf { it.isNotBlank() && matches.singleOrNull()?.optBoolean("archived") == false }
        return GpsStatusContract.sessionSnapshot(
            sessionActive = values["gps_service_active"] == true,
            trackingAllowed = open && consent,
            locationPermission = fine,
            providerUsable = provider != null,
            sessionStartMs = values["gps_session_start"] as? Long ?: 0L,
            lastFixMs = objectValue("gps_last_fix").optLong("time"),
            nowMs = System.currentTimeMillis(),
            problem = values["gps_service_problem"] as? String,
            confirmedObjectId = objectId,
            pendingProposalCount = pendingProposalCount
        )
    }
}
