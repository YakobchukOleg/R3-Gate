package com.r3gate.app

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.RootMatchers.isDialog
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
    @Before fun seed() { prefs.edit().clear().putString("data",JSONObject().put("schemaVersion",2).put("objects",JSONArray()).toString()).commit() }
    @Test fun historyRoundsSummaryDayAndShiftButKeepsOriginalStart() {
        val day=LocalDate.of(2026,11,2); val zone=ZoneId.systemDefault()
        val start=day.atTime(8,25,45).atZone(zone).toInstant().toEpochMilli()
        val end=day.atTime(17,30).atZone(zone).toInstant().toEpochMilli()
        val shift=JSONObject().put("id","rounding").put("start",start).put("end",end)
        prefs.edit().putString("data",JSONObject().put("schemaVersion",2).put("shifts",JSONArray().put(shift)).toString()).commit()
        ActivityScenario.launch(MainActivity::class.java).use { scenario -> scenario.onActivity { a ->
            all(a.window.decorView).filterIsInstance<Button>().first { it.contentDescription=="Mój czas" }.performClick()
            val rendered=texts(a)
            assertTrue(rendered.contains("Łączny czas: 09:05"));assertTrue(rendered.contains("09:05"))
            val row=all(a.window.decorView).filterIsInstance<Button>().single { it.text.toString().startsWith("08:25 → 17:30") }
            assertTrue(row.text.toString().endsWith("09:05"));row.performClick()
            assertTrue(texts(a).contains("Łączny czas: 09:05"))
        } }
        val saved=JSONObject(prefs.getString("data","{}")!!).getJSONArray("shifts").getJSONObject(0)
        assertEquals(start,saved.getLong("start"));assertEquals(end,saved.getLong("end"))
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
            assertEquals(WorkTimeRounding.end(raw),end)
            assertEquals(end,saved.getJSONArray("stages").getJSONObject(0).getLong("end"))
            assertFalse(prefs.getBoolean("gps_enabled",false))
        }
    }
}
