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
            val mini=profile==WidgetLayoutProfile.COMPACT
            val shortWide=profile==WidgetLayoutProfile.MEDIUM
            val wide=profile==WidgetLayoutProfile.LARGE
            val expanded=wide && heightDp>=200
            val fontScale=context.resources.configuration.fontScale
            val now=System.currentTimeMillis()
            val theme=appearance.theme ?: WorkMeterAppearance.theme(context)
            val layout=when { mini -> R.layout.widget_compact;shortWide -> R.layout.widget_medium;expanded -> R.layout.widget_expanded;else -> R.layout.widget }
            val v=RemoteViews(context.packageName,layout)
            val backing=when(theme) { WorkMeterTheme.SLATE_SAGE -> R.drawable.widget_bg;WorkMeterTheme.NORDIC_BLUE -> R.drawable.widget_bg_nordic;WorkMeterTheme.GRAPHITE_DARK -> R.drawable.widget_bg_graphite }
            v.setImageViewResource(R.id.wbackground,backing)
            v.setInt(R.id.wbackground,"setImageAlpha",appearance.backgroundAlpha(prefs.getInt("widgetTransparency",0)))
            // The foreground remains opaque when background transparency is selected.
            v.setInt(R.id.wsummary,"setBackgroundResource",backing)
            val travel=a?.optString("kind")=="Przejazd"
            val personal=a?.optString("kind")=="Prywatnie"
            val shopping=a?.optString("kind")=="Zakupy"
            val stage=when { a==null -> "Brak zmiany";travel -> "Przejazd";personal -> "Prywatnie";shopping -> "Zakupy";a.optString("objectId").isNotBlank() -> "Na obiekcie";else -> "Nieprzypisane" }
            val tag=a?.optString("object").orEmpty().substringBefore(" · ")
            val status="● "+stage+if(wide && widthDp>=250 && appearance.showObject && !travel && !personal && !shopping && tag.isNotBlank()) " · "+tag else ""
            val ink=0xFFF5F4EF.toInt()
            val dot=when { travel -> 0xFF8CBAE6.toInt();shopping -> 0xFFF0A126.toInt();personal -> WorkMeterTheme.PERSONAL;else -> 0xFF5DC8A7.toInt() }
            val statusInk=when { travel -> 0xFFBDD8F2.toInt();shopping -> 0xFFF5D19A.toInt();personal -> 0xFFE0C7DF.toInt();a==null -> ink;else -> 0xFFA9DEC9.toInt() }
            val styled=android.text.SpannableString(status).apply {
                setSpan(android.text.style.ForegroundColorSpan(statusInk),0,length,android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(android.text.style.ForegroundColorSpan(dot),0,1,android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            v.setTextViewText(R.id.wstatus,styled)
            val statusSp=if(fontScale>1.5f) (if(heightDp<200) 8f else 10f) else if(wide && heightDp<175) 10f else 12f
            v.setTextViewTextSize(R.id.wstatus,android.util.TypedValue.COMPLEX_UNIT_SP,statusSp)
            val side=if(widthDp<176) 0 else if(widthDp<250) 8 else 14
            val vertical=if(wide && heightDp<175) 2 else if(heightDp<200) 8 else 14
            fun px(dp:Int)=(dp*context.resources.displayMetrics.density).toInt()
            v.setViewPadding(R.id.wcontent,px(side),px(vertical),px(side),px(vertical))
            v.setViewVisibility(R.id.wstartbutton,if(a==null) View.VISIBLE else View.GONE)
            v.setViewVisibility(R.id.wstopbutton,if(a!=null) View.VISIBLE else View.GONE)
            v.setViewVisibility(R.id.wprimary,View.VISIBLE)
            val actions=wide && heightDp>=144 && appearance.showActions && a!=null && !personal
            v.setViewVisibility(R.id.wactions,if(actions) View.VISIBLE else View.GONE)
            v.setViewVisibility(R.id.wcamera,if(wide) View.VISIBLE else View.GONE)
            v.setViewVisibility(R.id.wswitch,if(actions || (shortWide && a!=null && !personal)) View.VISIBLE else View.GONE)
            if(wide) {
                v.setViewVisibility(R.id.wverticalgap,if(actions) View.VISIBLE else View.GONE)
                for(id in listOf(R.id.wgap1,R.id.wgap2)) v.setViewVisibility(id,if(widthDp<176) View.GONE else View.VISIBLE)
            }
            v.setInt(R.id.wstartbutton,"setBackgroundResource",if(mini) R.drawable.widget_round_start else R.drawable.widget_start_active)
            v.setInt(R.id.wstopbutton,"setBackgroundResource",if(mini) R.drawable.widget_round_stop else R.drawable.widget_active)
            v.setTextColor(R.id.wstartbutton,android.graphics.Color.WHITE);v.setTextColor(R.id.wstopbutton,android.graphics.Color.WHITE)
            v.setTextViewText(R.id.wstartbutton,if(mini) "" else if(wide && widthDp<220) "Start" else "Rozpocznij")
            v.setTextViewText(R.id.wstopbutton,if(mini) "" else if(wide && widthDp<220) "Stop" else if(wide) "Zakończ pracę" else "Zakończ")
            for((id,icon) in listOf(R.id.wstartbutton to R.drawable.ic_widget_start,R.id.wstopbutton to R.drawable.ic_widget_stop)) {
                v.setTextViewCompoundDrawables(id,icon,0,0,0)
                v.setContentDescription(id,if(id==R.id.wstartbutton) "Rozpocznij dzień pracy" else "Zakończ dzień pracy")
                v.setTextViewTextSize(id,android.util.TypedValue.COMPLEX_UNIT_SP,if(widthDp<220) 11f else 14f)
                if(mini) v.setViewPadding(id,px(12),px(12),px(12),px(12))
            }
            v.setContentDescription(R.id.wswitch,"Zmień obiekt")
            if(!shortWide) v.setTextViewText(R.id.wswitch,if(widthDp<220) "" else "Obiekt")
            v.setTextViewText(R.id.wtravel,if(wide && widthDp<220) "" else if(travel) "Dojechałem" else "Przejazd")
            v.setTextViewText(R.id.wshopping,if(wide && widthDp<220) "" else if(shopping) "Koniec" else "Zakupy")
            v.setContentDescription(R.id.wtravel,if(travel) "Dojechałem — wybierz obiekt" else "Rozpocznij przejazd")
            v.setContentDescription(R.id.wshopping,if(shopping) "Zakończ zakupy" else "Rozpocznij zakupy")
            for(id in listOf(R.id.wtravel,R.id.wshopping)) v.setTextColor(id,ink)
            if(wide) {
                v.setTextColor(R.id.wswitch,ink)
                val topIcons=expanded && fontScale<=1.5f && widthDp>=220
                for((id,icon) in listOf(R.id.wswitch to R.drawable.ic_widget_object,R.id.wtravel to R.drawable.ic_widget_car,R.id.wshopping to R.drawable.ic_widget_cart)) {
                    v.setTextViewCompoundDrawables(id,if(widthDp<220) icon else 0,if(topIcons) icon else 0,0,0)
                    v.setTextViewTextSize(id,android.util.TypedValue.COMPLEX_UNIT_SP,if(expanded) 12f else 11f)
                }
            }
            v.setViewVisibility(R.id.wobject,View.GONE);v.setViewVisibility(R.id.wgps,View.GONE);v.setViewVisibility(R.id.wdetails,View.GONE)
            val today=java.time.LocalDate.now()
            val analytics=TimeAnalytics.calculate(TimeAnalyticsSnapshot.shifts(d,now),today,today,java.time.ZoneId.systemDefault())
            val showDay=wide && widthDp>=250 && heightDp>=175 && a!=null
            val mainMillis=WorkMeterHomeState.stageElapsed(now,a?.optLong("start") ?: 0L,a?.optLong("stageStart") ?: 0L)
            v.setChronometer(R.id.wduration,SystemClock.elapsedRealtime()-mainMillis,null,a!=null)
            v.setTextColor(R.id.wduration,ink)
            if(wide) {
                v.setViewVisibility(R.id.wstagecolumn,if(showDay) View.VISIBLE else View.GONE)
                v.setTextViewText(R.id.wday,"Dziś")
                v.setTextColor(R.id.wday,0xFFC3C5BD.toInt());v.setTextColor(R.id.wstage,0xFFC3C5BD.toInt())
                v.setChronometer(R.id.wstage,SystemClock.elapsedRealtime()-analytics.workMillis,null,a!=null && !personal)
                val connected=prefs.getBoolean("driveConnected",false)
                val account=prefs.getString("driveEmail",null)
                val verified=WidgetCloudEvidence.verified(connected,account,prefs.getString(com.r3gate.app.backup.BackupStatus.VERIFIED_ACCOUNT,null),prefs.getString(com.r3gate.app.backup.BackupStatus.VERIFIED_AT,null),now)
                v.setImageViewResource(R.id.wcloud,if(verified) R.drawable.ic_widget_cloud_verified else R.drawable.ic_widget_cloud)
                v.setContentDescription(R.id.wcloud,if(verified) "Ostatnia kopia Google Drive została sprawdzona" else "Brak sprawdzonej kopii Google Drive")
                v.setViewVisibility(R.id.wcloud,if(appearance.showGps && widthDp>=250) View.VISIBLE else View.GONE)
            }
            // Fit native live chronometers to the exact available space, including long hours and 200% text.
            fun fit(id:Int,millis:Long,desired:Float,width:Float,height:Float) {
                val metrics=context.resources.displayMetrics
                val paint=android.graphics.Paint().apply { typeface=android.graphics.Typeface.create("sans-serif-light",android.graphics.Typeface.NORMAL) }
                val label=android.text.format.DateUtils.formatElapsedTime(millis.coerceAtLeast(0)/1000)
                fun fits(sp:Float):Boolean {
                    paint.textSize=android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP,sp,metrics)
                    return paint.measureText(label)<=width.coerceAtLeast(16f)*metrics.density && paint.fontMetrics.descent-paint.fontMetrics.ascent<=height.coerceAtLeast(18f)*metrics.density
                }
                var lo=0f;var hi=desired
                if(fits(desired)) lo=desired else repeat(16) { val mid=(lo+hi)/2;if(fits(mid)) lo=mid else hi=mid }
                v.setTextViewTextSize(id,android.util.TypedValue.COMPLEX_UNIT_SP,lo)
            }
            val header=if(expanded) 24f else if(wide) 20f else statusSp*fontScale*1.3f
            val row=if(expanded) 56f else 48f
            val available=heightDp-2f*vertical-(if(wide) 48f else 0f)-(if(actions) row+(if(expanded) 8f else 4f) else 0f)-header
            val timerWidth=when { mini -> widthDp-2f*side-56f;shortWide -> widthDp-2f*side-174f;showDay -> widthDp*0.6f-2f*side;else -> widthDp-2f*side }
            fit(R.id.wduration,mainMillis,if(wide) 42f else 30f,timerWidth,available)
            if(showDay) fit(R.id.wstage,analytics.workMillis,12f,widthDp*0.4f-28f*fontScale,24f*fontScale)
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
