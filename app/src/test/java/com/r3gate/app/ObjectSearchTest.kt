package com.r3gate.app
import org.junit.Assert.*
import org.junit.Test
class ObjectSearchTest {
    @Test fun searchesCityWithoutPolishDiacriticsOrCase() {
        assertTrue(ObjectSearch.matches("Łódź"," LODZ "))
        assertTrue(ObjectSearch.matches("Gdańsk","gdansk"))
        assertFalse(ObjectSearch.matches("Tczew","lodz"))
        assertTrue(ObjectSearch.matches("Tczew",""))
    }
    @Test fun tagOrderUsesNumericSuffix() {
        assertEquals(listOf("GDA-01","KOP-02","KOP-10","KOP-100"),listOf("KOP-100","KOP-10","GDA-01","KOP-02").sortedWith(Comparator(ObjectSearch::compareTags)))
    }
}
