package com.r3gate.app

import java.time.LocalDate
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PhotoDriveLayoutTest {
    private val date = LocalDate.of(2026, 11, 6)
    @Test fun sitePhotosUseOnlyYearAndTagWithAddress() {
        val site = JSONObject().put("TAG", "TCZ-01").put("city", "Tczew")
            .put("Adres", "Rokitki 12").put("organization", "Organization")
        assertEquals(listOf("2026", "TCZ-01 Tczew Rokitki 12"), PhotoDriveLayout.path(date, site, ""))
    }
    @Test fun differentDaysAndMonthsShareTheSameSiteFolder() {
        val site = JSONObject().put("TAG", "TCZ-01").put("Adres", "Rokitki 12")
        assertEquals(PhotoDriveLayout.path(date, site, ""), PhotoDriveLayout.path(LocalDate.of(2026, 1, 1), site, ""))
        assertEquals("2027", PhotoDriveLayout.path(LocalDate.of(2027, 1, 1), site, "").first())
    }
    @Test fun splitAddressFieldsAreSupportedWhenLegacyAddressIsBlank() {
        val site = JSONObject().put("TAG", " TCZ-01 ").put("city", " Tczew ")
            .put("street", "Lipowa").put("building", "12").put("postcode", "83-110")
        assertEquals("TCZ-01 Tczew Lipowa 12 83-110", PhotoDriveLayout.path(date, site, "").last())
    }
    @Test fun absentAddressKeepsTagWithoutOrganizationOrNullText() {
        val site = JSONObject().put("TAG", "TCZ-01").put("Adres", JSONObject.NULL).put("city", JSONObject.NULL)
            .put("organization", "Organization")
        assertEquals("TCZ-01", PhotoDriveLayout.path(date, site, "").last())
    }
    @Test fun unassignedPhotosAndCarStockHaveSeparateYearFolders() {
        assertEquals(listOf("2026", "Bez obiektu"), PhotoDriveLayout.path(date, null, ""))
        assertEquals(listOf("2026", "W samochodzie"), PhotoDriveLayout.path(date, null, "car"))
    }
    @Test fun entirelyBlankSiteDoesNotCreateAnEmptyFolder() {
        assertEquals(listOf("2026", "Bez obiektu"), PhotoDriveLayout.path(date, JSONObject(), ""))
        assertEquals("Rokitki 12", PhotoDriveLayout.path(date, JSONObject().put("Adres", "Rokitki 12"), "").last())
    }
}
