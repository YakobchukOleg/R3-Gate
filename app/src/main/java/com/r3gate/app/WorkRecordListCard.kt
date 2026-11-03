package com.r3gate.app

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

/** Stateless clickable record card. Navigation and persistence remain with the host. */
class WorkRecordListCard(
    context: Context,
    model: WorkRecordListUi.Card,
    onOpen: () -> Unit
) : LinearLayout(context) {
    private fun dp(n: Int) = (n * resources.displayMetrics.density + 0.5f).toInt()

    init {
        orientation = VERTICAL
        val pad=dp(14)
        setPadding(pad,pad,pad,pad)
        background = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius=dp(16).toFloat()
        }
        contentDescription=model.accessibilityLabel
        isClickable=true
        isFocusable=true
        setOnClickListener { onOpen() }
        val top=LinearLayout(context).apply {
            orientation=HORIZONTAL
            gravity=Gravity.CENTER_VERTICAL
        }
        top.addView(TextView(context).apply {
            text=model.title
            textSize=16f
            setTextColor(Color.rgb(35,57,67))
            setTypeface(null,Typeface.BOLD)
        },LayoutParams(0,LayoutParams.WRAP_CONTENT,1f))
        top.addView(TextView(context).apply {
            text=model.badge
            textSize=12f
            setTextColor(Color.rgb(37,136,109))
            setPadding(dp(9),dp(5),dp(9),dp(5))
            background=GradientDrawable().apply {
                setColor(Color.rgb(237,248,244))
                cornerRadius=dp(10).toFloat()
            }
        })
        addView(top)
        if (model.subtitle.isNotBlank()) {
            addView(TextView(context).apply {
                text=model.subtitle
                textSize=13f
                setTextColor(Color.rgb(114,130,139))
                setPadding(0,dp(9),0,0)
            })
        }
    }
}
