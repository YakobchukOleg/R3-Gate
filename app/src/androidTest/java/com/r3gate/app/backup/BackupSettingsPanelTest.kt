package com.r3gate.app.backup

import android.content.Context
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class BackupSettingsPanelTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val instant = Instant.parse("2026-11-05T08:00:00Z")

    private class Appearance(private val foreground: Int, private val background: Int) : BackupSettingsPanel.Appearance {
        var primaryButtons = 0
        override fun surface(view: View) { view.setBackgroundColor(background) }
        override fun text(view: TextView) { view.setTextColor(foreground) }
        override fun button(view: Button, primary: Boolean) {
            if (primary) primaryButtons++
        }
        override fun input(view: View) = Unit
    }

    private fun state(connected: Boolean = true, busy: Boolean = false): BackupSettingsPanel.State {
        val copy = LocalBackups.Copy("copy.zip", instant, 42)
        val local = LocalBackups.Status(LocalBackups.Schedule(true, 3, 7), instant, copy, null)
        return BackupSettingsPanel.State(
            BackupStatus.Snapshot(local, connected, instant, null, emptyList()),
            listOf(copy), busy
        )
    }

    private fun views(view: View): List<View> = listOf(view) + if (view is ViewGroup) {
        (0 until view.childCount).flatMap { views(view.getChildAt(it)) }
    } else emptyList()

    private fun button(panel: View, label: String) =
        views(panel).filterIsInstance<Button>().single { it.text.toString() == label }

    private fun appearance() = Appearance(Color.BLACK, Color.WHITE)

    @Test fun consumesHostThemeAcrossAllThreePalettes() = instrumentation.runOnMainSync {
        for ((text, background) in listOf(0xFF293F49.toInt() to 0xFFF5F7F7.toInt(),
            0xFF1F2937.toInt() to 0xFFF7F8FA.toInt(), Color.WHITE to 0xFF151D24.toInt())) {
            val style = Appearance(text, background)
            val panel = BackupSettingsPanel(context, style) {}
            panel.render(state())
            assertEquals(1, style.primaryButtons)
            val title = views(panel).filterIsInstance<TextView>()
                .single { it.text.toString() == "Kopia zapasowa i synchronizacja" }
            assertEquals(text, title.currentTextColor)
            assertTrue(views(panel).filterIsInstance<Button>().all { it.currentTextColor == text })
        }
    }

    @Test fun buttonsFitNarrowWidthAndLargeTextWithoutClipping() = instrumentation.runOnMainSync {
        val configuration = android.content.res.Configuration(context.resources.configuration).apply { fontScale = 1.6f }
        val enlarged: Context = context.createConfigurationContext(configuration)
        val panel = BackupSettingsPanel(enlarged, appearance()) {}
        panel.render(state())
        val density = enlarged.resources.displayMetrics.density
        val width = (280 * density).toInt()
        val height = (640 * density).toInt()
        panel.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        panel.layout(0, 0, width, height)
        for (action in views(panel).filterIsInstance<Button>()) {
            assertTrue(action.measuredHeight >= (48 * density).toInt())
            val layout = requireNotNull(action.layout)
            assertEquals(action.text.length, layout.getLineEnd(layout.lineCount - 1))
            assertTrue(action.right <= (action.parent as View).width)
        }
    }

    @Test fun restoreSelectionOnlyEmitsPreviewAction() = instrumentation.runOnMainSync {
        val actions = mutableListOf<BackupSettingsPanel.Action>()
        val panel = BackupSettingsPanel(context, appearance(), actions::add)
        panel.render(state())
        val restore = views(panel).filterIsInstance<Button>()
            .single { it.text.startsWith("Sprawdź kopię lokalną z") }
        restore.performClick()
        assertEquals(listOf(BackupSettingsPanel.Action.RestoreLocal("copy.zip")), actions)
    }

    @Test fun disconnectedAccountShowsExistingAuthorizationEntry() = instrumentation.runOnMainSync {
        val actions = mutableListOf<BackupSettingsPanel.Action>()
        val panel = BackupSettingsPanel(context, appearance(), actions::add)
        panel.render(state(connected = false))
        button(panel, "Połącz Google Drive").performClick()
        assertEquals(listOf(BackupSettingsPanel.Action.ConnectDrive), actions)
        assertFalse(views(panel).filterIsInstance<Button>().any { it.text.toString() == "Importuj z Google Drive" })
    }

    @Test fun invalidRetentionPreventsSavingAndValidSchedulePreservesInterval() = instrumentation.runOnMainSync {
        val actions = mutableListOf<BackupSettingsPanel.Action>()
        val panel = BackupSettingsPanel(context, appearance(), actions::add)
        panel.render(state())
        val retention = views(panel).filterIsInstance<EditText>().single()
        retention.setText("0")
        button(panel, "Zapisz harmonogram").performClick()
        assertTrue(actions.isEmpty())
        assertNotNull(retention.error)
        retention.setText("10")
        button(panel, "Zapisz harmonogram").performClick()
        assertEquals(listOf(BackupSettingsPanel.Action.UpdateSchedule(LocalBackups.Schedule(true, 3, 10))), actions)
    }

    @Test fun busyStateDisablesOperationsAndScheduleInputs() = instrumentation.runOnMainSync {
        val panel = BackupSettingsPanel(context, appearance()) {}
        panel.render(state(busy = true))
        assertTrue(views(panel).filter { it is Button || it is EditText || it is Spinner || it is android.widget.Switch }
            .all { !it.isEnabled })
    }

    @Test fun previewRequiresExplicitDecisionAndPreventsRepeatedConfirmation() = instrumentation.runOnMainSync {
        val panel = BackupSettingsPanel(context, appearance()) {}
        var commits = 0
        var discards = 0
        val summary = RestoreSummary(1, 0, 2, false, 3, 4, 5, 6, 7, 1, true)
        panel.preview(summary, { commits++ }, { discards++ })
        assertEquals(0, commits)
        assertTrue(views(panel).filterIsInstance<TextView>().any { it.text.startsWith("Wykryto możliwe konflikty: 1") })
        val confirm = button(panel, "Przywróć dane")
        confirm.performClick()
        confirm.performClick()
        button(panel, "Anuluj").performClick()
        assertEquals(1, commits)
        assertEquals(0, discards)
    }

    @Test fun cancelledPreviewNeverCommits() = instrumentation.runOnMainSync {
        val panel = BackupSettingsPanel(context, appearance()) {}
        var commits = 0
        var discards = 0
        panel.preview(RestoreSummary(0, 0, 0, false, 0, 0, 0, 0, 0, 0, false),
            { commits++ }, { discards++ })
        button(panel, "Anuluj").performClick()
        assertEquals(0, commits)
        assertEquals(1, discards)
    }
}
