package com.r3gate.app.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class BackupSettingsControllerTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val prefs get() = context.getSharedPreferences("r3_gate", Context.MODE_PRIVATE)
    private val local get() = context.getSharedPreferences(LocalBackupWorker.PREFERENCES, Context.MODE_PRIVATE)
    private var originalJournal: String? = null
    private var originalRetention: Int? = null
    private var originalLocal: Map<String, String?> = emptyMap()
    private var previousCopies: Set<String> = emptySet()
    private var previousStages: Set<File> = emptySet()
    private var checkpointBytes: ByteArray? = null
    private val checkpoint get() = JournalRecoveryCheckpoint.file(context.noBackupFilesDir)
    private lateinit var controller: BackupSettingsController
    private lateinit var host: Host
    private val initial = """{"objects":[{"id":"current"}]}"""
    private val incoming = """{"objects":[{"id":"old","archived":true}],"payments":[{"amount":"123.45"}]}"""

    private class Host : BackupSettingsController.Host {
        var uri: Uri? = null
        var restoredCount = 0
        var documentRequests = 0
        var replay = false
        var authorizations = 0
        override fun chooseDocument(intent: Intent, selected: (Uri?) -> Unit) {
            documentRequests++
            selected(uri)
            if (replay) selected(uri)
        }
        override fun authorizeDrive(authorized: (String?) -> Unit) {
            authorizations++
            authorized(null)
        }
        override fun chooseDriveCopy(copies: List<DriveBackups.Copy>, selected: (DriveBackups.Copy?) -> Unit) = selected(null)
        override fun restored() { restoredCount++ }
    }

    @Before fun setup() {
        originalJournal = prefs.getString("data", null)
        originalRetention = local.all[LocalBackupWorker.RETENTION] as? Int
        originalLocal = listOf(LocalBackupWorker.LAST_FILE, LocalBackupWorker.LAST_SUCCESS, LocalBackupWorker.STATUS_ERROR)
            .associateWith { local.getString(it, null) }
        previousCopies = LocalBackups.copies(context).map { it.name }.toSet()
        previousStages = stages()
        checkpointBytes = checkpoint.takeIf(File::isFile)?.readBytes()
        assertTrue(prefs.edit().putString("data", initial).commit())
        assertTrue(local.edit().putInt(LocalBackupWorker.RETENTION, 30).commit())
        host = Host()
        instrumentation.runOnMainSync {
            controller = BackupSettingsController(context, object : BackupSettingsPanel.Appearance {
                override fun surface(view: View) = Unit
                override fun text(view: TextView) = Unit
                override fun button(view: Button, primary: Boolean) = Unit
                override fun input(view: View) = Unit
            }, host)
            controller.refresh()
        }
        waitForText("Utwórz kopię lokalną")
    }

    @After fun cleanup() {
        if (::controller.isInitialized) instrumentation.runOnMainSync { controller.close() }
        await { stages() == previousStages }
        synchronized(LocalBackupStore) {
            LocalBackups.copies(context).filterNot { it.name in previousCopies }.forEach {
                File(context.filesDir, LocalBackupWorker.DIRECTORY + "/" + it.name).delete()
            }
            val editor = local.edit()
            originalLocal.forEach { (key, value) -> editor.putString(key, value) }
            if (originalRetention == null) editor.remove(LocalBackupWorker.RETENTION)
            else editor.putInt(LocalBackupWorker.RETENTION, requireNotNull(originalRetention))
            assertTrue(editor.commit())
        }
        assertTrue(prefs.edit().putString("data", originalJournal).commit())
        if (checkpointBytes == null) checkpoint.delete() else checkpoint.writeBytes(requireNotNull(checkpointBytes))
    }

    private fun views(view: View): List<View> = listOf(view) + if (view is ViewGroup) {
        (0 until view.childCount).flatMap { views(view.getChildAt(it)) }
    } else emptyList()

    private fun click(label: String) = instrumentation.runOnMainSync {
        views(controller.view).filterIsInstance<Button>().single { it.text.toString() == label }.performClick()
    }

    private fun waitForText(label: String) = await {
        var found = false
        instrumentation.runOnMainSync {
            found = views(controller.view).filterIsInstance<TextView>().any { it.text.toString() == label }
        }
        found
    }

    private fun await(condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 10_000
        while (!condition()) {
            check(SystemClock.elapsedRealtime() < deadline) { "Backup screen did not finish" }
            Thread.sleep(25)
        }
    }

    private fun stages(): Set<File> = File(context.filesDir, "photos").listFiles().orEmpty()
        .filter { it.isDirectory && it.name.startsWith("restore-") }.toSet()

    private fun archive(): File = File.createTempFile("controller-import-", ".zip", context.cacheDir).apply {
        outputStream().use { PhotoArchivePackage.write(incoming, emptyList(), { error("Unexpected photo") }, it) }
    }

    @Test fun createLocalButtonProducesVerifiedCopyWithoutChangingJournal() {
        click("Utwórz kopię lokalną")
        waitForText("Zapisano lokalnie.")
        val copy = LocalBackups.copies(context).single { it.name !in previousCopies }
        val archive = File(context.filesDir, LocalBackupWorker.DIRECTORY + "/" + copy.name)
        val journal = ArchivePreflight.verify { archive.inputStream() }.journalJson
        assertTrue(journal.contains("current"))
        assertEquals(initial, prefs.getString("data", null))
    }

    @Test fun cancelledSystemFilePickerLeavesJournalUnchanged() {
        host.uri = null
        click("Importuj z pliku")
        waitForText("Importuj z pliku")
        assertEquals(1, host.documentRequests)
        assertEquals(initial, prefs.getString("data", null))
        assertEquals(0, host.restoredCount)
    }

    @Test fun importWaitsForConfirmationThenRestoresExactlyOnce() {
        val file = archive()
        try {
            host.uri = Uri.fromFile(file)
            host.replay = true
            click("Importuj z pliku")
            waitForText("Znaleziono kopię zapasową")
            assertEquals(initial, prefs.getString("data", null))
            assertEquals(0, host.restoredCount)
            click("Przywróć dane")
            waitForText("Przywrócono dane.")
            assertEquals(incoming, prefs.getString("data", null))
            assertEquals(1, host.restoredCount)
        } finally {
            file.delete()
        }
    }

    @Test fun cancelPreviewDiscardsStagedDataWithoutRestoring() {
        val file = archive()
        try {
            host.uri = Uri.fromFile(file)
            click("Importuj z pliku")
            waitForText("Znaleziono kopię zapasową")
            assertTrue(stages().size > previousStages.size)
            click("Anuluj")
            waitForText("Anulowano przywracanie.")
            assertEquals(previousStages, stages())
            assertEquals(initial, prefs.getString("data", null))
            assertEquals(0, host.restoredCount)
        } finally {
            file.delete()
        }
    }

    @Test fun leavingSettingsDiscardsPreparedSession() {
        val file = archive()
        try {
            host.uri = Uri.fromFile(file)
            click("Importuj z pliku")
            waitForText("Znaleziono kopię zapasową")
            instrumentation.runOnMainSync { controller.close() }
            await { stages() == previousStages }
            assertEquals(initial, prefs.getString("data", null))
            assertEquals(0, host.restoredCount)
        } finally {
            file.delete()
        }
    }

    @Test fun exportButtonWritesFreshZipThroughFilePicker() {
        val target = File.createTempFile("controller-export-", ".zip", context.cacheDir)
        try {
            host.uri = Uri.fromFile(target)
            click("Eksportuj ZIP do pliku")
            waitForText("Zapisano plik ZIP.")
            assertTrue(ArchivePreflight.verify { target.inputStream() }.journalJson.contains("current"))
            assertEquals(initial, prefs.getString("data", null))
        } finally {
            target.delete()
        }
    }
    @Test fun refreshWhilePreviewVisiblePreservesTheConfirmationStep() {
        val file = archive()
        try {
            host.uri = Uri.fromFile(file)
            click("Importuj z pliku")
            waitForText("Znaleziono kopię zapasową")
            instrumentation.runOnMainSync { controller.refresh() }
            waitForText("Znaleziono kopię zapasową")
            assertEquals(initial, prefs.getString("data", null))
            click("Anuluj")
            waitForText("Anulowano przywracanie.")
            assertEquals(previousStages, stages())
        } finally {
            file.delete()
        }
    }

    @Test fun cancelledReconnectionKeepsTheUnresolvedAccessProblem() {
        val keys = listOf("driveEmail", BackupStatus.PROBLEM_ACCOUNT, BackupStatus.PROBLEM_STATE,
            BackupStatus.PROBLEM_MESSAGE, BackupStatus.PROBLEM_ACTION)
        val original = keys.associateWith { prefs.getString(it, null) }
        val connected = prefs.getBoolean("driveConnected", false)
        try {
            assertTrue(prefs.edit().putString("driveEmail", "reconnect@example.invalid")
                .putBoolean("driveConnected", true)
                .putString(BackupStatus.PROBLEM_ACCOUNT, "reconnect@example.invalid")
                .putString(BackupStatus.PROBLEM_STATE, BackupState.ACTION_REQUIRED.name)
                .putString(BackupStatus.PROBLEM_MESSAGE, "Połącz ponownie")
                .putString(BackupStatus.PROBLEM_ACTION, BackupStatus.RequiredAction.RECONNECT_DRIVE.name).commit())
            instrumentation.runOnMainSync { controller.refresh() }
            waitForText("Połącz ponownie Google Drive")
            click("Połącz ponownie Google Drive")
            waitForText("Nie ukończono łączenia z Google Drive.")
            assertEquals(1, host.authorizations)
            assertEquals(BackupStatus.RequiredAction.RECONNECT_DRIVE.name, prefs.getString(BackupStatus.PROBLEM_ACTION, null))
        } finally {
            val editor = prefs.edit().putBoolean("driveConnected", connected)
            original.forEach { (key, value) -> editor.putString(key, value) }
            editor.commit()
        }
    }

}
