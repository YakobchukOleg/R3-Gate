package com.r3gate.app

import org.junit.Assert.*
import org.junit.Test

class ObjectFormUiModelTest {
    @Test fun formSectionsContainFieldsInExpectedOrder() {
        assertEquals(listOf("Lokalizacja", "Informacje o obiekcie"),
            ObjectFormUiModel.sections.map { it.title })
        assertEquals(listOf("city", "Adres", "organization", "notes", "maps"),
            ObjectFormUiModel.sections.flatMap { it.fields }.map { it.key })
    }

    @Test fun cityAndAddressRequiredButOtherFieldsOptional() {
        val fields = ObjectFormUiModel.sections.flatMap { it.fields }
        assertEquals(listOf("city", "Adres"), fields.filter { it.required }.map { it.key })
        assertTrue(fields.filterNot { it.required }.none { it.caption.endsWith("*") })
    }

    @Test fun onlyNotesIsMultiline() {
        assertEquals(listOf("notes"),
            ObjectFormUiModel.sections.flatMap { it.fields }.filter { it.multiline }.map { it.key })
    }

    @Test fun editAndNewFormHaveDistinctPolishHeadings() {
        assertEquals("Nowy obiekt", ObjectFormUiModel.title(false))
        assertEquals("Edytuj obiekt", ObjectFormUiModel.title(true))
    }

    @Test fun tagHintDistinguishesGeneratedFromExistingTag() {
        assertTrue(ObjectFormUiModel.tagHint("").contains("automatycznie"))
        assertEquals("TAG: TCZ-10 (stały)", ObjectFormUiModel.tagHint(" TCZ-10 "))
    }

    @Test fun hintsMatchExistingValidationWithoutBusinessChanges() {
        assertEquals("* Pola wymagane", ObjectFormUiModel.requiredHint())
        assertTrue(ObjectFormUiModel.mapHint().contains("https://"))
        assertTrue(ObjectFormUiModel.footer(true).contains("Historia pracy"))
    }
}
