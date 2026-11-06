package com.r3gate.app

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Standalone presentation widget. Receives an already-resolved object summary;
 * never loads records, changes archive state, or accesses SharedPreferences.
 * The hosting screen owns navigation and all actions.
 */
class ObjectUiCard(context: Context) : LinearLayout(context) {
    private val palette=WorkMeterAppearance.theme(context).palette
    private val density = resources.displayMetrics.density
    private fun dp(value: Int) = (value * density + 0.5f).toInt()

    private val titleView = TextView(context).apply {
        setTextColor(palette.textPrimary)
        textSize = 15f
        setTypeface(typeface, Typeface.BOLD)
        maxLines = 3
    }
    private val statusView = TextView(context).apply {
        setTextColor(palette.secondaryText)
        textSize = 12f
        gravity = Gravity.CENTER_VERTICAL
    }

    init {
        orientation = VERTICAL
        isClickable = true
        isFocusable = true
        minimumHeight = dp(64)
        setPadding(dp(14), dp(12), dp(14), dp(12))
        background = GradientDrawable().apply {
            setColor(palette.surface)
            cornerRadius = dp(16).toFloat()
            setStroke(dp(1), palette.border)
        }
        addView(titleView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(statusView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(5)
        })
    }

    fun bind(
        tag: String,
        organization: String,
        city: String,
        address: String,
        archived: Boolean,
        onOpen: () -> Unit
    ) {
        val title = ObjectUiPresentation.title(tag, organization, city, address)
        val status = ObjectUiPresentation.status(archived)
        titleView.text = title
        statusView.text = status
        contentDescription = ObjectUiPresentation.accessibilityLabel(title, archived)
        setOnClickListener { onOpen() }
    }
}
