package com.r3gate.app

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Chronometer
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.hamcrest.Matchers.containsString
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UXPackageV2FlowTest {
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    private val prefs get()=context.getSharedPreferences("r3_gate",Context.MODE_PRIVATE)
    private fun data()=JSONObject(prefs.getString("data","{}")!!)
    private fun views(v:View):List<View> = listOf(v)+if(v is ViewGroup) (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()
    private fun press(a:MainActivity,text:String) = views(a.window.decorView).filterIsInstance<Button>().single { it.text.toString()==text }.performClick()

    @Before fun seed() {
        val obj=JSONObject().put("id","site").put("TAG","GDA-02").put("city","Gdańsk")
            .put("organization","Gate").put("Adres","Łąkowa 23B").put("postcode","80-001")
        val now=System.currentTimeMillis()
        val journal=JSONObject().put("schemaVersion",2).put("objects",JSONArray().put(obj).put(JSONObject(obj.toString()).put("id","archived").put("archived",true)))
            .put("walletSettings",JSONObject().put("defaultRate","30.00").put("openingCents",0))
            .put("active",JSONObject().put("id","day").put("start",now-600_000).put("stageStart",now-120_000)
                .put("kind","Nieprzypisane").put("rate","30.00").put("objectId","").put("stages",JSONArray()))
        prefs.edit().clear().putString("pin","protected-finance").putString("data",journal.toString()).commit()
    }

    @Test fun pickerSearchesAcrossFieldsAndShowsBothIdentityAndAddress() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { press(it,"Wybierz obiekt") }
            onView(withHint("Szukaj")).inRoot(isDialog()).perform(replaceText("GDANSK lakowa 23b 80-001"))
            onView(withContentDescription("GDA-02 · Gate · Gdańsk · Łąkowa 23B")).inRoot(isDialog())
                .check(matches(withText(containsString("\nGdańsk · Łąkowa 23B")))).perform(click())
            assertEquals("site",data().getJSONObject("active").getString("objectId"))
            assertEquals("protected-finance",prefs.getString("pin",null))
        }
    }

    @Test fun settingsExposeSixCategoriesWithoutOpeningFinanceOrChangingRecords() {
        val before=data().toString()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { a ->
                views(a.window.decorView).single { it.isClickable && it.contentDescription=="Ustawienia" }.performClick()
                val buttons=views(a.window.decorView).filterIsInstance<Button>()
                for(category in listOf("Wygląd i widżet","GPS i lokalizacja","Dane i kopie","Ustawienia działów","Bezpieczeństwo","O aplikacji")) {
                    assertEquals(category,1,buttons.count { it.isShown && it.text.toString().startsWith(category) })
                }
                assertFalse(buttons.single { it.text.toString()=="Portfel / rozliczenia (PIN)" }.isShown)
                buttons.single { it.text.toString().startsWith("Bezpieczeństwo") }.performClick()
                press(a,"Portfel / rozliczenia (PIN)")
            }
            onView(withText("Potwierdź PIN")).inRoot(isDialog()).check(matches(isDisplayed()))
            onView(withText("Anuluj")).inRoot(isDialog()).perform(click())
        }
        assertEquals(before,data().toString())
    }

    @Test fun expandedWidgetSeparatesStageTimerFromDayAndDoesNotEditData() = instrumentation.runOnMainSync {
        val before=data().toString()
        for(theme in WorkMeterTheme.entries) {
            val parent=FrameLayout(context)
            val widget=GateWidget.render(context,WidgetLayoutProfile.LARGE,WidgetAppearance(theme=theme),interactive=false,widthDp=300,heightDp=260).apply(context,parent)
            assertEquals(View.VISIBLE,widget.findViewById<View>(R.id.wstagecolumn).visibility)
            assertEquals("Dziś",widget.findViewById<TextView>(R.id.wday).text.toString())
            assertTrue(widget.findViewById<Chronometer>(R.id.wstage).base<widget.findViewById<Chronometer>(R.id.wduration).base)
            assertEquals(View.GONE,widget.findViewById<TextView>(R.id.wdetails).visibility)
        }
        assertEquals(before,data().toString())
    }

    @Test fun directNoteCompletionChangesOnlyTheChosenNote() {
        val note=JSONObject().put("id","note").put("text","Sprawdź przewód").put("date","2026-11-05")
            .put("objectId","site").put("aktualne",true).put("done",false)
        val untouched=JSONObject(note.toString()).put("id","other").put("aktualne",false)
        val journal=data().put("notes",JSONArray().put(note).put(untouched))
        val activeBefore=journal.getJSONObject("active").toString()
        prefs.edit().putString("data",journal.toString()).commit()
        ActivityScenario.launch(MainActivity::class.java).use { scenario -> scenario.onActivity { a ->
            press(a,"📝  NOTATKI"); press(a,"Aktualne (1)")
            val card=views(a.window.decorView).filterIsInstance<WorkRecordListCard>().single()
            val completion=views(card).filterIsInstance<Button>().single { it.text.toString()=="Wykonano" }
            assertTrue(completion.contentDescription.toString().contains("Sprawdź przewód"))
            completion.performClick()
        } }
        val after=data()
        assertEquals(activeBefore,after.getJSONObject("active").toString())
        assertEquals(2,after.getJSONArray("notes").length())
        assertEquals(untouched.toString(),after.getJSONArray("notes").getJSONObject(1).toString())
        val completed=after.getJSONArray("notes").getJSONObject(0)
        assertTrue(completed.getBoolean("done")); assertFalse(completed.getBoolean("aktualne"))
        assertEquals("site",completed.getString("objectId")); assertEquals("Sprawdź przewód",completed.getString("text"))
        assertEquals("protected-finance",prefs.getString("pin",null))
    }
}
