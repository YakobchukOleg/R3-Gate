package com.r3gate.app

/** UI-only labels for image previews. Never infer that a missing image was uploaded. */
object PhotoPreviewUi {
    data class State(
        val heading: String,
        val accessibilityLabel: String,
        val message: String?
    )

    fun state(index: Int, total: Int, available: Boolean): State {
        require(index >= 0 && total > 0 && index < total)
        val heading = "Zdjęcie ${index + 1} z ${total}"
        return if (available) State(heading, "$heading · Otwórz podgląd", null)
        else State(
            heading,
            "$heading · Niedostępne na urządzeniu",
            "Oryginał zdjęcia nie jest dostępny na tym urządzeniu"
        )
    }

    fun columns(count: Int, widthDp: Int): Int =
        if (count >= 2 && widthDp >= 360) 2 else 1

    fun summary(count: Int): String {
        require(count >= 0)
        return when (count) {
            0 -> "Brak zdjęć"
            1 -> "1 zdjęcie"
            else -> "$count zdjęć"
        }
    }
}
