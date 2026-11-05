package com.r3gate.app.backup

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.r3gate.app.DriveBackupWorker
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class BackupSchedulingTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun withSavedSettings(test: (WorkManager) -> Unit) = synchronized(LocalBackupStore) {
        val manager = WorkManager.getInstance(context)
        val settings = context.getSharedPreferences(LocalBackupWorker.PREFERENCES, Context.MODE_PRIVATE)
        val app = context.getSharedPreferences("r3_gate", Context.MODE_PRIVATE)
        val localSchedule = LocalBackups.status(context).schedule
        val connected = app.getBoolean("driveConnected", false)
        val daily = app.getBoolean("dailyBackupEnabled", true)
        try {
            manager.cancelUniqueWork(LocalBackupWorker.WORK).result.get(5, TimeUnit.SECONDS)
            manager.cancelUniqueWork(BackupStatus.DAILY_TAG).result.get(5, TimeUnit.SECONDS)
            test(manager)
        } finally {
            manager.cancelUniqueWork(LocalBackupWorker.WORK).result.get(5, TimeUnit.SECONDS)
            manager.cancelUniqueWork(BackupStatus.DAILY_TAG).result.get(5, TimeUnit.SECONDS)
            assertTrue(app.edit().putBoolean("driveConnected", connected)
                .putBoolean("dailyBackupEnabled", daily).commit())
            assertTrue(settings.edit().putBoolean(LocalBackupWorker.ENABLED, localSchedule.enabled)
                .putInt(LocalBackupWorker.INTERVAL_HOURS, localSchedule.intervalHours)
                .putInt(LocalBackupWorker.RETENTION, localSchedule.retention).commit())
            DriveBackupWorker.schedule(context).result.get(5, TimeUnit.SECONDS)
        }
    }

    @Test fun localSchedulePersistsAndReconcilesOneUniqueTask() = withSavedSettings { manager ->
        assertTrue(context.getSharedPreferences("r3_gate", 0).edit().putBoolean("driveConnected", false).commit())
        val desired = LocalBackups.Schedule(true, intervalHours = 4, retention = 3)
        LocalBackups.updateSchedule(context, desired).result.get(5, TimeUnit.SECONDS)
        assertEquals(desired, LocalBackups.status(context).schedule)
        LocalBackupWorker.schedule(context).result.get(5, TimeUnit.SECONDS)
        val jobs = manager.getWorkInfosForUniqueWork(LocalBackupWorker.WORK).get(5, TimeUnit.SECONDS)
            .filterNot { it.state.isFinished }
        assertEquals(1, jobs.size)
        assertEquals(TimeUnit.HOURS.toMillis(4), jobs.single().periodicityInfo?.repeatIntervalMillis)
    }

    @Test fun disablingLocalScheduleCancelsPendingTask() = withSavedSettings { manager ->
        LocalBackups.updateSchedule(context, LocalBackups.Schedule(true)).result.get(5, TimeUnit.SECONDS)
        LocalBackups.updateSchedule(context, LocalBackups.Schedule(false)).result.get(5, TimeUnit.SECONDS)
        assertFalse(LocalBackups.status(context).schedule.enabled)
        val pending = manager.getWorkInfosForUniqueWork(LocalBackupWorker.WORK).get(5, TimeUnit.SECONDS)
            .filterNot { it.state.isFinished }
        assertTrue(pending.isEmpty())
    }

    @Test fun disconnectedDriveCancelsExistingDailyTask() = withSavedSettings { manager ->
        val request = PeriodicWorkRequestBuilder<DriveBackupWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(1, TimeUnit.DAYS).addTag(BackupStatus.DRIVE_TAG).addTag(BackupStatus.DAILY_TAG).build()
        manager.enqueueUniquePeriodicWork(BackupStatus.DAILY_TAG, ExistingPeriodicWorkPolicy.UPDATE, request)
            .result.get(5, TimeUnit.SECONDS)
        assertTrue(context.getSharedPreferences("r3_gate", 0).edit().putBoolean("driveConnected", false).commit())
        DriveBackupWorker.schedule(context).result.get(5, TimeUnit.SECONDS)
        val pending = manager.getWorkInfosForUniqueWork(BackupStatus.DAILY_TAG).get(5, TimeUnit.SECONDS)
            .filterNot { it.state.isFinished }
        assertTrue(pending.isEmpty())
    }

    @Test fun disabledDailySettingCancelsExistingDailyTask() = withSavedSettings { manager ->
        val request = PeriodicWorkRequestBuilder<DriveBackupWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(1, TimeUnit.DAYS).addTag(BackupStatus.DRIVE_TAG).addTag(BackupStatus.DAILY_TAG).build()
        manager.enqueueUniquePeriodicWork(BackupStatus.DAILY_TAG, ExistingPeriodicWorkPolicy.UPDATE, request)
            .result.get(5, TimeUnit.SECONDS)
        assertTrue(context.getSharedPreferences("r3_gate", 0).edit().putBoolean("dailyBackupEnabled", false).commit())
        DriveBackupWorker.schedule(context).result.get(5, TimeUnit.SECONDS)
        val pending = manager.getWorkInfosForUniqueWork(BackupStatus.DAILY_TAG).get(5, TimeUnit.SECONDS)
            .filterNot { it.state.isFinished }
        assertTrue(pending.isEmpty())
    }
}
