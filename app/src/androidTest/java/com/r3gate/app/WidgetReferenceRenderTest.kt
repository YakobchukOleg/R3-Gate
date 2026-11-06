package com.r3gate.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import android.widget.Button
import android.widget.Chronometer
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class WidgetReferenceRenderTest {
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    private fun render(width:Int,height:Int):View {
        val parent=FrameLayout(context)
        val view=GateWidget.render(context,WidgetLayoutProfile.forBounds(width,height),WidgetAppearance(theme=WorkMeterTheme.SLATE_SAGE),interactive=false,widthDp=width,heightDp=height).apply(context,parent)
        val d=context.resources.displayMetrics.density
        view.measure(View.MeasureSpec.makeMeasureSpec((width*d).toInt(),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec((height*d).toInt(),View.MeasureSpec.EXACTLY))
        view.layout(0,0,view.measuredWidth,view.measuredHeight)
        return view
    }
    private fun withActive(block:()->Unit) {
        val prefs=context.getSharedPreferences("r3_gate",Context.MODE_PRIVATE)
        val previous=prefs.getString("data",null)
        val now=System.currentTimeMillis()
        prefs.edit().putString("data",JSONObject().put("objects",org.json.JSONArray())
            .put("active",JSONObject().put("id","widget-reference").put("start",now-5*3_600_000-12*60_000)
                .put("stageStart",now-58_000).put("kind","Obiekt").put("objectId","reference").put("object","Rokitki 12")).toString()).commit()
        try { block() } finally { prefs.edit().apply { if(previous==null) remove("data") else putString("data",previous) }.commit() }
    }
    @Test fun largeReferenceKeepsExactButtonSizesAndGaps() = instrumentation.runOnMainSync { withActive {
        val view=render(300,216);val d=context.resources.displayMetrics.density
        val stop=view.findViewById<View>(R.id.wstopbutton);val camera=view.findViewById<View>(R.id.wcamera)
        assertEquals(48f,stop.height/d,0.6f);assertEquals(48f,camera.width/d,0.6f);assertEquals(48f,camera.height/d,0.6f)
        assertEquals(216f,stop.width/d,1f)
        fun bounds(id:Int)=android.graphics.Rect().also { rect -> val child=view.findViewById<View>(id);child.getDrawingRect(rect);(view as android.view.ViewGroup).offsetDescendantRectToMyCoords(child,rect) }
        val primary=bounds(R.id.wstopbutton);val photo=bounds(R.id.wcamera)
        assertEquals(8f,(photo.left-primary.right)/d,0.6f)
        val site=bounds(R.id.wswitch);val travel=bounds(R.id.wtravel);val shop=bounds(R.id.wshopping)
        assertEquals(56f,site.height()/d,0.6f);assertEquals(8f,(travel.left-site.right)/d,0.6f);assertEquals(8f,(shop.left-travel.right)/d,0.6f)
        assertNotNull(view.findViewById<Button>(R.id.wswitch).compoundDrawables[1])
        assertNotNull(view.findViewById<Button>(R.id.wtravel).compoundDrawables[1])
        assertNotNull(view.findViewById<Button>(R.id.wshopping).compoundDrawables[1])
        assertEquals(View.GONE,view.findViewById<View>(R.id.wdetails).visibility)
    } }
    @Test fun compactAndMediumUseReferenceControlSizes() = instrumentation.runOnMainSync { withActive {
        val d=context.resources.displayMetrics.density
        val compact=render(180,76);assertEquals(48f,compact.findViewById<View>(R.id.wstopbutton).width/d,0.6f)
        val medium=render(300,80);assertEquals(110f,medium.findViewById<View>(R.id.wstopbutton).width/d,0.6f)
        assertEquals(48f,medium.findViewById<View>(R.id.wswitch).width/d,0.6f)
    } }
    @Test fun prominentTimerIsCurrentStageAndSmallTimerIsToday() = instrumentation.runOnMainSync { withActive {
        val view=render(300,216)
        val clock=android.os.SystemClock.elapsedRealtime()
        assertEquals(58_000.0,(clock-view.findViewById<Chronometer>(R.id.wduration).base).toDouble(),1500.0)
        assertTrue(clock-view.findViewById<Chronometer>(R.id.wstage).base>5*3_600_000)
    } }
    @Test fun exportActualReferenceRendersForVisualReview() = instrumentation.runOnMainSync { withActive {
        for((width,height) in listOf(180 to 76,300 to 80,300 to 216)) {
            val view=render(width,height)
            val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val file=java.io.File(context.getExternalFilesDir(null),"widget-reference-${width}x${height}.png")
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
            instrumentation.uiAutomation.executeShellCommand("cp "+file.absolutePath+" /sdcard/Download/"+file.name).use {
                java.io.FileInputStream(it.fileDescriptor).use { stream -> stream.readBytes() }
            }
        }
    } }
}
