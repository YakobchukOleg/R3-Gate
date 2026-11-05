package com.r3gate.app.backup

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.r3gate.app.DriveBackupWorker
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class BackupQueueActionsTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val manager get() = WorkManager.getInstance(context)
    private val preferences get() = context.getSharedPreferences("r3_gate", Context.MODE_PRIVATE)
    private val queue get() = context.getSharedPreferences("workmeter_drive_backup_queue", Context.MODE_PRIVATE)
    private val account = "queue-test@example.invalid"
    private val key = UUID.randomUUID()
    private val folder get() = File(context.filesDir, "drive-backup-queue/$key")
    private lateinit var jobId: UUID
    private var oldAccount: String? = null
    private var oldConnected = false

    @Before fun setup() {
        oldAccount = preferences.getString("driveEmail", null)
        oldConnected = preferences.getBoolean("driveConnected", false)
        assertTrue(preferences.edit().putString("driveEmail", account).putBoolean("driveConnected", true).commit())
        val archive = LocalBackupStore.create(folder, "{}", { error("No photo") }, retain = 1)
        assertTrue(queue.edit().putString(key.toString(), JSONObject()
            .put("account", account).put("file", archive.name).put("parent", "parent")
            .put("driveId", "stable-drive-id").put("session", "saved-session").toString()).commit())
        manager.cancelUniqueWork(BackupStatus.MANUAL_WORK).result.get(5, TimeUnit.SECONDS)
        val request = OneTimeWorkRequestBuilder<DriveBackupWorker>()
            .setInitialDelay(1, TimeUnit.DAYS)
            .setInputData(workDataOf(BackupStatus.QUEUE_ID to key.toString(), "backupAccount" to account))
            .addTag(BackupStatus.DRIVE_TAG).addTag(BackupQueueActions.queueTag(key)).build()
        jobId = request.id
        manager.enqueueUniqueWork(BackupStatus.MANUAL_WORK, ExistingWorkPolicy.KEEP, request)
            .result.get(5, TimeUnit.SECONDS)
    }

    @After fun cleanup() {
        manager.cancelUniqueWork(BackupStatus.MANUAL_WORK).result.get(5, TimeUnit.SECONDS)
        folder.deleteRecursively()
        queue.edit().remove(key.toString()).commit()
        preferences.edit().putString("driveEmail", oldAccount).putBoolean("driveConnected", oldConnected).commit()
    }

    @Test fun cancellationKeepsArchiveSessionAndDriveIdentityForLaterResume() {
        val record = queue.getString(key.toString(), null)
        BackupQueueActions.cancel(context, jobId).result.get(5, TimeUnit.SECONDS)
        assertEquals(WorkInfo.State.CANCELLED, manager.getWorkInfoById(jobId).get(5, TimeUnit.SECONDS)?.state)
        assertEquals(record, queue.getString(key.toString(), null))
        assertEquals(1, LocalBackupStore.list(folder).size)
    }

    @Test fun retryUsesOriginalQueueKeyDespiteDifferentWorkManagerId() {
        BackupQueueActions.cancel(context, jobId).result.get(5, TimeUnit.SECONDS)
        assertNotEquals(jobId, key)
        assertEquals(key, BackupQueueActions.retryQueueId(context, jobId))
        assertEquals("stable-drive-id", JSONObject(requireNotNull(queue.getString(key.toString(), null))).getString("driveId"))
        assertEquals("saved-session", JSONObject(requireNotNull(queue.getString(key.toString(), null))).getString("session"))
    }

    @Test fun activeOperationCannotBeRetried() {
        assertThrows(IllegalArgumentException::class.java) {
            BackupQueueActions.retryQueueId(context, jobId)
        }
    }

    @Test fun retryRejectsAnotherAccount() {
        BackupQueueActions.cancel(context, jobId).result.get(5, TimeUnit.SECONDS)
        preferences.edit().putString("driveEmail", "other@example.invalid").commit()
        assertThrows(IllegalArgumentException::class.java) {
            BackupQueueActions.retryQueueId(context, jobId)
        }
    }

    @Test fun retryRejectsMissingArchiveInsteadOfCreatingNewSnapshot() {
        BackupQueueActions.cancel(context, jobId).result.get(5, TimeUnit.SECONDS)
        LocalBackupStore.list(folder).single().delete()
        assertThrows(IllegalArgumentException::class.java) {
            BackupQueueActions.retryQueueId(context, jobId)
        }
    }

    @Test fun completedOperationCannotCreateSecondCloudCopy() {
        BackupQueueActions.cancel(context, jobId).result.get(5, TimeUnit.SECONDS)
        val saved = JSONObject(requireNotNull(queue.getString(key.toString(), null))).put("complete", true).toString()
        queue.edit().putString(key.toString(), saved).commit()
        assertThrows(IllegalArgumentException::class.java) {
            BackupQueueActions.retryQueueId(context, jobId)
        }
        assertEquals(saved, queue.getString(key.toString(), null))
    }

    @Test fun unrelatedOperationCannotBeCancelledThroughBackupApi() {
        assertThrows(IllegalArgumentException::class.java) {
            BackupQueueActions.cancel(context, UUID.randomUUID())
        }
        assertFalse(requireNotNull(manager.getWorkInfoById(jobId).get(5, TimeUnit.SECONDS)).state.isFinished)
    }

    @Test fun statusExposesOnlyEligibleManualActions() {
        val waiting = BackupStatus.read(context).operations.single { it.id == jobId }
        assertTrue(waiting.canCancel)
        assertFalse(waiting.canRetry)
        BackupQueueActions.cancel(context, jobId).result.get(5, TimeUnit.SECONDS)
        val cancelled = BackupStatus.read(context).operations.single { it.id == jobId }
        assertFalse(cancelled.canCancel)
        assertTrue(cancelled.canRetry)
    }
    @Test fun damagedPersistedArchiveCannotBeRetried() {
        BackupQueueActions.cancel(context, jobId).result.get(5, TimeUnit.SECONDS)
        LocalBackupStore.list(folder).single().writeText("damaged")
        assertThrows(IllegalArgumentException::class.java) {
            BackupQueueActions.retryQueueId(context, jobId)
        }
    }

    @Test fun disconnectedAccountCannotResumeQueuedData() {
        BackupQueueActions.cancel(context, jobId).result.get(5, TimeUnit.SECONDS)
        preferences.edit().putBoolean("driveConnected", false).commit()
        assertThrows(IllegalArgumentException::class.java) {
            BackupQueueActions.retryQueueId(context, jobId)
        }
        assertFalse(BackupStatus.read(context).operations.single { it.id == jobId }.canRetry)
    }

}
