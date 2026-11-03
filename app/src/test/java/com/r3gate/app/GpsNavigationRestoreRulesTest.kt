package com.r3gate.app

import org.junit.Assert.*
import org.junit.Test

class GpsNavigationRestoreRulesTest {
    private val rules=GpsNavigationRestoreRules

    @Test fun gpsSettingsIsRestorable() {
        assertTrue(rules.isGpsScreen("gps"))
    }
    @Test fun gpsDiagnosticsIsRestorable() {
        assertTrue(rules.isGpsScreen("gpsDiagnostics"))
    }
    @Test fun gpsGeofenceTypesAreRestorable() {
        assertTrue(rules.isGpsScreen("gpsPlaceKinds"))
    }
    @Test fun gpsPaySettingsAreRestorable() {
        assertTrue(rules.isGpsScreen("gpsPay"))
    }
    @Test fun gpsTripCategoriesAreRestorable() {
        assertTrue(rules.isGpsScreen("gpsCategories"))
    }
    @Test fun homeIsNotAGpsPage() {
        assertFalse(rules.isGpsScreen("home"))
    }
    @Test fun unrelatedSettingDoesNotMatchGpsPage() {
        assertFalse(rules.isGpsScreen("settings"))
    }
    @Test fun gpsPageIsNotLockedOnResume() {
        assertFalse(rules.lockOnRestart("gps"))
    }
    @Test fun nestedGpsDiagnosticsNotLockedOnResume() {
        assertFalse(rules.lockOnRestart("gpsDiagnostics"))
    }
    @Test fun editingAnObjectRemainsOpenOnResume() {
        assertFalse(rules.lockOnRestart("form"))
    }
    @Test fun walletMustBeLockedWhenReturning() {
        assertTrue(rules.lockOnRestart("wallet"))
    }
    @Test fun paymentFormMustBeLockedWhenReturning() {
        assertTrue(rules.lockOnRestart("payment"))
    }
    @Test fun expensesListMustBeLockedWhenReturning() {
        assertTrue(rules.lockOnRestart("expenses"))
    }
    @Test fun expenseDetailMustBeLockedWhenReturning() {
        assertTrue(rules.lockOnRestart("detail:expenses:abc"))
    }
    @Test fun objectSectionCanBeRestored() {
        assertTrue(rules.isSupportedSection("Obiekty"))
    }
    @Test fun gpsNotMistakenForObjectSection() {
        assertFalse(rules.isSupportedSection("gps"))
    }
}
