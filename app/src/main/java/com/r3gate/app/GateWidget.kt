package com.r3gate.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.*
import android.os.SystemClock
import android.widget.RemoteViews
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class GateWidget : AppWidgetProvider() {
    override fun onUpdate(context:Context,manager:AppWidgetManager,ids:IntArray) { ids.forEach { update(context,manager,it) } }
    companion object {
        private val time=SimpleDateFormat("HH:mm",Locale.US)
        fun refresh(context:Context) {
            val mgr=AppWidgetManager.getInstance(context)
            val ids=mgr.getAppWidgetIds(ComponentName(context,GateWidget::class.java))
            ids.forEach { update(context,mgr,it) }
        }
        private fun update(context:Context,mgr:AppWidgetManager,id:Int) {
            val prefs=context.getSharedPreferences("r3_gate",Context.MODE_PRIVATE)
            val d=try { JSONObject(prefs.getString("data","{}")!!) } catch (_:Exception) { JSONObject() }
            val a=d.optJSONObject("active"); val arr=d.optJSONArray("shifts")
            val last=if(arr!=null && arr.length()>0) arr.optJSONObject(arr.length()-1) else null
            val shown=a?:last
            val start=shown?.optLong("start")?:0L; val end=shown?.optLong("end")?:0L
            val v=RemoteViews(context.packageName,R.layout.widget)
            v.setTextViewText(R.id.wstatus,if(a!=null) "● ONLINE" else "● OFFLINE")
            v.setTextColor(R.id.wstatus,if(a!=null) 0xFF36D399.toInt() else 0xFFB9C3D0.toInt())
            v.setTextViewText(R.id.wstart,"Rozpoczęcie: "+if(start>0) time.format(Date(start)) else "—")
            v.setTextViewText(R.id.wend,"Zakończenie: "+if(a!=null || end<=0) "— Offline" else time.format(Date(end)))
            if(start>0) {
                val ms=if(a!=null) System.currentTimeMillis()-start else (end-start).coerceAtLeast(0)
                if(a!=null) {
                    v.setChronometer(R.id.wduration,SystemClock.elapsedRealtime()-ms,null,true)
                } else {
                    v.setChronometer(R.id.wduration,SystemClock.elapsedRealtime()-ms,null,false)
                }
            } else v.setChronometer(R.id.wduration,SystemClock.elapsedRealtime(),null,false)
            for((view,action,code) in listOf(Triple(R.id.wstartbutton,"start",1),Triple(R.id.wstopbutton,"stop",2),Triple(R.id.wswitch,"switch",3),Triple(R.id.wtravel,"travel",4),Triple(R.id.wshopping,"shopping",5))) {
                val intent=Intent(context,MainActivity::class.java).apply { putExtra("widget_action",action); flags=Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP }
                val pi=PendingIntent.getActivity(context,code,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                v.setOnClickPendingIntent(view,pi)
            }
            mgr.updateAppWidget(id,v)
        }
    }
}
