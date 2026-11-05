package com.r3gate.app.backup

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.r3gate.app.DriveHttpException
import com.r3gate.app.DriveStore
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.net.HttpURLConnection
import java.net.URI

@RunWith(AndroidJUnit4::class)
class DriveConnectionRecoveryTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs get() = context.getSharedPreferences("r3_gate", Context.MODE_PRIVATE)
    private val email = "reconnect@example.invalid"
    private val keys = listOf("driveEmail", "backupStatus", BackupStatus.PROBLEM_STATE, BackupStatus.PROBLEM_ACCOUNT,
        BackupStatus.PROBLEM_MESSAGE, BackupStatus.PROBLEM_ACTION, BackupStatus.VERIFIED_AT, BackupStatus.VERIFIED_ACCOUNT)
    private var original: Map<String, String?> = emptyMap()
    private var oldConnected = false

    @Before fun setup() {
        original = keys.associateWith { prefs.getString(it, null) }
        oldConnected = prefs.getBoolean("driveConnected", false)
        assertTrue(prefs.edit().putString("driveEmail", email).putBoolean("driveConnected", true).commit())
    }

    @After fun cleanup() {
        val editor = prefs.edit().putBoolean("driveConnected", oldConnected)
        original.forEach { (key, value) -> editor.putString(key, value) }
        assertTrue(editor.commit())
    }

    private fun problem(action: BackupStatus.RequiredAction) {
        assertTrue(prefs.edit().putString(BackupStatus.PROBLEM_ACCOUNT, email)
            .putString(BackupStatus.PROBLEM_STATE, BackupState.ACTION_REQUIRED.name)
            .putString(BackupStatus.PROBLEM_MESSAGE, "Wymagane działanie")
            .putString(BackupStatus.PROBLEM_ACTION, action.name).commit())
    }

    private fun drive(actual: String = email, status: Int = 200, beforeResponse: () -> Unit = {}) = DriveStore { endpoint ->
        assertEquals("https://www.googleapis.com/drive/v3/about?fields=user(emailAddress)", endpoint)
        object : HttpURLConnection(URI(endpoint).toURL()) {
            override fun getResponseCode() = status
            override fun getInputStream(): java.io.InputStream {
                beforeResponse()
                return JSONObject().put("user", JSONObject().put("emailAddress", actual)).toString().byteInputStream()
            }
            override fun connect() = Unit
            override fun disconnect() = Unit
            override fun usingProxy() = false
        }
    }

    @Test fun verifiedReconnectClearsOnlyAccessProblemWithoutClaimingNewBackup() {
        problem(BackupStatus.RequiredAction.RECONNECT_DRIVE)
        val previous = "2026-11-04T12:00:00Z"
        prefs.edit().putString(BackupStatus.VERIFIED_AT, previous)
            .putString(BackupStatus.VERIFIED_ACCOUNT, email).commit()
        DriveBackups.verifyConnection(context, "temporary-token", drive())
        assertNull(prefs.getString(BackupStatus.PROBLEM_STATE, null))
        assertNull(prefs.getString(BackupStatus.PROBLEM_ACTION, null))
        assertEquals(previous, prefs.getString(BackupStatus.VERIFIED_AT, null))
        assertEquals(email, prefs.getString(BackupStatus.VERIFIED_ACCOUNT, null))
        assertTrue(prefs.getString("backupStatus", "").orEmpty().contains("Możesz ponowić"))
    }

    @Test fun differentAuthorizedAccountCannotClearProblem() {
        problem(BackupStatus.RequiredAction.RECONNECT_DRIVE)
        assertThrows(SecurityException::class.java) {
            DriveBackups.verifyConnection(context, "temporary-token", drive(actual = "other@example.invalid"))
        }
        assertEquals(BackupStatus.RequiredAction.RECONNECT_DRIVE.name, prefs.getString(BackupStatus.PROBLEM_ACTION, null))
    }

    @Test fun successfulAuthorizationDoesNotResolveStorageQuota() {
        problem(BackupStatus.RequiredAction.FREE_DRIVE_SPACE)
        DriveBackups.verifyConnection(context, "temporary-token", drive())
        assertEquals(BackupStatus.RequiredAction.FREE_DRIVE_SPACE.name, prefs.getString(BackupStatus.PROBLEM_ACTION, null))
        assertEquals("Wymagane działanie", prefs.getString(BackupStatus.PROBLEM_MESSAGE, null))
    }

    @Test fun rejectedTokenKeepsReconnectProblem() {
        problem(BackupStatus.RequiredAction.RECONNECT_DRIVE)
        val failure = assertThrows(DriveHttpException::class.java) {
            DriveBackups.verifyConnection(context, "revoked-token", drive(status = 401))
        }
        assertEquals(401, failure.status)
        assertEquals(BackupStatus.RequiredAction.RECONNECT_DRIVE.name, prefs.getString(BackupStatus.PROBLEM_ACTION, null))
    }

    @Test fun accountChangeWhileVerifyingDoesNotClearStoredProblem() {
        problem(BackupStatus.RequiredAction.RECONNECT_DRIVE)
        assertThrows(SecurityException::class.java) {
            DriveBackups.verifyConnection(context, "temporary-token", drive {
                prefs.edit().putString("driveEmail", "changed@example.invalid").commit()
            })
        }
        assertEquals(email, prefs.getString(BackupStatus.PROBLEM_ACCOUNT, null))
        assertEquals(BackupStatus.RequiredAction.RECONNECT_DRIVE.name, prefs.getString(BackupStatus.PROBLEM_ACTION, null))
    }

    @Test fun legacyAndUnknownActionNamesRemainReadable() {
        assertEquals(BackupStatus.RequiredAction.CHECK_FILES,
            BackupStatus.readProblem(BackupState.FAILED.name, "Błąd")?.requiredAction)
        assertEquals(BackupStatus.RequiredAction.CHECK_FILES,
            BackupStatus.readProblem(BackupState.ACTION_REQUIRED.name, "Błąd", "FUTURE_ACTION")?.requiredAction)
    }
}
