package com.r3gate.app.backup

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import java.io.File
import java.io.FileNotFoundException

/** Synchronous single-process journal restoration with explicit confirmation. */
object JournalRestoreStore {
    private val restoreLock = Any()
    enum class Outcome { UNCHANGED, RESTORED }

    class RecoveryPreview internal constructor(internal val plan: RestorePlan.Preview, photoCount: Int) {
        val summary = RestoreSummary.from(plan, photoCount)
    }

    /** Read-only preview of the verified journal saved before the last replacement. */
    fun previewRecovery(context: Context): RecoveryPreview? = synchronized(restoreLock) {
        val checkpoint = JournalRecoveryCheckpoint.file(context.noBackupFilesDir)
        if (!checkpoint.exists()) return@synchronized null
        val plan = RestorePlan.prepare({ checkpoint.inputStream() }, journal(preferences(context)))
        RecoveryPreview(plan, verifyPhotoAccess(context, plan.journalJson))
    }

    fun recover(context: Context, preview: RecoveryPreview, confirmed: Boolean): Outcome =
        synchronized(restoreLock) {
            require(confirmed) { "User confirmation required" }
            val preferences = preferences(context)
            val current = journal(preferences)
            val restored = RestorePlan.confirmedReplacement(preview.plan, current, true)
            val checkpoint = JournalRecoveryCheckpoint.file(context.noBackupFilesDir)
            require(JournalRecoveryCheckpoint.readVerified(checkpoint) == restored) {
                "Recovery checkpoint changed since preview"
            }
            verifyPhotoAccess(context, restored)
            // Keep the original recovery checkpoint if the recovery itself fails.
            replace(preferences, current, restored, File(context.noBackupFilesDir, "before-recovery"))
        }

    fun restore(context: Context, preview: RestorePlan.Preview, confirmed: Boolean): Outcome =
        synchronized(restoreLock) {
            require(confirmed) { "User confirmation required" }
            val preferences = preferences(context)
            val current = journal(preferences)
            val replacement = RestorePlan.confirmedReplacement(preview, current, true)
            replace(preferences, current, replacement, context.noBackupFilesDir)
        }

    private fun replace(
        preferences: SharedPreferences, current: String, replacement: String, checkpointDirectory: File
    ): Outcome {
        if (replacement == current) return Outcome.UNCHANGED

        val checkpoint = JournalRecoveryCheckpoint.save(checkpointDirectory, current)
        require(journal(preferences) == current) { "Journal changed while preparing recovery checkpoint" }
        if (!preferences.edit().putString("data", replacement).commit() ||
            preferences.getString("data", null) != replacement) {
            val recovery = JournalRecoveryCheckpoint.readVerified(checkpoint)
            check(preferences.edit().putString("data", recovery).commit() &&
                preferences.getString("data", null) == recovery) {
                "Restore and rollback failed; verified recovery archive retained"
            }
            error("Restore failed; old journal restored from recovery archive")
        }
        return Outcome.RESTORED
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences("r3_gate", Context.MODE_PRIVATE)

    private fun journal(preferences: SharedPreferences) =
        preferences.getString("data", "{}") ?: error("Missing current journal")

    private fun verifyPhotoAccess(context: Context, json: String): Int {
        val references = PhotoArchiveJournal.references(json)
        for (reference in references) {
            val uri = Uri.parse(reference)
            require(uri.scheme == "content" || uri.scheme == "file") { "Unsupported photo URI" }
            context.contentResolver.openInputStream(uri)?.use {
                if (it.read() < 0) throw FileNotFoundException("Recovery photo is empty")
            } ?: throw FileNotFoundException("Recovery photo is unavailable")
        }
        return references.size
    }
}
