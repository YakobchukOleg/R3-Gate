package com.r3gate.app.backup

import java.io.InputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
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
    private const val MAX_MANIFEST_BYTES = 1024 * 1024
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
    private fun strictUtf8(bytes: ByteArray): String = try {
        StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString()
    } catch (e: CharacterCodingException) {
        throw IllegalArgumentException("Invalid UTF-8 in manifest", e)
    }

    private fun decoded(value: String): String = strictUtf8(Base64.getUrlDecoder().decode(value))

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
            val manifestBytes = manifest.toByteArray(Charsets.UTF_8)
            require(manifestBytes.size <= MAX_MANIFEST_BYTES) { "Manifest too large" }
            zip.write(manifestBytes)
            zip.closeEntry()
        }
        return Inspection(VERSION, entries, total)
    }

    /** Fully reads and hashes entries before returning a successful inspection. No writes to app storage. */
    /** Stream trailer verification without buffering archive contents. */
    private class TrailerStream(input: InputStream) : FilterInputStream(input) {
        private val last = ByteArray(22)
        private var position = 0L
        override fun read(): Int {
            val value = super.read()
            if (value >= 0) { last[(position % 22).toInt()] = value.toByte(); position++ }
            return value
        }
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val count = super.`in`.read(buffer, offset, length)
            if (count > 0) for (i in 0 until count) {
                last[(position % 22).toInt()] = buffer[offset + i]
                position++
            }
            return count
        }
        fun finish(expectedEntries: Int) {
            val buffer = ByteArray(65536)
            while (read(buffer, 0, buffer.size) >= 0) { }
            require(position >= 22) { "ZIP end record missing" }
            fun byte(index: Int) = last[((position - 22 + index) % 22).toInt()].toInt() and 255
            require(byte(0) == 0x50 && byte(1) == 0x4b && byte(2) == 0x05 && byte(3) == 0x06) {
                "ZIP central directory missing or truncated"
            }
            require(byte(20) == 0 && byte(21) == 0) { "ZIP archive comments unsupported" }
            require(byte(4) == 0 && byte(5) == 0 && byte(6) == 0 && byte(7) == 0) {
                "Multi-disk ZIP unsupported"
            }
            fun ushort(index: Int) = byte(index) or (byte(index + 1) shl 8)
            fun uint(index: Int): Long = ushort(index).toLong() or (ushort(index + 2).toLong() shl 16)
            val entries = ushort(10)
            require(ushort(8) == entries && entries == expectedEntries) {
                "ZIP central directory entry count differs"
            }
            val centralSize = uint(12)
            val centralOffset = uint(16)
            require(entries != 65535 && centralSize > 0L && centralOffset + centralSize == position - 22) {
                "ZIP central directory inconsistent"
            }
        }
    }

    fun inspect(source: InputStream): Inspection = try { inspectUnchecked(source) }
    catch (e: IOException) { throw IllegalArgumentException("Invalid or truncated ZIP", e) }

    private fun inspectUnchecked(source: InputStream): Inspection {
        val tracked = TrailerStream(source)
        val found = LinkedHashMap<String, Item>()
        var manifest: String? = null
        var total = 0L
        ZipInputStream(tracked).use { zip ->
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
                        require(bytes.size() + n <= MAX_MANIFEST_BYTES) { "Manifest too large" }
                        bytes.write(buffer, 0, n)
                    }
                    manifest = strictUtf8(bytes.toByteArray())
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
            tracked.finish(found.size + if (manifest != null) 1 else 0)
        }
        val manifestText = requireNotNull(manifest) { "Missing manifest" }
        require(manifestText.endsWith('\n')) { "Truncated manifest" }
        val lines = manifestText.dropLast(1).split('\n')
        require(lines.none { it.isEmpty() }) { "Invalid blank manifest line" }
        require(lines.firstOrNull() == HEADER) { "Unsupported archive version" }
        val expected = lines.drop(1).map { line ->
            val fields = line.split('\t')
            require(fields.size == 3) { "Invalid manifest" }
            val path = decoded(fields[0])
            require(valid(path)) { "Invalid manifest path" }
            val size = requireNotNull(fields[1].toLongOrNull()) { "Invalid size" }
            require(size >= 0 && fields[2].matches(Regex("[0-9a-f]{64}"))) { "Invalid digest" }
            Item(path, size, fields[2])
        }
        require(expected.size == found.size && expected.map { it.path }.distinct().size == expected.size) { "File list differs" }
        require(expected.all { found[it.path] == it }) { "Archive integrity mismatch" }
        return Inspection(VERSION, expected, total)
    }
}
