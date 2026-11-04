package com.r3gate.app.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/** Portable journal + photo container. Original JSON references remain unchanged. */
object PhotoArchivePackage {
    data class Report(val photoCount: Int, val missing: List<String>, val complete: Boolean, val legacy: Boolean)
    private const val JOURNAL = "journal/data.json"
    private const val MAX_INDEX_BYTES = 1024 * 1024

    fun write(
        json: String, photoUris: Iterable<String>,
        openPhoto: (String) -> InputStream, destination: OutputStream
    ): WorkMeterArchive.Inspection {
        require(JournalValidator.inspect(json).canRestore) { "Invalid journal" }
        val journal = json.toByteArray(Charsets.UTF_8)
        require(journal.size <= 32 * 1024 * 1024) { "Journal too large" }
        val entries = PhotoArchiveIndex.build(photoUris)
        val index = PhotoArchiveIndex.encode(entries)
        val sources = mutableListOf(
            WorkMeterArchive.Source(JOURNAL) { ByteArrayInputStream(journal) },
            WorkMeterArchive.Source(PhotoArchiveIndex.ENTRY) { ByteArrayInputStream(index) }
        )
        for (item in entries) {
            sources.add(WorkMeterArchive.Source(item.path) { openPhoto(item.uri) })
        }
        return WorkMeterArchive.write(destination, sources)
    }

    /** The opener supplies a fresh stream each call. Does not modify app storage. */
    fun inspect(open: () -> InputStream, referencedUris: Iterable<String>): Report {
        val expected = PhotoArchiveIndex.build(referencedUris)
        val expectedUris = expected.map { it.uri }.toSet()
        val inspected = open().use { WorkMeterArchive.inspect(it) }
        require(inspected.files.count { it.path == JOURNAL } == 1) { "Backup has no journal" }
        val indexItem = inspected.files.singleOrNull { it.path == PhotoArchiveIndex.ENTRY }
        val assets = inspected.files.filter { it.path.startsWith("photos/") && it.path != PhotoArchiveIndex.ENTRY }
        if (indexItem == null) {
            require(assets.isEmpty()) { "Photos without index" }
            return Report(0, expected.map { it.uri }, expected.isEmpty(), true)
        }
        require(indexItem.size in 1..MAX_INDEX_BYTES.toLong()) { "Invalid photo index size" }
        var indexBytes: ByteArray? = null
        open().use { input ->
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.name == PhotoArchiveIndex.ENTRY) {
                        val sink = ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            val n = zip.read(buffer)
                            if (n < 0) break
                            require(sink.size().toLong() + n <= MAX_INDEX_BYTES) { "Photo index too large" }
                            sink.write(buffer, 0, n)
                        }
                        indexBytes = sink.toByteArray()
                        break
                    }
                    zip.closeEntry()
                }
            }
        }
        val bytes = requireNotNull(indexBytes) { "Photo index missing on second read" }
        require(bytes.size.toLong() == indexItem.size) { "Photo index changed" }
        val sha = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
        require(sha == indexItem.sha256) { "Photo index modified during inspection" }
        val mapped = PhotoArchiveIndex.decode(bytes)
        require(mapped.map { it.path }.toSet() == assets.map { it.path }.toSet()) {
            "Photo files do not match index"
        }
        require(mapped.all { it.uri in expectedUris }) { "Unexpected photo reference in backup" }
        val actualUris = mapped.map { it.uri }.toSet()
        val missing = expected.filter { it.uri !in actualUris }.map { it.uri }
        return Report(mapped.size, missing, missing.isEmpty(), false)
    }

    fun verifyComplete(open: () -> InputStream, referencedUris: Iterable<String>): Report {
        val report = inspect(open, referencedUris)
        require(report.complete) { "Backup is missing ${report.missing.size} photos" }
        return report
    }
}
