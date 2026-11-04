package com.r3gate.app

internal enum class WidgetLayoutProfile(val minWidthDp: Int, val minHeightDp: Int, val displayName: String) {
    MINI(110, 68, "Mini"),
    COMPACT(110, 112, "Compact"),
    WIDE_SHORT(220, 64, "Wide short"),
    STANDARD(220, 128, "Standard"),
    EXPANDED(220, 208, "Expanded");

    companion object {
        fun forBounds(widthDp: Int, heightDp: Int): WidgetLayoutProfile =
            entries.lastOrNull { widthDp >= it.minWidthDp && heightDp >= it.minHeightDp } ?: MINI
    }
}
