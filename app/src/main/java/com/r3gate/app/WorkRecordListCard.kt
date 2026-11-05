package com.r3gate.app

import android.content.Context
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

    private val palette = WorkMeterAppearance.theme(context).palette

    init {
        orientation = VERTICAL
        val pad=dp(14)
        setPadding(pad,pad,pad,pad)
        background = GradientDrawable().apply {
            setColor(palette.surface)
            cornerRadius=dp(16).toFloat()
        }
        contentDescription=model.accessibilityLabel
        isClickable=true
        isFocusable=true
        setOnClickListener { onOpen() }
        val top=LinearLayout(context).apply {
            orientation=VERTICAL
            gravity=Gravity.CENTER_VERTICAL
        }
        top.addView(TextView(context).apply {
            text=model.title
            textSize=16f
            setTextColor(palette.textPrimary)
            setTypeface(null,Typeface.BOLD)
        },LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT))
        top.addView(TextView(context).apply {
            text=model.badge
            textSize=12f
            maxLines=3
            ellipsize=android.text.TextUtils.TruncateAt.END
            setTextColor(palette.accentText)
            setPadding(dp(9),dp(5),dp(9),dp(5))
            background=GradientDrawable().apply {
                setColor(palette.background)
                cornerRadius=dp(10).toFloat()
            }
        })
        addView(top)
        if (model.subtitle.isNotBlank()) {
            addView(TextView(context).apply {
                text=model.subtitle
                textSize=13f
                setTextColor(palette.secondaryText)
                setPadding(0,dp(9),0,0)
            })
        }
    }
}
