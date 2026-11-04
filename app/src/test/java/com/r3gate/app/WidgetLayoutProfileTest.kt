package com.r3gate.app

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetLayoutProfileTest {
    @Test
    fun selectsProfilesFromBothDimensions() {
        assertEquals(WidgetLayoutProfile.MINI, WidgetLayoutProfile.forBounds(144, 68))
        assertEquals(WidgetLayoutProfile.COMPACT, WidgetLayoutProfile.forBounds(144, 144))
        assertEquals(WidgetLayoutProfile.STANDARD, WidgetLayoutProfile.forBounds(296, 144))
        assertEquals(WidgetLayoutProfile.EXPANDED, WidgetLayoutProfile.forBounds(296, 220))
    }

    @Test
    fun retainsPrimaryControlsInWideShortLayouts() {
        assertEquals(WidgetLayoutProfile.WIDE_SHORT, WidgetLayoutProfile.forBounds(300, 111))
        assertEquals(WidgetLayoutProfile.MINI, WidgetLayoutProfile.forBounds(300, 63))
        assertEquals(WidgetLayoutProfile.COMPACT, WidgetLayoutProfile.forBounds(219, 208))
        assertEquals(WidgetLayoutProfile.WIDE_SHORT, WidgetLayoutProfile.forBounds(220, 127))
        assertEquals(WidgetLayoutProfile.STANDARD, WidgetLayoutProfile.forBounds(220, 128))
        assertEquals(WidgetLayoutProfile.STANDARD, WidgetLayoutProfile.forBounds(220, 207))
        assertEquals(WidgetLayoutProfile.EXPANDED, WidgetLayoutProfile.forBounds(220, 208))
    }
}
