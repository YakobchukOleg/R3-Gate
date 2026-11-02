package com.r3gate.app.backup

import android.content.Context

/**
 * Synchronous, single-process restore of the existing journal key only.
 * The caller must show RestorePlan.Preview and obtain explicit user confirmation.
 * Never touches PIN, credentials, uploaded files or other preference keys.
 */
object JournalRestoreStore {
    private val restoreLock = Any()

    enum class Outcome { UNCHANGED, RESTORED }

    fun restore(context: Context, preview: RestorePlan.Preview, confirmed: Boolean): Outcome =
        synchronized(restoreLock) {
            require(confirmed) { "User confirmation required" }
            val preferences = context.getSharedPreferences("r3_gate", Context.MODE_PRIVATE)
            val current = preferences.getString("data", "{}")
                ?: throw IllegalStateException("Missing current journal")
            val replacement = RestorePlan.confirmedReplacement(preview, current, true)
            if (replacement == current) return@synchronized Outcome.UNCHANGED
            // commit() blocks until the preference write finishes; apply() would not.
            check(preferences.edit().putString("data", replacement).commit()) {
                "Restore write failed"
            }
            check(preferences.getString("data", null) == replacement) {
                "Restore verification failed"
            }
            Outcome.RESTORED
        }
}
