package com.r3gate.app

import android.content.Context
import android.content.SharedPreferences

internal class WidgetAppearanceStore(context: Context) {
    private val prefs = context.getSharedPreferences("widget_appearance", Context.MODE_PRIVATE)

    fun read(id: Int): WidgetAppearance = WidgetAppearance(
        theme = prefs.getString("$id.theme", null)?.let(WorkMeterTheme::fromKey),
        transparency = prefs.getInt("$id.transparency", -1).takeIf { it >= 0 },
        showObject = prefs.getBoolean("$id.object", true),
        showActions = prefs.getBoolean("$id.actions", true),
        showGps = prefs.getBoolean("$id.gps", true)
    )

    fun save(id: Int, appearance: WidgetAppearance) {
        prefs.edit().write(id, appearance).apply()
    }

    fun delete(ids: IntArray) {
        val editor = prefs.edit()
        ids.forEach { editor.clearAppearance(it) }
        editor.apply()
    }

    fun restore(oldIds: IntArray, newIds: IntArray) {
        val mappings = oldIds.zip(newIds).filter { (old, new) -> old != new }
        val values = mappings.map { (old, new) -> new to read(old) }
        val editor = prefs.edit()
        mappings.forEach { (old, _) -> editor.clearAppearance(old) }
        values.forEach { (new, appearance) -> editor.write(new, appearance) }
        editor.apply()
    }

    private fun SharedPreferences.Editor.write(id: Int, appearance: WidgetAppearance): SharedPreferences.Editor =
        putString("$id.theme", appearance.theme?.key)
            .putInt("$id.transparency", appearance.transparency?.coerceIn(0, 100) ?: -1)
            .putBoolean("$id.object", appearance.showObject)
            .putBoolean("$id.actions", appearance.showActions)
            .putBoolean("$id.gps", appearance.showGps)

    private fun SharedPreferences.Editor.clearAppearance(id: Int) {
        remove("$id.theme").remove("$id.transparency").remove("$id.object").remove("$id.actions").remove("$id.gps")
    }
}
