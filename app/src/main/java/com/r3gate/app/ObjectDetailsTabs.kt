package com.r3gate.app

/** Presentation-only tabs for Obiekty. No synthetic task or service records. */
object ObjectDetailsTabs {
    enum class Tab(val label: String) {
        TODAY("Dzisiaj"),
        TASKS("Zadania"),
        PHOTOS("Zdjęcia"),
        MATERIALS("Materiały"),
        HISTORY("Historia")
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
        Tab.TODAY -> Section("Dzisiaj", "Informacje i dane techniczne obiektu", null)
        Tab.PHOTOS -> Section("Zdjęcia", "${counts.photos} zdjęć przypisanych do obiektu", "Otwórz zdjęcia")
        Tab.MATERIALS -> Section("Materiały", "${counts.materials} wpisów materiałowych", "Otwórz materiały")
        Tab.TASKS -> Section(
            "Zadania",
            "Lista zadań i zgłoszeń nie jest jeszcze dostępna. Ustalenia można zapisywać w notatkach obiektu.",
            "Otwórz notatki"
        )
        Tab.HISTORY -> Section("Historia", "Zapisany czas pracy na obiekcie", null)
    }
}
