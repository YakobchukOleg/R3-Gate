package com.r3gate.app

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.widget.Button
import kotlin.math.roundToInt

internal enum class WorkMeterButtonRole {
    PRIMARY, SECONDARY, DESTRUCTIVE, TEXT
}

internal fun workMeterButton(
    context: Context,
    label: String,
    role: WorkMeterButtonRole,
    onClick: () -> Unit
): Button {
    return Button(context).apply {
        text = label
        styleWorkMeterButton(this, role)
        setOnClickListener {
            WorkMeterDiagnostics.record(context,"UI_BUTTON",org.json.JSONObject().put("label",label.take(120)))
            onClick()
        }
    }
}

internal fun styleWorkMeterButton(button: Button, role: WorkMeterButtonRole) {
    val context = button.context
    val palette = WorkMeterAppearance.theme(context).palette
    val fill = when (role) {
        WorkMeterButtonRole.PRIMARY -> palette.primary
        WorkMeterButtonRole.SECONDARY, WorkMeterButtonRole.TEXT -> android.graphics.Color.TRANSPARENT
        WorkMeterButtonRole.DESTRUCTIVE -> WorkMeterTheme.ERROR
    }
    val ink = when (role) {
        WorkMeterButtonRole.PRIMARY -> palette.onPrimary
        WorkMeterButtonRole.SECONDARY, WorkMeterButtonRole.TEXT -> palette.textPrimary
        WorkMeterButtonRole.DESTRUCTIVE -> palette.onError
    }
    val density = context.resources.displayMetrics.density
    fun dp(value: Int) = (value * density).roundToInt()

    val shape = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(12).toFloat()
        if (role == WorkMeterButtonRole.SECONDARY) setStroke(dp(1), palette.border)
    }
    val ripple = ColorStateList.valueOf((ink and 0x00FFFFFF) or 0x22000000)
    button.apply {
        textSize = 14f
        isElegantTextHeight = false
        includeFontPadding = false
        isAllCaps = false
        minHeight = dp(48)
        minimumHeight = dp(48)
        minimumWidth = dp(48)
        setTextColor(ink)
        background = RippleDrawable(ripple, shape, null)
        backgroundTintList = null
        elevation = 0f
        stateListAnimator = null
        setPadding(dp(12), dp(6), dp(12), dp(6))
    }
}
