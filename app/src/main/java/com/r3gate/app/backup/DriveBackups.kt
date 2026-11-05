package com.r3gate.app.backup

import android.content.Context
import com.r3gate.app.DriveStore
import java.io.File
import java.time.Instant

/** Blocking Drive backup API for the settings screen. The existing Drive sign-in supplies the token. */
object DriveBackups {
    data class Copy(val id: String, val name: String, val modifiedAt: Instant, val sizeBytes: Long, internal val checksum: String)

    fun copies(context: Context, token: String): List<Copy> {
        val account = Account(context, token)
        return DriveBackupCatalog(account.drive, token).list(account::checkActive)
            .map { Copy(it.id, it.name, it.modifiedAt, it.size, it.md5) }
            .also { account.checkActive() }
    }

    fun prepareRestore(context: Context, token: String, copy: Copy): PhotoRestoreCoordinator.Session {
        val account = Account(context, token)
        val selected = DriveBackupCatalog.Copy(copy.id, copy.name, copy.modifiedAt, copy.sizeBytes, copy.checksum)
        DriveBackupCatalog(account.drive, token).requireCurrent(selected)
        account.checkActive()
        val archive = File.createTempFile("workmeter-drive-", ".zip", context.cacheDir)
        try {
            DriveBackupDownload(account.drive, token).write(selected, archive, account::checkActive)
            val session = PhotoRestoreCoordinator.prepare(context) {
                account.checkActive()
                archive.inputStream()
            }
            try {
                account.checkActive()
                return session
            } catch (failure: Exception) {
                session.discard()
                throw failure
            }
        } finally {
            archive.delete()
        }
    }

    private class Account(context: Context, token: String) {
        private val preferences = context.getSharedPreferences("r3_gate", Context.MODE_PRIVATE)
        private val email = preferences.getString("driveEmail", "").orEmpty()
        val drive = DriveStore()

        init {
            require(token.isNotBlank() && email.isNotBlank())
            checkActive()
            val actual = drive.json("GET", "https://www.googleapis.com/drive/v3/about?fields=user(emailAddress)", token)
                .optJSONObject("user")?.optString("emailAddress")
            require(email.equals(actual, true)) { "Google Drive account changed" }
            checkActive()
        }

        fun checkActive() {
            if (Thread.currentThread().isInterrupted) throw InterruptedException()
            check(preferences.getBoolean("driveConnected", false) &&
                email.equals(preferences.getString("driveEmail", ""), true)) {
                "Google Drive account changed"
            }
        }
    }
}
