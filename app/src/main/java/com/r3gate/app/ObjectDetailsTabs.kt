package com.r3gate.app

/** Presentation-only tabs for Obiekty. No synthetic service records. */
object ObjectDetailsTabs {
    enum class Tab(val label: String) {
        OVERVIEW("Przegląd"),
        PHOTOS("Zdjęcia"),
        MATERIALS("Materiały"),
        SERVICE("Serwis"),
        TECHNICAL("Techniczne")
    }

    data class Counts(val photos: Int = 0, val materials: Int = 0, val notes: Int = 0) {
        init {
            require(photos >= 0 && materials >= 0 && notes >= 0)
        }
    }

    data class Section(
        val heading: String,
        val description: String,
        val action: String?
    )

    fun section(tab: Tab, counts: Counts): Section = when (tab) {
        Tab.OVERVIEW -> Section("Przegląd", "Historia pracy i ważne informacje", null)
        Tab.PHOTOS -> Section("Zdjęcia", "${counts.photos} zdjęć przypisanych do obiektu", "Otwórz zdjęcia")
        Tab.MATERIALS -> Section("Materiały", "${counts.materials} wpisów materiałowych", "Otwórz materiały")
        Tab.SERVICE -> Section(
            "Serwis",
            "Osobna lista zgłoszeń serwisowych nie jest jeszcze dostępna. Informacje można zapisywać w notatkach obiektu.",
            "Otwórz notatki"
        )
        Tab.TECHNICAL -> Section("Techniczne", "Dane obiektu i lokalizacja", "Otwórz w Google Maps")
    }
}
