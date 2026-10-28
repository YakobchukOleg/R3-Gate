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
    override fun onAppWidgetOptionsChanged(context:Context,manager:AppWidgetManager,id:Int,options:android.os.Bundle) {
        update(context,manager,id)
    }
    companion object {
        private val time=SimpleDateFormat("dd-MM-yyyy HH:mm",Locale.US)
        fun refresh(context:Context) {
            val mgr=AppWidgetManager.getInstance(context)
            val ids=mgr.getAppWidgetIds(ComponentName(context,GateWidget::class.java))
            ids.forEach { update(context,mgr,it) }
        }
        private fun update(context:Context,mgr:AppWidgetManager,id:Int) {
            mgr.updateAppWidget(id,render(context,mgr.getAppWidgetOptions(id)))
        }
        internal fun render(context:Context,options:android.os.Bundle=android.os.Bundle()):RemoteViews {
            val prefs=context.getSharedPreferences("r3_gate",Context.MODE_PRIVATE)
            val d=try { JSONObject(prefs.getString("data","{}")!!) } catch (_:Exception) { JSONObject() }
            val a=d.optJSONObject("active"); val arr=d.optJSONArray("shifts")
            val last=if(arr!=null && arr.length()>0) arr.optJSONObject(arr.length()-1) else null
            val shown=a?:last
            val start=shown?.optLong("start")?:0L; val end=shown?.optLong("end")?:0L
            val v=RemoteViews(context.packageName,R.layout.widget)
            val travel=a?.optString("kind")=="Przejazd"
            v.setTextViewText(R.id.wobject,when { a==null -> "Gotowy do pracy"; travel -> "W drodze"; a.optString("kind")=="Zakupy" -> "Zakupy"; else -> a.optString("object") })
            v.setTextViewText(R.id.wtravel,if(travel) "PRZEJAZD ●" else "PRZEJAZD")
            v.setInt(R.id.wtravel,"setBackgroundResource",if(travel) R.drawable.widget_active else R.drawable.widget_button)
            v.setTextColor(R.id.wtravel,if(travel) 0xFF101B29.toInt() else 0xFFDBEEFF.toInt())
            v.setViewVisibility(R.id.wstartbutton,if(a==null) android.view.View.VISIBLE else android.view.View.GONE)
            v.setViewVisibility(R.id.wstopbutton,if(a!=null) android.view.View.VISIBLE else android.view.View.GONE)
            v.setViewVisibility(R.id.wactions,if(a!=null) android.view.View.VISIBLE else android.view.View.GONE)
            v.setViewVisibility(R.id.wend,if(a==null) android.view.View.VISIBLE else android.view.View.GONE)
            val compact=options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,250)<220
            v.setTextViewText(R.id.wswitch,if(compact) "OBIEKT ↔" else "ZMIEŃ OBIEKT")
            v.setTextViewText(R.id.wtitle,"R3 Gate")
            v.setViewVisibility(R.id.wstage,if(travel) android.view.View.VISIBLE else android.view.View.GONE)
            if(travel) v.setChronometer(R.id.wstage,SystemClock.elapsedRealtime()-(System.currentTimeMillis()-a!!.optLong("stageStart")),"W drodze: %s",true)
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
            for((view,action,code) in listOf(Triple(R.id.wstartbutton,"start",1),Triple(R.id.wstopbutton,"stop",2),Triple(R.id.wswitch,"switch",3),Triple(R.id.wtravel,"travel",4))) {
                val intent=Intent(context,MainActivity::class.java).apply { putExtra("widget_action",action); flags=Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP }
                val pi=PendingIntent.getActivity(context,code,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                v.setOnClickPendingIntent(view,pi)
            }
            return v
        }
    }
}
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
    override fun onAppWidgetOptionsChanged(context:Context,manager:AppWidgetManager,id:Int,options:android.os.Bundle) {
        update(context,manager,id)
    }
    companion object {
        private val time=SimpleDateFormat("dd-MM-yyyy HH:mm",Locale.US)
        fun refresh(context:Context) {
            val mgr=AppWidgetManager.getInstance(context)
            val ids=mgr.getAppWidgetIds(ComponentName(context,GateWidget::class.java))
            ids.forEach { update(context,mgr,it) }
        }
        private fun update(context:Context,mgr:AppWidgetManager,id:Int) {
            mgr.updateAppWidget(id,render(context,mgr.getAppWidgetOptions(id)))
        }
        internal fun render(context:Context,options:android.os.Bundle=android.os.Bundle()):RemoteViews {
            val prefs=context.getSharedPreferences("r3_gate",Context.MODE_PRIVATE)
            val d=try { JSONObject(prefs.getString("data","{}")!!) } catch (_:Exception) { JSONObject() }
            val a=d.optJSONObject("active"); val arr=d.optJSONArray("shifts")
            val last=if(arr!=null && arr.length()>0) arr.optJSONObject(arr.length()-1) else null
            val shown=a?:last
            val start=shown?.optLong("start")?:0L; val end=shown?.optLong("end")?:0L
            val v=RemoteViews(context.packageName,R.layout.widget)
            val travel=a?.optString("kind")=="Przejazd"
            v.setTextViewText(R.id.wobject,when { a==null -> "Gotowy do pracy"; travel -> "W drodze"; a.optString("kind")=="Zakupy" -> "Zakupy"; else -> a.optString("object") })
            v.setTextViewText(R.id.wtravel,if(travel) "PRZEJAZD ●" else "PRZEJAZD")
            v.setInt(R.id.wtravel,"setBackgroundResource",if(travel) R.drawable.widget_active else R.drawable.widget_button)
            v.setTextColor(R.id.wtravel,if(travel) 0xFF101B29.toInt() else 0xFFDBEEFF.toInt())
            v.setViewVisibility(R.id.wstartbutton,if(a==null) android.view.View.VISIBLE else android.view.View.GONE)
            v.setViewVisibility(R.id.wstopbutton,if(a!=null) android.view.View.VISIBLE else android.view.View.GONE)
            v.setViewVisibility(R.id.wactions,if(a!=null) android.view.View.VISIBLE else android.view.View.GONE)
            v.setViewVisibility(R.id.wend,if(a==null) android.view.View.VISIBLE else android.view.View.GONE)
            val compact=options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,250)<220
            v.setTextViewText(R.id.wswitch,if(compact) "OBIEKT ↔" else "ZMIEŃ OBIEKT")
            v.setTextViewText(R.id.wtitle,"R3 Gate")
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
            for((view,action,code) in listOf(Triple(R.id.wstartbutton,"start",1),Triple(R.id.wstopbutton,"stop",2),Triple(R.id.wswitch,"switch",3),Triple(R.id.wtravel,"travel",4))) {
                val intent=Intent(context,MainActivity::class.java).apply { putExtra("widget_action",action); flags=Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP }
                val pi=PendingIntent.getActivity(context,code,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                v.setOnClickPendingIntent(view,pi)
            }
            return v
        }
    }
}
