package com.r3gate.app

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetAppearanceTest {
    @Test fun inheritedTransparencyTracksTheAppDefault() {
        val inherited = WidgetAppearance()
        assertEquals(255, inherited.backgroundAlpha(0))
        assertEquals(191, inherited.backgroundAlpha(25))
        assertEquals(127, inherited.backgroundAlpha(50))
        assertEquals(63, inherited.backgroundAlpha(75))
        assertEquals(0, inherited.backgroundAlpha(100))
    }

    @Test fun instanceTransparencyOverridesAndClamps() {
        assertEquals(255, WidgetAppearance(transparency = 0).backgroundAlpha(100))
        assertEquals(127, WidgetAppearance(transparency = 50).backgroundAlpha(0))
        assertEquals(0, WidgetAppearance(transparency = 110).backgroundAlpha(0))
    }
}
