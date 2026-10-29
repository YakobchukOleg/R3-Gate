package com.r3gate.app

import android.content.Context
import android.graphics.*
import android.view.View
import java.util.Locale

class TimeChart(context:Context,private val result:TimeAnalytics.Result,private val ring:Boolean):View(context) {
    companion object {
        val colors=intArrayOf(0xFF55AD83.toInt(),0xFFE9B2B7.toInt(),0xFF9EA9AF.toInt(),0xFFD9CDB9.toInt())
        val names=listOf("Na obiekcie","Zakupy","Przejazdy","Nieprzypisane")
    }
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    init { contentDescription=if(ring) "Podział czasu pracy. Wartości w legendzie poniżej." else "Statystyka czasu pracy według godzin. Wartości w tabeli poniżej." }
    override fun onDraw(c:Canvas) {
        super.onDraw(c)
        val density=resources.displayMetrics.density
        paint.typeface=resources.getFont(R.font.clash_grotesk)
        val w=width.toFloat(); val h=height.toFloat()
        if(ring) {
            val sum=result.totals.sum(); val radius=minOf(w,h)*0.37f; val cx=w/2; val cy=h/2
            val rect=RectF(cx-radius,cy-radius,cx+radius,cy+radius)
            paint.style=Paint.Style.STROKE; paint.strokeWidth=radius*0.27f
            paint.color=0xFFECEFF1.toInt(); c.drawOval(rect,paint)
            var start=-90f
            if(sum>0) result.totals.forEachIndexed { i,n -> val angle=n.toFloat()/sum*360f; paint.color=colors[i]; c.drawArc(rect,start,angle,false,paint); start+=angle }
            paint.style=Paint.Style.FILL; paint.textAlign=Paint.Align.CENTER; paint.textSize=24*density; paint.color=0xFF37474F.toInt()
            val minutes=sum/60000
            c.drawText("%02d:%02d".format(Locale.ROOT,minutes/60,minutes%60),cx,cy+8*density,paint)
        } else {
            val left=40*density; val bottom=h-30*density; val chartHeight=h-50*density
            val max=result.hours.maxOf { it.sum() }.coerceAtLeast(3600000)
            paint.color=0xFF607D8B.toInt(); paint.textAlign=Paint.Align.RIGHT; paint.textSize=10*density
            c.drawText("%.1f h".format(Locale.ROOT,max/3600000.0),left-4*density,20*density,paint)
            c.drawText("0",left-4*density,bottom,paint)
            val step=(w-left-8*density)/24
            result.hours.forEachIndexed { hour,values ->
                var y=bottom
                values.forEachIndexed { i,n -> val bh=n.toFloat()/max*chartHeight; paint.color=colors[i]; c.drawRect(left+hour*step,y-bh,left+(hour+1)*step-2*density,y,paint); y-=bh }
                if(hour%3==0) { paint.color=0xFF37474F.toInt(); paint.textAlign=Paint.Align.CENTER; c.drawText(hour.toString(),left+(hour+0.5f)*step,bottom+18*density,paint) }
            }
        }
    }
}
