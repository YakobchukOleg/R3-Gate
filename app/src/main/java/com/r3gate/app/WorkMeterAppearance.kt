package com.r3gate.app

import android.content.Context

object WorkMeterAppearance {
    fun theme(context: Context): WorkMeterTheme =
        WorkMeterTheme.fromKey(context.getSharedPreferences("r3_gate",Context.MODE_PRIVATE)
            .getString(WorkMeterTheme.PREF_KEY,null))

    fun dialogStyle(theme: WorkMeterTheme): Int = when (theme) {
        WorkMeterTheme.SLATE_SAGE -> R.style.WorkMeterDialogTheme
        WorkMeterTheme.NORDIC_BLUE -> R.style.WorkMeterNordicDialog
        WorkMeterTheme.GRAPHITE_DARK -> R.style.WorkMeterGraphiteDialog
    }
}
