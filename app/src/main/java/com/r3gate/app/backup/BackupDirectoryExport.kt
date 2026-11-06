package com.r3gate.app.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.File

/** Only the explicitly selected SAF directory is writable outside private storage. */
internal object BackupDirectoryExport {
    const val KEY="public_backup_tree"
    fun copy(context:Context,archive:File) {
        val value=context.getSharedPreferences(LocalBackupWorker.PREFERENCES,0).getString(KEY,null) ?: return
        val tree=Uri.parse(value)
        val parent=DocumentsContract.buildDocumentUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree))
        val files=listOf(archive)+BackupCompanions.paths.map { BackupCompanions.file(archive,it) }.filter { it.exists() }
        for(file in files) {
            val mime=when { file.name.endsWith(".json") -> "application/json";file.name.endsWith(".jsonl") -> "application/x-ndjson";else -> "application/zip" }
            val destination=DocumentsContract.createDocument(context.contentResolver,parent,mime,file.name) ?: error("Nie utworzono pliku kopii")
            try { context.contentResolver.openOutputStream(destination,"wt")?.use { out -> file.inputStream().use { it.copyTo(out) } } ?: error("Nie zapisano pliku kopii") }
            catch(e:Exception) { runCatching { DocumentsContract.deleteDocument(context.contentResolver,destination) };throw e }
        }
    }
}
