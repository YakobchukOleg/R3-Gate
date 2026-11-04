package com.r3gate.app

import kotlin.math.pow

enum class WorkMeterTheme(
    val key: String,
    val title: String,
    val palette: Palette
) {
    SLATE_SAGE("slate_sage", "Slate & Sage", Palette(
        0xFFF5F7F7.toInt(), 0xFFFFFFFF.toInt(), 0xFF293F49.toInt(), 0xFF32866E.toInt(),
        0xFF293F49.toInt(), 0xFF71868D.toInt(), 0xFFDEE7E6.toInt())),
    NORDIC_BLUE("nordic_blue", "Nordic Blue", Palette(
        0xFFF7F8FA.toInt(), 0xFFFFFFFF.toInt(), 0xFF1F2937.toInt(), 0xFF2563EB.toInt(),
        0xFF1F2937.toInt(), 0xFF718096.toInt(), 0xFFE2E8F0.toInt())),
    GRAPHITE_DARK("graphite_dark", "Graphite Dark", Palette(
        0xFF151D24.toInt(), 0xFF26343E.toInt(), 0xFF202D36.toInt(), 0xFF54C6A1.toInt(),
        0xFFFFFFFF.toInt(), 0xFFB9CAD0.toInt(), 0xFF40515C.toInt()));

    data class Palette(
        val background: Int, val surface: Int, val header: Int, val primary: Int,
        val textPrimary: Int, val textSecondary: Int, val border: Int
    ) {
        val onHeader get() = foreground(header)
        val onPrimary get() = foreground(primary)
        val onError get() = foreground(ERROR)
        val secondaryText get() = readableText(textSecondary, textPrimary)
        val accentText get() = readableText(primary, textPrimary)

        private fun readableText(preferred: Int, fallback: Int): Int =
            if (contrast(preferred, background) >= MIN_CONTRAST &&
                contrast(preferred, surface) >= MIN_CONTRAST) preferred else fallback
    }

    companion object {
        const val PREF_KEY = "appearance_theme"
        const val MIN_CONTRAST = 4.5
        val ON_SITE = 0xFF32866E.toInt()
        val TRAVEL = 0xFF98A7B0.toInt()
        val SHOPPING = 0xFFDBA6AD.toInt()
        val PERSONAL = 0xFFC9AF78.toInt()
        val ERROR = 0xFFB94C5A.toInt()

        fun fromKey(key: String?): WorkMeterTheme = entries.firstOrNull { it.key == key } ?: SLATE_SAGE

        fun contrast(first: Int, second: Int): Double {
            val a = luminance(first)
            val b = luminance(second)
            return (maxOf(a,b)+0.05)/(minOf(a,b)+0.05)
        }

        private fun foreground(fill: Int): Int {
            val white = 0xFFFFFFFF.toInt()
            val black = 0xFF000000.toInt()
            return if (contrast(white,fill) >= contrast(black,fill)) white else black
        }

        private fun luminance(color: Int): Double {
            fun channel(shift: Int): Double {
                val value = ((color shr shift) and 255) / 255.0
                return if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
            }
            return 0.2126*channel(16) + 0.7152*channel(8) + 0.0722*channel(0)
        }
    }
}
