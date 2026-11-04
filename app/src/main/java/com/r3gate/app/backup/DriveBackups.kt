package com.r3gate.app.backup

import android.content.Context
import com.r3gate.app.DriveHttpException
import com.r3gate.app.DriveStore
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.time.Instant

/** Blocking Drive backup API for the settings screen. The existing Drive sign-in supplies the token. */
object DriveBackups {
    data class Copy(val id: String, val name: String, val modifiedAt: Instant, val sizeBytes: Long, internal val checksum: String)

    fun copies(context: Context, token: String): List<Copy> =
        catalog(context, token).list().map { Copy(it.id, it.name, it.modifiedAt, it.size, it.md5) }

    fun prepareRestore(context: Context, token: String, copy: Copy): PhotoRestoreCoordinator.Session {
        catalog(context, token).requireCurrent(
            DriveBackupCatalog.Copy(copy.id, copy.name, copy.modifiedAt, copy.sizeBytes, copy.checksum)
        )
        val archive = File.createTempFile("workmeter-drive-", ".zip", context.cacheDir)
        try {
            download(token, copy, archive)
            return PhotoRestoreCoordinator.prepare(context) { archive.inputStream() }
        } finally {
            archive.delete()
        }
    }

    private fun catalog(context: Context, token: String): DriveBackupCatalog {
        val prefs = context.getSharedPreferences("r3_gate", Context.MODE_PRIVATE)
        val account = prefs.getString("driveEmail", "").orEmpty()
        require(token.isNotBlank() && account.isNotBlank() && prefs.getBoolean("driveConnected", false))
        val drive = DriveStore()
        val actual = drive.json("GET", "https://www.googleapis.com/drive/v3/about?fields=user(emailAddress)", token)
            .optJSONObject("user")?.optString("emailAddress")
        require(account.equals(actual, true)) { "Google Drive account changed" }
        return DriveBackupCatalog(drive, token)
    }

    private fun download(token: String, copy: Copy, archive: File) {
        val connection = URL("${DriveBackupCatalog.API}/${copy.id}?alt=media").openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 20_000
            connection.readTimeout = 60_000
            connection.setRequestProperty("Authorization", "Bearer $token")
            val status = connection.responseCode
            if (status != 200) throw DriveHttpException(status)
            val digest = MessageDigest.getInstance("MD5")
            var remaining = copy.sizeBytes
            connection.inputStream.use { input ->
                archive.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        if (Thread.currentThread().isInterrupted) throw InterruptedException()
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
            if (remaining != 0L || checksum != copy.checksum) throw IOException("Drive backup failed transport verification")
        } finally {
            connection.disconnect()
        }
    }
}
