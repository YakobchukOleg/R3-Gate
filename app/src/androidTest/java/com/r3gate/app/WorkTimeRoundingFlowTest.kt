package com.r3gate.app

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.TimePicker
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class WorkTimeRoundingFlowTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs get()=context.getSharedPreferences("r3_gate",Context.MODE_PRIVATE)
    private fun all(v:View):List<View> = listOf(v)+if(v is ViewGroup) (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()
    private fun texts(a:MainActivity)=all(a.window.decorView).filterIsInstance<TextView>().map { it.text.toString() }
    @Before fun seed() { prefs.edit().clear().putString("pin","protected-finance").putString("data",JSONObject().put("schemaVersion",2).put("objects",JSONArray()).toString()).commit() }
    @Test fun historyRepairsTheNovemberSixthOverlapAndRoundsTheStart() {
        val day = LocalDate.of(2026, 11, 6)
        val zone = ZoneId.systemDefault()
        fun at(h: Int, m: Int) = day.atTime(h, m).atZone(zone).toInstant().toEpochMilli()
        val first = JSONObject().put("id", "first").put("start", at(8, 29)).put("end", at(17, 35))
            .put("recordedEnd", at(17, 32))
        val second = JSONObject().put("id", "second").put("start", at(17, 30)).put("end", at(18, 5))
        prefs.edit().putString("data", JSONObject().put("schemaVersion", 2)
            .put("shifts", JSONArray().put(first).put(second)).toString()).commit()

        ActivityScenario.launch(MainActivity::class.java).use { scenario -> scenario.onActivity { a ->
            all(a.window.decorView).filterIsInstance<Button>().first { it.contentDescription == "Mój czas" }.performClick()
            assertTrue(texts(a).contains("Łączny czas: 09:35"))
            val row = all(a.window.decorView).filterIsInstance<Button>()
                .single { it.text.toString().contains(" → ") }
            assertEquals("08:30 → 18:05 · 09:35", row.text.toString())
            row.performClick()
            assertTrue(texts(a).contains("Łączny czas: 09:35"))
        } }

        val shifts = JSONObject(prefs.getString("data", "{}")!!).getJSONArray("shifts")
        assertEquals(1, shifts.length())
        val shift = shifts.getJSONObject(0)
        assertEquals(at(8, 29), shift.getLong("recordedStart"))
        assertEquals(at(8, 30), shift.getLong("start"))
        assertEquals(at(18, 5), shift.getLong("end"))
    }
    @Test fun editOnlyJoinsShiftsAfterConfirmation() {
        val day = LocalDate.of(2026, 11, 6)
        val zone = ZoneId.systemDefault()
        fun at(h: Int, m: Int) = day.atTime(h, m).atZone(zone).toInstant().toEpochMilli()
        val first = JSONObject().put("id", "first").put("start", at(8, 30)).put("end", at(17, 35))
        val second = JSONObject().put("id", "second").put("start", at(17, 50)).put("end", at(18, 5))
        prefs.edit().putString("data", JSONObject().put("schemaVersion", 2)
            .put("shifts", JSONArray().put(first).put(second)).toString()).commit()
        fun answer(label: String) = onView(withText(label)).inRoot(isDialog()).perform(click())
        fun savedShifts() = JSONObject(prefs.getString("data", "{}")!!).getJSONArray("shifts")

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { a ->
                all(a.window.decorView).filterIsInstance<Button>()
                    .first { it.contentDescription == "Mój czas" }.performClick()
                all(a.window.decorView).filterIsInstance<Button>()
                    .first { it.contentDescription == "Opcje zmiany" }.performClick()
            }
            answer("Edytuj czas")
            answer("Tak")
            answer("Rozpoczęcie: 17:50")
            onView(isAssignableFrom(TimePicker::class.java)).inRoot(isDialog()).perform(object : ViewAction {
                override fun getConstraints() = isAssignableFrom(TimePicker::class.java)
                override fun getDescription() = "set the start to 17:34"
                override fun perform(uiController: UiController, view: View) {
                    val picker = view as TimePicker
                    picker.hour = 17
                    picker.minute = 34
                }
            })
            onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
            answer("Zapisz")
            answer("Nie")
            assertEquals(2, savedShifts().length())
            assertEquals(at(17, 50), savedShifts().getJSONObject(1).getLong("start"))
            answer("Zapisz")
            answer("Tak")
        }

        val shifts = savedShifts()
        assertEquals(1, shifts.length())
        val merged = shifts.getJSONObject(0)
        assertEquals(at(8, 30), merged.getLong("start"))
        assertEquals(at(18, 5), merged.getLong("end"))
    }

    @Test fun confirmedCloseRecordsRoundedEndAndClosesFinalStageAtTheSameBoundary() {
        val before=System.currentTimeMillis()
        val active=JSONObject().put("id","close-rounded").put("start",before-3_600_000L).put("stageStart",before-3_600_000L)
            .put("kind","Nieprzypisane").put("stages",JSONArray())
        prefs.edit().putString("data",JSONObject().put("schemaVersion",2).put("active",active).toString()).commit()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { a -> all(a.window.decorView).filterIsInstance<Button>().first { it.text.toString()=="Zakończ dzień" }.performClick() }
            onView(withText("Tak")).inRoot(isDialog()).perform(click())
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            val data=JSONObject(prefs.getString("data","{}")!!)
            assertFalse(data.has("active"));val saved=data.getJSONArray("shifts").getJSONObject(0)
            val raw=saved.getLong("recordedEnd");val end=saved.getLong("end")
            assertTrue(raw>=before);assertTrue(raw<=System.currentTimeMillis())
            assertEquals(WorkTimeRounding.ceil(raw),end)
            assertEquals(end,saved.getJSONArray("stages").getJSONObject(0).getLong("end"))
            assertFalse(prefs.getBoolean("gps_enabled",false))
        }
    }
}
