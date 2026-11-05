package com.r3gate.app.backup

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.r3gate.app.MainActivity
import com.r3gate.app.WorkMeterTheme
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class BackupSettingsActivityTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val prefs get() = context.getSharedPreferences("r3_gate", 0)
    private var previous = emptyMap<String, Any?>()
    private val initial = """{"objects":[{"id":"current","organization":"Łódź"}],"shifts":[],"materials":[],"payments":[]}"""
    private val incoming = """{"objects":[{"id":"historical","archived":true}],"shifts":[]}"""
    @Before fun seed() {
        previous = prefs.all.toMap()
        prefs.edit().clear().putString("data", initial).putString("pin", "not-used")
            .putBoolean("gps_enabled", false).commit()
    }
    @After fun restore() {
        val edit = prefs.edit().clear()
        previous.forEach { (key, value) -> when (value) {
            is String -> edit.putString(key, value)
            is Boolean -> edit.putBoolean(key, value)
            is Int -> edit.putInt(key, value)
            is Long -> edit.putLong(key, value)
            is Float -> edit.putFloat(key, value)
            is Set<*> -> edit.putStringSet(key, value.filterIsInstance<String>().toSet())
        } }
        edit.commit()
    }
    private fun views(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()
    private fun click(activity: Activity, label: String) = views(activity.window.decorView)
        .filterIsInstance<Button>().single { it.text.toString() == label }.performClick()
    private fun has(activity: Activity, label: String) = views(activity.window.decorView)
        .filterIsInstance<TextView>().any { it.text.toString() == label }
    private fun await(scenario: ActivityScenario<BackupSettingsActivity>, label: String) {
        val end = SystemClock.uptimeMillis() + 10000
        while (SystemClock.uptimeMillis() < end) {
            var found = false
            scenario.onActivity { activity ->
                found = views(activity.window.decorView).filterIsInstance<TextView>().any {
                    it.text.toString() == label && (it !is Button || it.isEnabled)
                }
            }
            if (found) return
            SystemClock.sleep(30)
        }
        fail("Missing Backup screen text: $label")
    }
    private fun documentFilter(action: String) = IntentFilter(action).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        addDataType("*/*")
    }
    private fun withPicker(result: Instrumentation.ActivityResult, action: () -> Unit) {
        val monitor = instrumentation.addMonitor(documentFilter(Intent.ACTION_CREATE_DOCUMENT), result, true)
        try { action(); assertEquals(1, monitor.hits) } finally { instrumentation.removeMonitor(monitor) }
    }

    @Test fun commonSettingsActuallyOpensTheBackupPanel() {
        val monitor = instrumentation.addMonitor(BackupSettingsActivity::class.java.name, null, false)
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    views(activity.window.decorView).single { it.contentDescription == "Ustawienia" }.performClick()
                    click(activity, "Kopia zapasowa i synchronizacja")
                }
                val opened = instrumentation.waitForMonitorWithTimeout(monitor, 5000)
                assertTrue(opened is BackupSettingsActivity)
                instrumentation.runOnMainSync { opened.finish() }
            }
        } finally { instrumentation.removeMonitor(monitor) }
    }

    @Test fun allThemesUseTheRealSharedPalette() {
        for (theme in WorkMeterTheme.entries) {
            prefs.edit().putString(WorkMeterTheme.PREF_KEY, theme.key).commit()
            ActivityScenario.launch(BackupSettingsActivity::class.java).use { scenario ->
                await(scenario, "Utwórz kopię lokalną")
                scenario.onActivity { activity ->
                    val panel = views(activity.window.decorView).filterIsInstance<BackupSettingsPanel>().single()
                    assertEquals(theme.palette.background, (panel.background as ColorDrawable).color)
                    val title = views(panel).filterIsInstance<TextView>().single { it.text == "Kopia zapasowa i synchronizacja" }
                    assertEquals(theme.palette.textPrimary, title.currentTextColor)
                    val primary = views(panel).filterIsInstance<Button>().single { it.text == "Utwórz kopię lokalną" }
                    assertEquals(theme.palette.onPrimary, primary.currentTextColor)
                }
                instrumentation.waitForIdleSync()
                SystemClock.sleep(250)
                val screenshot = File(context.getExternalFilesDir(null), "backup-settings-${theme.key}.png")
                screenshot.outputStream().use { instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                instrumentation.uiAutomation.executeShellCommand("cp ${screenshot.absolutePath} /sdcard/Download/${screenshot.name}").use {
                    java.io.FileInputStream(it.fileDescriptor).use { input -> input.readBytes() }
                }
            }
        }
    }

    @Test fun panelSurvivesActivityRecreation() {
        ActivityScenario.launch(BackupSettingsActivity::class.java).use { scenario ->
            await(scenario, "Utwórz kopię lokalną"); scenario.recreate()
            await(scenario, "Eksportuj CSV")
            assertEquals(initial, prefs.getString("data", null))
        }
    }

    @Test fun cancellingTheActualCsvPickerDoesNotReportSuccess() {
        ActivityScenario.launch(BackupSettingsActivity::class.java).use { scenario ->
            await(scenario, "Eksportuj CSV")
            withPicker(Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)) {
                scenario.onActivity { click(it, "Eksportuj CSV") }
                instrumentation.waitForIdleSync()
            }
            await(scenario, "Eksportuj CSV")
            scenario.onActivity { assertFalse(has(it, "Zapisano raport CSV.")) }
            assertEquals(initial, prefs.getString("data", null))
        }
    }

    @Test fun actualSafResultWritesCsvWithoutChangingTheJournal() {
        val file = File.createTempFile("backup-host-", ".csv", context.cacheDir)
        try {
            ActivityScenario.launch(BackupSettingsActivity::class.java).use { scenario ->
                await(scenario, "Eksportuj CSV")
                withPicker(Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(Uri.fromFile(file)))) {
                    scenario.onActivity { click(it, "Eksportuj CSV") }
                    await(scenario, "Zapisano raport CSV.")
                }
                assertTrue(file.readText().contains("Godziny przedziału (brutto)"))
                assertEquals(initial, prefs.getString("data", null))
            }
        } finally { file.delete() }
    }

    @Test fun actualSafResultWritesAReadablePdf() {
        val file = File.createTempFile("backup-host-", ".pdf", context.cacheDir)
        try {
            ActivityScenario.launch(BackupSettingsActivity::class.java).use { scenario ->
                await(scenario, "Eksportuj PDF")
                withPicker(Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(Uri.fromFile(file)))) {
                    scenario.onActivity { click(it, "Eksportuj PDF") }
                    await(scenario, "Zapisano raport PDF.")
                }
                android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                    android.graphics.pdf.PdfRenderer(fd).use { assertTrue(it.pageCount > 0) }
                }
                assertEquals(initial, prefs.getString("data", null))
            }
        } finally { file.delete() }
    }

    @Test fun failedSafWriteShowsAnErrorInsteadOfSuccess() {
        val absent = File(context.cacheDir, "absent-${java.util.UUID.randomUUID()}/out.csv")
        ActivityScenario.launch(BackupSettingsActivity::class.java).use { scenario ->
            await(scenario, "Eksportuj CSV")
            withPicker(Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(Uri.fromFile(absent)))) {
                scenario.onActivity { click(it, "Eksportuj CSV") }
                await(scenario, "Nie udało się wykonać operacji. Sprawdź dostęp i spróbuj ponownie.")
            }
            assertEquals(initial, prefs.getString("data", null))
        }
    }

    @Test fun cancellingAnImportPreviewKeepsCurrentData() {
        val file = File.createTempFile("backup-host-", ".zip", context.cacheDir)
        file.outputStream().use { JournalSnapshot.write(incoming, it) }
        val monitor = instrumentation.addMonitor(documentFilter(Intent.ACTION_OPEN_DOCUMENT),
            Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(Uri.fromFile(file))), true)
        try {
            ActivityScenario.launch(BackupSettingsActivity::class.java).use { scenario ->
                await(scenario, "Importuj z pliku")
                scenario.onActivity { click(it, "Importuj z pliku") }
                await(scenario, "Znaleziono kopię zapasową")
                assertEquals(initial, prefs.getString("data", null))
                scenario.onActivity { click(it, "Anuluj") }
                await(scenario, "Anulowano przywracanie.")
                assertEquals(initial, prefs.getString("data", null))
            }
        } finally { instrumentation.removeMonitor(monitor); file.delete() }
    }

    @Test fun openWorkdayBlocksReplacementInTheRealHost() {
        val active = org.json.JSONObject(initial).put("active", org.json.JSONObject().put("start", 1793689200000L)).toString()
        prefs.edit().putString("data", active).commit()
        ActivityScenario.launch(BackupSettingsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertThrows(BackupRestoreBlocked::class.java) { activity.beforeRestore() }
            }
            assertEquals(active, prefs.getString("data", null))
        }
    }

    @Test fun backClosesThePanelWithoutChangingData() {
        val scenario = ActivityScenario.launch(BackupSettingsActivity::class.java)
        try {
            await(scenario, "Utwórz kopię lokalną")
            scenario.onActivity { click(it, "← Wstecz") }
            val deadline = SystemClock.uptimeMillis() + 10000
            while (scenario.state != androidx.lifecycle.Lifecycle.State.DESTROYED && SystemClock.uptimeMillis() < deadline)
                SystemClock.sleep(30)
            assertEquals(androidx.lifecycle.Lifecycle.State.DESTROYED, scenario.state)
            assertEquals(initial, prefs.getString("data", null))
        } finally { scenario.close() }
    }
}
