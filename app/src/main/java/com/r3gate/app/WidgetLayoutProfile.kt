package com.r3gate.app

internal enum class WidgetLayoutProfile(val minWidthDp: Int, val minHeightDp: Int, val displayName: String) {
    COMPACT(110, 68, "Mały · 2×1"),
    MEDIUM(250, 68, "Poziomy · 4×1"),
    LARGE(110, 110, "Duży · 2×2 / 4×2 / 4×3");
    companion object {
        // Compatibility for saved preview callers; there are only three rendering classes.
        val MINI get() = COMPACT
        val WIDE_SHORT get() = MEDIUM
        val STANDARD get() = LARGE
        val EXPANDED get() = LARGE
        fun forBounds(widthDp: Int, heightDp: Int): WidgetLayoutProfile =
            if(heightDp>=110) LARGE else if(widthDp>=250) MEDIUM else COMPACT
    }
}
