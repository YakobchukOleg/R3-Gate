package com.r3gate.app.backup

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class BackupStatusTest {
    @Test fun verifiedTimestampBelongsOnlyToCurrentConnectedAccount() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = context.getSharedPreferences("r3_gate", Context.MODE_PRIVATE)
        val oldEmail = preferences.getString("driveEmail", null)
        val oldConnected = preferences.getBoolean("driveConnected", false)
        val oldAccount = preferences.getString(BackupStatus.VERIFIED_ACCOUNT, null)
        val oldVerifiedAt = preferences.getString(BackupStatus.VERIFIED_AT, null)
        val stamp = Instant.parse("2026-11-04T22:00:00Z")
        try {
            assertTrue(preferences.edit().putString("driveEmail", "A@example.invalid")
                .putBoolean("driveConnected", true)
                .putString(BackupStatus.VERIFIED_ACCOUNT, "a@example.invalid")
                .putString(BackupStatus.VERIFIED_AT, stamp.toString()).commit())
            assertEquals(stamp, BackupStatus.read(context).lastVerifiedDriveCopy)

            assertTrue(preferences.edit().putString("driveEmail", "b@example.invalid").commit())
            assertNull(BackupStatus.read(context).lastVerifiedDriveCopy)

            assertTrue(preferences.edit().putString("driveEmail", "a@example.invalid")
                .putBoolean("driveConnected", false).commit())
            val disconnected = BackupStatus.read(context)
            assertFalse(disconnected.driveConnected)
            assertNull(disconnected.lastVerifiedDriveCopy)
        } finally {
            preferences.edit().putString("driveEmail", oldEmail).putBoolean("driveConnected", oldConnected)
                .putString(BackupStatus.VERIFIED_ACCOUNT, oldAccount)
                .putString(BackupStatus.VERIFIED_AT, oldVerifiedAt).commit()
        }
    }
    @Test fun unresolvedPeriodicProblemSurvivesReadAndIsScopedToAccount() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = context.getSharedPreferences("r3_gate", Context.MODE_PRIVATE)
        val keys = listOf("driveEmail", BackupStatus.PROBLEM_ACCOUNT,
            BackupStatus.PROBLEM_STATE, BackupStatus.PROBLEM_MESSAGE, BackupStatus.PROBLEM_ACTION)
        val original = keys.associateWith { preferences.getString(it, null) }
        val message = "Brak miejsca w Google Drive."
        try {
            assertTrue(preferences.edit().putString("driveEmail", "a@example.invalid")
                .putString(BackupStatus.PROBLEM_ACCOUNT, "a@example.invalid")
                .putString(BackupStatus.PROBLEM_STATE, BackupState.ACTION_REQUIRED.name)
                .putString(BackupStatus.PROBLEM_MESSAGE, message).remove(BackupStatus.PROBLEM_ACTION).commit())
            repeat(2) {
                assertEquals(BackupStatus.Problem(BackupState.ACTION_REQUIRED, message),
                    BackupStatus.read(context).driveProblem)
            }
            assertTrue(preferences.edit().putString("driveEmail", "b@example.invalid").commit())
            assertNull(BackupStatus.read(context).driveProblem)
            assertTrue(preferences.edit().putString("driveEmail", "a@example.invalid")
                .remove(BackupStatus.PROBLEM_STATE).remove(BackupStatus.PROBLEM_ACCOUNT)
                .remove(BackupStatus.PROBLEM_MESSAGE).commit())
            assertNull(BackupStatus.read(context).driveProblem)
        } finally {
            val editor = preferences.edit()
            original.forEach { (key, value) -> editor.putString(key, value) }
            assertTrue(editor.commit())
        }
    }

    @Test fun invalidOrSuccessfulStoredStatesCannotBecomeProblems() {
        assertNull(BackupStatus.readProblem(BackupState.DRIVE_READY.name, "Gotowa"))
        assertNull(BackupStatus.readProblem("UNKNOWN", "Problem"))
        assertNull(BackupStatus.readProblem(BackupState.FAILED.name, ""))
        assertEquals(BackupStatus.Problem(BackupState.FAILED, "Błąd"),
            BackupStatus.readProblem(BackupState.FAILED.name, "Błąd"))
    }

}
