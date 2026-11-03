package com.r3gate.app.backup

import java.io.File
import java.io.FileOutputStream
import java.io.FilterOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * An independent recovery archive stored in private app storage (outside SharedPreferences).
 * Never uses shared/public directories. The previous checkpoint is preserved when preparation fails.
 */
internal object JournalRecoveryCheckpoint {
    private const val ARCHIVE = "journal-before-restore.wmb.zip"

    fun save(directory: File, journal: String): File {
        require(directory.isDirectory || directory.mkdirs()) { "Cannot create private backup directory" }
        val target = File(directory, ARCHIVE)
        val temp = File.createTempFile(".restore-", ".tmp", directory)
        try {
            FileOutputStream(temp).use { file ->
                val noClose = object : FilterOutputStream(file) {
                    override fun close() { flush() }
                }
                JournalSnapshot.write(journal, noClose)
                file.fd.sync()
            }
            require(readVerified(temp) == journal) { "Recovery checkpoint verification failed" }
            try {
                Files.move(temp.toPath(), target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (e: java.nio.file.AtomicMoveNotSupportedException) {
                throw IOException("Atomic private checkpoint replacement unavailable", e)
            }
            require(readVerified(target) == journal) { "Published checkpoint differs from journal" }
            return target
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    fun readVerified(file: File): String {
        require(file.isFile) { "Recovery checkpoint missing" }
        return ArchivePreflight.verify { file.inputStream() }.journalJson
    }
}
