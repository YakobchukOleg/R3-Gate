package com.r3gate.app

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.withText
import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhoneReviewFlowTest {
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    private val prefs get()=context.getSharedPreferences("r3_gate",Context.MODE_PRIVATE)
    private fun data()=JSONObject(prefs.getString("data","{}")!!)
    private fun all(v: View): List<View> = listOf(v)+if(v is ViewGroup) (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()
    @Before fun seed() {
        val site=JSONObject().put("id","site").put("TAG","TCZ-01").put("city","Tczew").put("organization","R3 Gate").put("Adres","Jana Stanisławskiego 5")
        prefs.edit().clear().putString("pin","protected-finance").putString("data",JSONObject().put("schemaVersion",2).put("objects",JSONArray().put(site)).toString()).commit()
    }
    private fun answer(text: String) { onView(withText(text)).inRoot(isDialog()).perform(click()); instrumentation.waitForIdleSync() }
    private fun action(a: MainActivity,text: String) { all(a.window.decorView).filterIsInstance<Button>().first { it.text.toString()==text }.performClick() }
    @Test fun homeUsesOneNavigationEntryPerDestinationAcrossThemesWithoutEditingRecords() {
        val journal=data().put("walletSettings",JSONObject().put("defaultRate","30.00").put("openingCents",0))
        prefs.edit().putString("data",journal.toString()).commit()
        for(theme in WorkMeterTheme.entries) {
            prefs.edit().putString(WorkMeterTheme.PREF_KEY,theme.key).commit()
            val before=data().toString()
            ActivityScenario.launch(MainActivity::class.java).use { scenario -> scenario.onActivity { a ->
                val buttons=all(a.window.decorView).filterIsInstance<Button>()
                for(destination in WorkMeterDestination.entries) {
                    assertEquals(destination.label,1,buttons.count { it.contentDescription==destination.label })
                }
                assertFalse(buttons.any { it.text.toString() in listOf("🏢  OBIEKTY","◷  MÓJ CZAS","☷  WIĘCEJ · MENU P1–P4") })
                assertTrue(all(a.window.decorView).filterIsInstance<TextView>().any { it.contentDescription=="Czas dnia" })
            } }
            assertEquals(before,data().toString())
            assertEquals("protected-finance",prefs.getString("pin",null))
        }
    }
    @Test fun startAndStopRequireConfirmationAndNeverPinOrPrematureMutation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { action(it,"Rozpocznij dzień") }
            assertFalse(data().has("active")); answer("Nie"); assertFalse(data().has("active"))
            scenario.onActivity { action(it,"Rozpocznij dzień") }; answer("Tak")
            assertTrue(data().has("active")); assertFalse(prefs.getBoolean("gps_enabled",false))
            answer("Anuluj")
            scenario.onActivity { action(it,"Zakończ dzień") }
            val before=data().getJSONObject("active").toString()
            answer("Nie"); assertEquals(before,data().getJSONObject("active").toString())
            scenario.onActivity { action(it,"Zakończ dzień") }; answer("Tak")
            assertFalse(data().has("active")); assertEquals(1,data().getJSONArray("shifts").length())
            assertFalse(prefs.getBoolean("gps_enabled",false))
            assertEquals("protected-finance",prefs.getString("pin",null))
        }
    }
    @Test fun widgetRouteSharesTheSameConfirmation() {
        ActivityScenario.launch<MainActivity>(Intent(context,MainActivity::class.java).putExtra("widget_action","start")).use {
            assertFalse(data().has("active")); answer("Nie"); assertFalse(data().has("active"))
        }
    }
    @Test fun gpsSaveRequiresConfirmedActiveOnSiteObject() {
        for(kind in listOf("Przejazd","Prywatnie","Zakupy","Obiekt")) {
            val d=data().put("active",JSONObject().put("id","day").put("start",System.currentTimeMillis()-60_000)
                .put("stageStart",System.currentTimeMillis()-60_000).put("kind",kind).put("objectId",if(kind=="Obiekt") "site" else ""))
            prefs.edit().putString("data",d.toString()).commit()
            ActivityScenario.launch(MainActivity::class.java).use { scenario -> scenario.onActivity { a ->
                val b=all(a.window.decorView).filterIsInstance<Button>().single { it.text.toString()=="Zapisz GPS obiektu" }
                assertEquals(kind=="Obiekt",b.isEnabled)
            } }
        }
    }
    @Test fun widgetsKeepPrimaryAndLargeActionsAcrossSizesStatesThemesAndFontScale() {
        instrumentation.runOnMainSync {
            for(theme in WorkMeterTheme.entries) for(kind in listOf("Obiekt","Przejazd","Zakupy","Prywatnie","Nieprzypisane","")) {
                val d=data()
                if(kind.isEmpty()) d.remove("active") else d.put("active",JSONObject().put("id","day").put("objectId","site")
                    .put("object","TCZ-01 · R3 Gate").put("kind",kind).put("start",System.currentTimeMillis()-60_000))
                prefs.edit().putString("data",d.toString()).commit()
                for((w,h) in listOf(144 to 68,144 to 144,300 to 90,300 to 180,300 to 260)) for(scale in listOf(1f,1.3f,2f)) {
                    val configuration=Configuration(context.resources.configuration).apply { fontScale=scale }
                    val ctx=context.createConfigurationContext(configuration)
                    val parent=FrameLayout(ctx)
                    val view=GateWidget.render(ctx,WidgetLayoutProfile.forBounds(w,h),WidgetAppearance(theme=theme,transparency=100),widthDp=w,heightDp=h).apply(ctx,parent) as ViewGroup
                    val density=ctx.resources.displayMetrics.density
                    val width=(w*density).toInt(); val height=(h*density).toInt()
                    view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY)); view.layout(0,0,width,height)
                    fun inside(id: Int) {
                        val b=view.findViewById<View>(id)
                        assertTrue("Hidden $id $w $h $kind",generateSequence(b) { it.parent as? View }.all { it.visibility==View.VISIBLE })
                        val bounds=android.graphics.Rect(0,0,b.width,b.height); view.offsetDescendantRectToMyCoords(b,bounds)
                        assertTrue("Clipped $id $w $h $scale $bounds",bounds.width()>0 && bounds.height()>=48*density-1 && bounds.left>=0 && bounds.right<=width && bounds.top>=0 && bounds.bottom<=height)
                    }
                    inside(if(kind.isEmpty()) R.id.wstartbutton else R.id.wstopbutton)
                    if(h>=110) { inside(R.id.wcamera); if(kind.isNotEmpty() && kind!="Prywatnie") { inside(R.id.wswitch); inside(R.id.wtravel); inside(R.id.wshopping) } }
                    val timer=view.findViewById<TextView>(R.id.wduration)
                    assertTrue("Unreadable timer at $w x $h scale $scale",timer.height>=18*density && timer.width>=40*density)
                    val timerBounds=android.graphics.Rect(0,0,timer.width,timer.height)
                    view.offsetDescendantRectToMyCoords(timer,timerBounds)
                    assertTrue("Timer outside widget $w $h $scale $timerBounds",timerBounds.top>=0 && timerBounds.bottom<=height)
                    assertTrue("Clipped digits $w $h $scale",timer.paint.measureText(timer.text.toString())<=timer.width+1f)
                    assertFalse(view.findViewById<TextView>(R.id.wstatus).text.contains("ONLINE"))
                    assertEquals(128,view.findViewById<android.widget.ImageView>(R.id.wbackground).imageAlpha)
                    if(kind=="Obiekt" && scale in listOf(1f,2f)) {
                        val bitmap=android.graphics.Bitmap.createBitmap(width,height,android.graphics.Bitmap.Config.ARGB_8888)
                        view.draw(android.graphics.Canvas(bitmap))
                        val file=java.io.File(context.getExternalFilesDir(null),"widget-${theme.key}-${w}x${h}"+(if(scale==2f) "-200" else "")+".png")
                        file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }; bitmap.recycle()
                        instrumentation.uiAutomation.executeShellCommand("cp "+file.absolutePath+" /sdcard/Download/"+file.name).use {
                            java.io.FileInputStream(it.fileDescriptor).use { stream -> stream.readBytes() }
                        }
                    }
                }
            }
        }
    }
    @Test fun financialPinIsPreservedAndObjectSettingsAreReachable() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { a -> all(a.window.decorView).single { it.isClickable && it.contentDescription=="Finanse" }.performClick() }
            onView(androidx.test.espresso.matcher.ViewMatchers.withHint("PIN")).inRoot(isDialog())
                .check(androidx.test.espresso.assertion.ViewAssertions.matches(androidx.test.espresso.matcher.ViewMatchers.isDisplayed()))
            answer("Anuluj")
            scenario.onActivity { a ->
                all(a.window.decorView).single { it.isClickable && it.contentDescription=="Obiekty" }.performClick()
                all(a.window.decorView).single { it.isClickable && it.contentDescription=="Ustawienia obiektów" }.performClick()
                action(a,"Grupowanie według miast: Tak")
                assertFalse(prefs.getBoolean("objects_grouped",true))
                a.onBackPressed()
                assertTrue(all(a.window.decorView).filterIsInstance<TextView>().any { it.text.toString()=="Obiekty" })
            }
        }
    }
    @Test fun hourlyChartOpensActualStageMinutes() {
        val today=java.time.LocalDate.now()
        val start=today.atTime(9,0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val d=data().put("shifts",JSONArray().put(JSONObject().put("id","shift").put("start",start).put("end",start+3_600_000)
            .put("stages",JSONArray().put(JSONObject().put("start",start).put("end",start+3_600_000).put("kind","Obiekt").put("objectId","site")))))
        prefs.edit().putString("data",d.toString()).putString("chartPeriod","TODAY").commit()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { action(it,"▥  GRAFIKI") }
            instrumentation.waitForIdleSync()
            scenario.onActivity { a ->
                val chart=all(a.window.decorView).filterIsInstance<TimeChart>().first()
                val density=a.resources.displayMetrics.density
                val x=40*density+(chart.width-48*density)/24*9.5f
                val now=android.os.SystemClock.uptimeMillis()
                val down=android.view.MotionEvent.obtain(now,now,android.view.MotionEvent.ACTION_DOWN,x,60*density,0)
                val up=android.view.MotionEvent.obtain(now,now+20,android.view.MotionEvent.ACTION_UP,x,60*density,0)
                chart.dispatchTouchEvent(down); chart.dispatchTouchEvent(up); down.recycle(); up.recycle()
            }
            onView(androidx.test.espresso.matcher.ViewMatchers.withSubstring("Na obiekcie: 60 min")).inRoot(isDialog())
                .check(androidx.test.espresso.assertion.ViewAssertions.matches(androidx.test.espresso.matcher.ViewMatchers.isDisplayed()))
            answer("Zamknij")
        }
    }

    @Test fun localPhotosUseOneQueueEntryWithoutFalseUploadedStatusOrAutomaticAuthorization() {
        val original=java.io.File(context.filesDir,"phone-review-original.jpg").apply { writeBytes(byteArrayOf(1,2,3)) }
        try {
            val row=JSONObject().put("id","photos").put("objectId","site").put("date",java.time.LocalDate.now().toString())
                .put("photos",JSONArray().put(android.net.Uri.fromFile(original).toString()))
            prefs.edit().putString("data",data().put("links",JSONArray().put(row)).toString()).commit()
            val status=PhotoQueue.status(context,"links",row)
            assertEquals(0,status.sent); assertEquals(1,status.pending); assertTrue(status.caption.startsWith("LOKALNIE"))
            ActivityScenario.launch(MainActivity::class.java).use { scenario -> scenario.onActivity { a ->
                action(a,"📷  ZDJĘCIA")
                assertEquals(1,all(a.window.decorView).filterIsInstance<Button>().count { it.text.toString()=="WYŚLIJ / KOLEJKA" })
                assertFalse(all(a.window.decorView).filterIsInstance<Button>().any { it.text.toString()=="WYŚLIJ TERAZ" })
                action(a,"WYŚLIJ / KOLEJKA")
                assertTrue(all(a.window.decorView).filterIsInstance<TextView>().any { it.text.toString()=="Kolejka wysyłania" })
                assertFalse(prefs.getBoolean("uploadQueued_links:photos",false)); assertTrue(original.exists())
            } }
        } finally { original.delete() }
    }

    @Test fun staleStartObjectPickerCannotReopenAClosedDay() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { action(it,"Rozpocznij dzień") }; answer("Tak")
            val d=data(); val opened=d.getJSONObject("active")
            opened.put("end",System.currentTimeMillis()); d.remove("active"); d.put("shifts",JSONArray().put(opened))
            prefs.edit().putString("data",d.toString()).commit()
            onView(androidx.test.espresso.matcher.ViewMatchers.withSubstring("TCZ-01")).inRoot(isDialog()).perform(click())
            instrumentation.waitForIdleSync()
            assertFalse(data().has("active")); assertEquals(1,data().getJSONArray("shifts").length())
            assertFalse(prefs.getBoolean("gps_enabled",false))
        }
    }

    @Test fun integratedBackupSettingsCanLoadAndReturnWithoutChangingTheJournal() {
        val monitor=instrumentation.addMonitor(com.r3gate.app.backup.BackupSettingsActivity::class.java.name,null,false)
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                var before=""
                scenario.onActivity { a ->
                    before=data().toString()
                    all(a.window.decorView).single { it.isClickable && it.contentDescription=="Ustawienia" }.performClick()
                    all(a.window.decorView).filterIsInstance<Button>().single { it.text.toString().startsWith("Dane i kopie") }.performClick()
                    action(a,"Kopia zapasowa i synchronizacja")
                }
                val child=requireNotNull(instrumentation.waitForMonitorWithTimeout(monitor,5000))
                instrumentation.runOnMainSync {
                    assertEquals(1,all(child.window.decorView).filterIsInstance<com.r3gate.app.backup.BackupSettingsPanel>().size)
                    child.finish()
                }
                instrumentation.waitForIdleSync()
                assertEquals(before,data().toString())
            }
        } finally { instrumentation.removeMonitor(monitor) }
    }

}
