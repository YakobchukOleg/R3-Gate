package com.r3gate.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ObjectCountLabelTest {
    @Test fun usesPolishSingularAndPluralForms() {
        val cases = mapOf(
            0 to "0 obiektów", 1 to "1 obiekt", 2 to "2 obiekty",
            4 to "4 obiekty", 5 to "5 obiektów", 12 to "12 obiektów",
            14 to "14 obiektów", 21 to "21 obiektów",
            22 to "22 obiekty", 25 to "25 obiektów", 101 to "101 obiektów"
        )
        for ((count, prefix) in cases)
            assertEquals(prefix + " · aktywna lista", ObjectCountLabel.format(count, false))
    }

    @Test fun archiveCountUsesArchiveSuffix() {
        assertEquals("1 obiekt · archiwum", ObjectCountLabel.format(1, true))
        assertEquals("3 obiekty · archiwum", ObjectCountLabel.format(3, true))
    }

    @Test fun rejectsNegativeObjectCount() {
        assertThrows(IllegalArgumentException::class.java) { ObjectCountLabel.format(-1, false) }
    }
}
