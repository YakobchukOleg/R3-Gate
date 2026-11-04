package com.r3gate.app

import android.content.SharedPreferences
import org.json.JSONObject

/** Shared workday boundary for foreground GPS, recovery and boot. */
object GpsWorkdaySession {
    const val CONSENT = "gps_workday_allowed"

    fun hasConsent(savedConsent: Boolean?, legacyEnabled: Boolean, resumePending: Boolean): Boolean =
        savedConsent ?: (legacyEnabled || resumePending)

    fun shouldTrack(workdayOpen: Boolean, consent: Boolean, sessionEnabled: Boolean): Boolean =
        workdayOpen && consent && sessionEnabled

    fun workdayOpen(prefs: SharedPreferences): Boolean = try {
        JSONObject(prefs.getString("data", "{}") ?: "{}").optJSONObject("active") != null
    } catch (_: org.json.JSONException) {
        false
    }

    fun hasConsent(prefs: SharedPreferences): Boolean = hasConsent(
        if (prefs.contains(CONSENT)) prefs.getBoolean(CONSENT, false) else null,
        prefs.getBoolean("gps_enabled", false),
        prefs.getBoolean("gps_resume_pending", false)
    )

    fun shouldTrack(prefs: SharedPreferences): Boolean = shouldTrack(
        workdayOpen(prefs), hasConsent(prefs), prefs.getBoolean("gps_enabled", false)
    )
}
