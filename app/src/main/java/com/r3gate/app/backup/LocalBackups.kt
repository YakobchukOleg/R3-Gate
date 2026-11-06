package com.r3gate.app.backup

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.time.Instant

/** Storage and schedule API for the settings screen. File I/O must run off the UI thread. */
object LocalBackups {
    data class Schedule(val enabled: Boolean, val intervalHours: Int = 24, val retention: Int = 7, val eveningHour:Int = 21) {
        init {
            require(intervalHours in 1..168)
            require(retention in 1..30)
            require(eveningHour in 0..23)
        }
    }

    data class Copy(val name: String, val modifiedAt: Instant, val sizeBytes: Long)
    data class Status(val schedule: Schedule, val lastSuccess: Instant?, val latestCopy: Copy?, val error: String?)

    fun status(context: Context): Status {
        val preferences = settings(context)
        val schedule = Schedule(
            preferences.getBoolean(LocalBackupWorker.ENABLED, false),
            preferences.getInt(LocalBackupWorker.INTERVAL_HOURS, 24).coerceIn(1, 168),
            preferences.getInt(LocalBackupWorker.RETENTION, 7).coerceIn(1, 30),
            preferences.getInt(EveningBackup.HOUR,21).coerceIn(0,23)
        )
        val latest = copies(context).firstOrNull { it.name == preferences.getString(LocalBackupWorker.LAST_FILE, null) }
        val timestamp = latest?.let {
            preferences.getString(LocalBackupWorker.LAST_SUCCESS, null)?.let { value ->
                runCatching { Instant.parse(value) }.getOrNull()
            }
        }
        return Status(schedule, timestamp, latest, preferences.getString(LocalBackupWorker.STATUS_ERROR, null))
    }

    fun updateSchedule(context: Context, schedule: Schedule): androidx.work.Operation {
        check(settings(context).edit().putBoolean(LocalBackupWorker.ENABLED, schedule.enabled)
            .putInt(LocalBackupWorker.INTERVAL_HOURS, schedule.intervalHours)
            .putInt(LocalBackupWorker.RETENTION, schedule.retention).putInt(EveningBackup.HOUR,schedule.eveningHour).commit()) {
            "Cannot save local backup settings"
        }
        return LocalBackupWorker.schedule(context)
    }

    fun copies(context: Context): List<Copy> = files(context).map(::describe)

    /** Creates an offline copy regardless of the periodic schedule. */
    fun createNow(context: Context): Copy = createNow(context) {}

    internal fun createNow(context: Context, checkActive: () -> Unit): Copy = synchronized(LocalBackupStore) {
        val preferences = settings(context)
        val retention = preferences.getInt(LocalBackupWorker.RETENTION, 7).coerceIn(1, 30),
            preferences.getInt(EveningBackup.HOUR,21).coerceIn(0,23)
        val archive = BackupSnapshot.create(context, directory(context), retention, checkActive)
        BackupDirectoryExport.copy(context,archive)
        val saved = preferences.edit().putString(LocalBackupWorker.LAST_SUCCESS, Instant.now().toString())
            .putString(LocalBackupWorker.LAST_FILE, archive.name).remove(LocalBackupWorker.STATUS_ERROR).commit()
        if (!saved) throw IOException("Cannot record the latest local backup")
        describe(archive)
    }

    /** Exports a fresh verified snapshot to the document selected by the user. */
    fun exportCurrent(context: Context, target: Uri): Copy = synchronized(LocalBackupStore) {
        createNow(context).also { export(context, it.name, target) }
    }

    private fun describe(file: File) =
        Copy(file.name, Instant.ofEpochMilli(file.lastModified()), file.length())

    /** Copies a previously published archive to a user-selected document. */
    fun export(context: Context, name: String, target: Uri) = synchronized(LocalBackupStore) {
        val file = find(context, name)
        PhotoArchiveAndroid.verify { file.inputStream() }
        BackupFilePicker.write(context.contentResolver, target) { output ->
            file.inputStream().use { input -> input.copyTo(output) }
        }
    }

    fun prepareRestore(context: Context, name: String): PhotoRestoreCoordinator.Session = synchronized(LocalBackupStore) {
        val file = find(context, name)
        PhotoRestoreCoordinator.prepare(context) { file.inputStream() }
    }

    fun prepareImport(context: Context, source: Uri): PhotoRestoreCoordinator.Session =
        PhotoRestoreCoordinator.prepare(context) {
            context.contentResolver.openInputStream(source) ?: throw FileNotFoundException()
        }

    private fun find(context: Context, name: String): File =
        files(context).singleOrNull { it.name == name } ?: throw FileNotFoundException()

    private fun files(context: Context): List<File> =
        LocalBackupStore.list(directory(context))

    private fun directory(context: Context) = File(context.filesDir, LocalBackupWorker.DIRECTORY)

    private fun settings(context: Context) =
        context.getSharedPreferences(LocalBackupWorker.PREFERENCES, Context.MODE_PRIVATE)
}
