package com.r3gate.app

import org.junit.Assert.*
import org.junit.Test

class MaterialDraftValidationTest {
    @Test fun restoredDraftNormalizesPolishDecimalWithoutChangingUnit() {
        assertEquals(MaterialStock.Draft("Kabel", "12.5", "m"),
            MaterialStock.validateDraft("  Kabel  ", "12,50", "m"))
    }

    @Test fun malformedBatchLineCannotBecomeAValidDraft() {
        assertNull(MaterialStock.validateDraft(" ", "12", "m"))
        assertNull(MaterialStock.validateDraft("Kabel", "-1", "m"))
        assertNull(MaterialStock.validateDraft("Uchwyt", "2.5", "szt."))
        assertNull(MaterialStock.validateDraft("Kabel", "12", "unknown"))
    }

    @Test fun extremeDecimalCannotExpandIntoAnUnboundedString() {
        assertNull(MaterialStock.validateDraft("Kabel", "1e2147483647", "m"))
        assertNull(MaterialStock.validateDraft("Kabel", "1e-2147483647", "m"))
        assertNull(MaterialStock.validateDraft("Kabel", "9".repeat(257), "m"))
        assertEquals("1000", MaterialStock.validateDraft("Kabel", "1e3", "m")?.quantity)
    }

    @Test fun corruptStoredPeriodDoesNotCrashRecordFiltering() {
        assertFalse(DomainRules.inRange("2026-11-04", "bad", ""))
        assertFalse(DomainRules.inRange("2026-11-04", "", "bad"))
        assertTrue(DomainRules.inRange("2026-11-04", "2026-11-04", "2026-11-04"))
    }
}
