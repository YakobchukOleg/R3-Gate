package com.r3gate.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkMeterThemeTest {
    @Test
    fun storedKeysResolveAndUnknownKeysUseDefault() {
        WorkMeterTheme.entries.forEach { assertEquals(it, WorkMeterTheme.fromKey(it.key)) }
        assertEquals(WorkMeterTheme.SLATE_SAGE, WorkMeterTheme.fromKey(null))
        assertEquals(WorkMeterTheme.SLATE_SAGE, WorkMeterTheme.fromKey("unknown"))
    }

    @Test
    fun textMeetsMinimumContrastOnItsAssignedSurfaces() {
        WorkMeterTheme.entries.forEach { theme ->
            val p = theme.palette
            val pairs = listOf(
                p.onHeader to p.header,
                p.onPrimary to p.primary,
                p.onError to WorkMeterTheme.ERROR,
                p.textPrimary to p.background,
                p.textPrimary to p.surface,
                p.secondaryText to p.background,
                p.secondaryText to p.surface,
                p.accentText to p.background,
                p.accentText to p.surface
            )
            pairs.forEach { (foreground, background) ->
                val ratio = WorkMeterTheme.contrast(foreground, background)
                assertTrue("${theme.title}: ratio $ratio", ratio >= WorkMeterTheme.MIN_CONTRAST)
            }
        }
    }
}
