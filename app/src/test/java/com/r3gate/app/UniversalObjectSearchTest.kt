package com.r3gate.app
import org.junit.Assert.*
import org.junit.Test
class UniversalObjectSearchTest {
    private val fields = listOf("TCZ-01", "R3 Gate", "Tczew", "Jana Stanisławskiego 5, 83-110")
    @Test fun tokensMatchAcrossFieldsInAnyOrder() {
        listOf("Tczew R3", "5 gate tcz", "stanislawskiego jana", "TCZ-01", "83-110", "r3 gat", "  ").forEach {
            assertTrue(it, ObjectSearch.matchesFields(fields, it))
        }
        assertFalse(ObjectSearch.matchesFields(fields, "Tczew missing"))
    }
    @Test fun polishAndCaseNormalize() {
        assertTrue(ObjectSearch.matchesFields(listOf("Śliwiny", "Gdańsk", "Łódź"), "LODZ sli gdan"))
    }
    @Test fun archivesAreExcludedAndDuplicateNamesStayDistinct() {
        val a = ObjectListUiModel.Item("a", "TCZ-01", "R3 Gate", "Tczew", "Jana 5", false)
        val b = a.copy(id="b", tag="GDA-02", city="Gdańsk")
        assertEquals(listOf("a"), ObjectListUiModel.display(listOf(a,b,a.copy(id="c",archived=true)),false,"Tczew R3").rows.map { it.id })
        assertEquals(2,ObjectListUiModel.display(listOf(a,b),false,"").rows.size)
    }
}
