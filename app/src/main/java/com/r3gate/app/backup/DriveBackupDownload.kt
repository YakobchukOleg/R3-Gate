package com.r3gate.app.backup

import com.r3gate.app.DriveHttpException
import com.r3gate.app.DriveStore
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/** Downloads one selected binary copy and verifies the complete transport payload. */
internal class DriveBackupDownload(private val drive: DriveStore, private val token: String) {
    fun write(copy: DriveBackupCatalog.Copy, target: File, checkActive: () -> Unit = {}) {
        require(copy.id.matches(Regex("[A-Za-z0-9_-]+")))
        require(copy.size in 1..DriveBackupCatalog.MAX_DOWNLOAD_BYTES)
        require(copy.md5.matches(Regex("[a-f0-9]{32}")))
        checkActive()
        var complete = false
        val connection = drive.connect("${DriveBackupCatalog.API}/${copy.id}?alt=media")
        try {
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 20_000
            connection.readTimeout = 60_000
            connection.setRequestProperty("Authorization", "Bearer $token")
            if (connection.responseCode != 200) throw DriveHttpException.from(connection)
            val digest = MessageDigest.getInstance("MD5")
            var remaining = copy.size
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        checkActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        remaining -= count
                        if (remaining < 0) throw IOException("Drive backup exceeded its listed size")
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                    }
                }
            }
            val checksum = digest.digest().joinToString("") { "%02x".format(it) }
            if (remaining != 0L || checksum != copy.md5) {
                throw IOException("Drive backup failed transport verification")
            }
            checkActive()
            complete = true
        } finally {
            connection.disconnect()
            if (!complete) target.delete()
        }
    }
}
