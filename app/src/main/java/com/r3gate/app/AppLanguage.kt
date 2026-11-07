package com.r3gate.app

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import java.util.Locale

internal enum class AppLanguage(val tag: String, val nameRes: Int) {
    POLISH("pl", R.string.polish),
    RUSSIAN("ru", R.string.russian);

    val locale: Locale get() = Locale.forLanguageTag(tag)

    companion object {
        const val PREFERENCE = "appLanguage"

        fun selected(context: Context): AppLanguage {
            val tag = context.getSharedPreferences("r3_gate", Context.MODE_PRIVATE)
                .getString(PREFERENCE, POLISH.tag)
            return entries.firstOrNull { it.tag == tag } ?: POLISH
        }

        fun context(base: Context): Context {
            val locale = selected(base).locale
            val current = base.resources.configuration
            if (current.locales[0] == locale) return base
            val localized = Configuration(current).apply { setLocales(LocaleList(locale)) }
            return base.createConfigurationContext(localized)
        }
    }
}
