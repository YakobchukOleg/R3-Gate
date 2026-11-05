package com.r3gate.app

internal enum class WorkMeterDestination(val icon: String, val label: String) {
    START("⌂", "Start"),
    OBJECTS("▦", "Obiekty"),
    TIME("◷", "Mój czas"),
    FINANCE("¤", "Finanse"),
    MORE("☷", "Więcej");

    companion object {
        fun forScreen(screen: String): WorkMeterDestination = when {
            screen == "home" -> START
            screen == "objects" || screen.startsWith("object:") -> OBJECTS
            screen.startsWith("list:") && screen.split(":").getOrNull(2)?.isNotBlank() == true -> OBJECTS
            screen == "report" || screen == "charts" || screen.startsWith("shift:") -> TIME
            screen in listOf("wallet", "expenses", "payment") -> FINANCE
            else -> MORE
        }
    }
}
