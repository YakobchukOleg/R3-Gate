package com.r3gate.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.*
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class GateWidget : AppWidgetProvider() {
    override fun onUpdate(context:Context,manager:AppWidgetManager,ids:IntArray) { ids.forEach { update(context,manager,it) } }
    override fun onAppWidgetOptionsChanged(context:Context,manager:AppWidgetManager,id:Int,options:android.os.Bundle) {
        update(context,manager,id,options)
    }
    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        WidgetAppearanceStore(context).delete(appWidgetIds)
    }

    override fun onRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) {
        WidgetAppearanceStore(context).restore(oldWidgetIds, newWidgetIds)
    }

    companion object {
        private val time=SimpleDateFormat("dd-MM-yyyy HH:mm",Locale.US)
        fun refresh(context:Context) {
            val mgr=AppWidgetManager.getInstance(context)
            val ids=mgr.getAppWidgetIds(ComponentName(context,GateWidget::class.java))
            ids.forEach { update(context,mgr,it) }
        }
        internal fun update(
            context: Context, mgr: AppWidgetManager, id: Int, options: Bundle = mgr.getAppWidgetOptions(id)
        ) {
            val appearance = WidgetAppearanceStore(context).read(id)
            mgr.updateAppWidget(id, renderForOptions(context, options, appearance))
        }

        internal fun renderForOptions(
            context: Context, options: Bundle, appearance: WidgetAppearance = WidgetAppearance()
        ): RemoteViews {
            val views = mutableMapOf<Pair<Int,Int>, RemoteViews>()
            fun at(width: Int, height: Int): RemoteViews {
                val profile = WidgetLayoutProfile.forBounds(width, height)
                return views.getOrPut(width to height) { render(context, profile, appearance, widthDp=width, heightDp=height) }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                @Suppress("DEPRECATION")
                val sizes = options.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
                    .orEmpty().filter { it.width.isFinite() && it.height.isFinite() && it.width > 0 && it.height > 0 }
                    .distinct()
                if (sizes.size in 1..16) {
                    return RemoteViews(sizes.associateWith { at(it.width.toInt(), it.height.toInt()) })
                }
            }

            val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
            val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 160)
            val maxWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, minWidth)
            val maxHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, minHeight)
            val portrait = at(minWidth, maxHeight)
            val landscape = at(maxWidth, minHeight)
            return if (portrait === landscape) portrait else RemoteViews(landscape, portrait)
        }

        internal fun render(
            context: Context,
            profile: WidgetLayoutProfile = WidgetLayoutProfile.STANDARD,
            appearance: WidgetAppearance = WidgetAppearance(),
            interactive: Boolean = true,
            widthDp: Int = 300, heightDp: Int = 180
        ): RemoteViews {
            val prefs=context.getSharedPreferences("r3_gate",Context.MODE_PRIVATE)
            val d=try { JSONObject(prefs.getString("data","{}")!!) } catch (_:Exception) { JSONObject() }
            val a=d.optJSONObject("active"); val arr=d.optJSONArray("shifts")
            val last=if(arr!=null && arr.length()>0) arr.optJSONObject(arr.length()-1) else null
            val shown=a?:last
            val start=shown?.optLong("start")?:0L; val end=shown?.optLong("end")?:0L
            val mini = profile == WidgetLayoutProfile.COMPACT
            val shortWide = profile == WidgetLayoutProfile.MEDIUM
            val wide = profile == WidgetLayoutProfile.LARGE
            val expanded = wide
            val v=RemoteViews(context.packageName,when(profile) { WidgetLayoutProfile.COMPACT -> R.layout.widget_compact; WidgetLayoutProfile.MEDIUM -> R.layout.widget_medium; WidgetLayoutProfile.LARGE -> R.layout.widget })
            val theme = appearance.theme ?: WorkMeterAppearance.theme(context)
            v.setImageViewResource(R.id.wbackground, when (theme) {
                WorkMeterTheme.SLATE_SAGE -> R.drawable.widget_bg
                WorkMeterTheme.NORDIC_BLUE -> R.drawable.widget_bg_nordic
                WorkMeterTheme.GRAPHITE_DARK -> R.drawable.widget_bg_graphite
            })
            v.setInt(R.id.wbackground, "setImageAlpha", appearance.backgroundAlpha(prefs.getInt("widgetTransparency", 0)))
            val travel=a?.optString("kind")=="Przejazd"
            val personal=a?.optString("kind")=="Prywatnie"
            v.setTextViewText(R.id.wobject,when { a==null -> "Gotowy do pracy"; travel -> "W drodze"; personal -> "Czas prywatny"; a.optString("kind")=="Zakupy" -> "Zakupy"; else -> a.optString("object").substringBefore(" · ") })
            v.setTextViewText(R.id.wtravel,if(travel) "DOTARŁEM" else "PRZEJAZD")
            v.setInt(R.id.wtravel,"setBackgroundResource",if(travel) R.drawable.widget_active else R.drawable.widget_button)
            v.setTextColor(R.id.wtravel, if (travel) theme.palette.onError else 0xFF37474F.toInt())
            v.setTextColor(R.id.wstopbutton, theme.palette.onError)
            v.setViewVisibility(R.id.wstartbutton,if(a==null) android.view.View.VISIBLE else android.view.View.GONE)
            v.setViewVisibility(R.id.wstopbutton,if(a!=null) android.view.View.VISIBLE else android.view.View.GONE)
            v.setViewVisibility(R.id.wcamera, if(wide) View.VISIBLE else View.GONE)
            v.setViewVisibility(R.id.wprimary, View.VISIBLE)
            v.setViewVisibility(R.id.wactions,if(wide && appearance.showActions && a!=null && !personal) View.VISIBLE else View.GONE)
            v.setViewVisibility(R.id.wswitch,if((wide && appearance.showActions && a!=null && !personal) || (shortWide && a!=null && !personal)) View.VISIBLE else View.GONE)
            v.setViewVisibility(R.id.wobject,if(wide && heightDp>=175 && appearance.showObject) View.VISIBLE else View.GONE)
            val stage=when {
                a==null -> "Brak zmiany"
                travel -> "Przejazd"
                personal -> "Prywatnie"
                a.optString("kind")=="Zakupy" -> "Zakupy"
                a.optString("objectId").isNotBlank() -> "Na obiekcie"
                else -> "Nieprzypisane"
            }
            v.setTextViewText(R.id.wstatus,"● "+stage)
            v.setTextViewText(R.id.wstartbutton,if(mini) "▶" else "Rozpocznij")
            v.setTextViewText(R.id.wstopbutton,if(mini) "□" else if(wide) "Zakończ pracę" else "Zakończ")
            v.setContentDescription(R.id.wstartbutton,"Rozpocznij dzień pracy")
            v.setContentDescription(R.id.wstopbutton,"Zakończ dzień pracy")
            v.setContentDescription(R.id.wswitch,"Zmień obiekt")
            v.setTextViewText(R.id.wswitch,if(shortWide) "⇄" else "Obiekt")
            v.setTextViewText(R.id.wtravel,if(travel) "Dojechałem" else "Przejazd")
            v.setTextViewText(R.id.wshopping,if(a?.optString("kind")=="Zakupy") "Koniec zakupów" else "Zakupy")
            v.setInt(R.id.wstopbutton,"setBackgroundResource",R.drawable.widget_active)
            val palette=theme.palette
            // The opaque foreground panel preserves text contrast on photo wallpapers.
            for(id in listOf(R.id.wstatus,R.id.wduration,R.id.wobject)) v.setTextColor(id,palette.onHeader)
            for(id in listOf(R.id.wswitch,R.id.wtravel,R.id.wshopping,R.id.wcamera)) v.setTextColor(id,0xFF304953.toInt())
            v.setViewPadding(R.id.wcontent,if(widthDp<170) 0 else (8*context.resources.displayMetrics.density).toInt(),(2*context.resources.displayMetrics.density).toInt(),if(widthDp<170) 0 else (8*context.resources.displayMetrics.density).toInt(),(2*context.resources.displayMetrics.density).toInt())
            if(wide && heightDp<175) v.setTextViewTextSize(R.id.wduration,android.util.TypedValue.COMPLEX_UNIT_SP,22f)
            val stageColor=when { a==null -> WorkMeterTheme.TRAVEL; personal -> WorkMeterTheme.PERSONAL; travel -> WorkMeterTheme.TRAVEL; a.optString("kind")=="Zakupy" -> WorkMeterTheme.SHOPPING; else -> WorkMeterTheme.ON_SITE }
            val stageText=android.text.SpannableString("● "+stage)
            stageText.setSpan(android.text.style.ForegroundColorSpan(stageColor),0,1,android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            v.setTextViewText(R.id.wstatus,stageText)
            val backing=when(theme) { WorkMeterTheme.SLATE_SAGE -> R.drawable.widget_bg; WorkMeterTheme.NORDIC_BLUE -> R.drawable.widget_bg_nordic; WorkMeterTheme.GRAPHITE_DARK -> R.drawable.widget_bg_graphite }
            for(id in listOf(R.id.wstatus,R.id.wduration,R.id.wobject,R.id.wgps)) v.setInt(id,"setBackgroundResource",backing)
            v.setViewVisibility(R.id.wgps,if(wide && heightDp>=220 && appearance.showGps) View.VISIBLE else View.GONE)
            if(wide && heightDp>=220 && appearance.showGps) v.setTextViewText(R.id.wgps,GpsServiceStatusUi.read(context))
            if(mini) v.setTextViewTextSize(R.id.wduration,android.util.TypedValue.COMPLEX_UNIT_SP,22f)
            v.setTextViewText(R.id.wstart,"Rozpoczęcie: "+if(start>0) time.format(Date(start)) else "—")
            v.setTextViewText(R.id.wend,"Zakończenie: "+if(a!=null || end<=0) "— Offline" else time.format(Date(end)))
            if(start>0) {
                val now=if(a!=null) System.currentTimeMillis() else end
                val stages=shown?.optJSONArray("stages")
                val personalPeriods=mutableListOf<Pair<Long,Long>>()
                if(stages!=null) for(i in 0 until stages.length()) {
                    val st=stages.optJSONObject(i) ?: continue
                    if(st.optString("kind")=="Prywatnie") personalPeriods.add(st.optLong("start") to st.optLong("end"))
                }
                if(personal && a!=null) personalPeriods.add(a.optLong("stageStart",now) to now)
                val clipped=personalPeriods.map { maxOf(start,it.first) to minOf(now,it.second) }.filter { it.second>it.first }.sortedBy { it.first }
                var personalMs=0L
                var until=start
                for(p in clipped) {
                    val from=maxOf(until,p.first)
                    if(p.second>from) personalMs+=p.second-from
                    until=maxOf(until,p.second)
                }
                val ms=(now-start-personalMs).coerceAtLeast(0)
                if(a!=null) {
                    v.setChronometer(R.id.wduration,SystemClock.elapsedRealtime()-ms,null,!personal)
                } else {
                    v.setChronometer(R.id.wduration,SystemClock.elapsedRealtime()-ms,null,false)
                }
            } else v.setChronometer(R.id.wduration,SystemClock.elapsedRealtime(),null,false)
            if (!interactive) return v
            for((view,action,code) in listOf(Triple(R.id.wstartbutton,"start",1),Triple(R.id.wstopbutton,"stop",2),Triple(R.id.wswitch,"switch",3),Triple(R.id.wtravel,"travel",4),Triple(R.id.wshopping,"shopping",5),Triple(R.id.wcamera,"camera",6))) {
                val intent=Intent(context,MainActivity::class.java).apply { putExtra("widget_action",action); flags=Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP }
                val pi=PendingIntent.getActivity(context,code,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                v.setOnClickPendingIntent(view,pi)
            }
            val open = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            v.setOnClickPendingIntent(
                R.id.wroot,
                PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_IMMUTABLE)
            )
            return v
        }
    }
}
