package com.r3gate.app

/** Polish count label used by the native Obiekty list. */
object ObjectCountLabel {
    fun format(count: Int, archived: Boolean): String {
        require(count >= 0)
        val last = count % 10
        val teens = count % 100
        val noun = when {
            count == 1 -> "obiekt"
            last in 2..4 && teens !in 12..14 -> "obiekty"
            else -> "obiektów"
        }
        return "${count} ${noun} · " +
            if (archived) "archiwum" else "aktywna lista"
    }
}
