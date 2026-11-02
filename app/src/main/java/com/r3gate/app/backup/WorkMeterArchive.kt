package com.r3gate.app.backup

import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Portable, offline archive container. No application data is mutated here.
 * The caller supplies consistent snapshots and is responsible for SAF selection.
 */
object WorkMeterArchive {
    const val VERSION = 1
    private const val MANIFEST = "workmeter-manifest.txt"
    private const val HEADER = "WORKMETER-ARCHIVE/1"
    private const val MAX_ENTRIES = 10000
    private const val MAX_ENTRY_BYTES = 1024L * 1024 * 1024 * 2
    private const val MAX_TOTAL_BYTES = 1024L * 1024 * 1024 * 8
    data class Source(val path: String, val open: () -> InputStream)
    data class Item(val path: String, val size: Long, val sha256: String)
    data class Inspection(val version: Int, val files: List<Item>, val totalBytes: Long)

    private fun valid(path: String): Boolean =
        path.isNotBlank() && path != MANIFEST && !path.startsWith("/") &&
        !path.contains('\\') && path.split('/').all { it.isNotEmpty() && it != "." && it != ".." } &&
        !path.any { it.code < 32 }

    private fun sha(): MessageDigest = MessageDigest.getInstance("SHA-256")
    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
    private fun encoded(path: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(path.toByteArray(Charsets.UTF_8))
    private fun decoded(value: String): String =
        String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8)

    /** Write to a private or SAF-backed destination, never a public temporary file. */
    fun write(destination: OutputStream, sources: List<Source>): Inspection {
        require(sources.size <= MAX_ENTRIES) { "Too many files" }
        require(sources.all { valid(it.path) } && sources.map { it.path }.distinct().size == sources.size) { "Invalid or duplicate path" }
        val entries = ArrayList<Item>()
        var total = 0L
        ZipOutputStream(destination).use { zip ->
            val buffer = ByteArray(65536)
            for (source in sources) {
                zip.putNextEntry(ZipEntry(source.path))
                val digest = sha()
                var count = 0L
                source.open().use { input ->
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        if (n == 0) continue
                        count += n
                        total += n
                        require(count <= MAX_ENTRY_BYTES && total <= MAX_TOTAL_BYTES) { "Archive exceeds limit" }
                        digest.update(buffer, 0, n)
                        zip.write(buffer, 0, n)
                    }
                }
                zip.closeEntry()
                entries.add(Item(source.path, count, hex(digest.digest())))
            }
            val manifest = (listOf(HEADER) + entries.map { "${encoded(it.path)}\t${it.size}\t${it.sha256}" }).joinToString("\n", postfix = "\n")
            zip.putNextEntry(ZipEntry(MANIFEST))
            zip.write(manifest.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        return Inspection(VERSION, entries, total)
    }

    /** Fully reads and hashes entries before returning a successful inspection. No writes to app storage. */
    fun inspect(source: InputStream): Inspection {
        val found = LinkedHashMap<String, Item>()
        var manifest: String? = null
        var total = 0L
        ZipInputStream(source).use { zip ->
            val buffer = ByteArray(65536)
            while (true) {
                val entry = zip.nextEntry ?: break
                require(!entry.isDirectory) { "Directory entries not permitted" }
                val path = entry.name
                require(path == MANIFEST || valid(path)) { "Unsafe entry path" }
                require(!found.containsKey(path) && !(path == MANIFEST && manifest != null)) { "Duplicate entry" }
                require(manifest == null) { "Manifest must be last" }
                if (path == MANIFEST) {
                    val bytes = java.io.ByteArrayOutputStream()
                    while (true) {
                        val n = zip.read(buffer)
                        if (n < 0) break
                        require(bytes.size() + n <= 1024 * 1024) { "Manifest too large" }
                        bytes.write(buffer, 0, n)
                    }
                    manifest = bytes.toString("UTF-8")
                } else {
                    require(found.size < MAX_ENTRIES) { "Too many entries" }
                    val digest = sha()
                    var count = 0L
                    while (true) {
                        val n = zip.read(buffer)
                        if (n < 0) break
                        count += n
                        total += n
                        require(count <= MAX_ENTRY_BYTES && total <= MAX_TOTAL_BYTES) { "Archive exceeds limit" }
                        digest.update(buffer, 0, n)
                    }
                    found[path] = Item(path, count, hex(digest.digest()))
                }
                zip.closeEntry()
            }
        }
        val lines = requireNotNull(manifest) { "Missing manifest" }.trimEnd('\n').split('\n')
        require(lines.firstOrNull() == HEADER) { "Unsupported archive version" }
        val expected = lines.drop(1).map { line ->
            val fields = line.split('\t')
            require(fields.size == 3) { "Invalid manifest" }
            val path = decoded(fields[0])
            require(valid(path)) { "Invalid manifest path" }
            val size = fields[1].toLongOrNull() ?: error("Invalid size")
            require(size >= 0 && fields[2].matches(Regex("[0-9a-f]{64}"))) { "Invalid digest" }
            Item(path, size, fields[2])
        }
        require(expected.size == found.size && expected.map { it.path }.distinct().size == expected.size) { "File list differs" }
        require(expected.all { found[it.path] == it }) { "Archive integrity mismatch" }
        return Inspection(VERSION, expected, total)
    }
}
