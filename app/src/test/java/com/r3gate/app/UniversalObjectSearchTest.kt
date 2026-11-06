package com.r3gate.app
import org.junit.Assert.*
import org.junit.Test
class UniversalObjectSearchTest {
    @Test fun relevancePrecedesRecencyAndAdditionalAddressFieldsAreSearchable() {
        val exact=ObjectListUiModel.Item("exact","GDA-10","Gate","Gdańsk","",false,lastUsed=1)
        val partial=exact.copy(id="partial",tag="GDA-01",organization="My Gate",lastUsed=100)
        assertEquals(listOf("exact","partial"),ObjectListUiModel.display(listOf(partial,exact),false,"gate").rows.map { it.id })
        assertEquals(listOf("partial","exact"),ObjectListUiModel.display(listOf(exact,partial),false,"").rows.map { it.id })
        val address=exact.copy(searchFields=listOf("Łąkowa","23B","80-001"))
        assertEquals("exact",ObjectListUiModel.display(listOf(address),false,"lakowa 23b 80-001").rows.single().id)
        assertTrue(ObjectListUiModel.display(listOf(address.copy(archived=true)),false,"lakowa").rows.isEmpty())
    }
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
