package com.r3gate.app
import org.junit.Assert.*
import org.junit.Test
class WidgetLayoutProfileTest {
    @Test fun compactAlwaysKeepsPrimaryAndUsesActualDp() {
        assertEquals(WidgetLayoutProfile.COMPACT,WidgetLayoutProfile.forBounds(144,68))
        assertEquals(WidgetLayoutProfile.COMPACT,WidgetLayoutProfile.forBounds(249,109))
    }
    @Test fun wideLowUsesMedium() {
        assertEquals(WidgetLayoutProfile.MEDIUM,WidgetLayoutProfile.forBounds(250,109))
        assertEquals(WidgetLayoutProfile.MEDIUM,WidgetLayoutProfile.forBounds(300,68))
    }
    @Test fun narrowAndWideTallUseLarge() {
        listOf(144 to 144,296 to 144,296 to 220,300 to 110).forEach { (w,h) ->
            assertEquals(WidgetLayoutProfile.LARGE,WidgetLayoutProfile.forBounds(w,h))
        }
    }
}
