package com.r3gate.app

import android.app.Activity
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

@RunWith(AndroidJUnit4::class)
class AppFlowTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    @Before fun seed() {
        val context=instrumentation.targetContext
        val pin=MessageDigest.getInstance("SHA-256").digest("test1234".toByteArray()).joinToString("") { "%02x".format(it) }
        val obj=JSONObject().put("id","object-test").put("TAG","KOP-01").put("city","Kopytkowo").put("Adres","24").put("organization","Test")
        val data=JSONObject().put("schemaVersion",2).put("objects",JSONArray().put(obj))
        context.getSharedPreferences("r3_gate",0).edit().clear().putString("salt","test").putString("pin",pin).putString("data",data.toString()).commit()
    }
    private fun views(v:View):List<View> = listOf(v)+if(v is ViewGroup) (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()
    private fun all(a:Activity) = views(a.window.decorView)
    private fun click(a:Activity,text:String) {
        val button=all(a).filterIsInstance<Button>().firstOrNull { it.text.toString()==text }
        assertNotNull("Missing button: "+text,button)
        button!!.performClick()
    }
    private fun has(a:Activity,text:String) = all(a).filterIsInstance<TextView>().any { it.text.toString()==text }
    private fun shot(name:String) {
        instrumentation.waitForIdleSync()
        val file=File(instrumentation.targetContext.getExternalFilesDir(null),name+".png")
        val bitmap=instrumentation.uiAutomation.takeScreenshot()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
        instrumentation.uiAutomation.executeShellCommand("cp "+file.absolutePath+" /sdcard/Download/r3-"+name+".png").use {
            java.io.FileInputStream(it.fileDescriptor).use { input -> input.readBytes() }
        }
    }
    @Test fun backAndInsetsAndNotes() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            instrumentation.waitForIdleSync()
            scenario.onActivity { a ->
                assertTrue(has(a,"R3 Gate"))
                val header=all(a).filterIsInstance<TextView>().first { it.text.toString()=="R3 Gate" }
                val y=IntArray(2); header.getLocationOnScreen(y)
                val inset=a.window.decorView.rootWindowInsets.getInsets(WindowInsets.Type.statusBars()).top
                assertTrue("Header must be below status bar",y[1]>=inset)
            }
            shot("01-home")
            scenario.onActivity { a -> click(a,"OBIEKTY"); assertTrue(has(a,"Obiekty")) }
            shot("02-objects")
            scenario.onActivity { a -> a.onBackPressed(); assertTrue(has(a,"R3 Gate")); assertFalse(a.isFinishing) }
            scenario.onActivity { a ->
                click(a,"NOTATKI"); click(a,"+ Dodaj notatkę")
                val inputs=all(a).filterIsInstance<EditText>()
                assertEquals(1,inputs.size)
                inputs[0].setText("Zabrać kabel")
                all(a).filterIsInstance<CheckBox>().first { it.text=="Aktualna" }.isChecked=true
                click(a,"Zapisz"); click(a,"Aktualne (1)")
            }
            shot("03-active-notes")
            scenario.onActivity { a ->
                click(a,"● Zabrać kabel\nBez obiektu")
                click(a,"Wykonano")
                assertFalse(all(a).filterIsInstance<Button>().any { it.text.toString().contains("Zabrać kabel") })
                a.onBackPressed(); click(a,"Historia / Logi — wszystkie wpisy")
                assertTrue(all(a).filterIsInstance<Button>().any { it.text.toString().contains("✓ Zabrać kabel") })
                a.onBackPressed(); a.onBackPressed(); assertTrue(has(a,"R3 Gate"))
            }
        }
    }
    @Test fun objectFormsAndDecimalMaterials() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { a ->
                click(a,"OBIEKTY"); click(a,"+ Dodaj obiekt")
                val inputs=all(a).filterIsInstance<EditText>()
                assertEquals(5,inputs.size)
                inputs[0].setText("Kopytkowo"); inputs[1].setText("25"); inputs[2].setText("Nowy obiekt")
                click(a,"Zapisz")
                assertTrue("Expected second city tag",all(a).filterIsInstance<Button>().any { it.text.toString().startsWith("KOP-02") })
                click(a,"KOP-01 · Test · Kopytkowo · 24"); click(a,"Materiały — ilości i daty"); click(a,"+ Dodaj")
                val fields=all(a).filterIsInstance<EditText>()
                fields[0].setText("Kabel")
                fields[1].setText("12.5")
                click(a,"Data: Wybierz datę")
            }
            // The unit picker is a platform dialog outside the activity content root.
            instrumentation.waitForIdleSync()
            androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withText("OK"))
                .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog())
                .perform(androidx.test.espresso.action.ViewActions.click())
            instrumentation.waitForIdleSync()
            scenario.onActivity { a -> click(a,"Jednostka: szt.") }
            androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withText("m"))
                .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog())
                .perform(androidx.test.espresso.action.ViewActions.click())
            instrumentation.waitForIdleSync()
            scenario.onActivity { a ->
                click(a,"Zapisz")
                assertTrue("Material total missing: "+all(a).filterIsInstance<TextView>().joinToString(" | ") { it.text },has(a,"Kabel · m: 12.5"))
                a.onBackPressed(); assertTrue(has(a,"KOP-01"))
            }
            shot("04-object-details")
        }
    }

    @Test fun migrationKeepsLegacyLinksAndNotes() {
        val context=instrumentation.targetContext
        val prefs=context.getSharedPreferences("r3_gate",0)
        val obj=JSONObject().put("id","legacy-object").put("TAG","KOP-01").put("Adres","Kopytkowo 24").put("Opis","Firma")
        val link=JSONObject().put("id","legacy-photo").put("Data (RRRR-MM-DD)","2026-10-28")
            .put("Obiekt","KOP-01 — Kopytkowo 24").put("Link Google Drive","https://drive.google.com/drive/folders/example")
        val note=JSONObject().put("id","legacy-note").put("Tytuł","Uwaga").put("Treść","Sprawdzić gniazdo")
        val data=JSONObject().put("objects",JSONArray().put(obj)).put("links",JSONArray().put(link)).put("notes",JSONArray().put(note))
        prefs.edit().putString("data",data.toString()).commit()
        ActivityScenario.launch(MainActivity::class.java).use {
            instrumentation.waitForIdleSync()
            val migrated=JSONObject(prefs.getString("data","{}")!!)
            assertEquals(2,migrated.getInt("schemaVersion"))
            assertEquals("2026-10-28",migrated.getJSONArray("links").getJSONObject(0).getString("date"))
            assertEquals("legacy-object",migrated.getJSONArray("links").getJSONObject(0).getString("objectId"))
            assertTrue(migrated.getJSONArray("notes").getJSONObject(0).getString("text").contains("Sprawdzić gniazdo"))
            assertEquals("KOP-01",migrated.getJSONArray("objects").getJSONObject(0).getString("TAG"))
        }
    }


    private fun stored():JSONObject = JSONObject(instrumentation.targetContext.getSharedPreferences("r3_gate",0).getString("data","{}")!!)
    private fun store(d:JSONObject) { instrumentation.targetContext.getSharedPreferences("r3_gate",0).edit().putString("data",d.toString()).commit() }
    private fun pin(value:String="1234") {
        androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withHint("PIN"))
            .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog())
            .perform(androidx.test.espresso.action.ViewActions.typeText(value),androidx.test.espresso.action.ViewActions.closeSoftKeyboard())
        androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withText("Potwierdź"))
            .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog())
            .perform(androidx.test.espresso.action.ViewActions.click())
        instrumentation.waitForIdleSync()
    }
    private fun dialogClick(text:String) {
        androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withText(text))
            .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog())
            .perform(androidx.test.espresso.action.ViewActions.click())
        instrumentation.waitForIdleSync()
    }
    private fun seedActive() {
        val d=stored(); val now=System.currentTimeMillis()-3600000
        d.put("active",JSONObject().put("id","active-test").put("start",now).put("stageStart",now)
            .put("objectId","object-test").put("object","KOP-01").put("kind","Obiekt").put("stages",JSONArray()))
        store(d)
    }
    @Test fun travelCancellationCreationAndArrival() {
        seedActive()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { a -> click(a,"🚗 PRZEJAZD") }
            assertEquals("Przejazd",stored().getJSONObject("active").getString("kind"))
            assertEquals(1,stored().getJSONObject("active").getJSONArray("stages").length())
            val departure=stored().getJSONObject("active").getLong("stageStart")
            scenario.onActivity { a -> click(a,"🚗 PRZEJAZD · W DRODZE — ZAKOŃCZ") }
            pin(); dialogClick("Anuluj — przejazd trwa")
            assertEquals(departure,stored().getJSONObject("active").getLong("stageStart"))
            scenario.recreate()
            assertEquals(departure,stored().getJSONObject("active").getLong("stageStart"))
            shot("05-travel-active")
            scenario.onActivity { a -> click(a,"🚗 PRZEJAZD · W DRODZE — ZAKOŃCZ") }
            pin(); dialogClick("+ Nowy obiekt")
            scenario.onActivity { a ->
                val inputs=all(a).filterIsInstance<EditText>()
                inputs[0].setText("Kopytkowo"); inputs[1].setText("25"); inputs[2].setText("Nowy")
                click(a,"Zapisz"); click(a,"🚗 PRZEJAZD · W DRODZE — ZAKOŃCZ")
            }
            pin(); dialogClick("KOP-02 · Nowy · Kopytkowo · 25")
            val current=stored().getJSONObject("active")
            assertEquals("Obiekt",current.getString("kind"))
            assertNotEquals("object-test",current.getString("objectId"))
            assertEquals("Przejazd",current.getJSONArray("stages").getJSONObject(1).getString("kind"))
            scenario.onActivity { a -> click(a,"🚗 PRZEJAZD"); click(a,"🚗 PRZEJAZD · W DRODZE — ZAKOŃCZ") }
            pin(); dialogClick("🛒 Zakupy")
            assertEquals("Zakupy",stored().getJSONObject("active").getString("kind"))
            assertEquals("",stored().getJSONObject("active").getString("objectId"))
            assertEquals(2,stored().getJSONArray("objects").length())
        }
    }
    @Test fun widgetSwitchRequiresPinAndSplitsTime() {
        seedActive()
        val d=stored(); d.getJSONArray("objects").put(JSONObject().put("id","second").put("TAG","GDA-01").put("city","Gdansk").put("Adres","2")); store(d)
        val context=instrumentation.targetContext
        val intent=android.content.Intent(context,MainActivity::class.java).putExtra("widget_action","switch")
        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            pin("0000")
            assertEquals("object-test",stored().getJSONObject("active").getString("objectId"))
            scenario.onActivity { a -> click(a,"🔄 ZMIEŃ OBIEKT") }
            pin(); dialogClick("GDA-01 · Gdansk · 2")
            val active=stored().getJSONObject("active")
            assertEquals("second",active.getString("objectId"))
            assertEquals("object-test",active.getJSONArray("stages").getJSONObject(0).getString("objectId"))
            scenario.onActivity { a ->
                val widgetView=GateWidget.render(a).apply(a,android.widget.FrameLayout(a))
                assertEquals("ZMIEŃ OBIEKT",widgetView.findViewById<TextView>(R.id.wswitch).text.toString())
                assertFalse(views(widgetView).filterIsInstance<TextView>().any { it.text=="ZAKUPY" })
                val frame=android.widget.FrameLayout(a).apply { setBackgroundColor(android.graphics.Color.BLACK); setPadding(24,100,24,24) }
                frame.addView(GateWidget.render(a).apply(a,frame),android.widget.FrameLayout.LayoutParams(-1,900))
                a.setContentView(frame)
            }
            shot("06-widget")
        }
    }
    @Test fun deleteShiftAndShoppingReceiptAndDates() {
        val d=stored(); val start=java.time.LocalDate.of(2026,10,28).atTime(8,0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        d.put("shifts",JSONArray().put(JSONObject().put("id","shift-delete").put("start",start).put("end",start+3600000)))
        store(d)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { a -> click(a,"📊 MÓJ RAPORT")
                assertTrue(all(a).filterIsInstance<TextView>().any { it.text.toString().startsWith("28-10-2026 08:00") })
                click(a,"Usuń zmianę (PIN)")
            }
            pin(); dialogClick("Anuluj")
            assertEquals(1,stored().getJSONArray("shifts").length())
            scenario.onActivity { a -> click(a,"Usuń zmianę (PIN)") }
            pin(); dialogClick("Usuń")
            assertEquals(0,stored().getJSONArray("shifts").length())
            scenario.onActivity { a ->
                a.onBackPressed(); click(a,"NOTATKI"); click(a,"+ Dodaj notatkę")
                all(a).filterIsInstance<EditText>().first().setText("Paragon — kabel")
                all(a).filterIsInstance<CheckBox>().first { it.text=="Zakupy / paragon" }.isChecked=true
                click(a,"Zapisz"); click(a,"Historia / Logi — wszystkie wpisy")
                click(a,"Zakupy / paragony: wszystkie notatki")
                assertTrue(all(a).filterIsInstance<Button>().any { it.text.toString().contains("Paragon — kabel") })
            }
            assertTrue(stored().getJSONArray("notes").getJSONObject(0).getBoolean("shopping"))
            shot("07-shopping-notes")
        }
    }

}
