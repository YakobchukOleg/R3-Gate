package com.r3gate.app.backup

import android.content.Context
import android.net.Uri
import com.r3gate.app.DriveBackupWorker
import java.io.File
import java.io.FileNotFoundException

/** Builds the same verified snapshot for manual, scheduled and cloud backups. */
internal object BackupSnapshot {
    fun create(context: Context, directory: File, retain: Int, checkActive: () -> Unit = {}): File {
        checkActive()
        val prefs=context.getSharedPreferences("r3_gate",0)
        val journal=DriveBackupWorker.snapshot(context).toString()
        val locations=prefs.all.filterKeys { it.startsWith("gps_object_") }.mapNotNull { (key,value) ->
            (value as? String)?.let { key.removePrefix("gps_object_") to it }
        }.toMap()
        val attachments=mapOf(
            BackupCompanions.OBJECTS to ObjectCatalog.export(journal,locations).toByteArray(),
            BackupCompanions.DIAGNOSTICS to com.r3gate.app.WorkMeterDiagnostics.snapshot(context).toByteArray())
        return LocalBackupStore.create(directory, journal, { value ->
            checkActive()
            val uri = Uri.parse(value)
            require(uri.scheme == "content" || uri.scheme == "file") { "Unsupported photo URI" }
            context.contentResolver.openInputStream(uri) ?: throw FileNotFoundException()
        }, retain, beforePublish = checkActive, attachments = attachments)
    }
}
