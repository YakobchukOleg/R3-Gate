package com.r3gate.app.backup

import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/**
 * Extracts verified photo bytes into a new private staging directory.
 * Does not alter existing photos or the journal; caller owns the returned directory.
 */
object PhotoRestoreStage {
    private const val MAX_PHOTO_BYTES = 100L * 1024 * 1024
    data class Result(val directory: File, val files: Map<String, File>) {
        fun discard() { directory.deleteRecursively() }
    }

    fun prepare(openArchive: () -> InputStream, requiredUris: Iterable<String>, parent: File): Result {
        val expected = PhotoArchiveIndex.build(requiredUris)
        val report = PhotoArchivePackage.verifyComplete(openArchive, expected.map { it.uri })
        require(!report.legacy || expected.isEmpty()) { "Legacy backup does not contain photos" }
        require(parent.isDirectory || parent.mkdirs()) { "Cannot create staging root" }

        val directory = java.nio.file.Files.createTempDirectory(parent.toPath(), "restore-").toFile()
        val paths = expected.associateBy { it.path }
        val outputs = LinkedHashMap<String, File>()
        val digests = openArchive().use { WorkMeterArchive.inspect(it) }.files.associateBy { it.path }
        try {
            openArchive().use { input ->
                ZipInputStream(input).use { zip ->
                    val buffer = ByteArray(65536)
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        val item = paths[entry.name]
                        if (item != null) {
                            require(item.uri !in outputs) { "Duplicate photo data" }
                            val target = File(directory, entry.name.substringAfter('/'))
                            val digest = MessageDigest.getInstance("SHA-256")
                            var count = 0L
                            FileOutputStream(target).use { output ->
                                while (true) {
                                    val n = zip.read(buffer)
                                    if (n < 0) break
                                    if (n == 0) continue
                                    count += n
                                    require(count <= MAX_PHOTO_BYTES) { "Photo is too large" }
                                    digest.update(buffer, 0, n)
                                    output.write(buffer, 0, n)
                                }
                                output.fd.sync()
                            }
                            val sha = digest.digest().joinToString("") { "%02x".format(it) }
                            require(digests[item.path]?.size == count && digests[item.path]?.sha256 == sha) {
                                "Photo changed during extraction"
                            }
                            outputs[item.uri] = target
                        }
                        zip.closeEntry()
                    }
                }
            }
            require(outputs.keys == expected.map { it.uri }.toSet()) { "Photo extraction incomplete" }
            return Result(directory, outputs)
        } catch (failure: Exception) {
            directory.deleteRecursively()
            throw failure
        }
    }
}
