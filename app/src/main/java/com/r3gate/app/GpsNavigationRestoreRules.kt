package com.r3gate.app

/** Screens that must remain available after an Android activity recreation. */
object GpsNavigationRestoreRules {
    fun isGpsScreen(screen:String):Boolean = screen in setOf(
        "gps","gpsDiagnostics","gpsPlaceKinds","gpsPay","gpsCategories"
    )

    fun preserveViewOnRestart(screen:String):Boolean =
        isGpsScreen(screen) || screen=="sectionSettings"

    fun isSupportedSection(section:String):Boolean =
        section in setOf("Mój czas","Obiekty","Zdjęcia","Materiały","Grafiki","Portfel")
}
