package com.r3gate.app.backup

import android.content.Context
import androidx.work.Operation
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.r3gate.app.DriveBackupWorker
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Blocking validation of a selected manual queue operation. Cloud files are never deleted here. */
object BackupQueueActions {
    fun cancel(context: Context, jobId: UUID): Operation {
        val job = manualJob(context, jobId)
        require(!job.state.isFinished) { "Backup operation has already finished" }
        return WorkManager.getInstance(context).cancelWorkById(jobId)
    }

    fun retry(context: Context, jobId: UUID): Operation =
        DriveBackupWorker.queueManual(context, retryQueueId(context, jobId), resumeOnly = true)

    internal fun retryQueueId(context: Context, jobId: UUID): UUID {
        val job = manualJob(context, jobId, requireIdle = true)
        require(job.state == WorkInfo.State.FAILED || job.state == WorkInfo.State.CANCELLED) {
            "Only a failed or cancelled backup can be resumed"
        }
        val key = queueId(job)
        val prefs = context.getSharedPreferences("r3_gate", Context.MODE_PRIVATE)
        require(prefs.getBoolean("driveConnected", false)) { "Google Drive is disconnected" }
        val account = prefs.getString("driveEmail", "").orEmpty()
        val queue = context.getSharedPreferences("workmeter_drive_backup_queue", Context.MODE_PRIVATE)
        val operation = queue.getString(key.toString(), null)?.let(::JSONObject)
        require(resumable(operation, account)) { "No pending backup for the current account" }
        val name = requireNotNull(operation).getString("file")
        require(LocalBackupStore.isBackupName(name)) { "Invalid pending backup filename" }
        val file = File(File(context.filesDir, "drive-backup-queue/$key"), name)
        require(file.isFile) { "Pending backup file is missing" }
        PhotoArchiveAndroid.verify { file.inputStream() }
        return key
    }

    private fun manualJob(context: Context, id: UUID, requireIdle: Boolean = false): WorkInfo {
        val jobs = WorkManager.getInstance(context).getWorkInfosForUniqueWork(BackupStatus.MANUAL_WORK)
            .get(5, TimeUnit.SECONDS)
        if (requireIdle) require(jobs.none { !it.state.isFinished }) { "Another manual backup is already active" }
        return requireNotNull(jobs.singleOrNull {
            it.id == id && BackupStatus.DRIVE_TAG in it.tags && BackupStatus.DAILY_TAG !in it.tags
        }) { "Not a manual backup operation" }
    }

    internal fun queueId(job: WorkInfo): UUID =
        job.tags.firstNotNullOfOrNull { parseQueueTag(it) } ?: job.id

    internal fun queueTag(id: UUID) = "driveBackupQueue:$id"

    internal fun parseQueueTag(tag: String): UUID? =
        if (tag.startsWith("driveBackupQueue:")) {
            runCatching { UUID.fromString(tag.substringAfter(':')) }.getOrNull()
        } else null

    internal fun resumable(operation: JSONObject?, account: String): Boolean =
        account.isNotBlank() && operation != null && !operation.optBoolean("complete") &&
            operation.optString("account").equals(account, true) &&
            LocalBackupStore.isBackupName(operation.optString("file"))
}
