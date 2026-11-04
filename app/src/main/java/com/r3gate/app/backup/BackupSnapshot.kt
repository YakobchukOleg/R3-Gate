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
        return LocalBackupStore.create(directory, DriveBackupWorker.snapshot(context).toString(), { value ->
            checkActive()
            val uri = Uri.parse(value)
            require(uri.scheme == "content" || uri.scheme == "file") { "Unsupported photo URI" }
            context.contentResolver.openInputStream(uri) ?: throw FileNotFoundException()
        }, retain, beforePublish = checkActive)
    }
}
