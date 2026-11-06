package com.r3gate.app.backup

import java.io.File
import java.io.FileOutputStream
import java.io.FilterOutputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

/** Publishes only complete, independently verified archives into the app's private directory. */
internal object LocalBackupStore {
    private val stamp = DateTimeFormatter.ofPattern("uuuu-MM-dd_HH-mm-ss", Locale.ROOT).withZone(ZoneOffset.UTC)
    private val publishedName = Regex("""WorkMeter-SOLO-\d{4}-\d{2}-\d{2}_\d{2}-\d{2}-\d{2}_[a-f0-9-]{36}\.wmb\.zip""")
    private const val STAGING_PREFIX = ".workmeter-local-"

    @Synchronized
    fun create(
        directory: File,
        journal: String,
        openPhoto: (String) -> InputStream,
        retain: Int,
        now: Instant = Instant.now(),
        beforePublish: () -> Unit = {},
        attachments: Map<String,ByteArray> = emptyMap()
    ): File {
        require(retain in 1..30)
        require(directory.isDirectory || directory.mkdirs()) { "Private backup directory unavailable" }
        directory.listFiles().orEmpty().filter { it.name.startsWith(STAGING_PREFIX) && it.name.endsWith(".tmp") }
            .forEach { it.delete() }
        val destination = File(directory, "WorkMeter-SOLO-${stamp.format(now)}_${UUID.randomUUID()}.wmb.zip")
        val staging = File.createTempFile(STAGING_PREFIX, ".tmp", directory)
        try {
            val references = PhotoArchiveJournal.references(journal)
            FileOutputStream(staging).use { output ->
                val flushOnly = object : FilterOutputStream(output) {
                    override fun close() = flush()
                    override fun write(bytes: ByteArray, offset: Int, length: Int) = out.write(bytes, offset, length)
                }
                PhotoArchivePackage.write(journal, references, openPhoto, flushOnly, attachments)
                output.fd.sync()
            }
            val open = { staging.inputStream() }
            require(ArchivePreflight.verify(open).journalJson == journal) { "Backup journal verification failed" }
            PhotoArchivePackage.verifyComplete(open, references)
            beforePublish()
            Files.move(staging.toPath(), destination.toPath(), ATOMIC_MOVE)
            list(directory).filterNot { it == destination }.drop(retain - 1).forEach { it.delete() }
            BackupCompanions.publish(destination,attachments)
            return destination
        } finally {
            staging.delete()
        }
    }

    fun isBackupName(name: String): Boolean = publishedName.matches(name)

    fun list(directory: File): List<File> = directory.listFiles().orEmpty()
        .filter { it.isFile && isBackupName(it.name) }
        .sortedWith(compareByDescending<File> { it.lastModified() }.thenByDescending { it.name })
}
