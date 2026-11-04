package com.r3gate.app

internal enum class TimeChartCategory(val label: String, val color: Int) {
    ON_SITE("Na obiekcie", WorkMeterTheme.ON_SITE),
    SHOPPING("Zakupy", WorkMeterTheme.SHOPPING),
    TRAVEL("Przejazdy", WorkMeterTheme.TRAVEL),
    UNASSIGNED("Nieprzypisane", 0xFF71868D.toInt()),
    PERSONAL("Prywatnie", WorkMeterTheme.PERSONAL);

    companion object {
        fun from(kind: String, hasObject: Boolean): TimeChartCategory = when (kind) {
            "Obiekt" -> if (hasObject) ON_SITE else UNASSIGNED
            "Zakupy" -> SHOPPING
            "Przejazd" -> TRAVEL
            "Prywatnie" -> PERSONAL
            else -> UNASSIGNED
        }
    }
}
