package com.r3gate.app.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@RunWith(AndroidJUnit4::class)
class JournalRestoreStoreTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val prefs get() = context.getSharedPreferences("r3_gate", Context.MODE_PRIVATE)
    private var original: String? = null
    private val checkpoint get() = java.io.File(context.noBackupFilesDir, "journal-before-restore.wmb.zip")
    private var originalCheckpoint: ByteArray? = null
    private val undoCheckpoint get() = JournalRecoveryCheckpoint.file(java.io.File(context.noBackupFilesDir, "before-recovery"))
    private var originalUndo: ByteArray? = null
    private val before = """{"objects":[{"id":"existing"}]}"""
    private val incoming = """{"objects":[{"id":"archived","archived":true}],"shifts":[{"objectId":"archived"}]}"""

    @Before fun setUp() {
        original = prefs.getString("data", null)
        originalCheckpoint = if (checkpoint.exists()) checkpoint.readBytes() else null
        originalUndo = if (undoCheckpoint.exists()) undoCheckpoint.readBytes() else null
        assertTrue(prefs.edit().putString("data", before).commit())
    }
    @After fun tearDown() {
        if (originalUndo == null) undoCheckpoint.delete() else undoCheckpoint.writeBytes(originalUndo!!)
        if (originalCheckpoint == null) checkpoint.delete()
        else checkpoint.writeBytes(originalCheckpoint!!)
        assertTrue(prefs.edit().apply {
            if (original == null) remove("data") else putString("data", original)
        }.commit())
    }
    private fun plan(json: String = incoming): RestorePlan.Preview {
        val out = ByteArrayOutputStream()
        JournalSnapshot.write(json, out)
        val bytes = out.toByteArray()
        return RestorePlan.prepare({ ByteArrayInputStream(bytes) }, before)
    }
    @Test fun refusingConfirmationKeepsExistingJournal() {
        val preview = plan()
        try {
            JournalRestoreStore.restore(context, preview, false)
            fail("Restore without confirmation")
        } catch (_: IllegalArgumentException) { }
        assertEquals(before, prefs.getString("data", null))
    }
    @Test fun approvedRestorePreservesArchiveObjectIds() {
        assertEquals(JournalRestoreStore.Outcome.RESTORED,
            JournalRestoreStore.restore(context, plan(), true))
        assertEquals(incoming, prefs.getString("data", null))
    }
    @Test fun changedJournalBlocksStalePreview() {
        val preview = plan()
        val changed = """{"objects":[{"id":"changed"}]}"""
        assertTrue(prefs.edit().putString("data", changed).commit())
        try {
            JournalRestoreStore.restore(context, preview, true)
            fail("Stale restore accepted")
        } catch (_: IllegalArgumentException) { }
        assertEquals(changed, prefs.getString("data", null))
    }
    @Test fun identicalJournalSkipsWrite() {
        val preview = plan(before)
        assertEquals(JournalRestoreStore.Outcome.UNCHANGED,
            JournalRestoreStore.restore(context, preview, true))
        assertEquals(before, prefs.getString("data", null))
    }
    @Test fun checkpointPreservesPreviousJournal() {
        JournalRestoreStore.restore(context, plan(), true)
        assertTrue(checkpoint.isFile)
        assertEquals(before, JournalRecoveryCheckpoint.readVerified(checkpoint))
        assertEquals(incoming, prefs.getString("data", null))
    }
    @Test fun rejectedRestoreDoesNotOverwriteExistingJournal() {
        try { JournalRestoreStore.restore(context, plan(), false) }
        catch (_: IllegalArgumentException) { }
        assertEquals(before, prefs.getString("data", null))
    }

    @Test fun corruptedCheckpointIsDetected() {
        JournalRestoreStore.restore(context, plan(), true)
        checkpoint.writeText("damaged", Charsets.UTF_8)
        try {
            JournalRecoveryCheckpoint.readVerified(checkpoint)
            fail("Corrupted recovery archive accepted")
        } catch (_: Exception) { }
    }

    @Test fun refusedRestoreDoesNotCreateRecoveryFile() {
        checkpoint.delete()
        try {
            JournalRestoreStore.restore(context, plan(), false)
            fail("Unconfirmed restore accepted")
        } catch (_: IllegalArgumentException) { }
        assertFalse(checkpoint.exists())
        assertEquals(before, prefs.getString("data", null))
    }

    @Test fun recoveryRequiresConfirmationAndKeepsCheckpoint() {
        JournalRestoreStore.restore(context, plan(), true)
        val recovery = requireNotNull(JournalRestoreStore.previewRecovery(context))
        assertTrue(recovery.summary.replacesJournal)
        assertEquals(1, recovery.summary.objects)
        assertThrows(IllegalArgumentException::class.java) { JournalRestoreStore.recover(context, recovery, false) }
        assertEquals(incoming, prefs.getString("data", null))
        assertEquals(before, JournalRecoveryCheckpoint.readVerified(checkpoint))
    }

    @Test fun recoveryRestoresPriorJournalAndPreservesBothVersions() {
        JournalRestoreStore.restore(context, plan(), true)
        val recovery = requireNotNull(JournalRestoreStore.previewRecovery(context))
        assertEquals(JournalRestoreStore.Outcome.RESTORED, JournalRestoreStore.recover(context, recovery, true))
        assertEquals(before, prefs.getString("data", null))
        assertEquals(before, JournalRecoveryCheckpoint.readVerified(checkpoint))
        assertEquals(incoming, JournalRecoveryCheckpoint.readVerified(undoCheckpoint))
    }

    @Test fun recoveryRejectsJournalEditedAfterPreview() {
        JournalRestoreStore.restore(context, plan(), true)
        val recovery = requireNotNull(JournalRestoreStore.previewRecovery(context))
        assertTrue(prefs.edit().putString("data", "{}").commit())
        assertThrows(IllegalArgumentException::class.java) { JournalRestoreStore.recover(context, recovery, true) }
        assertEquals("{}", prefs.getString("data", null))
    }

    @Test fun recoveryRejectsReplacedCheckpointAfterPreview() {
        JournalRestoreStore.restore(context, plan(), true)
        val recovery = requireNotNull(JournalRestoreStore.previewRecovery(context))
        JournalRecoveryCheckpoint.save(context.noBackupFilesDir, "{}")
        assertThrows(IllegalArgumentException::class.java) { JournalRestoreStore.recover(context, recovery, true) }
        assertEquals(incoming, prefs.getString("data", null))
    }

    @Test fun recoveryRejectsPhotoRemovedAfterPreview() {
        val photo = java.io.File.createTempFile("recovery-photo-", ".jpg", context.cacheDir)
        try {
            photo.writeBytes(byteArrayOf(1, 2, 3))
            val uri = android.net.Uri.fromFile(photo)
            JournalRecoveryCheckpoint.save(context.noBackupFilesDir, """{"links":[{"photos":["$uri"]}]}""")
            val recovery = requireNotNull(JournalRestoreStore.previewRecovery(context))
            assertEquals(1, recovery.summary.photos)
            assertTrue(photo.delete())
            assertThrows(java.io.FileNotFoundException::class.java) {
                JournalRestoreStore.recover(context, recovery, true)
            }
            assertEquals(before, prefs.getString("data", null))
        } finally {
            photo.delete()
        }
    }

}
