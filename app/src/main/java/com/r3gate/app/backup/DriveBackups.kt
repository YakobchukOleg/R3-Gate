package com.r3gate.app.backup

import android.content.Context
import com.r3gate.app.DriveStore
import java.io.File
import java.time.Instant

/** Blocking Drive backup API for the settings screen. The existing Drive sign-in supplies the token. */
object DriveBackups {
    data class Copy(val id: String, val name: String, val modifiedAt: Instant, val sizeBytes: Long, internal val checksum: String)

    fun verifyConnection(context: Context, token: String) {
        verifyConnection(context, token, DriveStore())
    }

    internal fun verifyConnection(context: Context, token: String, drive: DriveStore) {
        val account = Account(context, token, drive)
        account.checkActive()
        val preferences = context.getSharedPreferences("r3_gate", Context.MODE_PRIVATE)
        if (account.email.equals(preferences.getString(BackupStatus.PROBLEM_ACCOUNT, null), true) &&
            preferences.getString(BackupStatus.PROBLEM_ACTION, null) == BackupStatus.RequiredAction.RECONNECT_DRIVE.name) {
            val saved = preferences.edit().remove(BackupStatus.PROBLEM_STATE)
                .remove(BackupStatus.PROBLEM_MESSAGE).remove(BackupStatus.PROBLEM_ACCOUNT)
                .remove(BackupStatus.PROBLEM_ACTION)
                .putString("backupStatus", "Połączono Google Drive. Możesz ponowić wysyłanie kopii.").commit()
            if (!saved) throw java.io.IOException("Cannot save Drive connection verification")
        }
    }

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

    private class Account(context: Context, token: String, val drive: DriveStore = DriveStore()) {
        private val preferences = context.getSharedPreferences("r3_gate", Context.MODE_PRIVATE)
        val email = preferences.getString("driveEmail", "").orEmpty()

        init {
            require(token.isNotBlank() && email.isNotBlank())
            checkActive()
            val actual = drive.json("GET", "https://www.googleapis.com/drive/v3/about?fields=user(emailAddress)", token)
                .optJSONObject("user")?.optString("emailAddress")
            if (!email.equals(actual, true)) throw SecurityException("Google Drive account changed")
            checkActive()
        }

        fun checkActive() {
            if (Thread.currentThread().isInterrupted) throw InterruptedException()
            if (!preferences.getBoolean("driveConnected", false) ||
                !email.equals(preferences.getString("driveEmail", ""), true)) {
                throw SecurityException("Google Drive account changed")
            }
        }
    }
}
