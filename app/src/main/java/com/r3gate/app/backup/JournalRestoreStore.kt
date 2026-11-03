package com.r3gate.app.backup

import android.content.Context

/**
 * Synchronous single-process journal restoration.
 * Caller must confirm the inspected restore plan; no UI or automatic restore here.
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

            // Must succeed before the existing journal is ever modified.
            val checkpoint = JournalRecoveryCheckpoint.save(context.noBackupFilesDir, current)
            if (!preferences.edit().putString("data", replacement).commit() ||
                preferences.getString("data", null) != replacement) {
                // Best-effort rollback; independently verified checkpoint is retained even if rollback fails.
                val recovery = JournalRecoveryCheckpoint.readVerified(checkpoint)
                check(preferences.edit().putString("data", recovery).commit() &&
                    preferences.getString("data", null) == recovery) {
                    "Restore and rollback failed; verified recovery archive retained"
                }
                error("Restore failed; old journal restored from recovery archive")
            }
            Outcome.RESTORED
        }
}
