package com.r3gate.app.backup

import android.content.Context
import android.net.Uri
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.r3gate.app.DriveBackupWorker
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.time.Instant
import java.util.concurrent.TimeUnit

internal class LocalBackupWorker(context: Context, parameters: WorkerParameters) : Worker(context, parameters) {
    override fun doWork(): Result {
        val context = applicationContext
        val settings = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        if (!settings.getBoolean(ENABLED, false)) return Result.success()
        return try {
            val journal = DriveBackupWorker.snapshot(context).toString()
            val retained = settings.getInt(RETENTION, DEFAULT_RETENTION).coerceIn(1, 30)
            val published = LocalBackupStore.create(File(context.filesDir, DIRECTORY), journal, { value ->
                if (isStopped || !settings.getBoolean(ENABLED, false)) throw InterruptedException()
                val uri = Uri.parse(value)
                require(uri.scheme == "content" || uri.scheme == "file") { "Unsupported photo URI" }
                context.contentResolver.openInputStream(uri) ?: throw FileNotFoundException()
            }, retained, beforePublish = {
                if (isStopped || !settings.getBoolean(ENABLED, false)) throw InterruptedException()
            })
            settings.edit().putString(LAST_SUCCESS, Instant.now().toString())
                .putString(LAST_FILE, published.name).remove(STATUS_ERROR).apply()
            Result.success()
        } catch (_: InterruptedException) {
            Result.retry()
        } catch (_: SecurityException) {
            failure(settings, "Brak dostępu do załącznika.")
        } catch (_: FileNotFoundException) {
            failure(settings, "Brak pliku potrzebnego do utworzenia kopii.")
        } catch (_: IllegalArgumentException) {
            failure(settings, "Nie udało się zweryfikować kopii.")
        } catch (_: org.json.JSONException) {
            failure(settings, "Nie można odczytać dziennika.")
        } catch (_: IOException) {
            failure(settings, "Błąd zapisu kopii lokalnej.")
        }
    }

    private fun failure(settings: android.content.SharedPreferences, message: String): Result {
        settings.edit().putString(STATUS_ERROR, message).apply()
        return Result.failure()
    }

    companion object {
        const val PREFERENCES = "workmeter_local_backup"
        const val ENABLED = "enabled"
        const val INTERVAL_HOURS = "interval_hours"
        const val RETENTION = "retention"
        const val LAST_SUCCESS = "last_success"
        const val LAST_FILE = "last_file"
        const val STATUS_ERROR = "error"
        const val DIRECTORY = "local-backups"
        private const val DEFAULT_RETENTION = 7
        private const val WORK = "workmeter-local-backup"

        fun schedule(context: Context) {
            val settings = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            val manager = WorkManager.getInstance(context)
            if (!settings.getBoolean(ENABLED, false)) {
                manager.cancelUniqueWork(WORK)
                return
            }
            val hours = settings.getInt(INTERVAL_HOURS, 24).coerceIn(1, 168).toLong()
            manager.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<LocalBackupWorker>(hours, TimeUnit.HOURS).build())
        }
    }
}
