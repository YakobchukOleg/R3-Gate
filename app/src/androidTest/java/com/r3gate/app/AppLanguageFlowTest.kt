package com.r3gate.app

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLanguageFlowTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val prefs = instrumentation.targetContext.getSharedPreferences("r3_gate", Context.MODE_PRIVATE)

    private fun views(view: View): List<View> =
        listOf(view) + if (view is ViewGroup) (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()

    private fun chooseLanguage(activity: MainActivity, settings: String, about: String, language: String, choice: String) {
        fun button(match: (Button) -> Boolean) = views(activity.window.decorView)
            .filterIsInstance<Button>().first(match)
        button { it.contentDescription == settings }.performClick()
        button { it.text.toString().startsWith(about) }.performClick()
        button { it.text.toString() == language }.performClick()
        button { it.text.toString() == choice }.performClick()
    }

    @Test fun switchesToRussianPersistsAndReturnsToPolish() {
        val original = JSONObject().put("schemaVersion", 2).put("marker", "Dane użytkownika")
        prefs.edit().clear().putString("pin", "protected-finance").putString("data", original.toString()).commit()
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { chooseLanguage(it, "Ustawienia", "O aplikacji", "Język aplikacji", "Русский") }
                instrumentation.waitForIdleSync()
                scenario.onActivity { activity ->
                    assertEquals("ru", activity.resources.configuration.locales[0].language)
                    val text = views(activity.window.decorView).filterIsInstance<TextView>().map { it.text.toString() }
                    assertTrue(text.contains("Язык приложения"))
                    assertTrue(text.contains("Русский ✓"))
                }
            }

            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val buttons = views(activity.window.decorView).filterIsInstance<Button>()
                    assertTrue(buttons.any { it.text.toString() == "Начать день" })
                    assertTrue(buttons.any { it.contentDescription == "Моё время" })
                    chooseLanguage(activity, "Настройки", "О приложении", "Язык приложения", "Polski")
                }
                instrumentation.waitForIdleSync()
                scenario.onActivity { activity ->
                    assertEquals("pl", activity.resources.configuration.locales[0].language)
                    assertTrue(views(activity.window.decorView).filterIsInstance<Button>().any { it.text.toString() == "Polski ✓" })
                }
            }
            assertEquals("Dane użytkownika", JSONObject(prefs.getString("data", "{}")!!).getString("marker"))
        } finally {
            prefs.edit().remove(AppLanguage.PREFERENCE).commit()
        }
    }
}
