package com.r3gate.app

internal enum class WidgetLayoutProfile(val minWidthDp: Int, val minHeightDp: Int) {
    MINI(110, 68),
    COMPACT(110, 112),
    STANDARD(220, 144),
    EXPANDED(220, 208);

    companion object {
        fun forBounds(widthDp: Int, heightDp: Int): WidgetLayoutProfile =
            entries.lastOrNull { widthDp >= it.minWidthDp && heightDp >= it.minHeightDp } ?: MINI
    }
}
