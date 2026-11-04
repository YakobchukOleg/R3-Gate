package com.r3gate.app.backup

import android.content.Context
import androidx.work.WorkInfo
import androidx.work.WorkManager
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Read-only settings contract. Call off the UI thread. */
object BackupStatus {
    enum class Destination { DEVICE, DRIVE }

    data class Operation(val id: UUID, val destination: Destination, val state: BackupState)
    data class Snapshot(
        val local: LocalBackups.Status,
        val driveConnected: Boolean,
        val lastVerifiedDriveCopy: Instant?,
        val driveDetail: String?,
        val operations: List<Operation>
    )

    fun read(context: Context): Snapshot {
        val manager = WorkManager.getInstance(context)
        val drive = manager.getWorkInfosByTag(DRIVE_TAG)
        val local = manager.getWorkInfosForUniqueWork(LocalBackupWorker.WORK)
        val preferences = context.getSharedPreferences("r3_gate", Context.MODE_PRIVATE)
        val connected = preferences.getBoolean("driveConnected", false)
        val account = preferences.getString("driveEmail", null)
        val verified = if (connected && account != null &&
            account.equals(preferences.getString(VERIFIED_ACCOUNT, null), ignoreCase = true)) {
            preferences.getString(VERIFIED_AT, null)?.let { runCatching { Instant.parse(it) }.getOrNull() }
        } else null
        val operations = drive.get(3, TimeUnit.SECONDS).mapNotNull {
            it.operation(Destination.DRIVE, account.takeIf { connected })
        } + local.get(3, TimeUnit.SECONDS).mapNotNull { it.operation(Destination.DEVICE) }
        return Snapshot(LocalBackups.status(context), connected, verified,
            preferences.getString("backupStatus", null), operations)
    }

    private fun WorkInfo.operation(destination: Destination, account: String? = null): Operation? {
        val isDrive = destination == Destination.DRIVE
        if (state == WorkInfo.State.SUCCEEDED && (!outputData.getBoolean(VERIFIED, false) ||
                isDrive && (account == null || !account.equals(outputData.getString(VERIFIED_ACCOUNT), true)))) {
            return null
        }
        val job = when (state) {
            WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> BackupState.Job.WAITING
            WorkInfo.State.RUNNING -> BackupState.Job.RUNNING
            WorkInfo.State.SUCCEEDED -> BackupState.Job.FINISHED
            WorkInfo.State.FAILED -> BackupState.Job.FAILED
            WorkInfo.State.CANCELLED -> BackupState.Job.CANCELLED
        }
        return Operation(id, destination, BackupState.resolve(job, isDrive,
            !isDrive || DAILY_TAG in tags, runAttemptCount,
            progress.getBoolean(SENDING, false), outputData.getBoolean(ACTION_REQUIRED, false)))
    }

    internal const val DRIVE_TAG = "driveBackup"
    internal const val DAILY_TAG = "workmeter-daily-backup"
    internal const val SENDING = "backupSending"
    internal const val ACTION_REQUIRED = "needsAccount"
    internal const val VERIFIED = "backupVerified"
    internal const val VERIFIED_AT = "lastVerifiedDriveZipAt"
    internal const val VERIFIED_ACCOUNT = "lastVerifiedDriveZipAccount"
}
