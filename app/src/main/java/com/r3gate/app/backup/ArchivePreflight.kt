package com.r3gate.app.backup

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/**
 * Read-only preflight of a user-selected archive. Never changes app storage.
 * The opener must provide a fresh stream on each call (e.g. ContentResolver).
 */
object ArchivePreflight {
    const val JOURNAL = "journal/data.json"
    private const val MAX_JOURNAL_BYTES = 32 * 1024 * 1024

    data class Preview(val version: Int, val entries: Int, val totalBytes: Long, val journalJson: String)

    fun verify(open: () -> InputStream): Preview {
        val report = open().use { WorkMeterArchive.inspect(it) }
        val item = report.files.singleOrNull { it.path == JOURNAL }
            ?: throw IllegalArgumentException("Backup has no journal")
        require(item.size <= MAX_JOURNAL_BYTES) { "Journal is too large" }
        var result: ByteArray? = null
        open().use { input ->
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.name == JOURNAL) {
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(65536)
                        while (true) {
                            val n = zip.read(buffer)
                            if (n < 0) break
                            require(output.size().toLong() + n <= MAX_JOURNAL_BYTES) { "Journal too large" }
                            output.write(buffer, 0, n)
                        }
                        result = output.toByteArray()
                        break
                    }
                    zip.closeEntry()
                }
            }
        }
        val bytes = requireNotNull(result) { "Journal missing after verification" }
        require(bytes.size.toLong() == item.size) { "Journal changed during inspection" }
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
        require(digest == item.sha256) { "Journal changed during inspection" }
        // The original JSON remains opaque: objectId values and archived objects are never rewritten.
        val text = String(bytes, Charsets.UTF_8)
        require(text.trimStart().startsWith("{") && text.trimEnd().endsWith("}")) { "Journal is not a JSON object" }
        return Preview(report.version, report.files.size, report.totalBytes, text)
    }
}
