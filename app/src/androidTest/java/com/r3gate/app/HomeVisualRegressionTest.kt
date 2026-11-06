package com.r3gate.app

import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class HomeVisualRegressionTest {
    @Test fun compactHomeKeepsEqualPairsAndThreeQuickTilesAcrossThemes() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val prefs=context.getSharedPreferences("r3_gate",0)
        val now=System.currentTimeMillis()
        val journal=JSONObject().put("objects",JSONArray()).put("active",JSONObject()
            .put("id","visual-day").put("kind","Obiekt").put("objectId","site")
            .put("object","TCZ-01 · R3 Gate · Tczew · Jana Stanisławskiego 5")
            .put("start",now-6*3600000).put("stageStart",now-2*3600000))
        fun all(v:View):List<View> = listOf(v)+if(v is ViewGroup) (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()
        for(theme in WorkMeterTheme.entries) {
            prefs.edit().clear().putString("pin","unchanged-pin").putString("data",journal.toString())
                .putString(WorkMeterTheme.PREF_KEY,theme.key).commit()
            var before=""
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                instrumentation.waitForIdleSync()
                scenario.onActivity { activity ->
                    before=prefs.getString("data",null)!!
                    val buttons=all(activity.window.decorView).filterIsInstance<Button>()
                    fun button(text:String)=buttons.single { it.text.toString()==text }
                    for((first,second) in listOf("Zmień obiekt" to "Zakupy","Prywatnie" to "Zakończ dzień","Trasa do obiektu" to "Zapisz GPS obiektu")) {
                        val left=button(first); val right=button(second)
                        assertSame(left.parent,right.parent)
                        assertEquals(first,left.top,right.top)
                        assertEquals(first,left.height,right.height)
                        assertTrue(first,left.height>=48*context.resources.displayMetrics.density-1)
                        assertEquals(first,8*context.resources.displayMetrics.density,(right.left-left.right).toFloat(),1f)
                    }
                    val tiles=listOf("Zdjęcie","Notatka","Materiał").map(::button)
                    assertTrue(tiles.all { it.parent===tiles.first().parent })
                    assertTrue(tiles.all { it.width>0 && it.height>0 })
                    assertEquals(tiles.first().height,tiles.last().height)
                }
                android.os.SystemClock.sleep(1500)
                val image=instrumentation.uiAutomation.takeScreenshot()
                val path="/sdcard/Download/home-compact-${theme.key}.png"
                val file=java.io.File(context.getExternalFilesDir(null),"home-${theme.key}.png")
                file.outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
                instrumentation.uiAutomation.executeShellCommand("cp ${file.absolutePath} $path").use {
                    java.io.FileInputStream(it.fileDescriptor).use { input -> input.readBytes() }
                }
            }
            assertEquals(before,prefs.getString("data",null))
            assertEquals("unchanged-pin",prefs.getString("pin",null))
        }
    }
}
