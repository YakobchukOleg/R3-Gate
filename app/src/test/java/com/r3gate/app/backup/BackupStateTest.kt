package com.r3gate.app.backup

import com.r3gate.app.backup.BackupState.Job
import org.junit.Assert.*
import org.junit.Test

class BackupStateTest {
    private fun state(job: Job, drive: Boolean = true, periodic: Boolean = false,
        attempts: Int = 0, sending: Boolean = false, action: Boolean = false) =
        BackupState.resolve(job, drive, periodic, attempts, sending, action)

    @Test fun sleepingPeriodicWorkDoesNotLookLikeAnUpload() {
        assertEquals(BackupState.SCHEDULED, state(Job.WAITING, periodic = true))
    }

    @Test fun manualAndRetryingDriveWorkWaitToSend() {
        assertEquals(BackupState.WAITING_TO_SEND, state(Job.WAITING))
        assertEquals(BackupState.WAITING_TO_SEND, state(Job.WAITING, periodic = true, attempts = 1))
    }

    @Test fun distinguishesCreationFromSending() {
        assertEquals(BackupState.CREATING, state(Job.RUNNING))
        assertEquals(BackupState.SENDING, state(Job.RUNNING, sending = true))
        assertEquals(BackupState.CREATING, state(Job.RUNNING, drive = false, sending = true))
    }

    @Test fun distinguishesLocalAndCloudSuccess() {
        assertEquals(BackupState.DRIVE_READY, state(Job.FINISHED))
        assertEquals(BackupState.LOCAL_SAVED, state(Job.FINISHED, drive = false))
    }

    @Test fun onlyFailuresNeedAttention() {
        assertEquals(setOf(BackupState.FAILED, BackupState.ACTION_REQUIRED),
            BackupState.entries.filter(BackupState::needsAttention).toSet())
        assertEquals(BackupState.ACTION_REQUIRED, state(Job.FAILED, action = true))
        assertEquals(BackupState.FAILED, state(Job.FAILED))
        assertEquals(BackupState.CANCELLED, state(Job.CANCELLED))
    }
}
