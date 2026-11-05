package com.r3gate.app

internal data class WidgetAppearance(
    val theme: WorkMeterTheme? = null,
    val transparency: Int? = null,
    val showObject: Boolean = true,
    val showActions: Boolean = true,
    val showGps: Boolean = true
) {
    fun backgroundAlpha(defaultTransparency: Int): Int =
        255 * (100 - (transparency ?: defaultTransparency).coerceIn(0, 100)) / 100
}
