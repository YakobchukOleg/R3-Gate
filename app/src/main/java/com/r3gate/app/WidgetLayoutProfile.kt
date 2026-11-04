package com.r3gate.app

internal enum class WidgetLayoutProfile {
    MINI, COMPACT, STANDARD, EXPANDED;

    companion object {
        fun forBounds(widthDp: Int, heightDp: Int): WidgetLayoutProfile = when {
            heightDp < 112 -> MINI
            widthDp < 220 -> COMPACT
            heightDp < 208 -> STANDARD
            else -> EXPANDED
        }
    }
}
