package com.r3gate.app

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.util.SizeF
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleCallback
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import org.junit.Assert.*
import org.junit.Test

/** Goes through AppWidgetService and HostView's restricted RemoteViews context. */
class WidgetHostIntegrationTest {
    @Test fun systemHostInflatesResponsiveWidgetAndUpdatesEverySize() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val prefs=context.getSharedPreferences("r3_gate",0)
        val original=prefs.getString("data","{}")
        prefs.edit().putString("data","{\"objects\":[]}").commit()
        var before: String?=null
        val manager=AppWidgetManager.getInstance(context)
        val host=AppWidgetHost(context,7301)
        val id=host.allocateAppWidgetId()
        val provider=ComponentName(context,GateWidget::class.java)
        // onNewIntent replaces MainActivity.intent with the widget Intent, which
        // has no MAIN/LAUNCHER action/category. ActivityScenario filters lifecycle
        // events by its launch Intent and otherwise ignores even DESTROYED.
        // Keep the original identity for teardown; exercise the real widget Intent
        // unchanged through the confirmation and journal assertions first.
        val activities=linkedMapOf<Activity,Intent>()
        val lifecycle=ActivityLifecycleMonitorRegistry.getInstance()
        val callback=ActivityLifecycleCallback { activity, stage ->
            if(activity is MainActivity) {
                if(stage==Stage.CREATED) activities[activity]=Intent(activity.intent)
                if(stage==Stage.DESTROYED) activities.remove(activity)
            }
        }
        instrumentation.runOnMainSync { lifecycle.addLifecycleCallback(callback) }
        instrumentation.uiAutomation.adoptShellPermissionIdentity("android.permission.BIND_APPWIDGET")
        try {
            assertTrue("System refused binding widget",manager.bindAppWidgetIdIfAllowed(id,provider))
            val info=manager.getAppWidgetInfo(id)
            assertNotNull(info)
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                lateinit var view:android.appwidget.AppWidgetHostView
                scenario.onActivity { activity ->
                    before=prefs.getString("data",null)
                    assertFalse(org.json.JSONObject(before!!).has("active"))
                    host.startListening()
                    view=host.createView(activity,id,info)
                    activity.setContentView(view)
                }
                for((width,height) in listOf(144 to 68,144 to 144,300 to 90,300 to 180,300 to 260)) {
                    val options=Bundle().apply {
                        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,width)
                        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH,width)
                        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,height)
                        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,height)
                        putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES,arrayListOf(SizeF(width.toFloat(),height.toFloat()),SizeF(height.toFloat(),width.toFloat())))
                    }
                    manager.updateAppWidgetOptions(id,options)
                    GateWidget.update(context,manager,id,options)
                    var inflated=false
                    repeat(40) {
                        if(!inflated) {
                            Thread.sleep(100)
                            instrumentation.waitForIdleSync()
                            scenario.onActivity {
                                fun descendants(v:View):List<View> = listOf(v)+if(v is ViewGroup) (0 until v.childCount).flatMap { descendants(v.getChildAt(it)) } else emptyList()
                                val children=descendants(view)
                                inflated=children.any { it.id==R.id.wduration } && children.any { it.id==R.id.wstartbutton } &&
                                    children.filterIsInstance<TextView>().any { it.id==R.id.wstatus && it.text.toString().startsWith("● ") }
                                assertFalse("Host displays loading error: "+children.filterIsInstance<TextView>().joinToString { it.text },children.filterIsInstance<TextView>().any {
                                    it.text.toString().contains("Problem loading",true) || it.text.toString().contains("Can't load",true)
                                })
                            }
                        }
                    }
                    assertTrue("Widget did not inflate through system host at ${width}x${height}",inflated)
                }
                try {
                    scenario.onActivity {
                        assertTrue("Host action did not launch its PendingIntent",view.findViewById<View>(R.id.wstartbutton).performClick())
                    }
                    instrumentation.waitForIdleSync()
                    androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withText("Nie"))
                        .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog())
                        .perform(androidx.test.espresso.action.ViewActions.click())
                    assertEquals("Widget start bypassed Tak/Nie",before,prefs.getString("data",null))
                } finally {
                    // Restore the scenario's launch identity before finishing so its
                    // lifecycle observer can see the actual destruction. This only
                    // changes test teardown, after the real PendingIntent was checked.
                    instrumentation.runOnMainSync {
                        activities.toList().asReversed().forEach { (activity, launchIntent) ->
                            activity.intent=launchIntent
                            activity.finish()
                        }
                    }
                    instrumentation.waitForIdleSync()
                }
            }
        } finally {
            instrumentation.runOnMainSync { lifecycle.removeLifecycleCallback(callback) }
            host.stopListening(); host.deleteAppWidgetId(id)
            instrumentation.uiAutomation.dropShellPermissionIdentity()
            prefs.edit().putString("data",original).commit()
        }
    }
}
