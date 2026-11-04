package com.r3gate.app

import android.content.SharedPreferences
import org.json.JSONObject

/** Shared workday boundary for foreground GPS, recovery and boot. */
object GpsWorkdaySession {
    const val CONSENT = "gps_workday_allowed"

    fun hasConsent(savedConsent: Boolean?, legacyEnabled: Boolean, resumePending: Boolean): Boolean =
        savedConsent ?: (legacyEnabled || resumePending)

    fun shouldTrack(
        workdayOpen: Boolean, consent: Boolean, sessionEnabled: Boolean, privateTime: Boolean = false
    ): Boolean = workdayOpen && consent && sessionEnabled && !privateTime

    private fun activeShift(prefs: SharedPreferences): JSONObject? = try {
        JSONObject(prefs.getString("data", "{}") ?: "{}").optJSONObject("active")
    } catch (_: org.json.JSONException) {
        null
    }

    fun workdayOpen(prefs: SharedPreferences): Boolean = activeShift(prefs) != null

    fun hasConsent(prefs: SharedPreferences): Boolean = hasConsent(
        if (prefs.contains(CONSENT)) prefs.getBoolean(CONSENT, false) else null,
        prefs.getBoolean("gps_enabled", false),
        prefs.getBoolean("gps_resume_pending", false)
    )

    fun shouldTrack(prefs: SharedPreferences): Boolean {
        val active = activeShift(prefs) ?: return false
        return shouldTrack(true, hasConsent(prefs), prefs.getBoolean("gps_enabled", false),
            active.optString("kind") == PrivateTimeRules.KIND)
    }
}
