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
        // Let the compositor present the new frame and transient long toasts disappear.
        android.os.SystemClock.sleep(3700)
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
                assertTrue(has(a,"WorkMeter"))
                val header=all(a).filterIsInstance<TextView>().first { it.text.toString()=="WorkMeter" }
                val y=IntArray(2); header.getLocationOnScreen(y)
                val inset=a.window.decorView.rootWindowInsets.getInsets(WindowInsets.Type.statusBars()).top
                assertTrue("Header must be below status bar",y[1]>=inset)
            }
            shot("01-home")
            scenario.onActivity { a -> click(a,"OBIEKTY"); assertTrue(has(a,"Obiekty")) }
            shot("02-objects")
            scenario.onActivity { a -> a.onBackPressed(); assertTrue(has(a,"WorkMeter")); assertFalse(a.isFinishing) }
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
                a.onBackPressed(); a.onBackPressed(); assertTrue(has(a,"WorkMeter"))
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
                frame.addView(GateWidget.render(a).apply(a,frame),android.widget.FrameLayout.LayoutParams(-1,(160*a.resources.displayMetrics.density).toInt()))
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
            scenario.onActivity { a -> click(a,"MÓJ CZAS")
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
                clickFilters(a)
                click(a,"Zakupy / paragony: wszystkie notatki")
                assertTrue(all(a).filterIsInstance<Button>().any { it.text.toString().contains("Paragon — kabel") })
            }
            assertTrue(stored().getJSONArray("notes").getJSONObject(0).getBoolean("shopping"))
            shot("07-shopping-notes")
        }
    }


    private fun clickFilters(a:Activity) {
        all(a).filterIsInstance<Button>().first { it.isShown && it.text.toString().startsWith("Filtry") }.performClick()
    }
    private fun assertCollapsed(a:Activity,add:String="+ Dodaj") {
        val buttons=all(a).filterIsInstance<Button>().filter { it.isShown }
        assertEquals(listOf("← Wstecz","Filtry ▾",add),buttons.take(3).map { it.text.toString() })
        assertFalse(buttons.any { it.text.toString().startsWith("Data od:") || it.text.toString().startsWith("Sortowanie:") || it.text=="Wyczyść filtry" })
    }
    @Test fun filtersAreCollapsedAcrossScreensAndKeepSelection() {
        val d=stored()
        val today=java.time.LocalDate.now().toString()
        d.put("notes",JSONArray().put(JSONObject().put("id","filter-note").put("date",today).put("text","Aktywna uwaga").put("aktualne",true)))
        store(d)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { a -> click(a,"ZDJĘCIA"); assertCollapsed(a) }
            shot("08-filters-closed")
            scenario.onActivity { a ->
                clickFilters(a)
                assertTrue(all(a).filterIsInstance<Button>().any { it.isShown && it.text=="Data od: wszystkie" })
                click(a,"Sortowanie: data, najnowsze")
                assertTrue(all(a).filterIsInstance<Button>().any { it.isShown && it.text=="Sortowanie: nazwa" })
                clickFilters(a)
                assertFalse(all(a).filterIsInstance<Button>().any { it.isShown && it.text.toString().startsWith("Sortowanie:") })
            }
            scenario.recreate()
            scenario.onActivity { a ->
                assertFalse(all(a).filterIsInstance<Button>().any { it.isShown && it.text.toString().startsWith("Sortowanie:") })
                clickFilters(a)
                assertTrue(all(a).filterIsInstance<Button>().any { it.isShown && it.text=="Sortowanie: nazwa" })
                click(a,"Wyczyść filtry")
                assertTrue(all(a).filterIsInstance<Button>().any { it.isShown && it.text=="Sortowanie: data, najnowsze" })
            }
            shot("09-filters-open")
            scenario.onActivity { a ->
                a.onBackPressed(); click(a,"MATERIAŁY"); assertCollapsed(a)
                a.onBackPressed(); click(a,"NOTATKI"); click(a,"Historia / Logi — wszystkie wpisy"); assertCollapsed(a)
                clickFilters(a)
                assertTrue(all(a).filterIsInstance<Button>().any { it.isShown && it.text.toString().startsWith("Zakupy / paragony:") })
                a.onBackPressed(); click(a,"Aktualne (1)"); assertCollapsed(a)
                assertTrue(all(a).filterIsInstance<Button>().any { it.isShown && it.text.toString().contains("Aktywna uwaga") })
                a.onBackPressed(); a.onBackPressed(); click(a,"OBIEKTY")
                assertFalse(all(a).filterIsInstance<Button>().any { it.isShown && it.text.toString().startsWith("Filtry") })
                click(a,"Archiwum obiektów"); click(a,"Pokaż aktywne"); click(a,"KOP-01 · Test · Kopytkowo · 24")
                assertFalse(all(a).filterIsInstance<Button>().any { it.isShown && it.text.toString().startsWith("Filtry") })
                assertTrue(has(a,"Czas na obiekcie")); assertTrue(has(a,"Materiały")); assertTrue(has(a,"Zdjęcia"))
                click(a,"Zdjęcia"); assertCollapsed(a)
                clickFilters(a)
                assertFalse(all(a).filterIsInstance<Button>().any { it.isShown && it.text.toString().startsWith("Obiekt:") })
            }
            assertEquals(1,stored().getJSONArray("notes").length())
        }
    }


    private fun settings(a:Activity) {
        all(a).filterIsInstance<Button>().first { it.contentDescription=="Ustawienia" }.performClick()
    }
    private fun deliverDocumentResult(a:MainActivity,code:Int,file:File) {
        val method=MainActivity::class.java.getDeclaredMethod("onActivityResult",Integer.TYPE,Integer.TYPE,android.content.Intent::class.java)
        method.isAccessible=true
        method.invoke(a,code,Activity.RESULT_OK,android.content.Intent().setData(android.net.Uri.fromFile(file)))
    }
    @Test fun settingsWalletAndBackupRoundTrip() {
        val d=stored(); val start=java.time.LocalDate.now().atTime(8,0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        d.put("shifts",JSONArray().put(JSONObject().put("id","wallet-shift").put("start",start).put("end",start+8*3600000).put("rate","30.00")))
        store(d)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { a ->
                assertFalse(all(a).filterIsInstance<Button>().any { it.text=="GOOGLE DRIVE" || it.text=="ZAROBEK (pokaż)" })
                settings(a); assertTrue(has(a,"Ustawienia"))
                assertTrue(has(a,"GENERUJ RAPORT SMS")); assertTrue(has(a,"Logi / eksport / import")); assertTrue(has(a,"Google Disk"))
                click(a,"O aplikacji"); assertTrue(has(a,"Wersja: 2.1")); a.onBackPressed()
                click(a,"Portfel / rozliczenia (PIN)")
                assertFalse(has(a,"Portfel"))
            }
            pin()
            scenario.onActivity { a ->
                assertTrue(has(a,"Naliczone: 240,00 zł")); click(a,"+ Dodaj wypłatę")
                all(a).filterIsInstance<EditText>().first { it.hint=="Kwota (zł)" }.setText("100")
                click(a,"Zapisz"); click(a,"+ Dodaj wypłatę")
                all(a).filterIsInstance<EditText>().first { it.hint=="Kwota (zł)" }.setText("50,00")
                click(a,"Sposób: Gotówka")
            }
            dialogClick("Karta")
            scenario.onActivity { a ->
                click(a,"Zapisz")
                assertTrue(has(a,"Otrzymano — gotówka: 100,00 zł")); assertTrue(has(a,"Otrzymano — karta: 50,00 zł")); assertTrue(has(a,"Do wypłaty: 90,00 zł"))
            }
            shot("10-wallet")
            val backup=File(instrumentation.targetContext.filesDir,"test-backup.json")
            scenario.onActivity { a -> deliverDocumentResult(a,100,backup) }
            val exported=JSONObject(backup.readText())
            assertEquals(2,exported.getJSONArray("payments").length()); assertTrue(exported.has("walletSettings")); assertTrue(exported.has("wtSettings"))
            store(JSONObject().put("schemaVersion",2).put("objects",JSONArray()))
            scenario.onActivity { a -> deliverDocumentResult(a,101,backup) }
            dialogClick("Importuj")
            assertEquals(2,stored().getJSONArray("payments").length())
            scenario.onActivity { a ->
                assertTrue(has(a,"Eksportuj dziennik JSON")); assertTrue(has(a,"Importuj dziennik JSON"))
                a.onBackPressed(); click(a,"GENERUJ RAPORT SMS")
                assertTrue(all(a).filterIsInstance<Button>().any { it.text.toString().startsWith("Data od:") })
                a.onBackPressed(); assertTrue(has(a,"Ustawienia"))
            }
        }
    }
    @Test fun widgetTransparencyAffectsOnlyBackgroundAndSettingsRemainReachable() {
        seedActive()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { a ->
                val gear=all(a).filterIsInstance<Button>().first { it.contentDescription=="Ustawienia" }
                assertTrue(gear.isShown)
                val stop=all(a).filterIsInstance<Button>().first { it.text.toString().contains("ZAKOŃCZ PRACĘ") }
                assertTrue(stop.minimumHeight<=(32*a.resources.displayMetrics.density).toInt())
                settings(a); click(a,"Wygląd widżetu")
                all(a).filterIsInstance<android.widget.SeekBar>().first().progress=100
                val widget=GateWidget.render(a).apply(a,android.widget.FrameLayout(a))
                assertEquals(0,widget.findViewById<android.widget.ImageView>(R.id.wbackground).imageAlpha)
                assertEquals(1f,widget.findViewById<TextView>(R.id.wduration).alpha,0f)
                assertEquals(255,widget.findViewById<Button>(R.id.wstopbutton).background.alpha)
            }
            scenario.recreate()
            scenario.onActivity { a -> assertEquals(100,all(a).filterIsInstance<android.widget.SeekBar>().first().progress) }
            shot("11-widget-settings")
            scenario.onActivity { a ->
                a.onBackPressed(); assertTrue(has(a,"Ustawienia")); a.onBackPressed(); assertTrue(has(a,"WorkMeter"))
                settings(a); click(a,"Google Disk"); assertFalse(all(a).filterIsInstance<TextView>().any { it.isShown && it.text.toString().startsWith("Pakiet:") })
                a.onBackPressed(); assertTrue(has(a,"Ustawienia"))
            }
        }
    }

    @Test fun objectSearchByCityAndStyledPopup() {
        val d=stored()
        d.getJSONArray("objects").put(JSONObject().put("id","lodz").put("TAG","LOD-02").put("city","Łódź").put("Adres","1"))
        store(d)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { a -> click(a,"🟢 ROZPOCZNIJ PRACĘ") }
            pin()
            androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withHint("Szukaj miasta"))
                .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog())
                .perform(androidx.test.espresso.action.ViewActions.typeText("lodz"),androidx.test.espresso.action.ViewActions.closeSoftKeyboard())
            shot("12-object-search")
            dialogClick("LOD-02 · Łódź · 1")
            assertEquals("lodz",stored().getJSONObject("active").getString("objectId"))
        }
    }
    @Test fun dayTimelineShowsEveryIntervalAndNotificationHasPhotoAction() {
        val d=stored(); val start=java.time.LocalDate.now().atTime(8,0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val stages=JSONArray().put(JSONObject().put("start",start).put("end",start+3600000).put("kind","Obiekt").put("object","KOP-01 · Test").put("objectId","object-test"))
            .put(JSONObject().put("start",start+3600000).put("end",start+5400000).put("kind","Przejazd"))
            .put(JSONObject().put("start",start+5400000).put("end",start+7200000).put("kind","Zakupy"))
        d.put("shifts",JSONArray().put(JSONObject().put("id","timeline").put("start",start).put("end",start+7200000).put("stages",stages)));store(d)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { a ->
                click(a,"MÓJ CZAS")
                all(a).filterIsInstance<Button>().first { it.text.toString().contains("→") }.performClick()
                assertTrue(has(a,"Szczegóły dnia"))
                val text=all(a).filterIsInstance<TextView>().joinToString("\n") { it.text.toString() }
                assertTrue(text.contains("KOP-01 · Test")); assertTrue(text.contains("Przejazd")); assertTrue(text.contains("Zakupy"))
                assertTrue(text.contains("09:00")); assertTrue(text.contains("09:30"));assertTrue(text.contains("10:00"))
                assertTrue(has(a,"Łączny czas: 02:00"))
                val fg=UploadNotifications.foreground(a,"links:test","Zdjęcie 1 / 2",0,2)
                assertTrue(fg.notification.actions.any { it.title=="Dodaj zdjęcia" })
                assertEquals(android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,fg.foregroundServiceType)
                a.onBackPressed();assertTrue(has(a,"Mój czas"))
            }
            scenario.onActivity { a -> all(a).filterIsInstance<Button>().first { it.text.toString().contains("→") }.performClick() }
            shot("13-timeline")
        }
    }

}
