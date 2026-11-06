package com.r3gate.app

/** Navigation rules for returning to WorkMeter after Android backgrounds the UI. */
object GpsNavigationRestoreRules {
    private val gpsScreens=setOf("gps","gpsDiagnostics","gpsPlaceKinds","gpsPay","gpsCategories")
    fun isGpsScreen(screen:String):Boolean = screen in gpsScreens

    /** Financial details must re-lock; GPS and other non-sensitive pages remain open. */
    fun lockOnRestart(screen:String):Boolean =
        screen=="walletSettings" || screen=="wallet" || screen=="payment" || screen=="expenses" ||
        screen.startsWith("detail:expenses:")

    fun isSupportedSection(section:String):Boolean =
        section in setOf("Mój czas","Obiekty","Zdjęcia","Materiały","Grafiki","Portfel")
}
